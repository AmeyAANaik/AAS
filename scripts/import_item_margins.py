#!/usr/bin/env python3
import json
import re
import sys
import zipfile
import xml.etree.ElementTree as ET


NS = {
    "main": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "rel": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "pkgrel": "http://schemas.openxmlformats.org/package/2006/relationships",
}


def normalize_header(value):
    text = (value or "").strip().lower()
    text = text.replace("%", " percent ")
    text = re.sub(r"[^a-z0-9]+", " ", text)
    return " ".join(text.split())


def normalize_unit(value):
    return " ".join((value or "").strip().split())


def normalize_hsn(value):
    text = (value or "").strip()
    if text.startswith("'"):
        text = text[1:]
    return re.sub(r"[^A-Za-z0-9]+", "", text).upper()


def parse_decimal(value):
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    text = text.replace(",", "")
    if text.startswith("'"):
        text = text[1:]
    try:
        return float(text)
    except ValueError:
        return None


def load_shared_strings(archive):
    if "xl/sharedStrings.xml" not in archive.namelist():
        return []
    root = ET.fromstring(archive.read("xl/sharedStrings.xml"))
    values = []
    for item in root.findall("main:si", NS):
        text = "".join(node.text or "" for node in item.iterfind(".//main:t", NS))
        values.append(text)
    return values


def load_sheet_targets(archive):
    workbook = ET.fromstring(archive.read("xl/workbook.xml"))
    rels = ET.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
    rel_map = {
        rel.attrib["Id"]: rel.attrib["Target"]
        for rel in rels.findall("pkgrel:Relationship", NS)
    }
    sheets = []
    for sheet in workbook.find("main:sheets", NS):
        rid = sheet.attrib[
            "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"
        ]
        sheets.append((sheet.attrib["name"], "xl/" + rel_map[rid]))
    return sheets


def read_cell_value(cell, shared_strings):
    value = cell.find("main:v", NS)
    if value is None:
        inline = cell.find("main:is", NS)
        if inline is None:
            return ""
        return "".join(node.text or "" for node in inline.iterfind(".//main:t", NS))
    raw = value.text or ""
    if cell.attrib.get("t") == "s" and raw != "":
        return shared_strings[int(raw)]
    return raw


def column_index(cell_ref):
    letters = "".join(ch for ch in cell_ref if ch.isalpha()).upper()
    if not letters:
        return 0
    value = 0
    for ch in letters:
        value = value * 26 + (ord(ch) - ord("A") + 1)
    return value - 1


def read_sheet_rows(archive, target, shared_strings):
    root = ET.fromstring(archive.read(target))
    rows = []
    for row in root.findall(".//main:sheetData/main:row", NS):
        values = []
        for cell in row.findall("main:c", NS):
            ref = cell.attrib.get("r", "")
            index = column_index(ref)
            while len(values) <= index:
                values.append("")
            values[index] = read_cell_value(cell, shared_strings)
        rows.append(values)
    return rows


def pad_row(row, size):
    if len(row) >= size:
        return row
    return row + [""] * (size - len(row))


def detect_sheet(rows):
    best = None
    for idx, row in enumerate(rows[:25]):
        normalized = [normalize_header(value) for value in row]
        score = 0
        if any("hsn" in value and "code" in value for value in normalized):
            score += 3
        if any("margin" in value for value in normalized):
            score += 3
        if any("item" in value for value in normalized):
            score += 2
        if any("unit" in value for value in normalized):
            score += 1
        if best is None or score > best[0]:
            best = (score, idx, normalized)
    if best is None or best[0] < 5:
        return None
    return best[1], best[2]


def pick_index(headers, keywords):
    for keyword in keywords:
        for idx, header in enumerate(headers):
            if keyword in header:
                return idx
    return -1


def merge_headers(previous_headers, current_headers):
    merged = list(current_headers)
    for idx in range(max(len(previous_headers), len(current_headers))):
        prev = previous_headers[idx] if idx < len(previous_headers) else ""
        curr = current_headers[idx] if idx < len(current_headers) else ""
        chosen = curr or prev
        if idx >= len(merged):
            merged.append(chosen)
        elif not merged[idx]:
            merged[idx] = chosen
    return merged


def extract_rows(path):
    with zipfile.ZipFile(path) as archive:
        shared_strings = load_shared_strings(archive)
        for sheet_name, target in load_sheet_targets(archive):
            rows = read_sheet_rows(archive, target, shared_strings)
            detected = detect_sheet(rows)
            if detected is None:
                continue

            header_row_index, headers = detected
            if header_row_index > 0:
                previous_headers = [normalize_header(value) for value in rows[header_row_index - 1]]
                headers = merge_headers(previous_headers, headers)
                if previous_headers and "item descriptions" in previous_headers[0]:
                    while len(headers) < 1:
                        headers.append("")
                    headers[0] = previous_headers[0]
            item_idx = pick_index(headers, ["item descriptions", "item name", "item"])
            hsn_idx = pick_index(headers, ["hsn code", "hsn"])
            unit_idx = pick_index(headers, ["unit", "uom"])
            margin_idx = pick_index(headers, ["margin percent", "margin"])

            if item_idx < 0 or hsn_idx < 0 or margin_idx < 0:
                continue

            max_index = max(item_idx, hsn_idx, unit_idx, margin_idx)
            imported = []
            for row in rows[header_row_index + 1 :]:
                padded = pad_row(row, max_index + 1)
                item_name = " ".join((padded[item_idx] or "").strip().split())
                hsn_code = normalize_hsn(padded[hsn_idx])
                margin = parse_decimal(padded[margin_idx])
                unit = normalize_unit(padded[unit_idx]) if unit_idx >= 0 else ""

                if not item_name and not hsn_code:
                    continue
                if not hsn_code or margin is None:
                    continue

                imported.append(
                    {
                        "itemName": item_name,
                        "vendorHsnCode": hsn_code,
                        "measureUnit": unit or "Nos",
                        "marginPercent": margin,
                        "sourceSheet": sheet_name,
                    }
                )

            if imported:
                return {"sheet": sheet_name, "rows": imported}
    raise ValueError("Could not find an Excel sheet with item, HSN, and margin columns.")


def main():
    if len(sys.argv) != 2:
        print(json.dumps({"error": "Usage: import_item_margins.py <xlsx-path>"}))
        sys.exit(1)
    try:
        payload = extract_rows(sys.argv[1])
    except Exception as exc:
        print(json.dumps({"error": str(exc)}))
        sys.exit(1)
    print(json.dumps(payload))


if __name__ == "__main__":
    main()
