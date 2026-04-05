#!/usr/bin/env python3
"""Generate UI documentation PDF with screenshots."""

from fpdf import FPDF
from datetime import datetime
import os

class UIPDF(FPDF):
    def __init__(self):
        super().__init__()
        self.set_margins(10, 10, 10)
        self.set_auto_page_break(auto=True, margin=10)
        self.page_num = 0

    def new_page(self):
        self.page_num += 1
        self.add_page()

    def heading(self, text):
        self.ln(2)
        self.set_font("Helvetica", "B", 16)
        self.set_text_color(0, 51, 102)
        self.cell(0, 8, text, new_x='LMARGIN', new_y='NEXT')
        self.set_text_color(0, 0, 0)
        self.ln(2)

    def subheading(self, text):
        self.ln(2)
        self.set_font("Helvetica", "B", 11)
        self.set_text_color(0, 102, 153)
        self.cell(0, 6, text, new_x='LMARGIN', new_y='NEXT')
        self.set_text_color(0, 0, 0)

    def text(self, content):
        self.set_font("Helvetica", "", 8.5)
        self.multi_cell(0, 4, content)

    def bullet(self, content):
        self.set_font("Helvetica", "", 8)
        self.set_x(12)
        self.multi_cell(188, 3.5, "- " + content)

    def add_img(self, img_path, caption=""):
        if not os.path.exists(img_path):
            return
        try:
            self.ln(2)
            self.image(img_path, x=10, w=190)
            if caption:
                self.ln(1)
                self.set_font("Helvetica", "I", 7)
                self.set_text_color(120, 120, 120)
                self.cell(0, 3, caption, new_x='LMARGIN', new_y='NEXT')
                self.set_text_color(0, 0, 0)
            self.ln(1)
        except:
            pass

def main():
    pdf = UIPDF()
    ss_dir = "ui_screenshots"

    # COVER
    pdf.new_page()
    pdf.ln(30)
    pdf.set_font("Helvetica", "B", 32)
    pdf.set_text_color(0, 51, 102)
    pdf.cell(0, 12, "AAS", new_x='LMARGIN', new_y='NEXT')
    pdf.set_font("Helvetica", "B", 18)
    pdf.cell(0, 8, "UI Screenshots & Walkthrough", new_x='LMARGIN', new_y='NEXT')
    pdf.set_text_color(0, 0, 0)
    pdf.ln(15)
    pdf.set_font("Helvetica", "", 10)
    pdf.cell(0, 5, f"Generated: {datetime.now().strftime('%B %d, %Y')}", new_x='LMARGIN', new_y='NEXT')
    pdf.cell(0, 5, "Automated Accounting System - Visual Guide", new_x='LMARGIN', new_y='NEXT')

    # TOC
    pdf.new_page()
    pdf.heading("Table of Contents")
    toc = [
        "1. Login Screen",
        "2. Dashboard",
        "3. Orders Management",
        "4. Order Creation",
        "5. Vendor Operations",
        "6. Branch Operations",
        "7. Bills & Invoicing",
        "8. Stock Management",
        "9. Vendors Master Data",
        "10. Items Catalog"
    ]
    for item in toc:
        pdf.bullet(item)

    # 1. LOGIN
    pdf.new_page()
    pdf.heading("1. Login Screen")
    pdf.subheading("Overview")
    pdf.text("First screen where users authenticate to the system with username and password.")
    pdf.subheading("Features")
    pdf.bullet("Username/email input")
    pdf.bullet("Password field")
    pdf.bullet("Sign In button")
    pdf.bullet("Error message display")
    pdf.add_img(os.path.join(ss_dir, "01_login.png"), caption="Login page - User authentication")

    # 2. DASHBOARD
    pdf.new_page()
    pdf.heading("2. Dashboard")
    pdf.subheading("Overview")
    pdf.text("Home screen showing operational snapshot with KPIs for the current month.")
    pdf.subheading("Key Metrics")
    pdf.bullet("Order Status Breakdown - Orders by status")
    pdf.bullet("Sales Summary - Invoice count and revenue")
    pdf.bullet("Stock Snapshot - Total items and quantity")
    pdf.bullet("Billing by Vendor - Top vendor billing")
    pdf.bullet("Billing by Branch - Top branch billing")
    pdf.bullet("Vendor Operations - Operational KPIs")
    pdf.bullet("Branch Operations - Branch KPIs")
    pdf.add_img(os.path.join(ss_dir, "02_dashboard.png"), caption="Dashboard - Operational snapshot")

    # 3. ORDERS
    pdf.new_page()
    pdf.heading("3. Orders Management")
    pdf.subheading("Overview")
    pdf.text("Central hub for order management with search, filter, and action capabilities.")
    pdf.subheading("Features")
    pdf.bullet("Order list with pagination")
    pdf.bullet("Filter by vendor, status, date range")
    pdf.bullet("Order detail panel on selection")
    pdf.bullet("Assign vendor functionality")
    pdf.bullet("Upload vendor PDF")
    pdf.bullet("Capture vendor bill details")
    pdf.add_img(os.path.join(ss_dir, "03_orders_list.png"), caption="Orders list - Browse and manage all orders")

    # 4. ORDER CREATE
    pdf.new_page()
    pdf.heading("4. Order Creation")
    pdf.subheading("Overview")
    pdf.text("Create orders using two modes: upload images (AI parsing) or select from catalog.")
    pdf.subheading("Creation Methods")
    pdf.bullet("Image Mode - Upload and OCR parse order sheets")
    pdf.bullet("Item Mode - Select items from catalog")
    pdf.subheading("Form Fields")
    pdf.bullet("Customer (Branch) selection")
    pdf.bullet("Company selection (auto-filled)")
    pdf.bullet("Category filter")
    pdf.bullet("Order & Delivery dates")
    pdf.bullet("Image upload area")
    pdf.add_img(os.path.join(ss_dir, "04_order_create.png"), caption="Order creation form")

    # 5. VENDOR OPS
    pdf.new_page()
    pdf.heading("5. Vendor Operations")
    pdf.subheading("Overview")
    pdf.text("Vendor-centric dashboard with performance metrics, pending orders, and settlement status.")
    pdf.subheading("Dashboard Elements")
    pdf.bullet("Vendor summary table with KPIs")
    pdf.bullet("Pending orders count")
    pdf.bullet("Awaiting PDF uploads")
    pdf.bullet("Bill capture status")
    pdf.bullet("Pending bill amounts")
    pdf.bullet("Settlement status (Settled/Open/Overdue)")
    pdf.bullet("Click vendor for detailed drill-down view")
    pdf.add_img(os.path.join(ss_dir, "05_vendor_ops.png"), caption="Vendor Operations dashboard")

    # 6. BRANCH OPS
    pdf.new_page()
    pdf.heading("6. Branch Operations")
    pdf.subheading("Overview")
    pdf.text("Branch-centric dashboard tracking order pipelines, receivables, and payment health.")
    pdf.subheading("Dashboard Elements")
    pdf.bullet("Branch summary table with KPIs")
    pdf.bullet("Pending orders per branch")
    pdf.bullet("Awaiting vendor assignment count")
    pdf.bullet("Open receivable amounts")
    pdf.bullet("Payment collection rate")
    pdf.bullet("Ledger balance")
    pdf.bullet("Settlement status (Settled/Open/Overdue)")
    pdf.add_img(os.path.join(ss_dir, "06_branch_ops.png"), caption="Branch Operations dashboard")

    # 7. BILLS
    pdf.new_page()
    pdf.heading("7. Bills & Invoicing")
    pdf.subheading("Overview")
    pdf.text("Manage customer invoices, create from orders or manually, record payments, download PDFs.")
    pdf.subheading("Key Features")
    pdf.bullet("Invoice list with search and filters")
    pdf.bullet("Create from Order (pre-filled) or Manual entry")
    pdf.bullet("GST handling with auto tax template")
    pdf.bullet("Payment recording with allocation")
    pdf.bullet("Overpayment detection")
    pdf.bullet("PDF download of invoices")
    pdf.bullet("Invoice cancellation with cascade")
    pdf.add_img(os.path.join(ss_dir, "07_bills.png"), caption="Bills - Invoice management")

    # 8. STOCK
    pdf.new_page()
    pdf.heading("8. Stock Management")
    pdf.subheading("Overview")
    pdf.text("Real-time inventory tracking with low-stock alerting and threshold management.")
    pdf.subheading("Key Features")
    pdf.bullet("Complete item inventory with quantities")
    pdf.bullet("Per-item reorder threshold setting")
    pdf.bullet("Low stock status indicator")
    pdf.bullet("Vendor grouping of inventory")
    pdf.bullet("Summary: total items, qty, low stock count")
    pdf.bullet("Local threshold storage (per-device)")
    pdf.add_img(os.path.join(ss_dir, "08_stock.png"), caption="Stock - Inventory monitoring")

    # 9. VENDORS
    pdf.new_page()
    pdf.heading("9. Vendors Master Data")
    pdf.subheading("Overview")
    pdf.text("Supplier management with contact info, payment terms, and invoice template setup.")
    pdf.subheading("Key Features")
    pdf.bullet("Vendor list with search")
    pdf.bullet("Create/edit vendor records")
    pdf.bullet("Contact information")
    pdf.bullet("Payment terms and credit days")
    pdf.bullet("Invoice template management")
    pdf.bullet("Link vendors to items and orders")
    pdf.add_img(os.path.join(ss_dir, "09_vendors.png"), caption="Vendors - Supplier master data")

    # 10. ITEMS
    pdf.new_page()
    pdf.heading("10. Items Catalog")
    pdf.subheading("Overview")
    pdf.text("Central product catalog with pricing, GST rates, and vendor associations.")
    pdf.subheading("Key Features")
    pdf.bullet("Item code, name, description")
    pdf.bullet("Category assignment")
    pdf.bullet("Cost and sell rates")
    pdf.bullet("GST rate configuration")
    pdf.bullet("Vendor association")
    pdf.bullet("Search and filter by category/vendor")
    pdf.bullet("Create/edit items")
    pdf.add_img(os.path.join(ss_dir, "10_items.png"), caption="Items - Product catalog")

    # WORKFLOWS
    pdf.new_page()
    pdf.heading("End-to-End Workflows")
    pdf.subheading("Workflow 1: Order to Invoice to Payment")
    pdf.bullet("Create Order (Orders > Create Order)")
    pdf.bullet("Manage Order (Assign Vendor > Upload PDF > Capture Bill)")
    pdf.bullet("Create Invoice (Bills > Create Invoice > Select Order)")
    pdf.bullet("Record Payment (Bills > Payment > Select Invoice > Submit)")
    pdf.ln(1)

    pdf.subheading("Workflow 2: Monitor Vendor Health")
    pdf.bullet("View Vendor Summary (Operations > Vendor Ops)")
    pdf.bullet("Click Vendor for Details (Drill-down view)")
    pdf.bullet("Review KPIs, Orders, Ledger")
    pdf.bullet("Export Ledger to CSV")
    pdf.ln(1)

    pdf.subheading("Workflow 3: Track Branch Receivables")
    pdf.bullet("View Branch Summary (Operations > Branch Ops)")
    pdf.bullet("Click Branch for Details (Drill-down view)")
    pdf.bullet("Review Pending Orders, Payment Rate")
    pdf.bullet("Check Overdue Invoices & Exceptions")

    output = "/Users/roshninaik/Projects/AAS/AAS_UI_Screenshots.pdf"
    pdf.output(output)

    size = os.path.getsize(output) / 1024
    print(f"\nSUCCESS: PDF created!")
    print(f"File: {output}")
    print(f"Pages: {pdf.page_num}")
    print(f"Size: {size:.1f} KB")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"ERROR: {e}")
        import traceback
        traceback.print_exc()
