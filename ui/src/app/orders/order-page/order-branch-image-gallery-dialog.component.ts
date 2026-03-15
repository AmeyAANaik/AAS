import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { OrderBranchImage } from '../order.model';

interface OrderBranchImageGalleryData {
  orderId: string;
  images: OrderBranchImage[];
}

@Component({
  selector: 'app-order-branch-image-gallery-dialog',
  templateUrl: './order-branch-image-gallery-dialog.component.html',
  styleUrl: './order-branch-image-gallery-dialog.component.scss'
})
export class OrderBranchImageGalleryDialogComponent {
  selectedIndex = 0;

  constructor(
    @Inject(MAT_DIALOG_DATA) readonly data: OrderBranchImageGalleryData,
    private readonly dialogRef: MatDialogRef<OrderBranchImageGalleryDialogComponent>
  ) {}

  get images(): OrderBranchImage[] {
    return this.data.images ?? [];
  }

  get selectedImage(): OrderBranchImage | null {
    return this.images[this.selectedIndex] ?? null;
  }

  selectImage(index: number): void {
    if (index < 0 || index >= this.images.length) {
      return;
    }
    this.selectedIndex = index;
  }

  openCurrentInNewTab(): void {
    const image = this.selectedImage;
    if (!image?.file_url) {
      return;
    }
    window.open(image.file_url, '_blank', 'noopener,noreferrer');
  }

  close(): void {
    this.dialogRef.close();
  }
}
