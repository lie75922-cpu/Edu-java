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


def export(source: Path, output: Path):
    return export_junyi_catalog.export_catalog(
        source,
        output,
        source_type="THIRD_PARTY_PROCESSED",
        source_label="Junyi metadata mirror under provenance review",
        source_uri="https://example.invalid/source-for-test",
        acquired_at="2026-09-07T00:00:00Z",
    )


class JunyiCatalogExportTests(unittest.TestCase):
    def test_export_catalog_contract_provenance_and_duplicate_flags(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "junyi_Exercise_table.csv"
            output = tmp_path / "junyi_catalog_v2.json"
            write_csv(
                source,
                'a,TRUE,"p1,p",1,2,date,3,顯示A,短A,topic-a,area-a\n'
                "b,FALSE,p2,p2,2,date,4,顯示B,短B,N/A,N/A\n"
                'a,FALSE,"p1,p1",3,4,date,5,顯示A2,短A2,topic-a,area-a\n',
            )

            stats = export(source, output)
            data = json.loads(output.read_text(encoding="utf-8"))
            expected_sha = export_junyi_catalog.sha256_file(source)

        self.assertEqual(list(data.keys()), ["schemaVersion", "provenance", "items"])
        self.assertEqual(data["schemaVersion"], 2)
        self.assertEqual(data["provenance"]["sourceType"], "THIRD_PARTY_PROCESSED")
        self.assertEqual(data["provenance"]["sourceFileName"], "junyi_Exercise_table.csv")
        self.assertEqual(data["provenance"]["sourceSha256"], expected_sha)
        self.assertEqual(
            stats,
            {
                "row_count": 3,
                "item_count": 3,
                "unique_external_ids": 2,
                "duplicate_external_id_rows": 2,
                "source_sha256": expected_sha,
            },
        )
        self.assertEqual(data["items"][0]["prerequisiteRaw"], "p1,p")
        self.assertEqual(data["items"][0]["prerequisites"], ["p1", "p"])
        self.assertTrue(data["items"][0]["duplicateExternalId"])
        self.assertEqual(data["items"][1]["topic"], "N/A")
        self.assertEqual(data["items"][1]["area"], "N/A")
        self.assertFalse(data["items"][1]["duplicateExternalId"])

    def test_export_preserves_duplicate_prerequisite_tokens_and_raw_value(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "junyi_Exercise_table.csv"
            output = tmp_path / "junyi_catalog_v2.json"
            write_csv(source, 'x,TRUE,"p1,p1,p2",1,2,date,3,顯示X,短X,topic,area\n')

            export(source, output)
            data = json.loads(output.read_text(encoding="utf-8"))

        self.assertEqual(data["items"][0]["prerequisiteRaw"], "p1,p1,p2")
        self.assertEqual(data["items"][0]["prerequisites"], ["p1", "p1", "p2"])

    def test_bad_row_width_does_not_overwrite_existing_output(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "junyi_Exercise_table.csv"
            output = tmp_path / "junyi_catalog_v2.json"
            original = '{"schemaVersion":2,"provenance":{},"items":[]}\n'
            output.write_text(original, encoding="utf-8", newline="\n")
            write_csv(source, "bad,TRUE,p,1,2,date,3,顯示,短,topic,area,EXTRA\n")

            exit_code = export_junyi_catalog.main([
                "--input", str(source),
                "--output", str(output),
                "--source-type", "THIRD_PARTY_PROCESSED",
                "--source-label", "test",
            ])

            self.assertEqual(exit_code, 1)
            self.assertEqual(output.read_text(encoding="utf-8"), original)

    def test_bad_header_does_not_overwrite_existing_output(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "junyi_Exercise_table.csv"
            output = tmp_path / "junyi_catalog_v2.json"
            original = '{"schemaVersion":2,"provenance":{},"items":[]}\n'
            output.write_text(original, encoding="utf-8", newline="\n")
            source.write_text("wrong,live\nx,TRUE\n", encoding="utf-8", newline="\n")

            exit_code = export_junyi_catalog.main([
                "--input", str(source),
                "--output", str(output),
                "--source-type", "THIRD_PARTY_PROCESSED",
                "--source-label", "test",
            ])

            self.assertEqual(exit_code, 1)
            self.assertEqual(output.read_text(encoding="utf-8"), original)

    def test_source_type_must_be_explicit_and_supported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "junyi_Exercise_table.csv"
            output = tmp_path / "junyi_catalog_v2.json"
            write_csv(source, "x,TRUE,,1,2,date,3,顯示,短,topic,area\n")

            with self.assertRaises(export_junyi_catalog.CatalogExportError):
                export_junyi_catalog.export_catalog(
                    source,
                    output,
                    source_type="MIRROR",
                    source_label="ambiguous",
                )


if __name__ == "__main__":
    unittest.main()
