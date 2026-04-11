#!/usr/bin/env python3
"""Generate PDF with REAL application screenshots."""

from fpdf import FPDF
from datetime import datetime
import os

class RealAppPDF(FPDF):
    def __init__(self):
        super().__init__()
        self.set_margins(10, 10, 10)
        self.set_auto_page_break(auto=True, margin=10)
        self.page_num = 0

    def new_page(self):
        self.page_num += 1
        self.add_page()

    def page_title(self, text):
        self.ln(2)
        self.set_font("Helvetica", "B", 16)
        self.set_text_color(0, 51, 102)
        self.cell(0, 8, text, new_x='LMARGIN', new_y='NEXT')
        self.set_text_color(0, 0, 0)
        self.ln(2)

    def section(self, text):
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

    def add_screenshot(self, img_path, caption=""):
        if not os.path.exists(img_path):
            self.ln(1)
            self.set_font("Helvetica", "", 8)
            self.cell(0, 4, f"[Screenshot not found: {os.path.basename(img_path)}]", new_x='LMARGIN', new_y='NEXT')
            return
        try:
            self.ln(2)
            self.image(img_path, x=10, w=190)
            if caption:
                self.ln(1)
                self.set_font("Helvetica", "I", 7)
                self.set_text_color(100, 100, 100)
                self.cell(0, 3, caption, new_x='LMARGIN', new_y='NEXT')
                self.set_text_color(0, 0, 0)
            self.ln(1)
        except Exception as e:
            self.ln(1)
            self.set_font("Helvetica", "", 8)
            self.cell(0, 4, f"[Error loading image: {str(e)[:50]}]", new_x='LMARGIN', new_y='NEXT')

def main():
    pdf = RealAppPDF()
    ss_dir = "ui_screenshots_real"

    # COVER PAGE
    pdf.new_page()
    pdf.ln(30)
    pdf.set_font("Helvetica", "B", 32)
    pdf.set_text_color(0, 51, 102)
    pdf.cell(0, 12, "AAS", new_x='LMARGIN', new_y='NEXT')
    pdf.set_font("Helvetica", "B", 18)
    pdf.cell(0, 8, "Real Application Screenshots", new_x='LMARGIN', new_y='NEXT')
    pdf.set_text_color(0, 0, 0)
    pdf.ln(15)
    pdf.set_font("Helvetica", "", 10)
    pdf.cell(0, 5, f"Generated: {datetime.now().strftime('%B %d, %Y')}", new_x='LMARGIN', new_y='NEXT')
    pdf.cell(0, 5, "Actual application UI - Administrator View", new_x='LMARGIN', new_y='NEXT')
    pdf.ln(10)
    pdf.text("This document contains real screenshots of the AAS (Automated Accounting System) application, captured from the running system.")

    # TABLE OF CONTENTS
    pdf.new_page()
    pdf.page_title("Table of Contents")
    toc = [
        "1. Login Screen",
        "2. Admin Dashboard",
        "3. Orders Management",
        "4. Vendor Operations",
        "5. Branch Operations",
        "6. Bills & Invoicing",
        "7. Stock Management",
        "8. Vendors Master Data",
        "9. Items Catalog",
        "10. Categories"
    ]
    for item in toc:
        pdf.bullet(item)

    # 1. LOGIN
    pdf.new_page()
    pdf.page_title("1. Login Screen")
    pdf.section("Overview")
    pdf.text("The application login screen where administrators authenticate with their credentials.")
    pdf.section("Features")
    pdf.bullet("Username field (Email or Administrator ID)")
    pdf.bullet("Password field (encrypted)")
    pdf.bullet("Login button")
    pdf.bullet("Error handling and validation")
    pdf.ln(3)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(ss_dir, "01_login.png"),
        caption="Login Page - Secure authentication to access AAS"
    )

    # 2. DASHBOARD
    pdf.new_page()
    pdf.page_title("2. Admin Dashboard")
    pdf.section("Overview")
    pdf.text("The main dashboard showing operational overview with key performance indicators (KPIs) and business metrics.")
    pdf.section("Dashboard Sections")
    pdf.bullet("Operational Summary - Key metrics at a glance")
    pdf.bullet("Order Status - Breakdown by status")
    pdf.bullet("Branch Operations - Branch-level metrics")
    pdf.bullet("Bill Summary - Billing overview")
    pdf.bullet("Receivables - Outstanding amounts")
    pdf.bullet("Recent Activity - Latest transactions")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(ss_dir, "02_dashboard.png"),
        caption="Admin Dashboard - Operational overview and KPIs"
    )

    # 3. ORDERS
    pdf.new_page()
    pdf.page_title("3. Orders Management")
    pdf.section("Overview")
    pdf.text("Centralized order management interface showing all orders with status, vendor, amounts, and action buttons for managing the complete order lifecycle.")
    pdf.section("Key Features")
    pdf.bullet("Order list with search and filtering")
    pdf.bullet("Column visibility: Order ID, Date, Branch, Vendor, Status, Amount, Actions")
    pdf.bullet("Status indicators (color-coded)")
    pdf.bullet("Action buttons: Manage, View, Edit, Delete")
    pdf.bullet("Order detail panel on selection")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(ss_dir, "03_orders.png"),
        caption="Orders Management - Browse and manage all orders"
    )

    # 4. VENDOR OPS
    pdf.new_page()
    pdf.page_title("4. Vendor Operations")
    pdf.section("Overview")
    pdf.text("Vendor-centric operational dashboard with performance metrics, pending orders, PDF receipt status, bill capture status, and financial settlement tracking.")
    pdf.section("Dashboard Metrics")
    pdf.bullet("Total vendors count")
    pdf.bullet("Pending orders by vendor")
    pdf.bullet("Awaiting PDF uploads")
    pdf.bullet("Awaiting bill capture")
    pdf.bullet("Pending amounts")
    pdf.bullet("Settlement status (Settled/Open/Overdue)")
    pdf.bullet("Last activity timestamps")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(ss_dir, "04_vendor_ops.png"),
        caption="Vendor Operations - Monitor vendor performance and pending items"
    )

    # 5. BRANCH OPS
    pdf.new_page()
    pdf.page_title("5. Branch Operations")
    pdf.section("Overview")
    pdf.text("Branch-centric operational dashboard tracking order pipelines, outstanding receivables, payment collection rates, and financial settlement by branch.")
    pdf.section("Dashboard Metrics")
    pdf.bullet("Total branches count")
    pdf.bullet("Branches with pending orders")
    pdf.bullet("Total pending orders")
    pdf.bullet("Awaiting vendor assignment")
    pdf.bullet("Open receivable amounts")
    pdf.bullet("Payment collection rates")
    pdf.bullet("Ledger balances")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(ss_dir, "05_branch_ops.png"),
        caption="Branch Operations - Track branch receivables and performance"
    )

    # 6. BILLS
    pdf.new_page()
    pdf.page_title("6. Bills & Invoicing")
    pdf.section("Overview")
    pdf.text("Complete billing and invoicing system for managing customer invoices, recording payments, tracking outstanding amounts, and downloading invoice PDFs.")
    pdf.section("Key Features")
    pdf.bullet("Invoice list with status tracking")
    pdf.bullet("Create invoices from orders or manually")
    pdf.bullet("Record payment transactions")
    pdf.bullet("GST and tax management")
    pdf.bullet("Outstanding amount tracking")
    pdf.bullet("Payment status indicators")
    pdf.bullet("PDF download capability")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(ss_dir, "06_bills.png"),
        caption="Bills & Invoicing - Manage invoices and payments"
    )

    # 7. STOCK
    pdf.new_page()
    pdf.page_title("7. Stock Management")
    pdf.section("Overview")
    pdf.text("Real-time inventory tracking system with low-stock alerting, threshold management, and vendor grouping for comprehensive inventory control.")
    pdf.section("Features")
    pdf.bullet("Item inventory with quantities on hand")
    pdf.bullet("Per-item reorder thresholds")
    pdf.bullet("Low stock status indicators")
    pdf.bullet("Vendor-wise inventory grouping")
    pdf.bullet("Summary metrics (total items, quantity, low stock count)")
    pdf.bullet("Stock alerts and notifications")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(ss_dir, "07_stock.png"),
        caption="Stock Management - Monitor inventory and set thresholds"
    )

    # 8. VENDORS
    pdf.new_page()
    pdf.page_title("8. Vendors Master Data")
    pdf.section("Overview")
    pdf.text("Supplier management system for maintaining vendor records, contact information, payment terms, and invoice template setup for PDF parsing.")
    pdf.section("Features")
    pdf.bullet("Vendor list with search")
    pdf.bullet("Create/edit vendor records")
    pdf.bullet("Contact information (name, email, phone, address)")
    pdf.bullet("Payment terms and credit days")
    pdf.bullet("Invoice template management")
    pdf.bullet("Vendor categorization")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(ss_dir, "08_vendors.png"),
        caption="Vendors Master Data - Manage supplier information"
    )

    # 9. ITEMS
    pdf.new_page()
    pdf.page_title("9. Items Catalog")
    pdf.section("Overview")
    pdf.text("Central product catalog with pricing, GST rates, vendor associations, and category management for comprehensive item master data.")
    pdf.section("Features")
    pdf.bullet("Item code and description")
    pdf.bullet("Category assignment")
    pdf.bullet("Cost and selling rates")
    pdf.bullet("GST rate configuration")
    pdf.bullet("Vendor association")
    pdf.bullet("Search and filter by category/vendor")
    pdf.bullet("Create/edit/delete items")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(ss_dir, "09_items.png"),
        caption="Items Catalog - Manage product master data"
    )

    # 10. CATEGORIES
    pdf.new_page()
    pdf.page_title("10. Categories")
    pdf.section("Overview")
    pdf.text("Item categorization system for organizing products into logical groups for easier browsing, filtering, and management across the system.")
    pdf.section("Features")
    pdf.bullet("Category list with descriptions")
    pdf.bullet("Create new categories")
    pdf.bullet("Edit category details")
    pdf.bullet("Assign items to categories")
    pdf.bullet("Category-based filtering in orders")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(ss_dir, "10_categories.png"),
        caption="Categories - Organize items into logical groups"
    )

    # KEY WORKFLOWS
    pdf.new_page()
    pdf.page_title("Key Workflows")

    pdf.section("Workflow 1: Complete Order-to-Invoice-to-Payment Cycle")
    pdf.bullet("1. Create Order (from orders list or by uploading order sheets)")
    pdf.bullet("2. Assign Vendor to Order")
    pdf.bullet("3. Upload Vendor PDF and Bill Details")
    pdf.bullet("4. Create Sell Order (confirms purchase)")
    pdf.bullet("5. Create Invoice from Order (auto-populated data)")
    pdf.bullet("6. Record Payment (with allocation to invoice)")
    pdf.bullet("7. View Ledger and Settlement Status")
    pdf.ln(2)

    pdf.section("Workflow 2: Vendor Performance Monitoring")
    pdf.bullet("1. View Vendor Operations Dashboard")
    pdf.bullet("2. Identify vendors with pending orders or issues")
    pdf.bullet("3. Click vendor for detailed drill-down")
    pdf.bullet("4. Review vendor orders, ledger, and exceptions")
    pdf.bullet("5. Export vendor ledger to CSV for analysis")
    pdf.bullet("6. Follow up on overdue PDFs or bill captures")
    pdf.ln(2)

    pdf.section("Workflow 3: Branch Receivables Management")
    pdf.bullet("1. View Branch Operations Dashboard")
    pdf.bullet("2. Monitor pending orders and open receivables by branch")
    pdf.bullet("3. Review payment collection rates")
    pdf.bullet("4. Click branch for detailed financial view")
    pdf.bullet("5. Track overdue invoices and exceptions")
    pdf.bullet("6. Record payments and update ledger")
    pdf.ln(2)

    pdf.section("Workflow 4: Inventory Management")
    pdf.bullet("1. Monitor Stock Management dashboard")
    pdf.bullet("2. Set reorder thresholds for items")
    pdf.bullet("3. Receive low-stock alerts")
    pdf.bullet("4. View inventory by vendor")
    pdf.bullet("5. Create orders to replenish stock")
    pdf.bullet("6. Track stock receipt through order cycle")

    # FINAL PAGE
    pdf.new_page()
    pdf.set_font("Helvetica", "B", 14)
    pdf.ln(10)
    pdf.cell(0, 6, "About AAS", new_x='LMARGIN', new_y='NEXT')
    pdf.set_font("Helvetica", "", 9)
    pdf.ln(3)

    pdf.text(
        "AAS (Automated Accounting System) is a comprehensive procurement, inventory, and finance management platform designed for multi-branch businesses.\n\n"
        "Key Features:\n"
        "- Multi-branch order management\n"
        "- Vendor operations tracking\n"
        "- Branch receivables management\n"
        "- Automated invoicing with GST support\n"
        "- Payment allocation and tracking\n"
        "- Real-time inventory monitoring\n"
        "- Comprehensive reporting and analytics\n"
        "- Role-based access control\n"
        "- AI-powered PDF parsing for vendor invoices\n\n"
        "Integration: ERPNext backend ensures complete financial compliance and audit trails."
    )

    pdf.ln(10)
    pdf.set_font("Helvetica", "I", 8)
    pdf.set_text_color(100, 100, 100)
    pdf.cell(0, 4, f"Documentation generated on {datetime.now().strftime('%B %d, %Y at %H:%M')}", new_x='LMARGIN', new_y='NEXT')
    pdf.cell(0, 4, "Administrator View - Actual Application Screenshots", new_x='LMARGIN', new_y='NEXT')

    output = "/Users/roshninaik/Projects/AAS/AAS_Real_Screenshots.pdf"
    pdf.output(output)

    size = os.path.getsize(output) / 1024
    print(f"\n{'='*50}")
    print(f"SUCCESS: Real Application PDF Created!")
    print(f"{'='*50}")
    print(f"File: {output}")
    print(f"Pages: {pdf.page_num}")
    print(f"Size: {size:.1f} KB")
    print(f"Status: REAL SCREENSHOTS FROM ACTUAL APPLICATION")
    print(f"{'='*50}")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"ERROR: {e}")
        import traceback
        traceback.print_exc()
