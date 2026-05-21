import os
from pathlib import Path

import frappe
from frappe import _dict
from frappe.custom.doctype.custom_field.custom_field import create_custom_fields
from erpnext.setup.setup_wizard.setup_wizard import setup_complete


SITE_NAME = "aas.core.local"
BENCH_PATH = Path("/home/frappe/frappe-bench")
SITES_PATH = BENCH_PATH / "sites"
MARKER = SITES_PATH / SITE_NAME / ".setup_complete"
LOG_PATHS = [Path("/home/frappe/logs"), BENCH_PATH / "logs"]
SITE_LOG_PATHS = [
    SITES_PATH / SITE_NAME / "logs",
    BENCH_PATH / SITE_NAME / "logs",
]


def env(name: str, default: str) -> str:
    value = os.environ.get(name, "").strip()
    return value or default


def setup_args() -> _dict:
    return _dict(
        full_name=env("ERP_SETUP_FULL_NAME", "Administrator"),
        email=env("ERP_SETUP_EMAIL", "admin@example.com"),
        password=env("ERP_SETUP_PASSWORD", "admin"),
        company_name=env("ERP_SETUP_COMPANY_NAME", "AAS"),
        company_abbr=env("ERP_SETUP_COMPANY_ABBR", "AAS"),
        country=env("ERP_SETUP_COUNTRY", "United States"),
        currency=env("ERP_SETUP_CURRENCY", "INR"),
        timezone=env("ERP_SETUP_TIMEZONE", "America/New_York"),
        fy_start_date=env("ERP_SETUP_FY_START_DATE", "2026-01-01"),
        fy_end_date=env("ERP_SETUP_FY_END_DATE", "2026-12-31"),
        language="english",
        company_tagline="AAS",
        chart_of_accounts=env("ERP_SETUP_CHART_OF_ACCOUNTS", "Standard"),
    )


def ensure_customer_fields() -> None:
    create_custom_fields(
        {
            "Customer": [
                {
                    "fieldname": "aas_branch_location",
                    "label": "Branch Location",
                    "fieldtype": "Data",
                    "insert_after": "customer_name",
                },
                {
                    "fieldname": "aas_whatsapp_group_name",
                    "label": "WhatsApp Group Name",
                    "fieldtype": "Data",
                    "insert_after": "aas_branch_location",
                },
                {
                    "fieldname": "aas_credit_days",
                    "label": "Credit Days",
                    "fieldtype": "Int",
                    "insert_after": "aas_whatsapp_group_name",
                },
                {
                    "fieldname": "aas_invoice_email",
                    "label": "Invoice Email",
                    "fieldtype": "Data",
                    "insert_after": "aas_credit_days",
                },
                {
                    "fieldname": "aas_whatsapp_number",
                    "label": "WhatsApp Number",
                    "fieldtype": "Data",
                    "insert_after": "aas_invoice_email",
                },
            ]
        },
        ignore_validate=True,
        update=True,
    )
    frappe.db.commit()


def ensure_customer_naming_series() -> None:
    series = "BR-.#####"
    try:
        selling_settings = frappe.get_doc("Selling Settings", "Selling Settings")
        selling_settings.db_set("cust_master_name", "Naming Series", update_modified=False)
        if hasattr(selling_settings, "customer_naming_series"):
            selling_settings.db_set("customer_naming_series", series, update_modified=False)

        customer_doctype = frappe.get_doc("DocType", "Customer")
        if getattr(customer_doctype, "autoname", "") != "naming_series:":
            customer_doctype.db_set("autoname", "naming_series:", update_modified=False)
        frappe.db.commit()
        return
    except Exception:
        frappe.db.rollback()

    try:
        customer_doctype = frappe.get_doc("DocType", "Customer")
        customer_doctype.db_set("autoname", "naming_series:", update_modified=False)
        frappe.db.commit()
    except Exception:
        frappe.db.rollback()
        raise


def main() -> None:
    for path in LOG_PATHS:
        path.mkdir(parents=True, exist_ok=True)
    for path in SITE_LOG_PATHS:
        path.mkdir(parents=True, exist_ok=True)
    os.chdir(BENCH_PATH)
    frappe.init(site=SITE_NAME, sites_path=str(SITES_PATH))
    frappe.connect()
    try:
        if not MARKER.exists():
            setup_complete(setup_args())
            MARKER.touch()
        ensure_customer_fields()
        ensure_customer_naming_series()
    finally:
        frappe.destroy()


if __name__ == "__main__":
    main()
