import unittest
from unittest.mock import patch

from railway_deploy import RailwayApiError, deploy, wait_for_success


class RailwayDeployTest(unittest.TestCase):
    @patch("railway_deploy.request")
    def test_deploy_passes_exact_commit(self, api_request):
        api_request.return_value = {"serviceInstanceDeployV2": "deployment-1"}

        result = deploy("token", "environment", "service", "a" * 40)

        self.assertEqual(result, "deployment-1")
        self.assertEqual(api_request.call_args.args[2]["commitSha"], "a" * 40)

    @patch("railway_deploy.time.sleep")
    @patch("railway_deploy.request")
    def test_wait_rejects_terminal_failure(self, api_request, _sleep):
        api_request.return_value = {"deployment": {"id": "deployment-1", "status": "FAILED"}}

        with self.assertRaisesRegex(RailwayApiError, "ended as FAILED"):
            wait_for_success("token", "deployment-1", 30)


if __name__ == "__main__":
    unittest.main()
