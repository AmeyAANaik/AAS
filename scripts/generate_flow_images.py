#!/usr/bin/env python3
"""Generate simple flowchart images for AAS core workflows."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT_DIR = Path("docs/CORE_FLOWS/images")
OUT_DIR.mkdir(parents=True, exist_ok=True)

WORKFLOWS = {
    "order-workflow.png": {
        "title": "Order Workflow",
        "steps": [
            "Create Order (item or image mode)",
            "Assign Vendor",
            "Upload Vendor PDF",
            "Capture Vendor Bill",
            "Create Sell Order",
            "Create Sales Invoice",
        ],
    },
    "vendor-operations-flow.png": {
        "title": "Vendor Operations",
        "steps": [
            "Vendor Summary",
            "Vendor Detail",
            "Vendor Orders",
            "Vendor Ledger",
            "Exceptions & Follow-up",
            "CSV Export",
        ],
    },
    "branch-operations-flow.png": {
        "title": "Branch Operations",
        "steps": [
            "Branch Summary",
            "Branch Detail",
            "Branch Orders",
            "Branch Invoices",
            "Branch Ledger",
            "Collection Action",
        ],
    },
    "billing-invoice-flow.png": {
        "title": "Billing & Payment",
        "steps": [
            "Create Invoice",
            "Apply GST Templates",
            "Set Due Date",
            "Issue Invoice PDF",
            "Record Payment + Evidence",
            "Update Outstanding",
        ],
    },
    "reporting-flow.png": {
        "title": "Reporting",
        "steps": [
            "Select Report Type",
            "Apply Filters",
            "Middleware Aggregates ERP Data",
            "Return KPI Table",
            "Review Metrics",
            "Export CSV",
        ],
    },
}


def draw_workflow(path: Path, title: str, steps: list[str]) -> None:
    width, height = 1600, 560
    image = Image.new("RGB", (width, height), "#f6f8fb")
    draw = ImageDraw.Draw(image)

    title_font = ImageFont.load_default(size=38)
    text_font = ImageFont.load_default(size=24)

    draw.text((48, 24), title, font=title_font, fill="#123a6d")

    x0, y0 = 48, 120
    box_w, box_h = 220, 180
    gap = 30

    for i, step in enumerate(steps):
        x = x0 + i * (box_w + gap)
        draw.rounded_rectangle((x, y0, x + box_w, y0 + box_h), radius=20, fill="#ffffff", outline="#2d5d9f", width=4)
        draw.text((x + 16, y0 + 18), f"{i + 1}", font=text_font, fill="#2d5d9f")
        draw.text((x + 16, y0 + 68), step, font=text_font, fill="#111111")

        if i < len(steps) - 1:
            ax = x + box_w
            ay = y0 + box_h // 2
            bx = ax + gap - 8
            by = ay
            draw.line((ax + 6, ay, bx, by), fill="#2d5d9f", width=5)
            draw.polygon([(bx, by), (bx - 16, by - 10), (bx - 16, by + 10)], fill="#2d5d9f")

    image.save(path)


def main() -> None:
    for file_name, workflow in WORKFLOWS.items():
        draw_workflow(OUT_DIR / file_name, workflow["title"], workflow["steps"])
    print(f"Generated {len(WORKFLOWS)} flow images in {OUT_DIR}")


if __name__ == "__main__":
    main()
