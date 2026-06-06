#!/usr/bin/env python3
"""One-time hard-delete cleanup for duplicate Grocery items.

The runner preserves tuned margins, hard-deletes default 7% duplicates when
possible, repoints editable draft/open references to the kept item, and disables
items that ERPNext refuses to hard-delete because of historical references.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict
from http.cookiejar import CookieJar
from pathlib import Path
from typing import Any


DEFAULT_MW_BASE = "https://3.111.114.218"
DEFAULT_ERP_BASE = "http://3.111.114.218:8080"
DEFAULT_USER = "Administrator"
DEFAULT_PASSWORD = "admin"
DEFAULT_OUTPUT_DIR = Path("tmp/grocery_duplicate_cleanup")
DEFAULT_MARGIN = 7.0
REFERENCE_DOCTYPES = (
    ("Sales Order Item", "Sales Order"),
    ("Purchase Order Item", "Purchase Order"),
    ("Sales Invoice Item", "Sales Invoice"),
    ("Purchase Invoice Item", "Purchase Invoice"),
)


class HttpClient:
    def __init__(self, base_url: str, *, insecure_ssl: bool = False):
        self.base_url = base_url.rstrip("/")
        self.cookie_jar = CookieJar()
        handlers: list[urllib.request.BaseHandler] = [urllib.request.HTTPCookieProcessor(self.cookie_jar)]
        if insecure_ssl and self.base_url.startswith("https://"):
            handlers.append(urllib.request.HTTPSHandler(context=ssl._create_unverified_context()))
        self.opener = urllib.request.build_opener(*handlers)
        self.default_headers: dict[str, str] = {}

    def request(
        self,
        method: str,
        path: str,
        *,
        data: Any = None,
        headers: dict[str, str] | None = None,
        timeout: int = 30,
    ) -> Any:
        body = None
        request_headers = dict(self.default_headers)
        if headers:
            request_headers.update(headers)
        if data is not None:
            if isinstance(data, bytes):
                body = data
            elif isinstance(data, str):
                body = data.encode("utf-8")
            else:
                body = json.dumps(data).encode("utf-8")
                request_headers.setdefault("Content-Type", "application/json")
        req = urllib.request.Request(
            self.base_url + path,
            data=body,
            headers=request_headers,
            method=method,
        )
        try:
            with self.opener.open(req, timeout=timeout) as response:
                raw = response.read()
                if not raw:
                    return {}
                text = raw.decode("utf-8", errors="replace")
                try:
                    return json.loads(text)
                except json.JSONDecodeError:
                    return {"raw": text}
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {path} failed: HTTP {exc.code}: {raw}") from exc

    def get(self, path: str, params: dict[str, Any] | None = None) -> Any:
        query = ""
        if params:
            query = "?" + urllib.parse.urlencode(params)
        return self.request("GET", path + query)

    def post_form(self, path: str, fields: dict[str, str]) -> Any:
        body = urllib.parse.urlencode(fields).encode("utf-8")
        return self.request("POST", path, data=body, headers={"Content-Type": "application/x-www-form-urlencoded"})

    def put(self, path: str, payload: dict[str, Any]) -> Any:
        return self.request("PUT", path, data=payload)

    def delete(self, path: str) -> Any:
        return self.request("DELETE", path)


def as_float(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def safe_name(value: str) -> str:
    return urllib.parse.quote(value, safe="")


def list_live_items(mw: HttpClient) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    page = 1
    size = 200
    total = None
    while True:
        response = mw.get("/api/items/paged", {"page": page, "size": size})
        batch = response.get("items") or []
        items.extend(batch)
        total = int(response.get("total") or len(items))
        if not batch or len(items) >= total:
            break
        page += 1
    return items


def build_manifest(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    grocery = [item for item in items if str(item.get("item_group") or "") == "Grocery"]
    groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for item in grocery:
        name = str(item.get("item_name") or "").strip()
        if name:
            groups[name].append(item)

    manifest: list[dict[str, Any]] = []
    for item_name, rows in sorted(groups.items(), key=lambda entry: entry[0].lower()):
        if len(rows) <= 1:
            continue
        if not any(as_float(row.get("aas_margin_percent")) == DEFAULT_MARGIN for row in rows):
            continue

        non_default = [row for row in rows if as_float(row.get("aas_margin_percent")) != DEFAULT_MARGIN]
        if non_default:
            keep_rows = sorted(non_default, key=lambda row: str(row.get("item_code") or row.get("name") or ""))
            delete_rows = [row for row in rows if as_float(row.get("aas_margin_percent")) == DEFAULT_MARGIN]
            reason = "mixed-margin-delete-default-7"
        else:
            keep_row = sorted(
                rows,
                key=lambda row: (
                    "_GROCERY_" not in str(row.get("item_code") or row.get("name") or ""),
                    str(row.get("item_code") or row.get("name") or ""),
                ),
            )[0]
            keep_rows = [keep_row]
            keep_code = item_code(keep_row)
            delete_rows = [row for row in rows if item_code(row) != keep_code]
            reason = "all-7-keep-one"

        keep_codes = [item_code(row) for row in keep_rows]
        primary_keep_code = keep_codes[0]
        for row in sorted(delete_rows, key=item_code):
            manifest.append(
                {
                    "item_name": item_name,
                    "duplicate_count": len(rows),
                    "reason": reason,
                    "keep_item_codes": keep_codes,
                    "keep_item_code": primary_keep_code,
                    "delete_item_code": item_code(row),
                    "delete_margin": as_float(row.get("aas_margin_percent")),
                    "delete_vendor": str(row.get("aas_vendor") or ""),
                    "delete_hsn": str(row.get("aas_vendor_hsn_code") or ""),
                    "all_margins": [as_float(r.get("aas_margin_percent")) for r in rows],
                }
            )
    return manifest


def item_code(row: dict[str, Any]) -> str:
    return str(row.get("item_code") or row.get("name") or "").strip()


def erp_login(erp: HttpClient, username: str, password: str) -> None:
    erp.post_form("/api/method/login", {"usr": username, "pwd": password})


def mw_login(mw: HttpClient, username: str, password: str) -> None:
    response = mw.request(
        "POST",
        "/api/auth/login",
        data={"username": username, "password": password},
        headers={"Content-Type": "application/json"},
    )
    token = response.get("accessToken")
    if not token:
        raise RuntimeError("MW login failed: no accessToken returned")
    mw.default_headers["Authorization"] = f"Bearer {token}"


def list_references(erp: HttpClient, item: str) -> list[dict[str, Any]]:
    refs: list[dict[str, Any]] = []
    for child_doctype, parent_doctype in REFERENCE_DOCTYPES:
        fields = json.dumps(["name", "docstatus"])
        filters = json.dumps([[child_doctype, "item_code", "=", item]])
        response = erp.get(
            f"/api/resource/{safe_name(parent_doctype)}",
            {
                "fields": fields,
                "filters": filters,
                "limit_page_length": 1000,
            },
        )
        for row in response.get("data") or []:
            refs.append(
                {
                    "child_doctype": child_doctype,
                    "parent_doctype": parent_doctype,
                    "child_name": "",
                    "parent": row.get("name"),
                    "parentfield": "items",
                    "docstatus": int(as_float(row.get("docstatus"))),
                }
            )
    return refs


def repoint_editable_references(erp: HttpClient, delete_code: str, keep_code: str, refs: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    changed: list[dict[str, Any]] = []
    failures: list[dict[str, Any]] = []
    parents: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for ref in refs:
        if ref["docstatus"] == 0 and ref.get("parent"):
            parents[(ref["parent_doctype"], ref["parent"])].append(ref)

    for (doctype, parent), parent_refs in parents.items():
        try:
            doc_response = erp.get(f"/api/resource/{safe_name(doctype)}/{safe_name(parent)}")
            doc = doc_response.get("data") or {}
            if int(as_float(doc.get("docstatus"))) != 0:
                continue
            items = doc.get("items")
            if not isinstance(items, list):
                continue
            updated = False
            for row in items:
                if isinstance(row, dict) and str(row.get("item_code") or "") == delete_code:
                    row["item_code"] = keep_code
                    updated = True
            if not updated:
                continue
            erp.put(f"/api/resource/{safe_name(doctype)}/{safe_name(parent)}", {"items": items})
            changed.append(
                {
                    "doctype": doctype,
                    "parent": parent,
                    "rows": [ref.get("child_name") for ref in parent_refs],
                }
            )
        except Exception as exc:  # noqa: BLE001 - keep cleanup moving and report blocked docs.
            failures.append(
                {
                    "doctype": doctype,
                    "parent": parent,
                    "error": str(exc),
                }
            )
    return changed, failures


def hard_delete_or_disable(erp: HttpClient, item: str) -> tuple[str, str]:
    try:
        erp.delete(f"/api/resource/Item/{safe_name(item)}")
        return "deleted", ""
    except RuntimeError as exc:
        message = str(exc)
        try:
            erp.put(f"/api/resource/Item/{safe_name(item)}", {"disabled": 1})
            return "blocked_by_history", message
        except RuntimeError as disable_exc:
            return "failed", message + " | disable failed: " + str(disable_exc)


def write_artifacts(output_dir: Path, manifest: list[dict[str, Any]], results: list[dict[str, Any]] | None = None) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest_json = output_dir / "manifest.json"
    manifest_csv = output_dir / "manifest.csv"
    manifest_json.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    with manifest_csv.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=[
            "item_name",
            "duplicate_count",
            "reason",
            "keep_item_code",
            "keep_item_codes",
            "delete_item_code",
            "delete_margin",
            "delete_vendor",
            "delete_hsn",
            "all_margins",
        ])
        writer.writeheader()
        for row in manifest:
            csv_row = dict(row)
            csv_row["keep_item_codes"] = "|".join(row["keep_item_codes"])
            csv_row["all_margins"] = "|".join(str(v) for v in row["all_margins"])
            writer.writerow(csv_row)

    if results is not None:
        result_json = output_dir / "execution_results.json"
        result_csv = output_dir / "execution_results.csv"
        result_json.write_text(json.dumps(results, indent=2), encoding="utf-8")
        keys = [
            "item_name",
            "reason",
            "keep_item_code",
            "delete_item_code",
            "status",
            "reference_count_before",
            "draft_repoints",
            "draft_repoint_failures",
            "submitted_reference_count",
            "error",
        ]
        with result_csv.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=keys)
            writer.writeheader()
            for row in results:
                writer.writerow({key: row.get(key, "") for key in keys})


def load_existing_results(output_dir: Path) -> list[dict[str, Any]]:
    result_csv = output_dir / "execution_results.csv"
    if not result_csv.exists():
        return []
    with result_csv.open("r", newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def run(args: argparse.Namespace) -> int:
    mw = HttpClient(args.mw_base, insecure_ssl=args.insecure_ssl)
    erp = HttpClient(args.erp_base, insecure_ssl=args.insecure_ssl)
    mw_login(mw, args.username, args.password)
    erp_login(erp, args.username, args.password)

    manifest = build_manifest(list_live_items(mw))
    if args.limit:
        manifest = manifest[: args.limit]
    existing_results = load_existing_results(args.output_dir) if args.resume else []
    if existing_results:
        processed_delete_codes = {row.get("delete_item_code", "") for row in existing_results}
        manifest = [row for row in manifest if row["delete_item_code"] not in processed_delete_codes]
    write_artifacts(args.output_dir, manifest, existing_results or None)

    print(f"manifest_rows={len(manifest)}")
    print(f"manifest_json={args.output_dir / 'manifest.json'}")
    print(f"manifest_csv={args.output_dir / 'manifest.csv'}")
    if args.dry_run:
        return 0

    results: list[dict[str, Any]] = list(existing_results)
    for index, row in enumerate(manifest, start=1):
        delete_code = row["delete_item_code"]
        keep_code = row["keep_item_code"]
        print(f"[{index}/{len(manifest)}] {delete_code} -> keep {keep_code}", flush=True)
        try:
            refs = list_references(erp, delete_code)
            submitted = [ref for ref in refs if ref["docstatus"] == 1]
            repointed, repoint_failures = repoint_editable_references(erp, delete_code, keep_code, refs)
            status, error = hard_delete_or_disable(erp, delete_code)
            results.append(
                {
                    **row,
                    "status": "repointed_then_deleted" if status == "deleted" and repointed else status,
                    "reference_count_before": len(refs),
                    "draft_repoints": len(repointed),
                    "draft_repoint_failures": len(repoint_failures),
                    "submitted_reference_count": len(submitted),
                    "repointed_documents": repointed,
                    "repoint_failures": repoint_failures,
                    "error": error,
                }
            )
        except Exception as exc:  # noqa: BLE001 - one-time runner must keep processing.
            results.append(
                {
                    **row,
                    "status": "failed",
                    "reference_count_before": "",
                    "draft_repoints": "",
                    "submitted_reference_count": "",
                    "error": str(exc),
                }
            )
        write_artifacts(args.output_dir, manifest, results)
        time.sleep(args.pause)

    counts = defaultdict(int)
    for row in results:
        counts[row["status"]] += 1
    print("results=" + json.dumps(dict(counts), sort_keys=True))
    print(f"execution_json={args.output_dir / 'execution_results.json'}")
    print(f"execution_csv={args.output_dir / 'execution_results.csv'}")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mw-base", default=os.environ.get("AAS_MW_BASE", DEFAULT_MW_BASE))
    parser.add_argument("--erp-base", default=os.environ.get("AAS_ERP_BASE", DEFAULT_ERP_BASE))
    parser.add_argument("--username", default=os.environ.get("AAS_USERNAME", DEFAULT_USER))
    parser.add_argument("--password", default=os.environ.get("AAS_PASSWORD", DEFAULT_PASSWORD))
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--dry-run", action="store_true", help="Only write manifest artifacts.")
    parser.add_argument("--limit", type=int, default=0, help="Limit manifest rows for sample execution.")
    parser.add_argument("--pause", type=float, default=0.05, help="Delay between mutating rows.")
    parser.add_argument("--insecure-ssl", action="store_true", default=True, help="Allow self-signed HTTPS certs.")
    parser.add_argument("--resume", action="store_true", help="Skip delete codes already present in execution_results.csv.")
    return parser.parse_args()


if __name__ == "__main__":
    sys.exit(run(parse_args()))
