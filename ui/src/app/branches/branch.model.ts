export interface Branch {
  name?: string;
  customer_name?: string;
  aas_branch_location?: string;
  aas_whatsapp_group_name?: string;
  aas_credit_days?: number;
  aas_invoice_email?: string;
  aas_whatsapp_number?: string;
  disabled?: number | boolean;
}

export interface BranchMetadata {
  location?: string;
  whatsappGroupName?: string;
  invoiceEmail?: string;
  whatsappNumber?: string;
}

export interface BranchFormValue {
  branchName: string;
  location: string;
  whatsappGroupName: string;
  invoiceEmail: string;
  whatsappNumber: string;
  creditDays: number | null;
}

export interface BranchView {
  id: string;
  name: string;
  location: string;
  whatsappGroupName: string;
  invoiceEmail: string;
  whatsappNumber: string;
  creditDays: number | null;
  raw: Branch;
}
