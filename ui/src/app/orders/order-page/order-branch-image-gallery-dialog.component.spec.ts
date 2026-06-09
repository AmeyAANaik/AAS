import { of } from 'rxjs';
import { OrderService } from '../order.service';
import { OrderBranchImageGalleryDialogComponent } from './order-branch-image-gallery-dialog.component';

describe('OrderBranchImageGalleryDialogComponent', () => {
  let orderService: jasmine.SpyObj<OrderService>;
  let dialogRef: jasmine.SpyObj<any>;
  let component: OrderBranchImageGalleryDialogComponent;
  const image = {
    id: 'FILE-1',
    file_name: 'branch_order.jpeg',
    file_url: '/api/private/files/branch_order.jpeg'
  };

  beforeEach(() => {
    orderService = jasmine.createSpyObj('OrderService', ['downloadFile']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    orderService.downloadFile.and.returnValue(of(new Blob(['image'], { type: 'image/jpeg' })));
    spyOn(URL, 'createObjectURL').and.returnValue('blob:branch-order');
    spyOn(URL, 'revokeObjectURL');

    component = new OrderBranchImageGalleryDialogComponent(
      { orderId: 'SO-1', images: [image] },
      dialogRef,
      orderService
    );
  });

  it('loads branch images as authenticated blobs', () => {
    component.ngOnInit();

    expect(orderService.downloadFile).toHaveBeenCalledWith('/api/private/files/branch_order.jpeg');
    expect(component.imageSrc(image)).toBe('blob:branch-order');
  });

  it('opens the loaded blob URL in a new tab', () => {
    const openSpy = spyOn(window, 'open');

    component.ngOnInit();
    component.openCurrentInNewTab();

    expect(openSpy).toHaveBeenCalledWith('blob:branch-order', '_blank', 'noopener,noreferrer');
  });

  it('revokes loaded object URLs on destroy', () => {
    component.ngOnInit();
    component.ngOnDestroy();

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:branch-order');
  });
});
