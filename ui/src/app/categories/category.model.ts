export interface Category {
  name?: string;
  item_group_name?: string;
  aas_category_code?: string;
}

export interface CategoryFormValue {
  categoryName: string;
  categoryCode: string;
}

export interface CategoryView {
  id: string;
  name: string;
  code: string;
  raw: Category;
}
