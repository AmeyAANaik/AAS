export interface Category {
  name?: string;
  item_group_name?: string;
}

export interface CategoryFormValue {
  categoryName: string;
}

export interface CategoryView {
  id: string;
  name: string;
  raw: Category;
}
