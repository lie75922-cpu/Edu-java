from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import sys
import tempfile
from collections import Counter
from pathlib import Path
from typing import Any


EXPECTED_COLUMNS = [
    "name",
    "live",
    "prerequisites",
    "h_position",
    "v_position",
    "creation_date",
    "seconds_per_fast_problem",
    "pretty_display_name",
    "short_display_name",
    "topic",
    "area",
]

SCHEMA_VERSION = 2
ALLOWED_SOURCE_TYPES = {
    "PRIMARY",
    "AUTHOR_PREPROCESSED",
    "THIRD_PARTY_PROCESSED",
    "REFERENCE_CODE",
}


class CatalogExportError(Exception):
    pass


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_bool(value: str, *, record_number: int) -> bool:
    if value == "TRUE":
        return True
    if value == "FALSE":
        return False
    raise CatalogExportError(f"record {record_number}: live must be TRUE or FALSE, got {value!r}")


def parse_prerequisites(value: str) -> list[str]:
    if value == "":
        return []
    return value.split(",")


def read_rows(input_path: Path) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    with input_path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.reader(f)
        try:
            header = next(reader)
        except StopIteration as exc:
            raise CatalogExportError("input CSV is empty") from exc
        if header != EXPECTED_COLUMNS:
            raise CatalogExportError(
                "unexpected CSV header: "
                + json.dumps(header, ensure_ascii=False)
                + "; expected "
                + json.dumps(EXPECTED_COLUMNS, ensure_ascii=False)
            )

        expected_width = len(EXPECTED_COLUMNS)
        for record_number, raw in enumerate(reader, start=1):
            if len(raw) != expected_width:
                raise CatalogExportError(
                    "bad CSV row width at record "
                    f"{record_number}, physical end line {reader.line_num}: "
                    f"expected {expected_width}, got {len(raw)}; raw fields "
                    + json.dumps(raw, ensure_ascii=False)
                )
            rows.append(dict(zip(EXPECTED_COLUMNS, raw)))
    return rows


def build_catalog(
    rows: list[dict[str, str]],
    *,
    source_type: str,
    source_label: str,
    source_uri: str | None,
    source_file_name: str,
    source_sha256: str,
    acquired_at: str | None,
) -> dict[str, Any]:
    if source_type not in ALLOWED_SOURCE_TYPES:
        raise CatalogExportError(f"unsupported source type: {source_type}")
    if source_label.strip() == "":
        raise CatalogExportError("source label must not be blank")

    name_counts = Counter(row["name"] for row in rows)
    items = []
    for record_number, row in enumerate(rows, start=1):
        external_id = row["name"]
        if external_id == "":
            raise CatalogExportError(f"record {record_number}: name must not be blank")
        prerequisite_raw = row["prerequisites"]
        items.append(
            {
                "recordNumber": record_number,
                "externalId": external_id,
                "displayName": row["pretty_display_name"],
                "topic": row["topic"],
                "area": row["area"],
                "live": parse_bool(row["live"], record_number=record_number),
                "prerequisiteRaw": prerequisite_raw,
                "prerequisites": parse_prerequisites(prerequisite_raw),
                "duplicateExternalId": name_counts[external_id] > 1,
            }
        )
    return {
        "schemaVersion": SCHEMA_VERSION,
        "provenance": {
            "sourceType": source_type,
            "sourceLabel": source_label,
            "sourceUri": source_uri,
            "sourceFileName": source_file_name,
            "sourceSha256": source_sha256,
            "acquiredAt": acquired_at,
            "transformation": (
                "Research catalog projection only; rows are not deduplicated. "
                "prerequisiteRaw preserves the source cell and prerequisites is a reversible comma-token view."
            ),
        },
        "items": items,
    }


def write_json_atomically(output_path: Path, catalog: dict[str, Any]) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temp_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            "w",
            encoding="utf-8",
            newline="\n",
            dir=output_path.parent,
            prefix=output_path.name + ".",
            suffix=".tmp",
            delete=False,
        ) as f:
            temp_name = f.name
            json.dump(catalog, f, ensure_ascii=False, indent=2)
            f.write("\n")
        os.replace(temp_name, output_path)
    finally:
        if temp_name is not None:
            temp_path = Path(temp_name)
            if temp_path.exists():
                temp_path.unlink()


def export_catalog(
    input_path: Path,
    output_path: Path,
    *,
    source_type: str,
    source_label: str,
    source_uri: str | None = None,
    acquired_at: str | None = None,
) -> dict[str, int | str]:
    if not input_path.exists():
        raise CatalogExportError(f"input CSV not found: {input_path}")
    rows = read_rows(input_path)
    source_sha256 = sha256_file(input_path)
    catalog = build_catalog(
        rows,
        source_type=source_type,
        source_label=source_label,
        source_uri=source_uri,
        source_file_name=input_path.name,
        source_sha256=source_sha256,
        acquired_at=acquired_at,
    )
    write_json_atomically(output_path, catalog)
    duplicate_rows = sum(1 for item in catalog["items"] if item["duplicateExternalId"])
    return {
        "row_count": len(rows),
        "item_count": len(catalog["items"]),
        "unique_external_ids": len({row["name"] for row in rows}),
        "duplicate_external_id_rows": duplicate_rows,
        "source_sha256": source_sha256,
    }


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export a provenance-aware Junyi research Exercise catalog JSON.")
    parser.add_argument("--input", required=True, type=Path, help="Path to the audited Exercise metadata CSV")
    parser.add_argument("--output", required=True, type=Path, help="Output path for junyi_catalog_v2.json")
    parser.add_argument(
        "--source-type",
        required=True,
        choices=sorted(ALLOWED_SOURCE_TYPES),
        help="Provenance classification; use THIRD_PARTY_PROCESSED for a mirror/processed copy until proven otherwise",
    )
    parser.add_argument("--source-label", required=True, help="Human-readable source label; do not claim PRIMARY unless verified")
    parser.add_argument("--source-uri", default=None, help="Exact acquisition URL or repository/file URL when known")
    parser.add_argument("--acquired-at", default=None, help="Acquisition timestamp/date when known; otherwise omitted")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        stats = export_catalog(
            args.input,
            args.output,
            source_type=args.source_type,
            source_label=args.source_label,
            source_uri=args.source_uri,
            acquired_at=args.acquired_at,
        )
    except CatalogExportError as exc:
        print(f"junyi catalog export failed: {exc}", file=sys.stderr)
        print("output_updated=false", file=sys.stderr)
        return 1

    print("junyi catalog export complete")
    print(f"input={args.input}")
    print(f"output={args.output}")
    for key, value in stats.items():
        print(f"{key}={value}")
    print("output_updated=true")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
