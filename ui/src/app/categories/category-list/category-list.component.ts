import { Component, OnInit } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { Category, CategoryFormValue, CategoryView } from '../category.model';
import { CategoryService } from '../category.service';

@Component({
  selector: 'app-category-list',
  templateUrl: './category-list.component.html',
  styleUrl: './category-list.component.scss'
})
export class CategoryListComponent implements OnInit {
  displayedColumns: string[] = ['name', 'actions'];
  categories: CategoryView[] = [];
  selectedCategory: CategoryView | null = null;
  isLoading = false;
  isSaving = false;
  statusMessage = '';

  constructor(private categoryService: CategoryService) {}

  ngOnInit(): void {
    this.loadCategories();
  }

  loadCategories(): void {
    this.isLoading = true;
    this.categoryService
      .listCategories()
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: categories => {
          this.categories = categories.map(category => this.toViewModel(category));
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to load categories');
        }
      });
  }

  selectCategory(category: CategoryView): void {
    this.selectedCategory = category;
    this.statusMessage = 'Category edits are not available yet.';
  }

  clearSelection(): void {
    this.selectedCategory = null;
    this.statusMessage = '';
  }

  saveCategory(formValue: CategoryFormValue): void {
    this.isSaving = true;
    const payload = { item_group_name: formValue.categoryName.trim() };
    this.categoryService
      .createCategory(payload)
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: () => {
          this.statusMessage = 'Category saved.';
          this.selectedCategory = null;
          this.loadCategories();
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to save category');
        }
      });
  }

  private toViewModel(category: Category): CategoryView {
    const name = String(category.item_group_name ?? category.name ?? '').trim();
    return {
      id: String(category.name ?? name),
      name: name || String(category.name ?? ''),
      raw: category
    };
  }

  private formatError(err: unknown, fallback: string): string {
    if (err instanceof Error) {
      return err.message;
    }
    if (typeof err === 'string') {
      return err;
    }
    return fallback;
  }
}
