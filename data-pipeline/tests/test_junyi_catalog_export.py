from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "export_junyi_catalog.py"
spec = importlib.util.spec_from_file_location("export_junyi_catalog", SCRIPT_PATH)
export_junyi_catalog = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules["export_junyi_catalog"] = export_junyi_catalog
spec.loader.exec_module(export_junyi_catalog)


HEADER = (
    "name,live,prerequisites,h_position,v_position,creation_date,"
    "seconds_per_fast_problem,pretty_display_name,short_display_name,topic,area\n"
)


def write_csv(path: Path, body: str) -> None:
    path.write_text(HEADER + body, encoding="utf-8", newline="\n")


class JunyiCatalogExportTests(unittest.TestCase):
    def test_export_catalog_contract_and_duplicate_flags(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "junyi_Exercise_table.csv"
            output = tmp_path / "junyi_catalog_v1.json"
            write_csv(
                source,
                'a,TRUE,"p1,p",1,2,date,3,顯示A,短A,topic-a,area-a\n'
                "b,FALSE,p2,p2,2,date,4,顯示B,短B,N/A,N/A\n"
                'a,FALSE,"p1,p1",3,4,date,5,顯示A2,短A2,topic-a,area-a\n',
            )

            stats = export_junyi_catalog.export_catalog(source, output)

            data = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(list(data.keys()), ["schemaVersion", "source", "items"])
        self.assertEqual(data["schemaVersion"], 1)
        self.assertEqual(data["source"], "Junyi via USTC mirror")
        self.assertEqual(
            stats,
            {
                "row_count": 3,
                "item_count": 3,
                "unique_external_ids": 2,
                "duplicate_external_id_rows": 2,
            },
        )
        self.assertEqual(
            data["items"][0],
            {
                "recordNumber": 1,
                "externalId": "a",
                "displayName": "顯示A",
                "topic": "topic-a",
                "area": "area-a",
                "live": True,
                "prerequisites": ["p1", "p"],
                "duplicateExternalId": True,
            },
        )
        self.assertEqual(data["items"][1]["topic"], "N/A")
        self.assertEqual(data["items"][1]["area"], "N/A")
        self.assertIs(data["items"][1]["duplicateExternalId"], False)

    def test_export_preserves_duplicate_prerequisite_tokens(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "junyi_Exercise_table.csv"
            output = tmp_path / "junyi_catalog_v1.json"
            write_csv(source, 'x,TRUE,"p1,p1,p2",1,2,date,3,顯示X,短X,topic,area\n')

            export_junyi_catalog.export_catalog(source, output)

            data = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(data["items"][0]["prerequisites"], ["p1", "p1", "p2"])

    def test_bad_row_width_does_not_overwrite_existing_output(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "junyi_Exercise_table.csv"
            output = tmp_path / "junyi_catalog_v1.json"
            original = '{"schemaVersion":1,"source":"old","items":[]}\n'
            output.write_text(original, encoding="utf-8", newline="\n")
            write_csv(source, "bad,TRUE,p,1,2,date,3,顯示,短,topic,area,EXTRA\n")

            exit_code = export_junyi_catalog.main(["--input", str(source), "--output", str(output)])

            self.assertEqual(exit_code, 1)
            self.assertEqual(output.read_text(encoding="utf-8"), original)

    def test_bad_header_does_not_overwrite_existing_output(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "junyi_Exercise_table.csv"
            output = tmp_path / "junyi_catalog_v1.json"
            original = '{"schemaVersion":1,"source":"old","items":[]}\n'
            output.write_text(original, encoding="utf-8", newline="\n")
            source.write_text("wrong,live\nx,TRUE\n", encoding="utf-8", newline="\n")

            exit_code = export_junyi_catalog.main(["--input", str(source), "--output", str(output)])

            self.assertEqual(exit_code, 1)
            self.assertEqual(output.read_text(encoding="utf-8"), original)


if __name__ == "__main__":
    unittest.main()
