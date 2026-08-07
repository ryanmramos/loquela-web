#!/usr/bin/env python3
"""Deploy one exact Git commit through Railway's project-scoped GraphQL API."""

from __future__ import annotations

import argparse
import json
import os
import re
import time
import urllib.error
import urllib.request

API_URL = "https://backboard.railway.com/graphql/v2"
TERMINAL_FAILURES = {"CRASHED", "FAILED", "REMOVED", "SKIPPED"}
SHA_PATTERN = re.compile(r"[0-9a-f]{40}")


class RailwayApiError(RuntimeError):
    pass


def request(token: str, query: str, variables: dict[str, str]) -> dict:
    body = json.dumps({"query": query, "variables": variables}).encode()
    http_request = urllib.request.Request(
        API_URL,
        data=body,
        headers={
            "Content-Type": "application/json",
            "Project-Access-Token": token,
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(http_request, timeout=30) as response:
            payload = json.load(response)
    except (urllib.error.URLError, TimeoutError) as exception:
        raise RailwayApiError(f"Railway API request failed: {exception}") from exception
    if payload.get("errors"):
        messages = "; ".join(error.get("message", "unknown GraphQL error") for error in payload["errors"])
        raise RailwayApiError(messages)
    return payload["data"]


def deploy(token: str, environment_id: str, service_id: str, commit_sha: str) -> str:
    data = request(
        token,
        """
        mutation Deploy($serviceId: String!, $environmentId: String!, $commitSha: String!) {
          serviceInstanceDeployV2(
            serviceId: $serviceId
            environmentId: $environmentId
            commitSha: $commitSha
          )
        }
        """,
        {"serviceId": service_id, "environmentId": environment_id, "commitSha": commit_sha},
    )
    deployment_id = data.get("serviceInstanceDeployV2")
    if not isinstance(deployment_id, str) or not deployment_id:
        raise RailwayApiError("Railway did not return a deployment identifier")
    return deployment_id


def wait_for_success(token: str, deployment_id: str, timeout_seconds: int) -> None:
    deadline = time.monotonic() + timeout_seconds
    previous = None
    while time.monotonic() < deadline:
        data = request(
            token,
            """
            query Deployment($id: String!) {
              deployment(id: $id) { id status }
            }
            """,
            {"id": deployment_id},
        )
        status = data["deployment"]["status"]
        if status != previous:
            print(f"Railway deployment {deployment_id}: {status}", flush=True)
            previous = status
        if status == "SUCCESS":
            return
        if status in TERMINAL_FAILURES:
            raise RailwayApiError(f"Railway deployment {deployment_id} ended as {status}")
        time.sleep(10)
    raise RailwayApiError(f"Railway deployment {deployment_id} exceeded {timeout_seconds} seconds")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--service", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--timeout", type=int, default=900)
    args = parser.parse_args()

    if not SHA_PATTERN.fullmatch(args.commit):
        parser.error("--commit must be a lowercase 40-character Git SHA")
    token = os.environ.get("RAILWAY_PROJECT_TOKEN", "")
    environment_id = os.environ.get("RAILWAY_ENVIRONMENT_ID", "")
    if not token or not environment_id:
        parser.error("RAILWAY_PROJECT_TOKEN and RAILWAY_ENVIRONMENT_ID are required")

    deployment_id = deploy(token, environment_id, args.service, args.commit)
    print(f"Triggered Railway deployment {deployment_id} for {args.commit}", flush=True)
    wait_for_success(token, deployment_id, args.timeout)


if __name__ == "__main__":
    main()
