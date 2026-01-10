# ERPNext - Restaurant Inventory Management System

## Overview
This folder contains the ERPNext configuration, documentation, and data dump for a restaurant inventory management system. The system is designed for internal operations with multiple roles: Admin (Central Warehouse), Vendors (External Suppliers), and Shops (Internal restaurant outlets).

## Project Information
- **Start Date**: January 2026
- **Technology**: ERPNext (Open Source ERP)
- **Domain**: Restaurant Inventory Management
- **Team**: 2 Developers, 1 QA Engineer
- **Timeline**: 13 Weeks (90-Day Accelerated Schedule)

## System Architecture

The system implements a three-tier procurement and sales workflow:

### 1. Vendor Management
- External suppliers (e.g., Fresh Farm Vegetables, Dairy Cooperative)
- Manage supplier information and contact details
- Track purchase orders and receipts from vendors

### 2. Central Inventory Management
- Receive stock from vendors via Purchase Receipts
- Maintain stock levels and warehouse management
- Track inventory costs and valuations

### 3. Internal Shop Sales
- Multiple internal restaurant outlets (e.g., Paradise Restaurant)
- Place sales orders from central inventory
- Track internal sales and transfers
- Manage shop-wise inventory

## Database Setup

### Masters Created

#### Item Groups
- Vegetables
- Dairy
- Meat
- Spices

#### Items
- **Tomato** (Item Group: Vegetables, UOM: Nos)
- **Milk** (Item Group: Dairy, UOM: Nos)

#### Suppliers
- Fresh Farm Vegetables
- Dairy Cooperative

#### Customers/Shops
- Paradise Restaurant (Internal shop)

## Transaction Flow

### Phase 1: Procurement (Vendor → Central Warehouse)
**Purchase Order (PUR-ORD-2026-00001)**
- Supplier: Fresh Farm Vegetables
- Date: 10-01-2026
- Required By: 15-01-2026
- Items:
  - Tomato: 50 units @ ₹50 = ₹2,500
  - Milk: 100 units @ ₹150 = ₹15,000
- **Total: ₹17,500**

### Phase 2: Stock Receipt (Goods In)
**Purchase Receipt (MAT-PRE-2026-00001)**
- Status: Submitted (To Bill)
- Items Received:
  - Tomato: 50 units
  - Milk: 100 units
- Warehouse: Default Warehouse
- Posting Date: 10-01-2026

### Phase 3: Internal Sales (Shop Orders)
**Sales Order (SAL-ORD-2026-00001)**
- Customer: Paradise Restaurant (Internal Shop)
- Date: 10-01-2026
- Delivery Date: 20-01-2026
- Order Type: Sales
- Items:
  - Tomato: 30 units @ ₹500 = ₹15,000
  - Milk: 60 units @ ₹100 = ₹6,000
- **Total: ₹21,000**
- **Total Quantity: 90 units**

## File Structure

```
erp-next/
├── README.md                          # This file
├── SETUP_GUIDE.md                     # Installation and setup instructions
├── DATA_DUMP.json                     # Database export with all masters and transactions
├── CONFIGURATION.md                   # System configuration details
├── WORKFLOW_DOCUMENTATION.md          # Complete transaction workflow
├── configs/
│   ├── item-groups.json              # Item group definitions
│   ├── items.json                    # Item master data
│   ├── suppliers.json                # Supplier master data
│   └── customers.json                # Customer/Shop master data
├── transactions/
│   ├── purchase-orders/
│   │   └── PUR-ORD-2026-00001.json  # Purchase order export
│   ├── purchase-receipts/
│   │   └── MAT-PRE-2026-00001.json  # Purchase receipt export
│   └── sales-orders/
│       └── SAL-ORD-2026-00001.json  # Sales order export
└── screenshots/
    ├── system-overview.png            # System architecture diagram
    ├── transaction-flow.png           # Transaction flow diagram
    └── sample-transactions.png        # Sample transaction screenshots
```

## Key Features Implemented

✅ Multi-party system (Vendors, Warehouse, Shops)
✅ Item and category management
✅ Purchase order creation and submission
✅ Purchase receipt with goods inward
✅ Sales order with delivery scheduling
✅ Automatic price list management
✅ Quantity and amount calculations
✅ Document workflow and status management
✅ Date and deadline tracking
✅ UOM (Unit of Measure) management

## Access Information

**ERPNext Instance**: https://vigilant-palm-tree-pjv7q4r5x7r397rr-8080.app.github.dev

### Sample Data
- Supplier: Fresh Farm Vegetables
- Shop: Paradise Restaurant
- Key Dates: 10-01-2026 (Order Date), 15-01-2026 (PO Due), 20-01-2026 (Delivery)

## Next Steps

1. **Sales Invoice Creation** - Convert Sales Order to Sales Invoice
2. **Delivery Note** - Track goods delivery from warehouse to shop
3. **Stock Entry** - Record internal inventory transfers
4. **Reports Development** - Admin, Vendor, and Shop role-based reports
5. **Payment Processing** - Invoice payment and reconciliation
6. **Integration** - API endpoints for external systems

## Testing Checklist

- [x] Item Group creation
- [x] Item master setup
- [x] Supplier creation
- [x] Customer/Shop creation
- [x] Purchase Order workflow
- [x] Purchase Receipt workflow
- [x] Sales Order workflow
- [ ] Sales Invoice creation
- [ ] Delivery Note generation
- [ ] Stock movement validation
- [ ] Report generation (Admin/Vendor/Shop)
- [ ] Role-based access control

## Notes

- All transactions use INR (Indian Rupees) as currency
- System supports multiple shops/customers (currently 1 shop configured)
- Vendor base is extensible for multiple suppliers
- Warehouse defaults to "Default Warehouse"
- Price lists are auto-generated for each item

## Support

For issues or questions regarding this ERPNext setup:
1. Check SETUP_GUIDE.md for installation
2. Review WORKFLOW_DOCUMENTATION.md for transaction flows
3. Refer to configuration files in configs/ folder
4. Check sample transaction JSONs for data format

---

**Last Updated**: January 10, 2026
**Created By**: AAS Project Team
**Status**: In Development
