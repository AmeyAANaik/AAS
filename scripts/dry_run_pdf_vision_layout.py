#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import json
import os
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib import error, request


OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
DEFAULT_MODEL = "qwen/qwen-2.5-vl-7b-instruct"

MULTISPACE = re.compile(r"\s{2,}")
SUMMARY_LABEL = re.compile(r"(?i)\b(total|grand total|bill amount|transport|rounding off|round off|cgst|sgst|igst|taxable value)\b")
AMOUNT_PATTERN = re.compile(r"([0-9][0-9,]*\.\d{2})")
ISO_DATE_PATTERN = re.compile(r"\b(\d{4}-\d{2}-\d{2})\b")
DMY_DASH_PATTERN = re.compile(r"\b(\d{1,2}-[A-Za-z]{3}-\d{2,4})\b")


@dataclass
class LayoutMapping:
    table_headers: list[str]
    item_field_mapping: dict[str, str]
    summary_field_mapping: dict[str, str]
    notes: list[str]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Dry-run visual PDF layout mapping flow via OpenRouter-compatible VLM.")
    parser.add_argument("pdfs", nargs="+", help="PDF files to test.")
    parser.add_argument("--model", default=DEFAULT_MODEL, help="OpenRouter/OpenAI-compatible vision model.")
    parser.add_argument("--out-dir", default="temp/vision_layout_dry_run", help="Output directory.")
    return parser.parse_args()


def read_generator_api_key() -> str:
    env_key = os.environ.get("APP_INVOICE_TEMPLATE_GENERATOR_API_KEY", "").strip()
    if env_key:
        return env_key
    env_file = Path(".env.mw")
    if env_file.exists():
        for line in env_file.read_text().splitlines():
            if line.startswith("APP_INVOICE_TEMPLATE_GENERATOR_API_KEY="):
                return line.split("=", 1)[1].strip()
    return ""


def run(cmd: list[str], *, cwd: str | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, cwd=cwd, check=True, capture_output=True, text=True)


def render_pdf_pages(pdf_path: Path, out_dir: Path) -> list[Path]:
    prefix = out_dir / "page"
    cmd = [
        "pdftoppm",
        "-png",
        "-r",
        "180",
        str(pdf_path),
        str(prefix),
    ]
    run(cmd)
    return sorted(out_dir.glob("page-*.png"))


def extract_layout_text(pdf_path: Path, out_file: Path) -> str:
    cmd = ["pdftotext", "-layout", str(pdf_path), str(out_file)]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0 and not out_file.exists():
        raise RuntimeError(proc.stderr or proc.stdout or "pdftotext failed")
    return out_file.read_text() if out_file.exists() else ""


def build_data_url(image_path: Path) -> str:
    payload = base64.b64encode(image_path.read_bytes()).decode("ascii")
    return f"data:image/png;base64,{payload}"


def call_vision_model(pdf_name: str, image_paths: list[Path], model: str, api_key: str) -> dict[str, Any]:
    content: list[dict[str, Any]] = [
        {
            "type": "text",
            "text": (
                "You are analyzing an invoice PDF visually. "
                "Return exactly one valid JSON object. No markdown.\n\n"
                "Required top-level keys:\n"
                "- tableHeaders\n"
                "- itemFieldMapping\n"
                "- summaryFieldMapping\n"
                "- notes\n\n"
                "Rules:\n"
                "- Infer the line-item table header labels as they visually appear.\n"
                "- itemFieldMapping keys must be: item_name, item_id, qty, uom, rate, mrp, gst, total\n"
                "- summaryFieldMapping keys must be: final_bill_amount, transport_charge\n"
                "- Use an empty string for any missing mapping.\n"
                "- Do not extract all rows. Only identify layout and mapping.\n"
                "- For Tally-style invoices, use the actual invoice number and not e-Way Bill labels.\n"
                "- Prefer the invoice total amount, not tax subtotal amounts.\n\n"
                f"PDF name: {pdf_name}"
            ),
        }
    ]
    for image_path in image_paths:
        content.append({
            "type": "image_url",
            "image_url": {"url": build_data_url(image_path)},
        })

    payload = {
        "model": model,
        "temperature": 0,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": "Return strict JSON only."},
            {"role": "user", "content": content},
        ],
    }
    req = request.Request(
        OPENROUTER_BASE_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "HTTP-Referer": "http://localhost:4200",
            "X-Title": "AAS Ops Console Dry Run",
        },
        method="POST",
    )
    try:
        with request.urlopen(req, timeout=180) as resp:
            raw = resp.read().decode("utf-8")
    except error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Vision model HTTP {exc.code}: {body}") from exc
    body = json.loads(raw)
    message = body["choices"][0]["message"]["content"]
    return {
        "rawResponse": body,
        "content": message,
        "parsed": parse_json_object(message),
    }


def parse_json_object(text: str) -> dict[str, Any]:
    stripped = text.strip()
    if stripped.startswith("```"):
        stripped = re.sub(r"^```(?:json)?\s*", "", stripped)
        stripped = re.sub(r"\s*```$", "", stripped)
    try:
        return json.loads(stripped)
    except json.JSONDecodeError:
        start = stripped.find("{")
        end = stripped.rfind("}")
        if start >= 0 and end > start:
            return json.loads(stripped[start:end + 1])
        raise


def normalize_mapping(payload: dict[str, Any]) -> LayoutMapping:
    def as_text(value: Any) -> str:
        return "" if value is None else str(value).strip()

    headers = [as_text(x) for x in payload.get("tableHeaders", []) if as_text(x)]
    item_mapping = {k: as_text(v) for k, v in dict(payload.get("itemFieldMapping", {})).items()}
    summary_mapping = {k: as_text(v) for k, v in dict(payload.get("summaryFieldMapping", {})).items()}
    notes = [as_text(x) for x in payload.get("notes", []) if as_text(x)]
    return LayoutMapping(headers, item_mapping, summary_mapping, notes)


def normalize_layout_text(text: str) -> str:
    return text.replace("\f", "\n").strip()


def normalized_non_empty_lines(text: str) -> list[str]:
    return [line.strip() for line in text.splitlines() if line.strip()]


def looks_like_header(line: str) -> bool:
    lowered = line.lower()
    return ("description of goods" in lowered or "item description" in lowered or "particulars" in lowered) and (
        "hsn" in lowered or "sac" in lowered
    ) and ("qty" in lowered or "quantity" in lowered)


def parse_cells(line: str) -> list[str]:
    normalized = line.replace("\t", " ").strip()
    if "|" in normalized:
        return [cell.strip() for cell in normalized.split("|")]
    return [cell.strip() for cell in MULTISPACE.split(normalized) if cell.strip()]


def detect_table(lines: list[str]) -> tuple[list[str], list[list[str]], list[str]]:
    for idx, line in enumerate(lines):
        if not looks_like_header(line):
            continue
        headers = parse_cells(line)
        raw_lines = [line]
        rows: list[list[str]] = []
        for cursor in range(idx + 1, len(lines)):
            raw = lines[cursor]
            if not raw.strip():
                if rows:
                    break
                continue
            if looks_like_header(raw) and rows:
                break
            if SUMMARY_LABEL.search(raw) and rows:
                break
            cells = parse_cells(raw)
            if len(cells) < max(3, min(len(headers), 4)):
                if rows:
                    break
                continue
            raw_lines.append(raw)
            rows.append(cells)
        if rows:
            return headers, rows, raw_lines
    return [], [], []


def normalize_header(text: str) -> str:
    return re.sub(r"[^A-Za-z0-9]+", " ", text).strip().lower()


def align_row_cells(headers: list[str], cells: list[str]) -> list[str]:
    aligned = list(cells)
    if len(aligned) >= len(headers):
        return aligned
    if headers[:2] and len(headers) >= 2 and normalize_header(headers[0]) == "sl" and normalize_header(headers[1]) == "description of goods":
        m = re.match(r"^(\d+)\s+(.*)$", aligned[0])
        if m:
            aligned[0] = m.group(1).strip()
            aligned.insert(1, m.group(2).strip())
    gst_idx = next((i for i, h in enumerate(headers) if normalize_header(h) == "gst"), -1)
    qty_idx = next((i for i, h in enumerate(headers) if normalize_header(h) == "quantity"), -1)
    if gst_idx >= 0 and qty_idx == gst_idx + 1 and gst_idx < len(aligned) and len(aligned) < len(headers):
        m = re.match(r"^([0-9]+(?:\.[0-9]+)?\s*%?)\s+([0-9][0-9,]*\.?[0-9]*\s+[A-Za-z]+.*)$", aligned[gst_idx])
        if m:
            aligned[gst_idx] = m.group(1).strip()
            aligned.insert(gst_idx + 1, m.group(2).strip())
    return aligned


def parse_amount(raw: str) -> float | None:
    text = raw.replace("%", "").replace(",", "").replace("₹", "").replace("ī", "").strip()
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def parse_qty_uom(qty_text: str, uom_text: str) -> tuple[float | None, str | None]:
    qty_text = qty_text.strip()
    explicit_uom = uom_text.strip() or None
    if not qty_text:
        return None, explicit_uom
    parts = qty_text.replace(",", " ").split()
    if len(parts) >= 2:
        qty = parse_amount(parts[0])
        uom = explicit_uom or parts[1]
        return qty, uom
    return parse_amount(qty_text), explicit_uom


def find_value_by_loose_header(headers: list[str], cells: list[str], header: str) -> str:
    target = normalize_header(header)
    for idx, hdr in enumerate(headers):
        if idx >= len(cells):
            continue
        normalized = normalize_header(hdr)
        if normalized == target or normalized in target or target in normalized:
            return cells[idx]
    return ""


def map_rows(headers: list[str], rows: list[list[str]], mapping: LayoutMapping) -> list[dict[str, Any]]:
    indexes = {normalize_header(h): idx for idx, h in enumerate(headers)}
    parsed_rows = []
    for row in rows:
        aligned = align_row_cells(headers, row)
        source_values: dict[str, str] = {}
        for target_field, source_label in mapping.item_field_mapping.items():
            if not source_label:
                continue
            idx = indexes.get(normalize_header(source_label))
            value = aligned[idx] if idx is not None and idx < len(aligned) else find_value_by_loose_header(headers, aligned, source_label)
            source_values[target_field] = value
        qty, uom = parse_qty_uom(source_values.get("qty", ""), source_values.get("uom", ""))
        item = {
            "item_name": (source_values.get("item_name") or "").strip(),
            "item_id": (source_values.get("item_id") or "").strip(),
            "qty": qty,
            "uom": uom,
            "rate": parse_amount(source_values.get("rate", "")),
            "mrp": parse_amount(source_values.get("mrp", "")),
            "gst": parse_amount(source_values.get("gst", "")),
            "total": parse_amount(source_values.get("total", "")),
            "sourceValues": source_values,
        }
        if item["item_name"] and item["qty"] is not None and item["rate"] is not None and item["total"] is not None:
            parsed_rows.append(item)
    return parsed_rows


def detect_summary_lines(lines: list[str]) -> list[str]:
    return [line for line in lines if SUMMARY_LABEL.search(line)]


def extract_summary_amount(lines: list[str], label: str, *, prefer_max: bool = False) -> str:
    if not label.strip():
        return ""
    matches: list[str] = []
    for line in lines:
        if label.lower() in line.lower():
            matches.extend(m.group(1) for m in AMOUNT_PATTERN.finditer(line.replace("₹", "").replace("ī", "")))
    if not matches:
        return ""
    if prefer_max:
        amounts = [parse_amount(x) for x in matches]
        amounts = [x for x in amounts if x is not None]
        return f"{max(amounts):.2f}" if amounts else ""
    return matches[-1].replace(",", "")


def prefix_before_date(line: str) -> str:
    for pattern in (ISO_DATE_PATTERN, DMY_DASH_PATTERN):
        m = pattern.search(line)
        if m:
            return line[: m.start()]
    return ""


def last_invoice_like_token(text: str) -> str:
    candidate = ""
    for match in re.finditer(r"\b([A-Za-z]*\d[A-Za-z0-9\-/]*)\b", text):
        token = match.group(1).strip()
        lowered = token.lower()
        if "way" in lowered:
            continue
        if re.fullmatch(r"\d{1,6}", token) or "/" in token or "-" in token:
            candidate = token
    return candidate


def contextual_invoice_number(lines: list[str]) -> str:
    for idx, line in enumerate(lines):
        lowered = line.lower()
        if "invoice no" not in lowered:
            continue
        if idx + 1 < len(lines):
            candidate = last_invoice_like_token(prefix_before_date(lines[idx + 1]))
            if candidate:
                return candidate
        current = last_invoice_like_token(prefix_before_date(line)) or last_invoice_like_token(line)
        if current and current.lower() != "e-way":
            return current
    return ""


def contextual_invoice_date(lines: list[str]) -> str:
    for idx, line in enumerate(lines):
        lowered = line.lower()
        if "dated" not in lowered and "invoice date" not in lowered and "date" not in lowered:
            continue
        for candidate_line in ([lines[idx + 1]] if idx + 1 < len(lines) else []) + [line]:
            iso = ISO_DATE_PATTERN.search(candidate_line)
            if iso:
                return iso.group(1)
            dmy = DMY_DASH_PATTERN.search(candidate_line)
            if dmy:
                return dmy.group(1)
    return ""


def process_pdf(pdf_path: Path, model: str, api_key: str, out_dir: Path) -> dict[str, Any]:
    pdf_out_dir = out_dir / pdf_path.stem
    pdf_out_dir.mkdir(parents=True, exist_ok=True)
    image_dir = pdf_out_dir / "images"
    image_dir.mkdir(exist_ok=True)

    pages = render_pdf_pages(pdf_path, image_dir)
    selected_pages = [pages[0]]
    if len(pages) > 1:
        selected_pages.append(pages[-1])

    layout_txt_path = pdf_out_dir / "layout.txt"
    layout_text = normalize_layout_text(extract_layout_text(pdf_path, layout_txt_path))
    lines = normalized_non_empty_lines(layout_text)
    headers, rows, raw_table_lines = detect_table(lines)
    summary_lines = detect_summary_lines(lines)

    vision_response = call_vision_model(pdf_path.name, selected_pages, model, api_key)
    mapping = normalize_mapping(vision_response["parsed"])

    parsed_rows = map_rows(headers, rows, mapping)
    invoice_number = contextual_invoice_number(lines)
    invoice_date = contextual_invoice_date(lines)
    bill_amount = extract_summary_amount(summary_lines, mapping.summary_field_mapping.get("final_bill_amount", "Total"), prefer_max=True)
    transport = extract_summary_amount(summary_lines, mapping.summary_field_mapping.get("transport_charge", "Transport"))

    stage_payload = {
        "pdf": str(pdf_path),
        "model": model,
        "stage1_rendered_pages": [str(p) for p in pages],
        "stage1_selected_pages_for_vlm": [str(p) for p in selected_pages],
        "stage2_detected_layout": {
            "tableHeadersFromNativeText": headers,
            "sampleRowsFromNativeText": rows[:8],
            "summaryLinesFromNativeText": summary_lines[:10],
            "rawTablePreview": raw_table_lines[:12],
        },
        "stage3_vlm_layout_response": {
            "parsed": vision_response["parsed"],
            "rawContent": vision_response["content"],
        },
        "stage4_deterministic_parse_using_vlm_layout": {
            "invoiceNumber": invoice_number,
            "invoiceDate": invoice_date,
            "billAmount": bill_amount,
            "transportCharge": transport,
            "previewItemsCount": len(parsed_rows),
            "previewItems": parsed_rows[:8],
        },
    }
    (pdf_out_dir / "dry_run_output.json").write_text(json.dumps(stage_payload, indent=2))
    return stage_payload


def main() -> int:
    args = parse_args()
    api_key = read_generator_api_key()
    if not api_key:
        print("Missing APP_INVOICE_TEMPLATE_GENERATOR_API_KEY", file=sys.stderr)
        return 1

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    summary = []
    for pdf in args.pdfs:
        pdf_path = Path(pdf).expanduser().resolve()
        result = process_pdf(pdf_path, args.model, api_key, out_dir)
        summary.append({
            "pdf": str(pdf_path),
            "model": args.model,
            "invoiceNumber": result["stage4_deterministic_parse_using_vlm_layout"]["invoiceNumber"],
            "invoiceDate": result["stage4_deterministic_parse_using_vlm_layout"]["invoiceDate"],
            "billAmount": result["stage4_deterministic_parse_using_vlm_layout"]["billAmount"],
            "transportCharge": result["stage4_deterministic_parse_using_vlm_layout"]["transportCharge"],
            "previewItemsCount": result["stage4_deterministic_parse_using_vlm_layout"]["previewItemsCount"],
            "outputFile": str((out_dir / Path(pdf).stem / "dry_run_output.json").resolve()),
        })
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
