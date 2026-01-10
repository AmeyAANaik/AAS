# ERPNext Restaurant Workflow Documentation

## System Overview

The ERPNext Restaurant Inventory Management System implements a three-tier procurement and sales workflow. This document describes the complete workflow and transaction flow.

## Transaction Flow Diagram

```
Supplier (Vendor)
    |
    | Creates Purchase Order
    |
    v
[Purchase Order]
(PUR-ORD-2026-00001)
    |
    | Receive Items
    |
    v
[Purchase Receipt]
(MAT-PRE-2026-00001)
    |
    | Stock Updated
    |
    v
[Warehouse Inventory]
    |
    | Select Items
    |
    v
[Sales Order]
(SAL-ORD-2026-00001)
    |
    | Deliver Items
    |
    v
[Customer/Shop]
(Paradise Restaurant)
```

## Detailed Workflow Steps

### Phase 1: Purchasing (Procurement)

#### Step 1: Create Purchase Order
- **Module**: Buying > Purchase Order
- **Document**: PUR-ORD-2026-00001
- **Key Fields**:
  - Supplier: Fresh Farm Vegetables
  - Date: 2026-01-01
  - Required By Date: 2026-01-03
  - Items: Tomato 50 Kg @ 50 INR
- **Status**: Submitted
- **Total Amount**: 2,500 INR

**Business Logic**:
- Specifies items needed from vendor
- Sets quantity and delivery date expectations
- Enables vendor to prepare shipment

#### Step 2: Receive Purchase Receipt
- **Module**: Stock > Purchase Receipt
- **Document**: MAT-PRE-2026-00001
- **Key Fields**:
  - Supplier: Fresh Farm Vegetables
  - Date: 2026-01-02
  - Items: Tomato 50 Kg @ 50 INR
- **Status**: Submitted
- **Total Amount**: 2,500 INR

**Business Logic**:
- Confirms physical receipt of ordered items
- Updates warehouse stock automatically
- Creates stock ledger entries
- Validates received quantity against PO

### Phase 2: Inventory Management

#### Warehouse Stock Update
After Purchase Receipt submission:
- Warehouse: Default Warehouse
- Item: TOMATO-001
- New Stock: 50 Kg
- Warehouse Cost: 2,500 INR

### Phase 3: Sales (Outbound)

#### Step 3: Create Sales Order
- **Module**: Selling > Sales Order
- **Document**: SAL-ORD-2026-00001
- **Key Fields**:
  - Customer: Paradise Restaurant
  - Order Date: 2026-01-02
  - Delivery Date: 2026-01-05
  - Items:
    - Tomato: 30 Kg @ 500 INR = 15,000 INR
    - Milk: 60 L @ 100 INR = 6,000 INR
- **Status**: Submitted
- **Total Amount**: 21,000 INR

**Business Logic**:
- Specifies items customer wants to purchase
- Reserves stock from warehouse
- Sets delivery date and terms
- Enables customer fulfillment

## Key Transaction Data

### Sample Transactions Included

```json
Purchase Order (PUR-ORD-2026-00001):
  Supplier: Fresh Farm Vegetables
  Item: Tomato 50 Kg
  Rate: 50 INR/Kg
  Amount: 2,500 INR

Purchase Receipt (MAT-PRE-2026-00001):
  Supplier: Fresh Farm Vegetables  
  Item: Tomato 50 Kg
  Rate: 50 INR/Kg
  Amount: 2,500 INR

Sales Order (SAL-ORD-2026-00001):
  Customer: Paradise Restaurant
  Items: Tomato (30 Kg), Milk (60 L)
  Total: 21,000 INR
```

## Role-Based Access

### Admin Role
- Full access to all modules
- Can create/modify/delete any document
- Can view reports across all entities

### Vendor Role
- Can view their own purchase orders
- Can see delivery schedules
- Can submit purchase receipts

### Shop Role  
- Can create sales orders
- Can view inventory levels
- Can track deliveries

## Reports and Dashboards

### Stock Report
- Shows current inventory levels
- Warehouse-wise breakdown
- Item-wise quantities and values

### Purchase Register
- Lists all purchase orders and receipts
- Vendor performance metrics
- Cost analysis by supplier

### Sales Register
- Tracks all sales orders
- Customer-wise sales analysis
- Revenue reports

## System Integration Points

### Master Data Integration
1. **Item Groups**: Vegetables, Dairy, Meat, Spices
2. **Items**: Individual products with rates
3. **Suppliers**: External vendors
4. **Customers**: Internal shops and restaurants

### Stock Management
1. **Warehouse**: Central storage location
2. **Stock Ledger**: Transaction history
3. **Bin**: Item location in warehouse

## Data Consistency Rules

1. **Quantity Validation**
   - PR quantity <= PO quantity
   - SO quantity <= Available stock

2. **Date Validation**
   - SO delivery date >= SO order date
   - PO required by date >= PO date

3. **Monetary Validation**
   - Unit rate > 0
   - Total amount = Qty × Rate

## Troubleshooting Common Issues

### Issue: Stock not updating after PR submission
**Solution**: Verify warehouse is configured and item is marked as stock item

### Issue: SO showing insufficient stock
**Solution**: Check PR status is "Submitted" and warehouse stock is updated

### Issue: Cannot delete submitted documents  
**Solution**: This is by design for audit trail. Amend document instead

## Future Enhancements

1. Automated reorder points
2. Delivery note generation
3. Invoice creation from SO
4. Payment tracking
5. Role-based dashboards
6. Inventory forecasting
7. Multiple warehouse support
8. Barcode integration
