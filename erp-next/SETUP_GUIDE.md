# ERPNext Restaurant Setup Guide

## Prerequisites

- ERPNext instance running (v15 or higher)
- Administrator access
- Basic understanding of ERPNext concepts

## Step 1: Setup Master Data

### 1.1 Create Item Groups

1. Navigate to **Stock > Item Group**
2. Create the following item groups:
   - **Vegetables** - For fresh vegetable items
   - **Dairy** - For milk, yogurt, cheese
   - **Meat** - For meat and poultry products
   - **Spices** - For spices and condiments

### 1.2 Create Items

1. Navigate to **Stock > Item**
2. Create items with the following details:

#### Item 1: Tomato
- **Item Code**: TOMATO-001
- **Item Name**: Fresh Tomato
- **Item Group**: Vegetables
- **Unit of Measure**: Kg
- **Standard Selling Rate**: 500 INR/Kg
- **Standard Buying Rate**: 50 INR/Kg

#### Item 2: Milk
- **Item Code**: MILK-001
- **Item Name**: Fresh Milk
- **Item Group**: Dairy
- **Unit of Measure**: Litre
- **Standard Selling Rate**: 100 INR/L
- **Standard Buying Rate**: 150 INR/L

### 1.3 Create Suppliers (Vendors)

1. Navigate to **Buying > Supplier**
2. Create suppliers:

#### Supplier 1: Fresh Farm Vegetables
- **Supplier Name**: Fresh Farm Vegetables
- **Supplier Type**: Manufacturer
- **Country**: India
- **Address**: Village Farm, Agricultural Region

#### Supplier 2: Dairy Cooperative
- **Supplier Name**: Dairy Cooperative
- **Supplier Type**: Distributor
- **Country**: India
- **Address**: Dairy Farm, Milk District

### 1.4 Create a Customer

1. Navigate to **CRM > Customer**
2. Create a customer:
   - **Customer Name**: Paradise Restaurant
   - **Customer Type**: Individual
   - **Territory**: India
   - **Default Currency**: INR

## Step 2: Create Transaction Documents

### 2.1 Purchase Order (PO)

1. Navigate to **Buying > Purchase Order**
2. Click **+ New**
3. Fill in details:
   - **Supplier**: Fresh Farm Vegetables
   - **Date**: 01-01-2026
   - **Required By Date**: 03-01-2026
4. Add items in the Items table:
   - **Item Code**: TOMATO-001
   - **Quantity**: 50 Kg
   - **Rate**: 50 INR
   - **Amount**: 2,500 INR
5. Click **Submit**

### 2.2 Purchase Receipt (PR)

1. Navigate to **Stock > Purchase Receipt**
2. Click **+ New**
3. Fill in details:
   - **Supplier**: Fresh Farm Vegetables
   - **Date**: 02-01-2026
4. Click **Get Items From** and select the Purchase Order
5. Verify quantities and rates
6. Click **Submit**

### 2.3 Sales Order (SO)

1. Navigate to **Selling > Sales Order**
2. Click **+ New**
3. Fill in details:
   - **Customer**: Paradise Restaurant
   - **Order Date**: 02-01-2026
   - **Delivery Date**: 05-01-2026
4. Add items:
   - **Item Code**: TOMATO-001
   - **Quantity**: 30 Kg
   - **Rate**: 500 INR
   - **Amount**: 15,000 INR
5. Click **Submit**

## Step 3: Generate Reports

### 3.1 Stock Report
- Navigate to **Stock > Stock Report**
- Filter by date range
- View inventory levels

### 3.2 Purchase Register
- Navigate to **Buying > Purchase Register**
- View all purchase transactions
- Analyze supplier performance

### 3.3 Sales Register
- Navigate to **Selling > Sales Register**
- View all sales transactions
- Analyze customer performance

## Troubleshooting

### Issue: Items not appearing in dropdown
**Solution**: Ensure items are created with proper Item Code and enabled for the transaction type

### Issue: Cannot submit Purchase Order
**Solution**: Check all mandatory fields are filled (Supplier, Date, Items with quantity and rate)

### Issue: Warehouse not found
**Solution**: Verify default warehouse is configured in Stock Settings

## Key Concepts

- **Item Code**: Unique identifier for each product
- **Warehouse**: Storage location for inventory
- **Purchase Order**: Document to order items from suppliers
- **Purchase Receipt**: Confirmation of receipt of ordered items
- **Sales Order**: Document to sell items to customers
- **Stock Ledger**: Tracks all inventory movements

## Next Steps

1. Create invoices for sales orders
2. Setup automatic reorder levels
3. Configure payment terms
4. Create delivery notes
5. Setup role-based permissions
