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
  isFormOpen = false;
  isLoading = false;
  isSaving = false;
  isDeleting = false;
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
    this.isFormOpen = true;
    this.statusMessage = '';
  }

  openCreate(): void {
    this.selectedCategory = null;
    this.isFormOpen = true;
    this.statusMessage = '';
  }

  clearSelection(): void {
    this.selectedCategory = null;
    this.isFormOpen = false;
    this.statusMessage = '';
  }

  saveCategory(formValue: CategoryFormValue): void {
    this.isSaving = true;
    const payload = { item_group_name: formValue.categoryName.trim() };
    const request$ = this.selectedCategory
      ? this.categoryService.updateCategory(this.selectedCategory.id, payload)
      : this.categoryService.createCategory(payload);
    request$.pipe(finalize(() => (this.isSaving = false))).subscribe({
      next: () => {
        this.statusMessage = this.selectedCategory ? 'Category updated.' : 'Category saved.';
        this.selectedCategory = null;
        this.isFormOpen = false;
        this.loadCategories();
      },
      error: err => {
        this.statusMessage = this.formatError(err, 'Unable to save category');
      }
    });
  }

  deleteCategory(category: CategoryView): void {
    const categoryId = category.id?.trim();
    if (!categoryId || this.isDeleting) {
      return;
    }
    const confirmed = window.confirm(`Delete category "${category.name}"? This works only when no items or child categories use it.`);
    if (!confirmed) {
      return;
    }
    this.isDeleting = true;
    this.categoryService
      .deleteCategory(categoryId)
      .pipe(finalize(() => (this.isDeleting = false)))
      .subscribe({
        next: () => {
          this.statusMessage = 'Category deleted.';
          if (this.selectedCategory?.id === categoryId) {
            this.clearSelection();
          }
          this.loadCategories();
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to delete category');
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
