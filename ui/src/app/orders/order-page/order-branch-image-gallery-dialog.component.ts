import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subscription } from 'rxjs';
import { OrderBranchImage } from '../order.model';
import { OrderService } from '../order.service';

interface OrderBranchImageGalleryData {
  orderId: string;
  images: OrderBranchImage[];
}

@Component({
  selector: 'app-order-branch-image-gallery-dialog',
  templateUrl: './order-branch-image-gallery-dialog.component.html',
  styleUrl: './order-branch-image-gallery-dialog.component.scss'
})
export class OrderBranchImageGalleryDialogComponent implements OnInit, OnDestroy {
  selectedIndex = 0;
  private readonly imageUrls = new Map<string, string>();
  private readonly subscriptions = new Subscription();

  constructor(
    @Inject(MAT_DIALOG_DATA) readonly data: OrderBranchImageGalleryData,
    private readonly dialogRef: MatDialogRef<OrderBranchImageGalleryDialogComponent>,
    private readonly orderService: OrderService
  ) {}

  ngOnInit(): void {
    this.images.forEach(image => this.loadImage(image));
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.imageUrls.forEach(url => URL.revokeObjectURL(url));
    this.imageUrls.clear();
  }

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

  imageSrc(image: OrderBranchImage): string {
    return this.imageUrls.get(this.imageKey(image)) ?? image.file_url;
  }

  openCurrentInNewTab(): void {
    const image = this.selectedImage;
    if (!image?.file_url) {
      return;
    }
    window.open(this.imageSrc(image), '_blank', 'noopener,noreferrer');
  }

  close(): void {
    this.dialogRef.close();
  }

  private loadImage(image: OrderBranchImage): void {
    const key = this.imageKey(image);
    if (!image.file_url || this.imageUrls.has(key)) {
      return;
    }
    this.subscriptions.add(
      this.orderService.downloadFile(image.file_url).subscribe({
        next: blob => {
          const previousUrl = this.imageUrls.get(key);
          if (previousUrl) {
            URL.revokeObjectURL(previousUrl);
          }
          this.imageUrls.set(key, URL.createObjectURL(blob));
        },
        error: () => {
          this.imageUrls.delete(key);
        }
      })
    );
  }

  private imageKey(image: OrderBranchImage): string {
    return image.id || image.file_url;
  }
}
