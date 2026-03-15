import { Location } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { CategoryService } from '../../categories/category.service';
import { ItemService } from '../../items/item.service';
import { OrderService } from '../order.service';
import { OrderCreateComponent } from './order-create.component';

describe('OrderCreateComponent', () => {
  let component: OrderCreateComponent;
  let fixture: ComponentFixture<OrderCreateComponent>;
  let orderService: jasmine.SpyObj<OrderService>;
  let categoryService: jasmine.SpyObj<CategoryService>;
  let itemService: jasmine.SpyObj<ItemService>;
  let location: jasmine.SpyObj<Location>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    orderService = jasmine.createSpyObj('OrderService', [
      'createOrder',
      'createOrderFromBranchImage',
      'uploadOrderImage',
      'listBranches',
      'listCompanies'
    ]);
    orderService.createOrder.and.returnValue(of({ name: 'Shop_A_Grocery_20260315' }));
    orderService.createOrderFromBranchImage.and.returnValue(of({ name: 'Shop_A_Grocery_20260315' }));
    orderService.uploadOrderImage.and.returnValue(of({}));
    orderService.listBranches.and.returnValue(of([{ name: 'SHOP-1', customer_name: 'Shop A' }]));
    orderService.listCompanies.and.returnValue(of([{ name: 'AAS' }]));

    categoryService = jasmine.createSpyObj('CategoryService', ['listCategories']);
    categoryService.listCategories.and.returnValue(of([{ name: 'Grocery', item_group_name: 'Grocery' }]));

    itemService = jasmine.createSpyObj('ItemService', ['listItems']);
    itemService.listItems.and.returnValue(of([]));

    location = jasmine.createSpyObj('Location', ['back']);
    router = jasmine.createSpyObj('Router', ['navigate']);
    router.navigate.and.returnValue(Promise.resolve(true));

    await TestBed.configureTestingModule({
      declarations: [OrderCreateComponent],
      imports: [
        ReactiveFormsModule,
        MatButtonModule,
        MatCardModule,
        MatCheckboxModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
        MatSelectModule,
        NoopAnimationsModule
      ],
      providers: [
        { provide: OrderService, useValue: orderService },
        { provide: CategoryService, useValue: categoryService },
        { provide: ItemService, useValue: itemService },
        { provide: Location, useValue: location },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrderCreateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the order create component', () => {
    expect(component).toBeTruthy();
  });

  it('loads branches into shops', () => {
    expect(orderService.listBranches).toHaveBeenCalled();
    expect(component.shops).toEqual([{ id: 'SHOP-1', name: 'Shop A' }]);
  });

  it('loads categories and companies', () => {
    expect(categoryService.listCategories).toHaveBeenCalled();
    expect(orderService.listCompanies).toHaveBeenCalled();
    expect(component.categories).toEqual([{ id: 'Grocery', name: 'Grocery' }]);
    expect(component.companies).toEqual([{ id: 'AAS', name: 'AAS' }]);
  });
});
