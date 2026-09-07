from __future__ import annotations

import argparse
import csv
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

SOURCE_LABEL = "Junyi via USTC mirror"


class CatalogExportError(Exception):
    pass


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


def build_catalog(rows: list[dict[str, str]]) -> dict[str, Any]:
    name_counts = Counter(row["name"] for row in rows)
    items = []
    for record_number, row in enumerate(rows, start=1):
        external_id = row["name"]
        if external_id == "":
            raise CatalogExportError(f"record {record_number}: name must not be blank")
        items.append(
            {
                "recordNumber": record_number,
                "externalId": external_id,
                "displayName": row["pretty_display_name"],
                "topic": row["topic"],
                "area": row["area"],
                "live": parse_bool(row["live"], record_number=record_number),
                "prerequisites": parse_prerequisites(row["prerequisites"]),
                "duplicateExternalId": name_counts[external_id] > 1,
            }
        )
    return {
        "schemaVersion": 1,
        "source": SOURCE_LABEL,
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


def export_catalog(input_path: Path, output_path: Path) -> dict[str, int]:
    if not input_path.exists():
        raise CatalogExportError(f"input CSV not found: {input_path}")
    rows = read_rows(input_path)
    catalog = build_catalog(rows)
    write_json_atomically(output_path, catalog)
    duplicate_rows = sum(1 for item in catalog["items"] if item["duplicateExternalId"])
    return {
        "row_count": len(rows),
        "item_count": len(catalog["items"]),
        "unique_external_ids": len({row["name"] for row in rows}),
        "duplicate_external_id_rows": duplicate_rows,
    }


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export a Junyi research Exercise catalog JSON for Java-side reads.")
    parser.add_argument("--input", required=True, type=Path, help="Path to junyi_Exercise_table.csv")
    parser.add_argument("--output", required=True, type=Path, help="Output path for junyi_catalog_v1.json")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        stats = export_catalog(args.input, args.output)
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
