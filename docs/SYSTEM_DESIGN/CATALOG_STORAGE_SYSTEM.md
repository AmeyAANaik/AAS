# Catalog Storage System - Complete Analysis

## Overview

AAS doesn't maintain a separate "catalog database" - instead, it uses **ERPNext's Item master** as the authoritative source of truth. Items are:

1. **Fetched from ERPNext** (GET /api/items) - All active items available
2. **Created dynamically** when new items appear in vendor PDFs
3. **Stored permanently** in ERPNext (not in memory, not in cache)
4. **Displayed in UI** via frontend master data list

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  AAS Frontend (Angular)                                         │
│  ├─ Items Master Data Page                                     │
│  ├─ GET /api/items → Display all active items                  │
│  └─ Create/Edit/Delete items via forms                         │
└──────────────────────┬──────────────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────────────┐
│  AAS Backend (Spring Boot)                                      │
│  ├─ MasterDataController /api/items                            │
│  ├─ MasterDataService (CRUD operations)                        │
│  └─ Calls ERPNext API                                          │
└──────────────────────┬──────────────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────────────┐
│  ERPNext (ERP System)                                           │
│  ├─ Item doctype (persistent database)                         │
│  ├─ All items stored permanently                               │
│  ├─ Version history & audit trails                             │
│  └─ Database tables (not exposed to frontend)                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Catalog Storage Locations

### 1. **Primary Storage: ERPNext Item Doctype**

```
ERPNext Database:
├─ tabItem (table)
│  ├─ name (item_code) - Primary Key
│  ├─ item_name
│  ├─ item_code
│  ├─ item_group (category)
│  ├─ stock_uom
│  ├─ aas_vendor (primary vendor)
│  ├─ aas_vendor_hsn_code
│  ├─ aas_packaging_unit
│  ├─ aas_margin_percent (7% default)
│  ├─ aas_vendor_rate (cached cost)
│  ├─ is_stock_item (1=yes, 0=no)
│  ├─ disabled (1=soft deleted)
│  ├─ creation (timestamp)
│  ├─ modified (timestamp)
│  └─ owner (user who created)
```

**Characteristics:**
- **Persistent** - Survives application restarts
- **Indexed** - Query by item_code, item_name, category
- **Versioned** - ERPNext keeps audit trail
- **Searchable** - Supports LIKE queries, filters
- **Sortable** - By name, category, code, margin

### 2. **Secondary Storage: Frontend Metadata (localStorage)**

Items may have client-side metadata stored locally:

```javascript
// ItemMetadataService
const METADATA_KEY = 'aas_item_metadata';

// Example in localStorage:
{
  "VEN001-ELEC-854430": {
    "packagingUnit": "10 units/box"  // User customization
  },
  "VEN001-CHEM-454301": {
    "packagingUnit": "1 liter/unit"
  }
}

// ItemVendorPricingService
const PRICING_KEY = 'aas_item_vendor_pricing';

// Example:
{
  "VEN001-ELEC-854430::VEN001": {
    "itemId": "VEN001-ELEC-854430",
    "itemName": "Controller Unit",
    "vendorId": "VEN001",
    "vendorName": "Supplier A",
    "originalRate": 95,
    "marginPercent": 8,
    "finalRate": 102.6
  }
}
```

**Characteristics:**
- **Client-side only** - Per-device, per-user
- **Not synced** - Different across devices
- **Volatile** - Lost if browser cache cleared
- **Supplementary** - Enhances master data

---

## Item Creation Flow (How Catalog Grows)

### Path 1: Manual Item Creation (UI)

```
User navigates to: Items Master Data
    ↓
Clicks: Create Item
    ↓
Fills Form:
├─ Category (required)
├─ Item Name (required)
├─ Vendor HSN Code (required)
├─ Measure Unit (optional)
├─ Packaging Unit (optional)
└─ Margin % (optional)
    ↓
POST /api/items
    ├─ Backend: MasterDataService.createItem()
    │  ├─ Resolve vendor from category
    │  ├─ Generate item_code: VENDOR-CATEGORY-HSN
    │  ├─ Validate category exists
    │  └─ Prepare payload
    │
    └─ erpNextClient.createResource("Item", payload)
        ├─ Create in ERPNext database
        ├─ Return item with generated code
        └─ Item now in catalog
    ↓
Item visible in Items list
Item can be used in orders
```

### Path 2: Automatic Item Creation (PDF Parsing)

When vendor PDF is uploaded, new items may be created automatically:

```
Vendor PDF Upload
    ↓
VendorPdfService.processVendorPdf()
    │
    ├─ Parse PDF
    │  └─ Extract: item name, HSN, qty, rate
    │
    └─ resolveItems()
         ├─ For each parsed item:
         │  ├─ Build item_code: VENDOR-CATEGORY-HSN_ITEMNAME
         │  │
         │  ├─ Check: Does item exist in ERPNext?
         │  │  ├─ YES → Use existing
         │  │  └─ NO → Create new item
         │  │
         │  └─ ensureItemEnabled()
         │      └─ Re-enable if was soft-deleted
         │
         ├─ createItem() called for new items:
         │  ├─ Payload:
         │  │  ├─ item_code (auto-generated)
         │  │  ├─ item_name (from PDF)
         │  │  ├─ item_group (vendor's category)
         │  │  ├─ stock_uom (normalized UOM)
         │  │  ├─ is_stock_item: 1
         │  │  ├─ aas_vendor (vendor ID)
         │  │  └─ aas_vendor_hsn_code (from PDF)
         │  │
         │  └─ erpNextClient.createResource("Item", payload)
         │      └─ Stored in ERPNext
         │
         └─ resolveItemMarginPercent()
            └─ Look up item in master, get margin (7% default)
    ↓
Items added to catalog
Immediately available for future orders
```

**Key Points:**
- New items created **on-demand** during PDF processing
- Item code format: `VENDOR_CATEGORY_HSN[_ITEMNAME]`
- Margin resolved from existing item or default to 7%
- Item stored **permanently** in ERPNext

---

## Item Code Generation Strategy

### 1. Manual Item Creation

```java
// MasterDataService.createItem()
String vendorCode = "VEN001";        // From vendor resolution
String categoryCode = "ELEC";        // From category
String vendorHsnCode = "854430";     // From HSN field

// Build format: VENDOR_CATEGORY_HSN
String itemCode = catalogRoutingService.buildItemCode(
  vendorCode, 
  categoryCode, 
  vendorHsnCode
);
// Result: "VEN001_ELEC_854430"
```

### 2. PDF-Parsed Item Creation

```java
// VendorPdfService.createItem() for new items
String vendorCode = "VEN001";
String categoryCode = "ELEC";
String vendorHsnCode = "854430";
String itemName = "Controller Unit";

// Build format: VENDOR_CATEGORY_HSN_ITEMNAME
String itemCode = catalogRoutingService.buildParsedItemCode(
  vendorCode,
  categoryCode,
  vendorHsnCode,
  itemName
);
// Result: "VEN001_ELEC_854430_CONTROLLER_UNIT"
```

### 3. Normalization Rules

```java
public String normalizeCodeSegment(String value) {
  String normalized = asText(value).trim().toUpperCase(Locale.ROOT);
  
  // Replace non-alphanumeric with underscore
  normalized = NON_ALNUM.matcher(normalized).replaceAll("_");
  
  // Collapse multiple underscores
  normalized = normalized.replaceAll("_+", "_");
  
  // Remove leading/trailing underscores
  normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
  
  return normalized;
}

// Examples:
// "HSN-Code (2024)" → "HSN_CODE_2024"
// "Electronics & Parts" → "ELECTRONICS_PARTS"
// "Controller Unit  " → "CONTROLLER_UNIT"
```

---

## Item Retrieval (Reading from Catalog)

### 1. List All Items

**Frontend:**
```typescript
// ItemService.listItems()
GET /api/items

// Returns: Item[]
[
  {
    name: "VEN001_ELEC_854430",
    item_code: "VEN001_ELEC_854430",
    item_name: "Controller Unit",
    item_group: "Electronics",
    stock_uom: "Nos",
    aas_vendor: "VEN001",
    aas_vendor_hsn_code: "854430",
    aas_packaging_unit: "10 units/box",
    aas_margin_percent: 7,
    disabled: 0
  },
  // ... more items
]
```

**Backend:**
```java
// MasterDataService.listItems()
public List<Map<String, Object>> listItems() {
  Map<String, Object> params = new HashMap<>();
  
  // Fetch specific fields only
  params.put("fields", 
    "[\"name\",\"item_name\",\"item_code\",\"item_group\"," +
    "\"stock_uom\",\"aas_margin_percent\",\"aas_vendor_rate\"," +
    "\"aas_packaging_unit\",\"aas_vendor\",\"aas_vendor_hsn_code\"]");
  
  // Filter: Only active items (disabled ≠ 1)
  params.put("filters", 
    "[[\"Item\",\"disabled\",\"=\",0]," +
    "[\"Item\",\"item_code\",\"!=\",\"AAS-SYSTEM-BRANCH-IMAGE\"]]");
  
  // Limit for performance
  params.put("limit_page_length", 1000);
  
  // Query ERPNext
  return erpNextClient.listResources("Item", params);
}
```

### 2. List with Pagination & Search

**Frontend:**
```typescript
// ItemService.listItemsPaged()
GET /api/items/paged?page=1&size=25&search=controller&sort=name&dir=asc
```

**Backend:**
```java
// MasterDataService.listItemsPaged()
public Map<String, Object> listItemsPaged(
    int page, int size, String search, String sort, String dir) {
  
  // 1. Pagination
  int limit_start = (page - 1) * size;
  int limit_page_length = Math.min(size, 200);  // Max 200 per page
  
  // 2. Sorting
  String orderBy = resolveItemOrderBy(sort, dir);
  // Maps: code→item_code, name→item_name, category→item_group, etc.
  
  // 3. Filters
  params.put("filters", activeItemFiltersJson());  // disabled=0 only
  
  // 4. Search (if provided)
  if (!search.isEmpty()) {
    params.put("or_filters", buildItemSearchFilters(search));
    // Searches: item_code LIKE '%search%' OR item_name LIKE '%search%'
  }
  
  // 5. Get results
  List<Map<String, Object>> items = erpNextClient.listResources("Item", params);
  
  // 6. Get total count
  long total = erpNextClient.getCount("Item", countParams);
  
  // 7. Return paginated response
  return Map.of(
    "items", items,
    "total", total,
    "page", page,
    "size", size
  );
}
```

---

## Item Update & Deletion

### 1. Update Item

**What can be updated:**
- `item_name` - Display name
- `stock_uom` - Unit of measure
- `aas_packaging_unit` - Quantity per package
- `aas_margin_percent` - Markup percentage

**What cannot be updated:**
- `item_code` - Generated once, immutable
- `aas_vendor` - Assigned at creation
- `aas_vendor_hsn_code` - Set at creation
- `item_group` - Category assignment

```java
// MasterDataService.updateItem()
public Map<String, Object> updateItem(String id, FieldsRequest request) {
  Map<String, Object> fields = request.getFields();
  Map<String, Object> payload = new HashMap<>();
  
  // Whitelist approach - only copy allowed fields
  copyIfPresent(fields, payload, "item_name");
  copyIfPresent(fields, payload, "stock_uom");
  copyIfPresent(fields, payload, "aas_packaging_unit");
  copyIfPresent(fields, payload, "aas_margin_percent");
  
  if (payload.isEmpty()) {
    return unwrapResource(erpNextClient.getResource("Item", id));
  }
  
  return erpNextClient.updateResource("Item", id, payload);
}
```

### 2. Delete Item (Soft Delete)

```java
// MasterDataService.deleteItem()
public Map<String, Object> deleteItem(String id) {
  // Verify item exists
  Map<String, Object> item = unwrapResource(
    erpNextClient.getResource("Item", id)
  );
  if (item.isEmpty()) {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
      "Item not found.");
  }
  
  // Soft delete: Set disabled flag
  erpNextClient.updateResource("Item", id, Map.of("disabled", 1));
  
  return Map.of(
    "itemId", id, 
    "deleted", true, 
    "softDeleted", true
  );
}
```

**Why Soft Delete?**
- Item remains in database history
- Can be re-enabled by setting `disabled = 0`
- Preserves audit trail
- Linked orders still reference the item
- No data loss

---

## Catalog Organization

### 1. By Category (Item Group)

```
Item Group Hierarchy:
├─ Electronics
│  ├─ Controller Units
│  ├─ Sensors
│  └─ Cables
├─ Chemicals
│  ├─ Solvents
│  ├─ Acids
│  └─ Bases
└─ Consumables
   ├─ Paper
   └─ Plastics
```

### 2. By Vendor Assignment

```
Vendor ↔ Category Mapping:
Vendor A:
  ├─ Electronics (primary)
  └─ Chemicals (secondary)

Vendor B:
  ├─ Consumables (primary)
  └─ Electronics (secondary)
```

### 3. By Item Code Pattern

```
Item Code Format:
VENDOR_CATEGORY_HSN[_ITEMNAME]

Examples:
- VEN001_ELEC_854430          (simple)
- VEN001_ELEC_854430_RESISTOR (parsed item)
- VEN002_CHEM_291010          (chemical)
- VEN003_CONS_000000_PAPER    (consumable)
```

---

## Query Performance

### 1. Indexed Fields (Fast Queries)

```sql
-- These are fast (indexed in ERPNext)
SELECT * FROM Item WHERE disabled = 0;
SELECT * FROM Item WHERE item_code LIKE 'VEN001%';
SELECT * FROM Item WHERE item_group = 'Electronics';
SELECT * FROM Item WHERE aas_vendor = 'VEN001';
```

### 2. Field Selection (Reduced Bandwidth)

```java
// Only fetch needed fields
params.put("fields", 
  "[\"name\",\"item_name\",\"item_code\"," +  // Essential
  "\"item_group\",\"stock_uom\",\"aas_vendor\"]"  // Needed
);
// Don't fetch: description, long_description, internal_details, etc.
```

### 3. Pagination Limits

```java
// Prevent huge result sets
int limit_page_length = Math.min(safeSize, 200);  // Max 200 per page
params.put("limit_page_length", 1000);            // listItems: 1000 max
```

---

## Data Flow Example: Item Lifecycle

```
┌─────────────────────────────────────────────────────────────────┐
│ STEP 1: Item Creation (Manual or Automatic)                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│ User creates or PDF auto-creates:                              │
│   Item Code: VEN001_ELEC_854430                                │
│   Name: Controller Unit                                         │
│   Category: Electronics                                         │
│   Vendor: VEN001                                               │
│   HSN: 854430                                                  │
│   Margin: 7% (default)                                         │
│                                                                  │
│ → Stored in ERPNext Item table                                  │
└─────────────────────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 2: Catalog Discovery (Frontend List)                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│ GET /api/items/paged?page=1&size=25&search=controller         │
│   ├─ Backend queries ERPNext                                    │
│   ├─ Filters: disabled=0, item_code NOT "AAS-SYSTEM-*"        │
│   ├─ Searches: item_code or item_name LIKE '%controller%'      │
│   └─ Returns paginated list with total count                    │
│                                                                  │
│ Frontend:                                                       │
│   ├─ Display items in master data list                          │
│   ├─ Show category summary counts                               │
│   └─ Allow edit/delete actions                                  │
└─────────────────────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 3: Usage in Orders                                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│ User selects item in order creation:                           │
│   ├─ Item appears in search dropdown                            │
│   ├─ Item's margin (7%) auto-populated                          │
│   ├─ Item's vendor (VEN001) shown                               │
│   └─ Item's UOM (Nos) defaults                                  │
│                                                                  │
│ Order line created with item reference                         │
│ → Stored in Sales Order in ERPNext                              │
└─────────────────────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 4: Updates & Soft Deletion                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│ User updates item:                                              │
│   ├─ Change item_name: "Controller Unit v2"                     │
│   ├─ Change margin: 7% → 8%                                     │
│   └─ Change UOM: Nos → Kg                                       │
│                                                                  │
│ PUT /api/items/VEN001_ELEC_854430                               │
│   └─ Only whitelisted fields updated in ERPNext                 │
│                                                                  │
│ User deletes item:                                              │
│   └─ SET disabled = 1 (soft delete)                             │
│   └─ Item hidden from UI                                        │
│   └─ History & linked orders preserved                          │
└─────────────────────────────────────────────────────────────────┘
```

---

## Key Characteristics

### ✅ Strengths
- **Persistent** - All items stored permanently in ERPNext
- **Queryable** - Search, filter, sort by any field
- **Versioned** - ERPNext maintains audit trail
- **Distributed** - No single point of failure
- **Scalable** - ERPNext handles large catalogs
- **Standardized** - Uses ERPNext Item doctype (industry standard)

### ⚠️ Design Notes
- **No Local Cache** - Always query ERPNext (stateless architecture)
- **Soft Delete Only** - Never physically removes items
- **Auto-Creation** - PDF parsing creates items on-demand
- **Hierarchical** - Categories organized with parent-child
- **Vendor-Linked** - Each item linked to primary vendor

---

## Summary

**The Catalog is:**

1. **Stored Persistently** in ERPNext's Item doctype (database)
2. **Organized** by category (Item Group) and vendor
3. **Queried On-Demand** via REST API (no caching)
4. **Created** manually via UI or automatically from PDF parsing
5. **Updated** with whitelist validation (only safe fields)
6. **Deleted** via soft-delete (disabled flag, not removed)
7. **Accessed** in orders for cost/margin/UOM lookups

**Not stored in:**
- Memory (stateless backend)
- Redis (no cache layer)
- LocalStorage (client metadata only)
- Separate database (ERPNext is single source of truth)

The catalog grows organically as users create items or PDFs parse new items, all persisted in ERPNext for long-term availability and audit compliance.
