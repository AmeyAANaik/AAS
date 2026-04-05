#!/usr/bin/env python3
"""
AAS Application Flow Documentation Generator
Generates a comprehensive PDF explaining the AAS system architecture and workflows.
"""

from fpdf import FPDF
from datetime import datetime

class AASDocs(FPDF):
    """Custom PDF class for AAS documentation."""

    def __init__(self):
        super().__init__()
        self.set_margins(12, 12, 12)
        self.set_auto_page_break(auto=True, margin=12)
        self.set_font("Helvetica", size=9)
        self.page_num = 0

    def header(self):
        """Page header with page number."""
        if self.page_num > 1:
            self.set_font("Helvetica", "I", 7)
            self.set_text_color(150, 150, 150)
            self.cell(0, 6, f"AAS Application Flow Documentation | Page {self.page_num}", new_x='LMARGIN', new_y='NEXT')

    def footer(self):
        """Page footer."""
        pass

    def new_page(self):
        """Add a new page and increment counter."""
        self.page_num += 1
        self.add_page()

    def section_title(self, text):
        """Section title."""
        self.ln(4)
        self.set_font("Helvetica", "B", 14)
        self.set_text_color(0, 51, 102)
        self.cell(0, 8, text, new_x='LMARGIN', new_y='NEXT')
        self.set_text_color(0, 0, 0)
        self.ln(2)

    def subsection_title(self, text):
        """Subsection title."""
        self.ln(3)
        self.set_font("Helvetica", "B", 11)
        self.set_text_color(51, 102, 153)
        self.cell(0, 7, text, new_x='LMARGIN', new_y='NEXT')
        self.set_text_color(0, 0, 0)

    def paragraph(self, text):
        """Paragraph of text."""
        self.set_font("Helvetica", "", 9)
        self.multi_cell(0, 5, text)
        self.ln(1)

    def bullet_item(self, text):
        """Single bullet item."""
        self.set_font("Helvetica", "", 8.5)
        self.set_x(18)
        self.multi_cell(168, 4, "- " + text)
        self.ln(0.5)

    def diagram_line(self, text):
        """Diagram line."""
        self.set_font("Courier", "", 7.5)
        self.set_x(15)
        self.cell(170, 3.5, text[:70], new_x='LMARGIN', new_y='NEXT')


def generate_pdf():
    """Generate the complete AAS documentation PDF."""
    pdf = AASDocs()

    # ========== COVER PAGE ==========
    pdf.new_page()
    pdf.ln(25)
    pdf.set_font("Helvetica", "B", 28)
    pdf.set_text_color(0, 51, 102)
    pdf.cell(0, 12, "AAS", new_x='LMARGIN', new_y='NEXT')
    pdf.set_font("Helvetica", "B", 16)
    pdf.cell(0, 8, "Automated Accounting System", new_x='LMARGIN', new_y='NEXT')
    pdf.set_text_color(0, 0, 0)
    pdf.ln(10)
    pdf.set_font("Helvetica", "I", 11)
    pdf.cell(0, 6, "Application Flow & User Guide", new_x='LMARGIN', new_y='NEXT')
    pdf.ln(15)
    pdf.set_font("Helvetica", "", 9)
    pdf.cell(0, 5, f"Generated: {datetime.now().strftime('%B %d, %Y')}", new_x='LMARGIN', new_y='NEXT')
    pdf.cell(0, 5, "Version: 1.0", new_x='LMARGIN', new_y='NEXT')
    pdf.ln(5)
    pdf.multi_cell(0, 5,
        "A comprehensive guide to the AAS system architecture, user workflows, and integration patterns.")

    # ========== TABLE OF CONTENTS ==========
    pdf.new_page()
    pdf.section_title("Table of Contents")
    toc_items = [
        "1. System Overview",
        "2. Authentication & Login Flow",
        "3. Application Shell & Navigation",
        "4. Dashboard",
        "5. Orders Workflow",
        "6. Vendor Operations",
        "7. Branch Operations",
        "8. Bills & Invoicing",
        "9. Master Data",
        "10. Stock Monitoring",
        "11. API Reference",
    ]
    for item in toc_items:
        pdf.bullet_item(item)

    # ========== SECTION 1: SYSTEM OVERVIEW ==========
    pdf.new_page()
    pdf.section_title("1. System Overview")

    pdf.subsection_title("What is AAS?")
    pdf.paragraph(
        "AAS (Automated Accounting System) is a comprehensive procurement, inventory, and finance "
        "management platform designed for multi-branch businesses. It streamlines order creation, "
        "vendor management, inventory tracking, and invoice generation while integrating with ERPNext."
    )

    pdf.subsection_title("Architecture")
    pdf.diagram_line("Angular UI (Frontend)")
    pdf.diagram_line("      |")
    pdf.diagram_line("Spring Boot Middleware (Business Logic)")
    pdf.diagram_line("      |")
    pdf.diagram_line("ERPNext (Data Layer)")
    pdf.ln(2)

    pdf.subsection_title("Key Features")
    features = [
        "Dual-mode order creation: Image-based (OCR) or catalog-based",
        "Vendor PDF parsing with AI-assisted template generation",
        "Automated invoice creation with GST support",
        "Role-based access control via feature flags",
        "Real-time dashboards: Orders, Vendors, Branches, Stock",
        "Multi-branch operational views with ledger tracking",
        "Payment allocation and receivables management",
    ]
    for feature in features:
        pdf.bullet_item(feature)

    # ========== SECTION 2: AUTHENTICATION ==========
    pdf.new_page()
    pdf.section_title("2. Authentication & Login Flow")

    pdf.subsection_title("Login Process")
    pdf.paragraph("User navigates to /login -> Enters credentials -> POST /api/auth/login -> "
        "JWT token returned -> Stored in localStorage -> Redirected to dashboard")
    pdf.ln(2)

    pdf.subsection_title("Token Storage")
    storage_items = [
        "aas_auth_token: JWT bearer token",
        "aas_auth_role: User role (admin, shop, etc)",
        "aas_auth_features: Array of feature flags",
        "aas_auth_home_route: Default route for user's role",
    ]
    for item in storage_items:
        pdf.bullet_item(item)

    pdf.subsection_title("Security Guards")
    pdf.bullet_item("authGuard: Checks if token exists. If missing, redirects to /login")
    pdf.bullet_item("featureGuard: Calls GET /api/me to fetch user profile. Validates feature "
        "permissions. If unauthorized, bounces to user's homeRoute")
    pdf.ln(2)

    pdf.subsection_title("Logout")
    pdf.paragraph("User clicks Logout -> AuthTokenService clears all localStorage keys -> "
        "Redirects to /login")

    # ========== SECTION 3: APP SHELL ==========
    pdf.new_page()
    pdf.section_title("3. Application Shell & Navigation")

    pdf.subsection_title("Layout")
    pdf.paragraph("Header: Company logo, branch info, user profile")
    pdf.paragraph("Sidebar: Feature-filtered navigation tree (modules user has access to)")
    pdf.paragraph("Main Content: Currently selected module")
    pdf.ln(1)

    pdf.subsection_title("Dynamic Sidebar")
    pdf.paragraph("On init, buildNavSections() filters the full nav tree to only show modules "
        "matching user's feature flags. This ensures role-based visibility.")
    pdf.ln(1)

    pdf.subsection_title("Navigation Sections")
    sections = [
        "Home: Dashboard",
        "Procure: Orders, Vendors",
        "Inventory: Stock, Items",
        "Finance: Bills, Payments",
        "Master Data: Vendors, Branches, Categories, Items",
        "Reports: Various reports",
        "Operations: Vendor Ops, Branch Ops",
    ]
    for section in sections:
        pdf.bullet_item(section)

    # ========== SECTION 4: DASHBOARD ==========
    pdf.new_page()
    pdf.section_title("4. Dashboard")

    pdf.subsection_title("Overview")
    pdf.paragraph("Snapshot of operations for current calendar month with multiple KPI cards.")
    pdf.ln(1)

    pdf.subsection_title("KPIs Displayed")
    kpis = [
        "Order Status Breakdown: Count by status",
        "Sales Summary: Invoice count, total revenue",
        "Stock Snapshot: Item count, total quantity",
        "Billing by Vendor: Top vendors by amount",
        "Billing by Branch: Top branches by amount",
        "Vendor Operations: 6 KPIs (total vendors, pending orders, etc)",
        "Branch Operations: 6 KPIs (total branches, pending orders, etc)",
    ]
    for kpi in kpis:
        pdf.bullet_item(kpi)

    # ========== SECTION 5: ORDERS WORKFLOW ==========
    pdf.new_page()
    pdf.section_title("5. Orders Workflow")
    pdf.paragraph("(Current Development Branch: order_workflow_branch)")

    pdf.subsection_title("Order Status Pipeline")
    statuses = ["DRAFT", "VENDOR_ASSIGNED", "VENDOR_PDF_RECEIVED", "VENDOR_BILL_CAPTURED",
                "SELL_ORDER_CREATED", "INVOICED"]
    for i, status in enumerate(statuses):
        pdf.set_x(20)
        pdf.set_font("Courier", "B", 8)
        pdf.cell(150, 4, status, new_x='LMARGIN', new_y='NEXT')
        if i < len(statuses) - 1:
            pdf.diagram_line("    |")
    pdf.ln(2)

    pdf.subsection_title("Order Creation: Mode 1 (Image-Based)")
    items = [
        "Upload photos of branch order sheets",
        "Backend uses OCR/AI to parse items",
        "Best for: Existing physical order sheets",
    ]
    for item in items:
        pdf.bullet_item(item)

    pdf.subsection_title("Order Creation: Mode 2 (Catalog-Based)")
    items = [
        "Browse items from catalog, filtered by category",
        "Items pre-populated with vendor pricing",
        "Submit structured item list",
        "Best for: Known items, precise quantities",
    ]
    for item in items:
        pdf.bullet_item(item)

    pdf.subsection_title("Order Management")
    mgmt_items = [
        "Assign Vendor: Link order to supplier",
        "Upload Vendor PDF: Scan/upload vendor invoice",
        "Parse PDF: Backend AI extracts line items",
        "Capture Bill: Enter vendor bill total, reference, date",
        "Sell Preview: View margin before selling",
        "Create Sell Order: Creates Purchase Invoice in ERPNext",
    ]
    for item in mgmt_items:
        pdf.bullet_item(item)

    # ========== SECTION 6: VENDOR OPERATIONS ==========
    pdf.new_page()
    pdf.section_title("6. Vendor Operations")

    pdf.subsection_title("Summary View")
    pdf.paragraph("Table showing all vendors with pending order counts, PDF status, bill "
        "capture status, pending amount, last activity, and ledger balance.")
    pdf.ln(1)

    pdf.subsection_title("Per-Vendor Drill-Down")
    drill_items = [
        "KPIs: Pending orders, PDF status, bill status",
        "Template Status: Invoice parsing template setup",
        "Billing Summary: Bills captured, outstanding amount",
        "Exceptions: Mismatches, parse failures, overdue PDFs",
        "Orders Table: Status and details per order",
        "Ledger: Double-entry with running balance",
    ]
    for item in drill_items:
        pdf.bullet_item(item)

    pdf.subsection_title("Settlement States")
    states = [
        "Settled: Ledger balance ~0",
        "Open: Outstanding balance exists",
        "Overdue: Unpaid invoices or overdue PDFs",
    ]
    for state in states:
        pdf.bullet_item(state)

    pdf.paragraph("CSV export available for ledger analysis.")

    # ========== SECTION 7: BRANCH OPERATIONS ==========
    pdf.new_page()
    pdf.section_title("7. Branch Operations")

    pdf.subsection_title("Overview")
    pdf.paragraph("Branch-centric operational view tracking order pipelines, receivables, "
        "and payment health.")
    pdf.ln(1)

    pdf.subsection_title("Summary Metrics")
    metrics = [
        "Pending Orders",
        "Awaiting Vendor Assignment",
        "Open Receivables",
        "Payment Collection Rate",
        "Ledger Balance",
        "Last Activity",
    ]
    for metric in metrics:
        pdf.bullet_item(metric)

    pdf.subsection_title("Per-Branch View")
    branch_items = [
        "KPIs: Pending orders, receivables, payment rate",
        "Billing Summary: Invoices, payments, balance",
        "Exceptions: Unassigned orders, overdue invoices",
        "Orders Table: Full order details per branch",
        "Ledger: Transaction history with running balance",
    ]
    for item in branch_items:
        pdf.bullet_item(item)

    # ========== SECTION 8: BILLS & INVOICING ==========
    pdf.new_page()
    pdf.section_title("8. Bills & Invoicing")

    pdf.subsection_title("Invoice Creation: From Order")
    pdf.bullet_item("Select order -> Pre-fills customer, items, quantities")
    pdf.bullet_item("Toggle GST, adjust rates, set rounding")
    pdf.bullet_item("Submit -> Creates Sales Invoice in ERPNext")
    pdf.ln(1)

    pdf.subsection_title("Invoice Creation: Manual")
    pdf.bullet_item("Select customer and company")
    pdf.bullet_item("Manually add line items")
    pdf.bullet_item("Apply GST and taxes")
    pdf.bullet_item("Submit -> Creates Sales Invoice")
    pdf.ln(1)

    pdf.subsection_title("GST Handling")
    pdf.bullet_item("Auto-creates Item Tax Template (AAS GST X%)")
    pdf.bullet_item("Creates GST account under Duties & Taxes")
    pdf.bullet_item("Applies taxes_and_charges template")
    pdf.ln(1)

    pdf.subsection_title("Payment Recording")
    pdf.bullet_item("Optionally link to invoice (auto-fills outstanding)")
    pdf.bullet_item("Enter payment amount")
    pdf.bullet_item("Calculates balance_after_payment and surplus")
    pdf.bullet_item("Submit -> Creates Payment Entry in ERPNext")
    pdf.ln(1)

    pdf.subsection_title("Invoice Deletion")
    pdf.bullet_item("Finds all linked Payment Entries")
    pdf.bullet_item("Cancels and deletes payment entries")
    pdf.bullet_item("Cancels and deletes invoice")
    pdf.bullet_item("Graceful handling of GL entry retention")

    # ========== SECTION 9: MASTER DATA ==========
    pdf.new_page()
    pdf.section_title("9. Master Data")

    pdf.subsection_title("Vendors (Suppliers)")
    pdf.bullet_item("Supplier records and management")
    pdf.bullet_item("Invoice template setup for PDF parsing")
    pdf.bullet_item("Payment terms and contact details")
    pdf.ln(1)

    pdf.subsection_title("Branches (Shops/Customers)")
    pdf.bullet_item("Branch records and management")
    pdf.bullet_item("Credit days for invoicing")
    pdf.bullet_item("Location, contact, default currency")
    pdf.ln(1)

    pdf.subsection_title("Categories")
    pdf.bullet_item("Item grouping for easier browsing")
    pdf.bullet_item("Used in order creation filters")
    pdf.ln(1)

    pdf.subsection_title("Items (Catalog)")
    pdf.bullet_item("Item code, name, category, vendor")
    pdf.bullet_item("Cost and sell rates")
    pdf.bullet_item("GST rate per item")
    pdf.bullet_item("Vendor-specific pricing")

    # ========== SECTION 10: STOCK MONITORING ==========
    pdf.new_page()
    pdf.section_title("10. Stock Monitoring")

    pdf.subsection_title("Item Tracking")
    pdf.bullet_item("Item ID, code, name, vendor")
    pdf.bullet_item("Current quantity on hand")
    pdf.bullet_item("Reorder threshold (per-item)")
    pdf.bullet_item("Status: Low (below threshold) or OK")
    pdf.ln(1)

    pdf.subsection_title("Summary Panel")
    pdf.bullet_item("Total Items: count of all items")
    pdf.bullet_item("Total Quantity: sum of quantities")
    pdf.bullet_item("Low Stock Count: items below threshold")
    pdf.bullet_item("Vendor Breakdown: items grouped by vendor")
    pdf.ln(1)

    pdf.subsection_title("Threshold Management")
    pdf.paragraph("Thresholds stored in browser localStorage (key: aas_stock_thresholds). "
        "Per-device, not synced across browsers. Items with null threshold are not alerted.")

    # ========== SECTION 11: API REFERENCE ==========
    pdf.new_page()
    pdf.section_title("11. API Reference")

    pdf.subsection_title("Authentication")
    pdf.bullet_item("POST /api/auth/login (username, password)")
    pdf.bullet_item("GET /api/me (user profile + features)")
    pdf.ln(1)

    pdf.subsection_title("Orders")
    orders = [
        "GET /api/orders (list with filters)",
        "POST /api/orders (create from items)",
        "POST /api/orders/branch-image (create from image)",
        "POST /api/orders/{id}/assign-vendor",
        "POST /api/orders/{id}/vendor-pdf (upload PDF)",
        "POST /api/orders/{id}/vendor-bill (capture bill)",
        "GET /api/orders/{id}/sell-preview",
        "POST /api/orders/{id}/sell-order",
    ]
    for endpoint in orders:
        pdf.bullet_item(endpoint)
    pdf.ln(1)

    pdf.subsection_title("Invoices & Payments")
    invoice_payments = [
        "GET /api/invoices (list invoices)",
        "POST /api/invoices (create invoice)",
        "GET /api/invoices/{id}/pdf (download PDF)",
        "DELETE /api/invoices/{id}",
        "POST /api/payments (record payment)",
    ]
    for endpoint in invoice_payments:
        pdf.bullet_item(endpoint)
    pdf.ln(1)

    pdf.subsection_title("Vendor Operations")
    vendor_ops = [
        "GET /api/vendor-ops/summary",
        "GET /api/vendor-ops/{id}",
        "GET /api/vendor-ops/{id}/orders",
        "GET /api/vendor-ops/{id}/ledger",
    ]
    for endpoint in vendor_ops:
        pdf.bullet_item(endpoint)
    pdf.ln(1)

    pdf.subsection_title("Branch Operations")
    branch_ops = [
        "GET /api/branch-ops/summary",
        "GET /api/branch-ops/{id}",
        "GET /api/branch-ops/{id}/orders",
        "GET /api/branch-ops/{id}/ledger",
    ]
    for endpoint in branch_ops:
        pdf.bullet_item(endpoint)

    # ========== WORKFLOWS ==========
    pdf.new_page()
    pdf.section_title("Common Workflows")

    pdf.subsection_title("Workflow 1: Create Order to Invoice to Payment")
    workflow1 = [
        "Orders -> Create Order (upload image or select items)",
        "Orders -> Manage (assign vendor, upload PDF, capture bill)",
        "Bills -> Create Invoice (from order snapshot)",
        "Bills -> Payment (record payment with allocation)",
    ]
    for step in workflow1:
        pdf.bullet_item(step)
    pdf.ln(2)

    pdf.subsection_title("Workflow 2: Monitor Vendor Health")
    workflow2 = [
        "Operations -> Vendor Ops (view summary of all vendors)",
        "Click vendor (drill-down view with KPIs and exceptions)",
        "Review pending orders, PDF status, ledger balance",
        "Export ledger to CSV for analysis",
    ]
    for step in workflow2:
        pdf.bullet_item(step)
    pdf.ln(2)

    pdf.subsection_title("Workflow 3: Track Branch Receivables")
    workflow3 = [
        "Operations -> Branch Ops (view all branches)",
        "Click branch (drill-down with KPIs and invoices)",
        "Review payment collection rate and overdue invoices",
        "Export branch ledger to CSV",
    ]
    for step in workflow3:
        pdf.bullet_item(step)

    # ========== FINAL PAGE ==========
    pdf.new_page()
    pdf.set_font("Helvetica", "B", 12)
    pdf.ln(20)
    pdf.cell(0, 6, "End of Documentation", new_x='LMARGIN', new_y='NEXT')
    pdf.set_font("Helvetica", "", 9)
    pdf.ln(5)
    pdf.multi_cell(0, 5,
        f"This comprehensive guide covers the AAS system architecture, user workflows, "
        f"and API reference. For questions, contact the development team.\n\n"
        f"Generated: {datetime.now().strftime('%B %d, %Y at %H:%M')}")

    # Save PDF
    output_path = "/Users/roshninaik/Projects/AAS/AAS_Application_Flow.pdf"
    pdf.output(output_path)
    print(f"\nSUCCESS: PDF generated -> {output_path}")
    print(f"Total pages: {pdf.page_num}")
    return output_path


if __name__ == "__main__":
    try:
        generate_pdf()
    except Exception as e:
        print(f"ERROR: {e}")
        import traceback
        traceback.print_exc()
