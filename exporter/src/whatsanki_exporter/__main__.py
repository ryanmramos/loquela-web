"""Machine-readable CLI entry point used by the Spring job worker."""

from __future__ import annotations

import json
import logging
import sys
from pathlib import Path

from .contract import ContractError, parse_snapshot

LOGGER = logging.getLogger("whatsanki_exporter")


def main(arguments: list[str] | None = None) -> int:
    arguments = list(sys.argv[1:] if arguments is None else arguments)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s %(message)s")
    if len(arguments) != 2:
        return _error("EXPORTER_USAGE_INVALID")
    input_path, output_path = map(Path, arguments)
    try:
        raw = json.loads(input_path.read_text(encoding="utf-8"))
        snapshot = parse_snapshot(raw)
        # Import lazily so malformed/untrusted input is rejected before genanki or
        # SQLite performs any package work.
        from .export import export_package
        byte_size, checksum = export_package(snapshot, output_path)
        print(json.dumps({
            "status": "ok",
            "artifactPath": str(output_path),
            "sha256": checksum,
            "byteSize": byte_size,
            "errorCode": None,
        }, separators=(",", ":")))
        LOGGER.info("Export completed cardCount=%d byteCount=%d", len(snapshot.cards), byte_size)
        return 0
    except (ContractError, json.JSONDecodeError, UnicodeDecodeError) as error:
        LOGGER.warning("Rejected invalid export snapshot errorType=%s", type(error).__name__)
        return _error("EXPORT_SNAPSHOT_INVALID")
    except OSError as error:
        LOGGER.error("Exporter file operation failed errorType=%s", type(error).__name__)
        return _error("EXPORTER_WRITE_FAILED")
    except Exception as error:  # genanki/SQLite failures are kept out of stdout.
        LOGGER.error("Exporter failed errorType=%s", type(error).__name__)
        return _error("EXPORTER_INTERNAL_ERROR")


def _error(code: str) -> int:
    print(json.dumps({"status": "error", "errorCode": code}, separators=(",", ":")))
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
