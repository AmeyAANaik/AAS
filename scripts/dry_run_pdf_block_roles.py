#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path
from typing import Any
from urllib import error, request

OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
DEFAULT_MODEL = "qwen/qwen2.5-vl-32b-instruct"
AMOUNT_PATTERN = re.compile(r"([0-9][0-9,]*\.\d{2})")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Second-pass dry run: classify invoice blocks using layout output.")
    parser.add_argument("dry_run_outputs", nargs="+", help="Paths to stage-1 dry run output JSON files.")
    parser.add_argument("--model", default=DEFAULT_MODEL, help="OpenRouter/OpenAI-compatible model.")
    return parser.parse_args()


def read_api_key() -> str:
    env_key = os.environ.get("APP_INVOICE_TEMPLATE_GENERATOR_API_KEY", "").strip()
    if env_key:
        return env_key
    env_file = Path(".env.mw")
    if env_file.exists():
        for line in env_file.read_text().splitlines():
            if line.startswith("APP_INVOICE_TEMPLATE_GENERATOR_API_KEY="):
                return line.split("=", 1)[1].strip()
    return ""


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


def call_model(prompt: str, model: str, api_key: str) -> dict[str, Any]:
    payload = {
        "model": model,
        "temperature": 0,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": "Return strict JSON only."},
            {"role": "user", "content": prompt},
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
            body = json.loads(resp.read().decode("utf-8"))
    except error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code}: {raw}") from exc
    content = body["choices"][0]["message"]["content"]
    return {"raw": body, "content": content, "parsed": parse_json_object(content)}


def build_prompt(obj: dict[str, Any]) -> str:
    stage2 = obj["stage2_detected_layout"]
    stage3 = obj["stage3_vlm_layout_response"]["parsed"]
    payload = {
        "pdf": obj["pdf"],
        "layoutResponse": stage3,
        "candidateBlocks": [
            {
                "blockId": "table_1",
                "blockType": "table_candidate",
                "headers": stage2.get("tableHeadersFromNativeText", []),
                "sampleRows": stage2.get("sampleRowsFromNativeText", [])[:8],
                "rawPreview": stage2.get("rawTablePreview", [])[:12],
            },
            {
                "blockId": "summary_1",
                "blockType": "summary_lines_candidate",
                "lines": stage2.get("summaryLinesFromNativeText", [])[:12],
            },
        ],
    }
    return (
        "You are classifying invoice layout blocks using a prior layout-mapping response.\n\n"
        "Return exactly one valid JSON object. No markdown.\n\n"
        "Top-level keys:\n"
        "- primaryItemTableBlockId\n"
        "- gstSummaryBlockId\n"
        "- summaryFieldRoles\n"
        "- parsingHints\n"
        "- rowRules\n"
        "- fieldParsingRules\n"
        "- notes\n\n"
        "Rules:\n"
        "- choose one block id for the item table\n"
        "- choose one block id for the gst/tax summary block\n"
        "- summaryFieldRoles must contain keys: final_bill_amount, transport_charge\n"
        "- parsingHints must contain keys: preferredRateColumn, amountLabel, taxLabel, totalComputation, totalSourceLabel\n"
        "- totalComputation must be one of: max_amount, last_amount, sum_matching_labels\n"
        "- rowRules must contain keys: skipLabels, headerContinuationLabels\n"
        "- fieldParsingRules must contain keys for at least: gst, qty, rate, total\n"
        "- each fieldParsingRules value must be one of: percentage, number_with_uom, decimal_amount, integer, text\n"
        "- skipLabels should include labels that are not item rows, such as totals, taxes, transport, or round off, only when visible in the candidate blocks\n"
        "- headerContinuationLabels should include labels that appear on wrapped header-only rows and should not be parsed as items\n"
        "- if transport is not explicit, use empty string\n"
        "- do not invent blocks that are not in the payload\n\n"
        f"Payload:\n{json.dumps(payload, ensure_ascii=False)}"
    )


def extract_summary(lines: list[str], label: str, *, mode: str = "last_amount") -> str:
    if not label:
        return ""
    matches: list[str] = []
    for line in lines:
        if label.lower() in line.lower():
            matches.extend(m.group(1) for m in AMOUNT_PATTERN.finditer(line.replace('₹', '').replace('ī', '')))
    if not matches:
        return ""
    if mode == "max_amount":
        values = [float(x.replace(",", "")) for x in matches]
        return f"{max(values):.2f}" if values else ""
    if mode == "sum_matching_labels":
        values = [float(x.replace(",", "")) for x in matches]
        return f"{sum(values):.2f}" if values else ""
    return matches[-1].replace(",", "")


def extract_prioritized_total(summary_lines: list[str], roles: dict[str, Any], hints: dict[str, Any]) -> str:
    preferred_label = (roles.get("final_bill_amount") or "").strip()
    if preferred_label.lower() == "bill amount":
        values = []
        for line in summary_lines:
            if "bill amount" not in line.lower():
                continue
            values.extend(m.group(1).replace(",", "") for m in AMOUNT_PATTERN.finditer(line.replace("₹", "").replace("ī", "")))
        non_zero = [value for value in values if float(value) > 0]
        if non_zero:
            return f"{float(non_zero[-1]):.2f}"

    strategy_label = hints.get("totalSourceLabel") or preferred_label
    strategy = hints.get("totalComputation", "last_amount")
    return extract_summary(summary_lines, strategy_label, mode=strategy)


def collect_summary_candidates(stage1_output_path: Path) -> list[str]:
    layout_path = stage1_output_path.with_name("layout.txt")
    if not layout_path.exists():
        return []
    candidates = []
    for line in layout_path.read_text().splitlines():
        lowered = line.lower()
        if any(label in lowered for label in ["total", "bill amount", "transport", "rounding off", "round off", "cgst", "sgst", "igst", "taxable value"]):
            candidates.append(line.strip())
    return candidates


def filter_rows(rows: list[list[str]], row_rules: dict[str, Any]) -> list[list[str]]:
    skip_labels = [str(x).strip().lower() for x in row_rules.get("skipLabels", []) if str(x).strip()]
    header_labels = [str(x).strip().lower() for x in row_rules.get("headerContinuationLabels", []) if str(x).strip()]
    filtered: list[list[str]] = []
    for row in rows:
        joined = " ".join(str(cell) for cell in row).strip().lower()
        if not joined:
            continue
        if any(label and label in joined for label in skip_labels):
            continue
        if any(label and joined == label for label in header_labels):
            continue
        filtered.append(row)
    return filtered


def main() -> int:
    args = parse_args()
    api_key = read_api_key()
    if not api_key:
        print("Missing API key", file=sys.stderr)
        return 1

    results = []
    for path_str in args.dry_run_outputs:
        path = Path(path_str)
        obj = json.loads(path.read_text())
        llm = call_model(build_prompt(obj), args.model, api_key)
        parsed = llm["parsed"]
        summary_lines = collect_summary_candidates(path)
        if not summary_lines:
            summary_lines = obj["stage2_detected_layout"].get("summaryLinesFromNativeText", [])
        roles = parsed.get("summaryFieldRoles", {})
        hints = parsed.get("parsingHints", {})
        row_rules = parsed.get("rowRules", {})
        filtered_rows = filter_rows(obj["stage2_detected_layout"].get("sampleRowsFromNativeText", []), row_rules)
        deterministic = {
            "final_bill_amount": extract_prioritized_total(summary_lines, roles, hints),
            "transport_charge": extract_summary(summary_lines, roles.get("transport_charge", ""), mode="last_amount"),
            "filteredSampleRowCount": len(filtered_rows),
            "filteredSampleRows": filtered_rows[:8],
        }
        out = {
            "pdf": obj["pdf"],
            "model": args.model,
            "stage5_block_role_response": parsed,
            "stage6_deterministic_summary_using_roles": deterministic,
        }
        out_path = path.with_name("block_roles_output.json")
        out_path.write_text(json.dumps(out, indent=2, ensure_ascii=False))
        results.append({
            "pdf": obj["pdf"],
            "primaryItemTableBlockId": parsed.get("primaryItemTableBlockId"),
            "gstSummaryBlockId": parsed.get("gstSummaryBlockId"),
            "summaryFieldRoles": parsed.get("summaryFieldRoles"),
            "fieldParsingRules": parsed.get("fieldParsingRules"),
            "deterministicSummary": deterministic,
            "outputFile": str(out_path.resolve()),
        })
    print(json.dumps(results, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
