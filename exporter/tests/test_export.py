import sqlite3
import tempfile
import unittest
import zipfile
from pathlib import Path
from uuid import UUID

from test_contract import valid_snapshot
from whatsanki_exporter.contract import parse_snapshot
from whatsanki_exporter.export import export_package, stable_guid, stable_integer


class ExportPackageTest(unittest.TestCase):
    def test_writes_readable_apkg_and_escapes_hostile_unicode_fields(self):
        source = valid_snapshot()
        source["cards"][0]["anki"]["fields"]["ContextBefore"] = '<script>alert("x")</script> café '
        snapshot = parse_snapshot(source)

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory, "deck.apkg")
            byte_size, checksum = export_package(snapshot, output)

            self.assertGreater(byte_size, 0)
            self.assertEqual(64, len(checksum))
            with zipfile.ZipFile(output) as archive:
                self.assertIn("collection.anki2", archive.namelist())
                collection = Path(directory, "collection.anki2")
                collection.write_bytes(archive.read("collection.anki2"))
            with sqlite3.connect(collection) as database:
                fields = database.execute("select flds from notes").fetchone()[0]
                self.assertIn("&lt;script&gt;", fields)
                self.assertNotIn("<script>", fields)
                self.assertIn("café", fields)

    def test_stable_ids_do_not_depend_on_python_hash_randomization(self):
        value = UUID("7bdd1bda-2bd1-40db-b85d-7331d5653b37")

        self.assertEqual(stable_integer(value, bits=31), stable_integer(value, bits=31))
        self.assertEqual(stable_guid(value), stable_guid(value))
        self.assertGreater(stable_integer(value, bits=31), 0)


if __name__ == "__main__":
    unittest.main()
