# Stock Item Maintenance System

## Overview

The Stock Item Maintenance system manages the complete lifecycle of inventory items in AAS, from creation in the catalog to consumption through orders, with tracking across vendors, categories, and branches. Items are the fundamental data units that flow through orders, invoices, and stock monitoring.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  Item Management (Master Data)                                  │
│  - Create/Update/Delete items                                   │
│  - Categorize items                                              │
│  - Set vendor associations and pricing                           │
└──────────────────┬──────────────────────────────────────────────┘
                   │
        ┌──────────┴──────────┐
        │                     │
        ↓                     ↓
┌──────────────────┐  ┌──────────────────┐
│ Item Service     │  │ Item Metadata    │
│ (Backend API)    │  │ Service (Client) │
└──────────────────┘  └──────────────────┘
        │                     │
        ↓                     ↓
┌──────────────────┐  ┌──────────────────┐
│ ERPNext Item     │  │ localStorage     │
│ (Item doctype)   │  │ (aas_item_*)     │
└──────────────────┘  └──────────────────┘
        │
        ↓
┌──────────────────────────────────────────┐
│  Item Consumption                        │
│  ├─ Orders                               │
│  ├─ Purchase Invoices (cost side)        │
│  ├─ Sales Orders (sell side)             │
│  └─ Stock Monitoring & Thresholds        │
└──────────────────────────────────────────┘
```

---

## Data Model & Fields

### Item (ERPNext doctype)

**Core Fields:**
- `name` - System ID (usually same as item_code)
- `item_code` - Unique identifier (auto-generated, format: VENDOR-CATEGORY-HSN)
- `item_name` - Human-readable name
- `item_group` - Category assignment (links to Item Group)
- `stock_uom` - Unit of Measure (Nos, KG, LTR, etc.)
- `is_stock_item` - Flag: 0=non-stock, 1=stock item

**AAS Custom Fields (Custom prefixed with `aas_`):**
- `aas_vendor` - Primary vendor association (Supplier name)
- `aas_vendor_hsn_code` - HSN code for GST classification
- `aas_packaging_unit` - Qty per package/case/box
- `aas_margin_percent` - Default markup % on cost
- `aas_vendor_rate` - Cached vendor cost (informational)

**System Fields:**
- `disabled` - Flag: 0=active, 1=disabled/deleted
- `creation`, `modified` - Timestamps
- `owner`, `modified_by` - User tracking

### Item Group (Category doctype)

- `name` - Category ID
- `item_group_name` - Display name
- `parent_item_group` - Parent category (allows hierarchy)
- `aas_category_code` - Short code for item_code generation (e.g., "ELEC", "CHEM")

---

## Complete Item Lifecycle

### Phase 1: Creation

#### User Interface Flow
```
Items List Page
  ↓
Click "Create" / Category button
  ↓
Item Form Dialog Opens
  ├─ Category (required) - pre-selected or choosable
  ├─ Item Name (required)
  ├─ Vendor HSN Code (required) - for GST classification
  ├─ Measure Unit (required) - default "Nos"
  ├─ Packaging Unit (optional) - qty per package
  └─ Margin % (optional) - default 7%
  ↓
Submit Form
  ↓
POST /api/items with { fields: {...} }
```

#### Backend Processing (MasterDataService.createItem)

```java
public Map<String, Object> createItem(FieldsRequest request) {
    // 1. Extract category from request
    String categoryId = asText(payload.get("item_group"));
    if (categoryId.isBlank()) {
        throw new IllegalArgumentException("Category is required.");
    }

    // 2. Resolve primary vendor for category
    CatalogRoutingService.VendorCategoryResolution resolution 
        = catalogRoutingService.resolveTopVendorForCategory(categoryId);
    
    // 3. Build item_code from components
    // Format: {VENDOR_CODE}-{CATEGORY_CODE}-{HSN_CODE}
    String vendorHsnCode = firstText(payload.get("aas_vendor_hsn_code"), 
                                     payload.get("vendor_hsn_code"));
    payload.put("item_code", catalogRoutingService.buildItemCode(
        resolution.vendorCode(),      // e.g., "VEN001"
        resolution.categoryCode(),    // e.g., "ELEC"
        vendorHsnCode                 // e.g., "85443000"
    ));
    
    // 4. Assign vendor automatically
    payload.put("aas_vendor", resolution.vendorId());
    
    // 5. Normalize HSN code
    payload.put("aas_vendor_hsn_code", 
        catalogRoutingService.normalizeCodeSegment(vendorHsnCode));
    
    // 6. Set defaults
    payload.putIfAbsent("stock_uom", "Nos");
    payload.putIfAbsent("is_stock_item", 1);
    
    // 7. Save to ERPNext
    return erpNextClient.createResource("Item", payload);
}
```

**Key Details:**
- Item code is **auto-generated** from vendor + category + HSN
- Primary vendor is **resolved automatically** based on category
- Items are **enabled for stock tracking** by default
- HSN code is **normalized** (alphanumeric only)

### Phase 2: Retrieval & Display

#### Frontend: Item List Component

```typescript
// 1. Load data on init
ngOnInit() {
  Promise.all([
    categoryService.listCategories(),
    itemService.listItems(),           // GET /api/items
    vendorService.listVendors()
  ]).then(([categories, items, vendors]) => {
    // 2. Merge with local metadata
    const mergedItems = this.metadataService.mergeMetadata(items);
    
    // 3. Convert to view model
    this.items = mergedItems.map(item => this.toViewModel(item));
    
    // 4. Build category summary
    this.refreshCategoryRows();
  });
}

// Item → ItemView transformation
toViewModel(item: Item): ItemView {
  return {
    id: item.item_code || item.name,
    code: item.item_code,
    name: item.item_name ?? item.name,
    category: item.item_group,
    vendorId: item.aas_vendor,
    vendorHsnCode: item.aas_vendor_hsn_code,
    measureUnit: item.stock_uom,
    packagingUnit: item.aas_packaging_unit || item.packagingUnit,
    marginPercent: item.aas_margin_percent,
    raw: item
  };
}
```

#### Backend: Item Retrieval (MasterDataService.listItems)

```java
public List<Map<String, Object>> listItems() {
    Map<String, Object> params = new HashMap<>();
    
    // Only fetch specific fields (performance optimization)
    params.put("fields",
        "[\"name\",\"item_name\",\"item_code\",\"item_group\"," +
        "\"stock_uom\",\"aas_margin_percent\",\"aas_vendor_rate\"," +
        "\"aas_packaging_unit\",\"aas_vendor\",\"aas_vendor_hsn_code\"]");
    
    // Filter: Only active items (disabled ≠ 1)
    params.put("filters", 
        "[[\"Item\",\"disabled\",\"=\",0]," +
        "[\"Item\",\"item_code\",\"!=\",\"AAS-SYSTEM-BRANCH-IMAGE\"]]");
    
    // Limit to 1000 items (performance cap)
    params.put("limit_page_length", 1000);
    
    return erpNextClient.listResources("Item", params);
}
```

**Query Strategy:**
- Only **active items** (disabled = 0) are returned
- **System items** (AAS-SYSTEM-BRANCH-IMAGE) are excluded
- Limited to **1000 items** per call (pagination required for large catalogs)
- Only **essential fields** fetched (reduces bandwidth)

### Phase 3: Updates

#### Update Item Endpoint

```typescript
// Frontend: ItemService
updateItem(id: string, fields: Record<string, unknown>): Observable<unknown> {
    return this.http.put(`/api/items/${encodeURIComponent(id)}`, 
        { fields }, 
        { headers: this.authHeaders() });
}
```

#### Backend: Item Update (MasterDataService.updateItem)

```java
public Map<String, Object> updateItem(String id, FieldsRequest request) {
    Map<String, Object> fields = request == null ? Map.of() : request.getFields();
    Map<String, Object> payload = new HashMap<>();
    
    // Whitelist: Only these fields can be updated
    copyIfPresent(fields, payload, "item_name");
    copyIfPresent(fields, payload, "stock_uom");
    copyIfPresent(fields, payload, "aas_packaging_unit");
    copyIfPresent(fields, payload, "aas_margin_percent");
    
    // If no updatable fields, return current state
    if (payload.isEmpty()) {
        return unwrapResource(erpNextClient.getResource("Item", id));
    }
    
    // Update only whitelisted fields
    return erpNextClient.updateResource("Item", id, payload);
}
```

**Immutable Fields (Cannot be updated):**
- `item_code` - Auto-generated, never changes
- `aas_vendor` - Resolved at creation
- `aas_vendor_hsn_code` - Set at creation
- `item_group` - Category assignment

**Mutable Fields:**
- `item_name` - Display name
- `stock_uom` - Unit of measure
- `aas_packaging_unit` - Package quantity
- `aas_margin_percent` - Markup percentage

### Phase 4: Deletion (Soft Delete)

```java
public Map<String, Object> deleteItem(String id) {
    if (SYSTEM_BRANCH_IMAGE_ITEM.equals(id)) {
        throw new IllegalStateException("System item cannot be deleted.");
    }
    
    // Verify item exists
    Map<String, Object> item = unwrapResource(erpNextClient.getResource("Item", id));
    if (item.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found.");
    }
    
    // Soft delete: Set disabled flag
    erpNextClient.updateResource("Item", id, Map.of("disabled", 1));
    
    return Map.of("itemId", id, "deleted", true, "softDeleted", true);
}
```

**Deletion Strategy:**
- **Soft delete only** - item remains in database, marked as disabled
- Item disappears from UI (filtered out by `disabled = 0`)
- Preserved in history and linked documents (orders, invoices)
- Can be re-enabled by updating `disabled` to 0

---

## Item Categorization

### Category Management

```
Category (Item Group doctype)
├─ name: "Electronics"
├─ item_group_name: "Electronics"
├─ parent_item_group: "All Item Groups" or parent category
├─ aas_category_code: "ELEC"
└─ [child categories...]
```

### Category-Vendor Linkage

The system maintains a **many-to-many** relationship:

```
┌─────────────────────────────────────────┐
│ Category "Electronics"                  │
│ Code: "ELEC"                            │
└──────────┬──────────────────────────────┘
           │
     ┌─────┴──────────┐
     │                │
     ↓                ↓
  Vendor A          Vendor B
  (VEN001)          (VEN002)
  [Primary]         [Secondary]
   └─ Items        └─ Items
```

**CatalogRoutingService:**
- Maintains vendor-category mappings
- Resolves **primary vendor** for each category
- Generates **item codes** from vendor+category+hsn
- Normalizes code segments (alphanumeric, uppercase)

```java
// Resolution example
VendorCategoryResolution resolution = catalogRoutingService
    .resolveTopVendorForCategory("Electronics");
// Returns: vendorId="VEN001", vendorCode="VEN", categoryCode="ELEC"

String itemCode = catalogRoutingService.buildItemCode(
    "VEN",           // Vendor code
    "ELEC",          // Category code  
    "85443000"       // HSN
);
// Result: "VEN-ELEC-85443000"
```

---

## Item Metadata (Client-Side)

Items also have **local client metadata** stored in `localStorage` for per-device customization:

### ItemMetadataService

```typescript
const METADATA_KEY = 'aas_item_metadata';

readMetadata(itemId: string): ItemMetadata {
  // Get item-specific metadata from localStorage
  const store = this.readStore();
  return store[itemId] ?? {};
}

saveMetadata(itemId: string, metadata: ItemMetadata): void {
  // Persist item metadata
  const store = this.readStore();
  store[itemId] = metadata;
  localStorage.setItem(METADATA_KEY, JSON.stringify(store));
}

mergeMetadata(items: Item[]): Array<Item & ItemMetadata> {
  // Combine API items with local metadata
  const store = this.readStore();
  return items.map(item => ({
    ...item,
    ...store[item.item_code ?? item.name]
  }));
}
```

**Current Metadata Fields:**
- `packagingUnit` - Local override for packaging unit (if needed)

---

## Item Vendor Pricing

Separate service manages **vendor-specific pricing** for items:

### ItemVendorPricingService

```typescript
const PRICING_KEY = 'aas_item_vendor_pricing';

interface ItemVendorPricingEntry {
  itemId: string;
  itemName: string;
  vendorId: string;
  vendorName: string;
  originalRate: number;      // Cost from vendor
  marginPercent: number;     // Markup %
  finalRate: number;         // Calculated selling price
}

upsertPricing(entry: ItemVendorPricingEntry): void {
  // Store pricing entry
  const store = this.readStore();
  const key = `${entry.itemId}::${entry.vendorId}`;
  store[key] = entry;
  localStorage.setItem(PRICING_KEY, JSON.stringify(store));
}

calculateFinalRate(originalRate: number, marginPercent: number): number {
  // Final Rate = Original Rate × (1 + Margin% / 100)
  const margin = Number(marginPercent) || 0;
  return Number((originalRate * (1 + margin / 100)).toFixed(2));
}
```

**Storage:**
- Key: `itemId::vendorId` (composite key)
- Stored in `localStorage` under `aas_item_vendor_pricing`
- Computed dynamically when orders are created

**Usage Flow:**
1. User selects item + vendor in order creation
2. System looks up `ItemVendorPricingEntry` from localStorage
3. If not found, uses `aas_margin_percent` from item master
4. Calculates `finalRate = originalRate * (1 + margin / 100)`
5. Uses calculated rate for sell-side pricing

---

## Item Usage in Orders

### Order Line Item

When an item is used in an order:

```java
// OrderItemLine (DTO)
public class OrderItemLine {
    private String itemId;              // item_code
    private String itemName;            // item_name
    private String vendorId;            // aas_vendor
    private Double quantity;            // qty ordered
    private String uom;                 // stock_uom
    private Double rate;                // Cost per unit
    private Double amount;              // quantity × rate
    private Double marginPercent;       // Selling markup
    private Double sellingRate;         // Cost × (1 + margin%)
    private Double sellingAmount;       // quantity × sellingRate
}
```

### Complete Order Line Flow

```
User selects item from catalog in order
    ↓
System fetches item details
    ├─ item_code, item_name
    ├─ stock_uom
    ├─ aas_vendor
    └─ aas_margin_percent
    ↓
User enters quantity
    ↓
User selects vendor (may be different from primary)
    ↓
System resolves vendor pricing
    ├─ Look up ItemVendorPricingEntry[itemId][vendorId]
    └─ Extract originalRate, marginPercent
    ↓
Backend processes PDF / bill entry
    ├─ Parses item quantity and rate
    └─ Creates Purchase Invoice line with parsed values
    ↓
Sell-side creation
    ├─ Calculate selling rate = cost × (1 + margin%)
    └─ Create Sales Order / Invoice with selling rate
```

---

## Item Stock Tracking

### Stock Quantity Fields

Items in ERPNext have multiple quantity fields (for backward compatibility):

**From stock.service.ts:**
```typescript
private parseQuantity(item: StockItem): number {
  // Try fields in order of preference
  const candidates = [
    item.stock_qty,      // Primary - from Stock Entry
    item.actual_qty,     // Fallback 1 - physical count
    item.quantity,       // Fallback 2 - generic qty field
    item.qty             // Fallback 3 - short form
  ];
  
  for (const candidate of candidates) {
    const value = Number(candidate);
    if (Number.isFinite(value)) {
      return value;  // Use first valid value found
    }
  }
  return 0;  // Default to zero
}
```

**Why Multiple Fields?**
- Different ERPNext versions use different qty field names
- Backward compatibility with existing deployments
- Fallback mechanism for robustness

### Stock Monitoring

Items are monitored with **per-device thresholds**:

```typescript
// StockView model
interface StockView {
  id: string;
  name: string;
  code: string;
  vendorName: string;
  quantity: number;           // Resolved qty
  threshold: number | null;   // User-defined alert level
  isLow: boolean;             // quantity <= threshold
  statusLabel: "Low" | "OK";
}

// Threshold determines status
if (threshold === null) {
  isLow = false;  // No alert if unset
} else if (quantity <= threshold) {
  isLow = true;   // Alert: stock below threshold
} else {
  isLow = false;  // Stock OK
}
```

---

## Item Search & Filtering

### Paginated Search (for large catalogs)

```typescript
// Frontend: ItemService
listItemsPaged(page: number, size: number, search: string, sort: string, dir: string)
  → GET /api/items/paged?page=1&size=25&search=controller&sort=name&dir=asc
```

### Backend Search Logic

```java
public Map<String, Object> listItemsPaged(int page, int size, String search, 
                                         String sort, String dir) {
    // 1. Validate parameters
    int safePage = Math.max(page, 1);
    int safeSize = Math.min(Math.max(size, 1), 200);  // Max 200 per page
    String safeDir = "desc".equalsIgnoreCase(dir) ? "desc" : "asc";
    
    // 2. Resolve sort field
    String orderBy = resolveItemOrderBy(sort, safeDir);
    // Mapping: code→item_code, name→item_name, category→item_group, etc.
    
    // 3. Set query parameters
    params.put("limit_start", (safePage - 1) * safeSize);
    params.put("limit_page_length", safeSize);
    params.put("order_by", orderBy);
    params.put("filters", activeItemFiltersJson());
    
    // 4. Add search filters if provided
    if (!search.trim().isEmpty()) {
        params.put("or_filters", buildItemSearchFilters(search));
        // Searches: item_code LIKE '%search%' OR item_name LIKE '%search%'
    }
    
    // 5. Execute query
    List<Map<String, Object>> items = erpNextClient.listResources("Item", params);
    
    // 6. Get total count separately
    long total = erpNextClient.getCount("Item", countParams);
    
    // 7. Return paginated response
    return Map.of(
        "items", items,
        "total", total,
        "page", safePage,
        "size", safeSize
    );
}
```

**Search Strategy:**
- Searches both `item_code` and `item_name` fields
- Case-insensitive pattern matching
- Maximum 200 items per page
- Total count fetched separately for pagination
- JSON escaping to prevent injection

---

## Item Validation Rules

### On Creation
```
✓ Category is required
✓ Item Name is required
✓ Vendor HSN Code is required
✓ Stock UOM defaults to "Nos"
✓ System assigns item_code automatically
✓ System assigns primary vendor automatically
✓ Item enabled for stock tracking by default
```

### On Update
```
✓ Only whitelisted fields can be updated:
  - item_name
  - stock_uom
  - aas_packaging_unit
  - aas_margin_percent
✗ Cannot change:
  - item_code (auto-generated)
  - aas_vendor (assigned at creation)
  - aas_vendor_hsn_code (set at creation)
  - item_group (category)
```

### On Deletion
```
✓ Only inactive vendors can be deleted
✓ Soft delete: set disabled = 1
✓ Item remains in history/audit trail
✓ System item "AAS-SYSTEM-BRANCH-IMAGE" cannot be deleted
```

---

## Data Flow Diagram: Complete Item Lifecycle

```
┌─────────────────────────────────────────────────────────────────┐
│                    ITEM CREATION                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  User enters:                                                    │
│  ├─ Category (required)                                         │
│  ├─ Item Name (required)                                        │
│  ├─ Vendor HSN Code (required)                                  │
│  ├─ Measure Unit (optional)                                     │
│  ├─ Packaging Unit (optional)                                   │
│  └─ Margin % (optional)                                         │
│         │                                                       │
│         ↓                                                       │
│  Backend: MasterDataService.createItem()                       │
│  ├─ Resolve category                                           │
│  ├─ Resolve primary vendor for category                        │
│  ├─ Generate item_code = VENDOR-CATEGORY-HSN                   │
│  ├─ Normalize HSN code                                         │
│  ├─ Set defaults (stock_uom="Nos", is_stock_item=1)           │
│  └─ Create in ERPNext                                          │
│         │                                                       │
│         ↓                                                       │
│  ERPNext: Item created                                         │
│  ├─ Stored with all fields                                     │
│  └─ Available for orders/invoices                              │
└─────────────────────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────────────────────┐
│               ITEM IN ORDER LIFECYCLE                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. Item displayed in catalog (ItemService.listItems)           │
│  2. User selects item + quantity + vendor in order creation     │
│  3. Pricing resolved:                                           │
│     ├─ ItemVendorPricingService finds pricing entry            │
│     ├─ Calculates: sellingRate = cost × (1 + margin%)          │
│     └─ Uses in order line                                      │
│  4. Order created with item lines:                              │
│     ├─ Creates Purchase Invoice in ERPNext (cost)               │
│     └─ Creates Sales Order (selling side)                       │
│  5. Stock updated:                                              │
│     ├─ ERPNext updates stock_qty                                │
│     └─ Stock module reflects new quantity                       │
└─────────────────────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────────────────────┐
│              ITEM STOCK MONITORING                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  StockService.listStockItems()                                  │
│  ├─ ItemService.listItems() — from ERPNext                      │
│  ├─ readMetadata() — from localStorage                          │
│  └─ mergeMetadata() — combine sources                           │
│         │                                                       │
│  For each item:                                                 │
│  ├─ parseQuantity() — resolve qty field                         │
│  ├─ parseThreshold() — get user-defined threshold               │
│  ├─ isLowStock = quantity <= threshold?                         │
│  └─ toViewModel() — create StockView                            │
│         │                                                       │
│  buildSummary():                                                │
│  ├─ Total items                                                 │
│  ├─ Total quantity                                              │
│  ├─ Low stock count                                             │
│  └─ Vendor count                                                │
└─────────────────────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────────────────────┐
│              ITEM UPDATES & DELETION                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Update Item:                                                   │
│  ├─ Only these fields updatable:                                │
│  │  ├─ item_name                                               │
│  │  ├─ stock_uom                                               │
│  │  ├─ aas_packaging_unit                                       │
│  │  └─ aas_margin_percent                                       │
│  ├─ All others immutable                                        │
│  └─ Return updated item                                         │
│         │                                                       │
│  Delete Item:                                                   │
│  ├─ Verify not system item                                      │
│  ├─ Soft delete: set disabled = 1                               │
│  ├─ Item hidden from UI                                         │
│  ├─ Historical data preserved                                   │
│  └─ Can be re-enabled                                           │
└─────────────────────────────────────────────────────────────────┘
```

---

## Key Characteristics

### ✅ Strengths
- **Consistent Identification** - Auto-generated item_code prevents duplicates
- **Vendor Linkage** - Primary vendor assigned at creation
- **Categorization** - Hierarchical categories for organization
- **Flexible Pricing** - Per-vendor pricing with margin calculation
- **Stock Tracking** - Multiple quantity fields for compatibility
- **Safe Deletion** - Soft delete preserves history
- **Whitelist Updates** - Only safe fields updatable

### ⚠️ Design Notes
- **Item Code Immutable** - Once created, cannot change (prevents order breakage)
- **Vendor Assignment** - Primary vendor set at creation (not changeable)
- **Soft Delete Only** - Never physically removes items (audit trail)
- **No Batch Operations** - Items managed one-at-a-time (could add bulk import later)
- **Client Metadata** - Per-device settings (not synced across devices)

---

## Summary

Stock item maintenance in AAS is a **tiered system** with:

1. **Master Data (Backend)** - Items stored in ERPNext with standardized fields
2. **Automatic Generation** - Item codes auto-generated from vendor + category + HSN
3. **Category Linkage** - Items organized by categories with vendor associations
4. **Pricing Management** - Per-vendor pricing calculated and stored client-side
5. **Stock Tracking** - Quantity monitoring with fallback field resolution
6. **Client Metadata** - Per-device customizations via localStorage
7. **Safe Lifecycle** - Create → Use in Orders → Monitor → Soft Delete

This hybrid approach combines **robust backend data** with **flexible client-side configuration**, enabling seamless order processing while maintaining audit trails and historical data.
