#!/usr/bin/env python3
"""
AAS UI Documentation Generator with Screenshots
Generates a PDF with actual UI screenshots and explanations.
"""

from fpdf import FPDF
from datetime import datetime
import os

class UIDocsPDF(FPDF):
    """Custom PDF for UI documentation with screenshots."""

    def __init__(self):
        super().__init__()
        self.set_margins(10, 10, 10)
        self.set_auto_page_break(auto=True, margin=10)
        self.set_font("Helvetica", size=9)
        self.page_num = 0

    def new_page(self):
        """Add a new page and increment counter."""
        self.page_num += 1
        self.add_page()

    def page_title(self, text):
        """Page title."""
        self.ln(2)
        self.set_font("Helvetica", "B", 16)
        self.set_text_color(0, 51, 102)
        self.cell(0, 8, text, new_x='LMARGIN', new_y='NEXT')
        self.set_text_color(0, 0, 0)
        self.ln(2)

    def section(self, text):
        """Section header."""
        self.ln(2)
        self.set_font("Helvetica", "B", 12)
        self.set_text_color(0, 102, 153)
        self.cell(0, 7, text, new_x='LMARGIN', new_y='NEXT')
        self.set_text_color(0, 0, 0)

    def desc(self, text):
        """Description text."""
        self.set_font("Helvetica", "", 8.5)
        self.multi_cell(0, 4, text)
        self.ln(1)

    def bullet(self, text):
        """Bullet point."""
        self.set_font("Helvetica", "", 8)
        self.set_x(12)
        self.multi_cell(188, 3.5, "- " + text)

    def add_screenshot(self, image_path, width=190, caption=None):
        """Add a screenshot with optional caption."""
        if os.path.exists(image_path):
            try:
                self.ln(2)
                self.image(image_path, x=10, w=width)
                if caption:
                    self.ln(2)
                    self.set_font("Helvetica", "I", 7)
                    self.set_text_color(100, 100, 100)
                    self.cell(0, 3, caption, new_x='LMARGIN', new_y='NEXT')
                    self.set_text_color(0, 0, 0)
                self.ln(1)
            except Exception as e:
                self.ln(1)
                self.set_font("Helvetica", "", 8)
                self.cell(0, 4, f"[Screenshot could not be loaded: {os.path.basename(image_path)}]", new_x='LMARGIN', new_y='NEXT')


def generate_ui_docs():
    """Generate UI documentation PDF with screenshots."""
    pdf = UIDocsPDF()
    screenshots_dir = "/Users/roshninaik/Projects/AAS/ui_screenshots"

    # ========== COVER PAGE ==========
    pdf.new_page()
    pdf.ln(30)
    pdf.set_font("Helvetica", "B", 32)
    pdf.set_text_color(0, 51, 102)
    pdf.cell(0, 12, "AAS", new_x='LMARGIN', new_y='NEXT')
    pdf.set_font("Helvetica", "B", 18)
    pdf.cell(0, 8, "UI Walkthrough & Screenshots", new_x='LMARGIN', new_y='NEXT')
    pdf.set_text_color(0, 0, 0)
    pdf.ln(15)
    pdf.set_font("Helvetica", "I", 11)
    pdf.cell(0, 6, "Automated Accounting System", new_x='LMARGIN', new_y='NEXT')
    pdf.ln(10)
    pdf.set_font("Helvetica", "", 9)
    pdf.cell(0, 5, f"Generated: {datetime.now().strftime('%B %d, %Y')}", new_x='LMARGIN', new_y='NEXT')
    pdf.cell(0, 5, "Version: 1.0", new_x='LMARGIN', new_y='NEXT')
    pdf.ln(8)
    pdf.multi_cell(0, 5,
        "A complete visual guide to the AAS application UI, showing all major screens, "
        "workflows, and user interactions.")

    # ========== TABLE OF CONTENTS ==========
    pdf.new_page()
    pdf.page_title("Table of Contents")
    contents = [
        "1. Login Screen",
        "2. Dashboard",
        "3. Orders Management",
        "4. Order Creation",
        "5. Vendor Operations",
        "6. Branch Operations",
        "7. Bills & Invoicing",
        "8. Stock Management",
        "9. Vendors Master Data",
        "10. Items Catalog",
    ]
    for item in contents:
        pdf.bullet(item)

    # ========== 1. LOGIN SCREEN ==========
    pdf.new_page()
    pdf.page_title("1. Login Screen")
    pdf.section("Overview")
    pdf.desc(
        "The first screen users see when accessing AAS. Users enter their username and password "
        "to authenticate. The system validates credentials against the backend and issues a JWT token."
    )
    pdf.section("Features")
    pdf.bullet("Username/email input field")
    pdf.bullet("Password input field (masked)")
    pdf.bullet("Sign In button")
    pdf.bullet("Error messages for invalid credentials")
    pdf.bullet("Remember login option")
    pdf.ln(3)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(screenshots_dir, "01_login.png"),
        width=160,
        caption="Login Page - Users authenticate here to access the system"
    )

    # ========== 2. DASHBOARD ==========
    pdf.new_page()
    pdf.title("2. Dashboard")
    pdf.section("Overview")
    pdf.desc(
        "The home screen after login showing a comprehensive snapshot of business operations "
        "for the current month. Displays key performance indicators (KPIs) across orders, "
        "sales, inventory, vendors, and branches."
    )
    pdf.section("KPI Cards Displayed")
    pdf.bullet("Order Status Breakdown - Orders by status (Draft, Assigned, etc.)")
    pdf.bullet("Sales Summary - Invoice count and total revenue for the month")
    pdf.bullet("Stock Snapshot - Total items and quantity on hand")
    pdf.bullet("Billing by Vendor - Top vendors by billing amount")
    pdf.bullet("Billing by Branch - Top branches by billing amount")
    pdf.bullet("Vendor Operations - 6 operational metrics")
    pdf.bullet("Branch Operations - 6 operational metrics")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(screenshots_dir, "02_dashboard.png"),
        width=165,
        caption="Dashboard - High-level operational snapshot with KPI cards"
    )

    # ========== 3. ORDERS LIST ==========
    pdf.new_page()
    pdf.title("3. Orders Management")
    pdf.section("Overview")
    pdf.desc(
        "Central hub for order management. Shows all orders in a searchable, filterable table. "
        "Users can view order details, manage status, assign vendors, upload PDFs, and capture "
        "vendor bills - all from this interface."
    )
    pdf.section("Key Features")
    pdf.bullet("Searchable order list with pagination")
    pdf.bullet("Filter by vendor, status, date range, branch")
    pdf.bullet("Order detail panel (opens on selection)")
    pdf.bullet("Action buttons: Assign Vendor, Upload PDF, Capture Bill")
    pdf.bullet("Status tracking from DRAFT to INVOICED")
    pdf.bullet("Quick access to order line items")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(screenshots_dir, "03_orders_list.png"),
        width=165,
        caption="Orders List - Browse, filter, and manage all orders"
    )

    # ========== 4. ORDER CREATION ==========
    pdf.new_page()
    pdf.title("4. Order Creation")
    pdf.section("Overview")
    pdf.desc(
        "Create new orders using two different approaches: upload images of physical order sheets "
        "(AI/OCR processing) or select items from the catalog. Both methods lead to the same "
        "order management interface."
    )
    pdf.section("Creation Methods")
    pdf.bullet("Image Mode: Upload photos of branch order sheets for AI parsing")
    pdf.bullet("Item Mode: Browse and select items from the catalog")
    pdf.ln(1)
    pdf.section("Form Fields")
    pdf.bullet("Customer (Branch) selection")
    pdf.bullet("Company selection (auto-filled from context)")
    pdf.bullet("Category filter for items")
    pdf.bullet("Order Date and Delivery Date")
    pdf.bullet("Image upload area (drag-and-drop supported)")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(screenshots_dir, "04_order_create.png"),
        width=165,
        caption="Order Creation - Create orders from images or item catalog"
    )

    # ========== 5. VENDOR OPERATIONS ==========
    pdf.new_page()
    pdf.title("5. Vendor Operations")
    pdf.section("Overview")
    pdf.desc(
        "Comprehensive vendor-centric dashboard showing vendor performance, pending orders, "
        "PDF receipt status, bill capture status, and financial settlement. Includes detailed "
        "per-vendor views with KPIs, ledger, and exception tracking."
    )
    pdf.section("Dashboard Elements")
    pdf.bullet("Vendor summary table with KPIs (pending orders, awaiting PDF, bill status)")
    pdf.bullet("Pending bill amounts and last activity timestamps")
    pdf.bullet("Settlement status (Settled, Open, Overdue)")
    pdf.bullet("Template status (invoice parsing template availability)")
    pdf.bullet("Click vendor for drill-down view with ledger and exceptions")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(screenshots_dir, "05_vendor_ops.png"),
        width=165,
        caption="Vendor Operations - Monitor vendor performance and pending items"
    )

    # ========== 6. BRANCH OPERATIONS ==========
    pdf.new_page()
    pdf.title("6. Branch Operations")
    pdf.section("Overview")
    pdf.desc(
        "Branch-centric operational dashboard mirroring vendor operations but focused on branches "
        "(shops/customers). Tracks order pipelines, outstanding receivables, payment health, and "
        "ledger balances per branch."
    )
    pdf.section("Dashboard Elements")
    pdf.bullet("Branch summary table with operational metrics")
    pdf.bullet("Pending orders and awaiting vendor assignment counts")
    pdf.bullet("Open receivable amounts (outstanding invoices)")
    pdf.bullet("Payment collection rate per branch")
    pdf.bullet("Ledger balance and last activity")
    pdf.bullet("Settlement status (Settled, Open, Overdue)")
    pdf.bullet("Click branch for detailed view with orders and ledger")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(screenshots_dir, "06_branch_ops.png"),
        width=165,
        caption="Branch Operations - Track branch receivables and pending orders"
    )

    # ========== 7. BILLS ==========
    pdf.new_page()
    pdf.title("7. Bills & Invoicing")
    pdf.section("Overview")
    pdf.desc(
        "Manage all customer invoices in one place. Create invoices from orders (auto-populated) "
        "or manually. Record payments with optional invoice allocation. Download invoice PDFs. "
        "Track outstanding receivables and payment status."
    )
    pdf.section("Key Features")
    pdf.bullet("Invoice list with search and filters")
    pdf.bullet("Create from Order (pre-filled data) or Manual entry")
    pdf.bullet("GST handling with automatic tax template creation")
    pdf.bullet("Payment recording with allocation to invoices")
    pdf.bullet("Overpayment/surplus detection and tracking")
    pdf.bullet("PDF download of invoices")
    pdf.bullet("Invoice cancellation with cascade handling")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(screenshots_dir, "07_bills.png"),
        width=165,
        caption="Bills - Create invoices, record payments, download PDFs"
    )

    # ========== 8. STOCK MONITORING ==========
    pdf.new_page()
    pdf.title("8. Stock Management")
    pdf.section("Overview")
    pdf.desc(
        "Real-time inventory tracking with low-stock alerting. Monitor quantities on hand across "
        "all items, set reorder thresholds per item, and view inventory by vendor. Thresholds "
        "are saved locally per browser for personalized alerts."
    )
    pdf.section("Key Features")
    pdf.bullet("Complete item inventory with quantities")
    pdf.bullet("Per-item reorder threshold setting")
    pdf.bullet("Low stock status indicator (Low/OK)")
    pdf.bullet("Vendor grouping of inventory")
    pdf.bullet("Summary panel: total items, total qty, low stock count")
    pdf.bullet("Local threshold storage (per-device, not synced)")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(screenshots_dir, "08_stock.png"),
        width=165,
        caption="Stock - Monitor inventory and set reorder thresholds"
    )

    # ========== 9. VENDORS ==========
    pdf.new_page()
    pdf.title("9. Vendors Master Data")
    pdf.section("Overview")
    pdf.desc(
        "Master data management for suppliers. Create and manage vendor records including contact "
        "information, payment terms, and invoice template setup for PDF parsing. Essential for "
        "the order-to-invoice workflow."
    )
    pdf.section("Key Features")
    pdf.bullet("Vendor list with search")
    pdf.bullet("Create/edit vendor records")
    pdf.bullet("Contact information (name, email, phone)")
    pdf.bullet("Payment terms and credit days")
    pdf.bullet("Invoice template management (for PDF parsing)")
    pdf.bullet("Link vendors to items and orders")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(screenshots_dir, "09_vendors.png"),
        width=165,
        caption="Vendors - Manage supplier master data and payment terms"
    )

    # ========== 10. ITEMS ==========
    pdf.new_page()
    pdf.title("10. Items Catalog")
    pdf.section("Overview")
    pdf.desc(
        "Central catalog of all purchasable items. Manage item codes, names, categories, pricing, "
        "and GST rates. Vendors can be associated with items for cost tracking. Used in order "
        "creation and invoicing workflows."
    )
    pdf.section("Key Features")
    pdf.bullet("Complete item master with code, name, description")
    pdf.bullet("Category assignment for easy browsing")
    pdf.bullet("Cost and sell rates per item")
    pdf.bullet("GST rate configuration")
    pdf.bullet("Vendor association for cost tracking")
    pdf.bullet("Search and filter by category or vendor")
    pdf.bullet("Create/edit items for catalog management")
    pdf.ln(2)
    pdf.section("Screenshot")
    pdf.add_screenshot(
        os.path.join(screenshots_dir, "10_items.png"),
        width=165,
        caption="Items - Manage product catalog and pricing"
    )

    # ========== WORKFLOWS ==========
    pdf.new_page()
    pdf.title("End-to-End Workflows")

    pdf.section("Workflow 1: Order to Invoice to Payment")
    pdf.bullet("Step 1: Create Order (Orders > Create Order)")
    pdf.bullet("Step 2: Manage Order (Orders > Select > Assign Vendor > Upload PDF > Capture Bill)")
    pdf.bullet("Step 3: Create Invoice (Bills > Create Invoice > Select Order)")
    pdf.bullet("Step 4: Record Payment (Bills > Payment > Select Invoice > Submit)")
    pdf.ln(2)

    pdf.section("Workflow 2: Monitor Vendor Health")
    pdf.bullet("Step 1: View Vendor Summary (Operations > Vendor Ops)")
    pdf.bullet("Step 2: Click Vendor for Details (Drill-down view)")
    pdf.bullet("Step 3: Review KPIs, Orders, and Ledger")
    pdf.bullet("Step 4: Export Ledger to CSV for Analysis")
    pdf.ln(2)

    pdf.section("Workflow 3: Track Branch Receivables")
    pdf.bullet("Step 1: View Branch Summary (Operations > Branch Ops)")
    pdf.bullet("Step 2: Click Branch for Details (Drill-down view)")
    pdf.bullet("Step 3: Review Pending Orders and Payment Collection Rate")
    pdf.bullet("Step 4: Check Overdue Invoices and Exceptions")
    pdf.ln(2)

    pdf.section("Workflow 4: Setup & Configuration")
    pdf.bullet("Step 1: Add Vendors (Master Data > Vendors)")
    pdf.bullet("Step 2: Add Items (Master Data > Items)")
    pdf.bullet("Step 3: Set Stock Thresholds (Stock > Edit Threshold)")
    pdf.bullet("Step 4: Configure Branches (Master Data > Branches)")

    # ========== FINAL PAGE ==========
    pdf.new_page()
    pdf.set_font("Helvetica", "B", 14)
    pdf.ln(15)
    pdf.cell(0, 6, "Additional Notes", new_x='LMARGIN', new_y='NEXT')
    pdf.set_font("Helvetica", "", 9)
    pdf.ln(3)

    pdf.multi_cell(0, 5,
        "Navigation:\n"
        "The application uses a sidebar for main navigation. All features are organized into "
        "sections: Home, Procure, Inventory, Finance, Master Data, Reports, and Operations. "
        "Access to sections depends on your user role and feature flags.\n\n"
        "Data Persistence:\n"
        "All data is persisted in ERPNext through the Spring Boot middleware. Authentication "
        "tokens are stored locally in the browser for session persistence.\n\n"
        "CSV Exports:\n"
        "Ledger data (vendor and branch) can be exported to CSV for external analysis in Excel "
        "or other tools.\n\n"
        "Real-time Updates:\n"
        "Dashboards and lists are updated in real-time as orders and invoices change status.\n\n"
        "Mobile Responsive:\n"
        "The interface is responsive and works on desktop, tablet, and mobile devices."
    )

    pdf.ln(10)
    pdf.set_font("Helvetica", "I", 8)
    pdf.set_text_color(100, 100, 100)
    pdf.cell(0, 4, f"Documentation generated on {datetime.now().strftime('%B %d, %Y at %H:%M')}", new_x='LMARGIN', new_y='NEXT')

    # Save PDF
    output_path = "/Users/roshninaik/Projects/AAS/AAS_UI_Walkthrough.pdf"
    pdf.output(output_path)
    print(f"\nSUCCESS: UI Documentation PDF created!")
    print(f"File: {output_path}")
    print(f"Pages: {pdf.page_num}")
    print(f"Size: {os.path.getsize(output_path) / 1024:.1f} KB")
    return output_path


if __name__ == "__main__":
    try:
        generate_ui_docs()
    except Exception as e:
        print(f"ERROR: {e}")
        import traceback
        traceback.print_exc()
