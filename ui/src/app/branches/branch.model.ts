export interface Branch {
  name?: string;
  customer_name?: string;
  aas_branch_location?: string;
  aas_whatsapp_group_name?: string;
  aas_credit_days?: number;
  aas_invoice_email?: string;
  aas_whatsapp_number?: string;
  tax_id?: string;
  aas_food_license_no?: string;
  aas_is_deleted?: number | boolean;
  aas_deleted_at?: string;
  disabled?: number | boolean;
}

export interface BranchMetadata {
  location?: string;
  whatsappGroupName?: string;
  invoiceEmail?: string;
  whatsappNumber?: string;
  taxId?: string;
  fssaiNo?: string;
}

export interface BranchFormValue {
  branchName: string;
  location: string;
  whatsappGroupName: string;
  invoiceEmail: string;
  whatsappNumber: string;
  creditDays: number | null;
  taxId: string;
  fssaiNo: string;
}

export interface BranchView {
  id: string;
  name: string;
  location: string;
  whatsappGroupName: string;
  invoiceEmail: string;
  whatsappNumber: string;
  creditDays: number | null;
  taxId: string;
  fssaiNo: string;
  disabled: boolean;
  isDeleted: boolean;
  raw: Branch;
}
