#!/usr/bin/env python3
import json
import os
import re
import sys
import urllib.parse
import urllib.request


def request_json(opener: urllib.request.OpenerDirector, req: urllib.request.Request) -> dict:
    with opener.open(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def post_form_json(
    opener: urllib.request.OpenerDirector,
    url: str,
    data: dict,
    headers: dict | None = None,
) -> dict:
    encoded = urllib.parse.urlencode(data).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=encoded,
        headers={"Content-Type": "application/x-www-form-urlencoded", **(headers or {})},
        method="POST",
    )
    return request_json(opener, req)


def main() -> int:
    erp_url = os.environ.get("ERP_URL", "http://host.docker.internal:8080").rstrip("/")
    user = os.environ.get("ERP_USER", "Administrator")
    password = os.environ.get("ERP_PASSWORD", "admin")

    cookie_handler = urllib.request.HTTPCookieProcessor()
    opener = urllib.request.build_opener(cookie_handler)

    post_form_json(
        opener,
        f"{erp_url}/api/method/login",
        {"usr": user, "pwd": password},
    )
    # This ERP instance blocks `frappe.sessions.get_csrf_token` (not whitelisted),
    # so extract the token from the desk HTML.
    app_html = opener.open(f"{erp_url}/app", timeout=30).read().decode("utf-8", errors="ignore")
    match = re.search(r'frappe\.csrf_token\s*=\s*"([^"]+)"', app_html)
    csrf = match.group(1) if match else None
    if not csrf:
        print("Failed to extract CSRF token from ERPNext /app HTML.", file=sys.stderr)
        return 2

    reports: dict[str, str] = {
        "AAS - Sales Profit Summary": """
WITH sales_by_so AS (
  SELECT
    si.aas_source_sales_order AS source_so,
    si.customer AS branch,
    SUM(si.grand_total) AS sales_total
  FROM `tabSales Invoice` si
  WHERE si.docstatus = 1
    AND si.posting_date >= %(from_date)s
    AND si.posting_date <= %(to_date)s
    AND si.aas_invoice_version_status = 'CURRENT'
    AND (IFNULL(%(company)s, '') = '' OR si.company = %(company)s)
    AND (IFNULL(%(branch)s, '') = '' OR si.customer = %(branch)s)
    AND (IFNULL(%(vendor)s, '') = '' OR EXISTS (
      SELECT 1 FROM `tabPurchase Invoice` pi2
      WHERE pi2.docstatus = 1
        AND pi2.aas_invoice_version_status = 'CURRENT'
        AND pi2.aas_source_sales_order = si.aas_source_sales_order
        AND pi2.supplier = %(vendor)s
    ))
  GROUP BY si.aas_source_sales_order, si.customer
),
cost_by_so AS (
  SELECT
    pi.aas_source_sales_order AS source_so,
    SUM(pi.grand_total) AS cost_total
  FROM `tabPurchase Invoice` pi
  WHERE pi.docstatus = 1
    AND pi.aas_invoice_version_status = 'CURRENT'
    AND (IFNULL(%(company)s, '') = '' OR pi.company = %(company)s)
    AND (IFNULL(%(vendor)s, '') = '' OR pi.supplier = %(vendor)s)
  GROUP BY pi.aas_source_sales_order
)
SELECT
  ROUND(SUM(s.sales_total), 2) AS sales_total,
  ROUND(SUM(IFNULL(c.cost_total, 0)), 2) AS cost_total,
  ROUND(SUM(s.sales_total) - SUM(IFNULL(c.cost_total, 0)), 2) AS profit_total,
  ROUND(
    CASE WHEN SUM(s.sales_total) = 0 THEN 0
         ELSE ((SUM(s.sales_total) - SUM(IFNULL(c.cost_total, 0))) / SUM(s.sales_total)) * 100
    END,
  2) AS profit_percent
FROM sales_by_so s
LEFT JOIN cost_by_so c ON c.source_so = s.source_so
""".strip(),
        "AAS - Branch Sales & Profit": """
WITH sales_by_so AS (
  SELECT
    si.aas_source_sales_order AS source_so,
    si.customer AS branch,
    SUM(si.grand_total) AS sales_total
  FROM `tabSales Invoice` si
  WHERE si.docstatus = 1
    AND si.posting_date >= %(from_date)s
    AND si.posting_date <= %(to_date)s
    AND si.aas_invoice_version_status = 'CURRENT'
    AND (IFNULL(%(company)s, '') = '' OR si.company = %(company)s)
    AND (IFNULL(%(branch)s, '') = '' OR si.customer = %(branch)s)
    AND (IFNULL(%(vendor)s, '') = '' OR EXISTS (
      SELECT 1 FROM `tabPurchase Invoice` pi2
      WHERE pi2.docstatus = 1
        AND pi2.aas_invoice_version_status = 'CURRENT'
        AND pi2.aas_source_sales_order = si.aas_source_sales_order
        AND pi2.supplier = %(vendor)s
    ))
  GROUP BY si.aas_source_sales_order, si.customer
),
cost_by_so AS (
  SELECT
    pi.aas_source_sales_order AS source_so,
    SUM(pi.grand_total) AS cost_total
  FROM `tabPurchase Invoice` pi
  WHERE pi.docstatus = 1
    AND pi.aas_invoice_version_status = 'CURRENT'
    AND (IFNULL(%(company)s, '') = '' OR pi.company = %(company)s)
    AND (IFNULL(%(vendor)s, '') = '' OR pi.supplier = %(vendor)s)
  GROUP BY pi.aas_source_sales_order
)
SELECT
  s.branch AS branch,
  ROUND(SUM(s.sales_total), 2) AS sales_total,
  ROUND(SUM(IFNULL(c.cost_total, 0)), 2) AS cost_total,
  ROUND(SUM(s.sales_total) - SUM(IFNULL(c.cost_total, 0)), 2) AS profit_total,
  ROUND(
    CASE WHEN SUM(s.sales_total) = 0 THEN 0
         ELSE ((SUM(s.sales_total) - SUM(IFNULL(c.cost_total, 0))) / SUM(s.sales_total)) * 100
    END,
  2) AS profit_percent
FROM sales_by_so s
LEFT JOIN cost_by_so c ON c.source_so = s.source_so
GROUP BY s.branch
ORDER BY profit_total DESC
""".strip(),
        "AAS - Vendor Sales & Profit": """
WITH sales_by_so AS (
  SELECT
    si.aas_source_sales_order AS source_so,
    SUM(si.grand_total) AS sales_total
  FROM `tabSales Invoice` si
  WHERE si.docstatus = 1
    AND si.posting_date >= %(from_date)s
    AND si.posting_date <= %(to_date)s
    AND si.aas_invoice_version_status = 'CURRENT'
    AND (IFNULL(%(company)s, '') = '' OR si.company = %(company)s)
  GROUP BY si.aas_source_sales_order
),
cost_by_so_vendor AS (
  SELECT
    pi.aas_source_sales_order AS source_so,
    pi.supplier AS vendor,
    SUM(pi.grand_total) AS cost_total
  FROM `tabPurchase Invoice` pi
  WHERE pi.docstatus = 1
    AND pi.aas_invoice_version_status = 'CURRENT'
    AND (IFNULL(%(company)s, '') = '' OR pi.company = %(company)s)
    AND (IFNULL(%(vendor)s, '') = '' OR pi.supplier = %(vendor)s)
  GROUP BY pi.aas_source_sales_order, pi.supplier
)
SELECT
  c.vendor AS vendor,
  ROUND(SUM(s.sales_total), 2) AS sales_total,
  ROUND(SUM(c.cost_total), 2) AS cost_total,
  ROUND(SUM(s.sales_total) - SUM(c.cost_total), 2) AS profit_total,
  ROUND(
    CASE WHEN SUM(s.sales_total) = 0 THEN 0
         ELSE ((SUM(s.sales_total) - SUM(c.cost_total)) / SUM(s.sales_total)) * 100
    END,
  2) AS profit_percent
FROM cost_by_so_vendor c
JOIN sales_by_so s ON s.source_so = c.source_so
GROUP BY c.vendor
ORDER BY profit_total DESC
""".strip(),
    }

    for report_name, query in reports.items():
        # Fail early if report doesn't exist.
        encoded_name = urllib.parse.quote(report_name, safe="")
        get_req = urllib.request.Request(
            f"{erp_url}/api/method/frappe.client.get?doctype=Report&name={encoded_name}",
            method="GET",
        )
        get_resp = request_json(opener, get_req)
        if not get_resp.get("message", {}).get("name"):
            print(f'Report "{report_name}" not found.', file=sys.stderr)
            return 3

        post_form_json(
            opener,
            f"{erp_url}/api/method/frappe.client.set_value",
            {"doctype": "Report", "name": report_name, "fieldname": "query", "value": query},
            headers={"X-Frappe-CSRF-Token": csrf},
        )
        print(f"Updated: {report_name}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
