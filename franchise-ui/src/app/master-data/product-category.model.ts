export type ProductCategoryStatus = 'Active' | 'Inactive';

export interface ProductCategoryRecord {
  id: string;
  name: string;
  code: string;
  description: string;
  status: ProductCategoryStatus;
  system?: boolean;
  sortOrder: number;
}

export interface ProductCategoryInput {
  name: string;
  code: string;
  description: string;
  status: ProductCategoryStatus;
}
