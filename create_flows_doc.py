#!/usr/bin/env python3
"""Create comprehensive flows documentation PDF."""

from fpdf import FPDF
from datetime import datetime

class FlowsDocPDF(FPDF):
    def __init__(self):
        super().__init__()
        self.set_margins(10, 10, 10)
        self.set_auto_page_break(auto=True, margin=10)
        self.page_num = 0

    def new_page(self):
        self.page_num += 1
        self.add_page()

    def heading(self, text, size=18):
        self.ln(2)
        self.set_font("Helvetica", "B", size)
        self.set_text_color(0, 51, 102)
        self.cell(0, 8, text, new_x='LMARGIN', new_y='NEXT')
        self.set_text_color(0, 0, 0)
        self.ln(1)

    def subheading(self, text):
        self.ln(2)
        self.set_font("Helvetica", "B", 12)
        self.set_text_color(0, 102, 153)
        self.cell(0, 6, text, new_x='LMARGIN', new_y='NEXT')
        self.set_text_color(0, 0, 0)

    def text(self, content):
        self.set_font("Helvetica", "", 9)
        self.multi_cell(180, 4, content, new_x='LMARGIN', new_y='NEXT')

    def step(self, num, title, description):
        self.ln(1)
        self.set_font("Helvetica", "B", 10)
        self.set_x(15)
        self.cell(180, 5, f"Step {num}: {title}", new_x='LMARGIN', new_y='NEXT')
        self.set_font("Helvetica", "", 8.5)
        self.set_x(25)
        self.multi_cell(165, 4, description)

    def flow_box(self, text):
        self.ln(1)
        self.set_font("Courier", "", 8)
        self.set_fill_color(240, 248, 255)
        lines = text.split('\n')
        for line in lines:
            self.cell(0, 4, line[:90], new_x='LMARGIN', new_y='NEXT', fill=True)
        self.ln(1)

def main():
    pdf = FlowsDocPDF()

    # COVER
    pdf.new_page()
    pdf.ln(40)
    pdf.set_font("Helvetica", "B", 36)
    pdf.set_text_color(0, 51, 102)
    pdf.cell(0, 12, "AAS Comprehensive Flows", new_x='LMARGIN', new_y='NEXT')
    pdf.set_text_color(0, 0, 0)
    pdf.ln(15)
    pdf.set_font("Helvetica", "", 12)
    pdf.text("Complete Step-by-Step Workflows & Business Processes")
    pdf.ln(20)
    pdf.set_font("Helvetica", "", 10)
    pdf.text(f"Generated: {datetime.now().strftime('%B %d, %Y')}")
    pdf.text("Automated Accounting System (AAS)")

    # TABLE OF CONTENTS
    pdf.new_page()
    pdf.heading("Table of Contents", 16)
    toc = [
        "1. Order Workflow - Complete order lifecycle",
        "2. Vendor Operations - Vendor-centric tracking",
        "3. Branch Operations - Branch receivables & health",
        "4. Billing & Invoice - Customer invoicing process",
        "5. Stock Management - Inventory monitoring",
        "6. Key Concepts - Statuses, calculations, errors"
    ]
    for item in toc:
        pdf.set_font("Helvetica", "", 10)
        pdf.set_x(20)
        pdf.cell(0, 6, item, new_x='LMARGIN', new_y='NEXT')

    # FLOW 1: ORDER
    pdf.new_page()
    pdf.heading("1. Order Workflow")
    pdf.text("Core business process from order creation to invoicing.")

    pdf.subheading("Status Pipeline")
    pdf.flow_box("DRAFT => VENDOR_ASSIGNED => VENDOR_PDF_RECEIVED =>\nVENDOR_BILL_CAPTURED => SELL_ORDER_CREATED => INVOICED")

    pdf.step(1, "Create Order", "Navigate Orders > Create Order. Choose image or item mode.\nUpload image/select items. Set branch, company, dates.\nPOST /api/orders or /api/orders/branch-image.\nOrder created in DRAFT status.")

    pdf.step(2, "Assign Vendor", "Click Assign Vendor. Select from dropdown.\nPOST /api/orders/{id}/assign-vendor.\nStatus -> VENDOR_ASSIGNED.")

    pdf.step(3, "Upload Vendor PDF", "Upload vendor bill PDF.\nPOST /api/orders/{id}/vendor-pdf.\nBackend parses using AI: extracts items, qty, rates.\nStatus -> VENDOR_PDF_RECEIVED.")

    pdf.step(4, "Capture Bill", "Review parsed data. Enter: Bill Total, Ref, Date, Transport Charge.\nPOST /api/orders/{id}/vendor-bill.\nStatus -> VENDOR_BILL_CAPTURED.")

    pdf.step(5, "Preview Sell", "GET /api/orders/{id}/sell-preview.\nCheck margin %. Adjust sell rates if needed.")

    pdf.step(6, "Create Sell Order", "POST /api/orders/{id}/sell-order.\nBackend creates Purchase Invoice (cost) & Sales Order (sell).\nStatus -> SELL_ORDER_CREATED.")

    pdf.step(7, "Create Invoice", "Bills > Create Invoice > Select Order.\nAuto-fill: customer, items, rates.\nApply GST if needed. POST /api/invoices.\nOrder Status -> INVOICED.")

    # FLOW 2: VENDOR OPS
    pdf.new_page()
    pdf.heading("2. Vendor Operations Workflow")
    pdf.text("Track vendor performance, pending orders, and financial settlement.")

    pdf.step(1, "View Summary", "Operations > Vendor Ops.\nGET /api/vendor-ops/summary.\nTable: all vendors with pending orders, awaiting PDF/bill, pending amount.")

    pdf.step(2, "Identify Issues", "Look for: high pending, awaiting PDF, awaiting bill, overdue status.\nPrioritize follow-up actions.")

    pdf.step(3, "Drill-Down", "Click vendor name.\nGET /api/vendor-ops/{id}.\nDetailed view: KPIs, template status, exceptions.")

    pdf.step(4, "View Orders", "GET /api/vendor-ops/{id}/orders.\nTable: order ID, PDF status, parsed items, bill total, mismatch flag.")

    pdf.step(5, "Check Ledger", "GET /api/vendor-ops/{id}/ledger.\nDouble-entry ledger: date, voucher, debit, credit, running balance.\nReconcile payments vs invoices.")

    pdf.step(6, "Assess Settlement", "SETTLED = balance ~0. OPEN = money owed. OVERDUE = action needed.\nDecide next step: follow-up payment or investigation.")

    pdf.step(7, "Export Ledger", "GET /api/vendor-ops/{id}/ledger/export.\nDownload CSV. Open in Excel for detailed analysis.")

    # FLOW 3: BRANCH OPS
    pdf.new_page()
    pdf.heading("3. Branch Operations Workflow")
    pdf.text("Track branch orders, receivables, payment health, and settlement.")

    pdf.step(1, "View Summary", "Operations > Branch Ops.\nGET /api/branch-ops/summary.\nTable: all branches with pending orders, receivables, payment rate.")

    pdf.step(2, "Analyze Health", "Look for: high receivables, low payment rate, overdue status, recent activity.\nAssess financial risk.")

    pdf.step(3, "Drill-Down", "Click branch name.\nGET /api/branch-ops/{id}.\nDetailed view: KPIs, billing summary, exceptions, overdue invoices.")

    pdf.step(4, "View Orders", "GET /api/branch-ops/{id}/orders.\nTable: order ID, vendor, status, dates, PDF status, bill/sell totals, invoice link.")

    pdf.step(5, "Check Ledger", "GET /api/branch-ops/{id}/ledger.\nDouble-entry: date, voucher, debit (invoices), credit (payments), balance.\nTrack account reconciliation.")

    pdf.step(6, "Outstanding Analysis", "Sum open invoices = total outstanding. Review overdue.\nCalculate days past due. Determine collection priority.")

    pdf.step(7, "Export & Act", "GET /api/branch-ops/{id}/ledger/export -> CSV.\nExcel analysis. Send payment reminders, adjust terms, escalate if needed.")

    # FLOW 4: BILLING
    pdf.new_page()
    pdf.heading("4. Billing & Invoice Workflow")
    pdf.text("Create customer invoices and record payments.")

    pdf.step(1, "Create from Order", "Bills > Create Invoice. Select order.\nGET /api/orders/{id}. Auto-fill: customer, items, rates.")

    pdf.step(2, "Apply GST", "Optional: toggle GST.\nBackend creates Item Tax Template, GST account.\nCalculates tax per item.")

    pdf.step(3, "Auto Due Date", "Backend: Due Date = Invoice Date + Customer.credit_days.\nUsed for payment tracking.")

    pdf.step(4, "Submit", "POST /api/invoices with { fields: {...} }.\nBackend creates Sales Invoice in ERPNext.")

    pdf.step(5, "Download PDF", "GET /api/invoices/{id}/pdf.\nUses company's aas_sales_invoice_print_format.\nDownloads PDF.")

    pdf.step(6, "Record Payment", "Bills > Record Payment. Select invoice (auto-fill outstanding).\nEnter payment amount. Check for surplus/overpayment.\nPOST /api/payments.")

    pdf.step(7, "Allocation", "Backend allocates min(payment, outstanding) to invoice.\nSurplus stored. Creates Payment Entry in ERPNext.\nOutstanding amount reduced.")

    # FLOW 5: STOCK
    pdf.new_page()
    pdf.heading("5. Stock Management Workflow")
    pdf.text("Monitor inventory and set reorder thresholds.")

    pdf.step(1, "View Stock", "Inventory > Stock.\nGET /api/stock.\nTable: item code, qty, threshold, status (Low/OK).\nSummary: total items, qty, low count.")

    pdf.step(2, "Set Thresholds", "Click item > Edit Threshold. Enter qty threshold.\nSave to localStorage (per-device).\nExample: 20 = alert when qty < 20.")

    pdf.step(3, "Monitor", "System checks: qty <= threshold -> LOW. qty > threshold -> OK.\nLow items highlighted. Count shown in summary.")

    pdf.step(4, "Create Order", "When low, Orders > Create > select low items.\nSubmit order through normal workflow.\nStatus tracked in Vendor/Branch Ops.")

    pdf.step(5, "Receive", "Vendor updates qty in ERPNext.\nQty visible in Stock list. Status -> OK.\nCycle repeats.")

    # KEY CONCEPTS
    pdf.new_page()
    pdf.heading("Key Concepts")

    pdf.subheading("Order Statuses")
    pdf.text("DRAFT: Not confirmed. VENDOR_ASSIGNED: Vendor picked. VENDOR_PDF_RECEIVED: Bill uploaded. VENDOR_BILL_CAPTURED: Bill confirmed. SELL_ORDER_CREATED: Internal documents created. INVOICED: Customer invoice created.")

    pdf.subheading("Settlement Statuses")
    pdf.text("SETTLED: Balance = 0 (all settled). OPEN: Outstanding balance (money owed). OVERDUE: Past due or critical (action needed).")

    pdf.subheading("Key Calculations")
    pdf.text("Margin % = ((Sell - Cost) / Cost) * 100. Outstanding = Total - Paid. Days Overdue = Today - Due. Balance = Previous + Current.")

    output = "/Users/roshninaik/Projects/AAS/AAS_Comprehensive_Flows.pdf"
    pdf.output(output)

    import os
    size = os.path.getsize(output) / 1024
    print(f"\nSUCCESS: AAS_Comprehensive_Flows.pdf ({size:.0f} KB, {pdf.page_num} pages)")

if __name__ == "__main__":
    main()
