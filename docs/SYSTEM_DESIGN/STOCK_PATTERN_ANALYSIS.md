# Stock Management Pattern Analysis

## Overview

The Stock module implements a **local-client state management pattern** with localStorage persistence for threshold metadata, combined with remote backend API calls for item master data.

---

## Architecture Pattern

### Hybrid Data Management Strategy

```
┌─────────────────────────────────────────────────────────────┐
│  Stock List Component (UI)                                  │
│  - Displays items, quantities, thresholds, status            │
│  - Handles user interactions                                 │
└──────────────────┬──────────────────────────────────────────┘
                   │
        ┌──────────┴──────────┐
        │                     │
        ↓                     ↓
┌──────────────────┐  ┌──────────────────┐
│ StockService     │  │ ItemService      │
│ (Local State)    │  │ (Remote API)     │
└──────────────────┘  └──────────────────┘
        │                     │
        ↓                     ↓
┌──────────────────┐  ┌──────────────────┐
│ localStorage     │  │ /api/items       │
│ (Thresholds)     │  │ (Item Master)    │
└──────────────────┘  └──────────────────┘
```

### Key Components

**1. StockService** - Local state management
```typescript
- listStockItems(): Observable
  • Calls ItemService.listItems()
  • Merges with localStorage metadata
  • Returns combined view

- saveThreshold(itemId, threshold): void
  • Reads localStorage
  • Updates threshold for item
  • Writes back to localStorage
  • Synchronous operation (no HTTP)

- readMetadata(): Record
  • Retrieves localStorage data
  • Parses JSON safely
  • Fallback to empty object

- mergeMetadata(items): Array
  • Combines API items with stored thresholds
  • Joins on item.name key
```

**2. StockListComponent** - View & business logic
```typescript
- Properties:
  • stockItems: StockView[]  (combined model)
  • vendorGroups: VendorStockGroup[]  (grouped by vendor)
  • summary: { totalItems, totalQuantity, lowStock, vendors }
  • selectedItem: StockView | null  (detail view)

- Methods:
  • loadStock()  - Load items and merge thresholds
  • selectItem(item)  - Open detail view
  • saveThreshold(formValue)  - Save to localStorage
  • buildSummary()  - Compute summary stats
  • buildVendorGroups()  - Group by vendor
  • parseQuantity()  - Handle qty field variations
  • parseThreshold()  - Extract threshold value
  • isLowStock()  - Check if qty ≤ threshold
```

**3. Data Models**
```typescript
StockItem (from API)
  ├── name
  ├── item_name
  ├── item_code
  ├── stock_qty | actual_qty | quantity | qty  (qty fields)
  └── [aas_vendor]  (vendor association)

StockMetadata (from localStorage)
  └── threshold: number | null

StockView (combined, for UI)
  ├── id  (unique identifier)
  ├── name  (display name)
  ├── code  (item code)
  ├── vendorName  (from item.aas_vendor)
  ├── quantity  (resolved from qty fields)
  ├── threshold  (from localStorage)
  ├── isLow  (qty ≤ threshold)
  ├── statusLabel  ("Low" | "OK")
  ├── thresholdLabel  ("Not set" | number)
  └── raw  (original API item)

VendorStockGroup (aggregation)
  ├── vendorName
  ├── itemCount
  ├── totalQuantity
  └── itemNames[]
```

---

## Data Flow

### 1. Load Stock Items

```
Component.ngOnInit()
    ↓
loadStock() → isLoading = true
    ↓
StockService.listStockItems()
    ├─ ItemService.listItems()  (GET /api/items)
    │       ↓
    │  [Returns: StockItem[] from backend]
    │
    └─ mergeMetadata(items)
          ├─ readMetadata()  (from localStorage)
          │     ↓
          │  { [itemId]: { threshold: number } }
          │
          └─ Combine items + thresholds
                ↓
          [Returns: Array<StockItem & StockMetadata>]
    ↓
toViewModel() for each item
    ├─ Extract: id, name, code, vendorName
    ├─ parseQuantity()  (handle qty field variants)
    ├─ parseThreshold()  (from metadata)
    ├─ isLowStock = quantity ≤ threshold
    └─ [Returns: StockView]
    ↓
Component receives StockView[]
    ├─ buildSummary()  (compute totals, count low)
    ├─ buildVendorGroups()  (group by vendor)
    ├─ syncSelection()  (refresh if item selected)
    └─ isLoading = false
```

### 2. Save Threshold

```
User edits threshold for item
    ↓
saveThreshold(formValue: { itemId, threshold })
    ↓
readMetadata()  (from localStorage)
    ↓
store[itemId] = { threshold }
    ↓
localStorage.setItem('aas_stock_thresholds', JSON.stringify(store))
    ↓
statusMessage = "Threshold saved locally"
    ↓
loadStock()  (refresh to show updated status)
```

### 3. Status Determination

```
isLowStock(quantity, threshold):
    ├─ If threshold === null
    │     └─ return false  (no alert for unset threshold)
    │
    └─ If threshold !== null
          ├─ If quantity ≤ threshold
          │     └─ return true  (ALERT: Low stock)
          │
          └─ Else
                └─ return false  (OK: Stock adequate)
```

---

## Key Design Patterns

### 1. **Hybrid State Management**

**Remote State** (Backend):
- Item master data (code, name, vendor)
- Quantities (stock_qty, actual_qty)
- Item metadata (aas_vendor, etc.)
- Source: API (/api/items)
- Updates: By admin/system

**Local State** (Client):
- User-defined thresholds
- Per-device configuration
- Not synced across devices
- Storage: localStorage (aas_stock_thresholds)
- Updates: By user

**Merged View** (Component):
- Combines remote + local
- Single source for UI
- Computed on each load
- No caching layer

### 2. **Reactive Data Flow (RxJS)**

```typescript
StockService.listStockItems(): Observable<...>
    ↓
Component subscribes
    ↓
Data flows through the observable
    ↓
finalize(() => isLoading = false)  - Cleanup after success/error
    ↓
next: (items) => { process items }
error: (err) => { show error message }
```

### 3. **Resilient Quantity Parsing**

Since different ERPNext versions use different qty field names:

```typescript
private parseQuantity(item: StockItem): number {
  const candidates = [
    item.stock_qty,      // Primary field
    item.actual_qty,     // Fallback 1
    item.quantity,       // Fallback 2
    item.qty             // Fallback 3
  ];
  
  for (const candidate of candidates) {
    const value = Number(candidate);
    if (Number.isFinite(value)) {
      return value;  // Use first valid value
    }
  }
  return 0;  // Default if all invalid
}
```

### 4. **Safe localStorage Access**

```typescript
private readMetadata(): Record<string, StockMetadata> {
  try {
    const raw = localStorage.getItem('aas_stock_thresholds');
    if (!raw) return {};
    return JSON.parse(raw);
  } catch {
    return {};  // Graceful fallback on corrupt data
  }
}
```

### 5. **Vendor Grouping with Sorting**

```typescript
buildVendorGroups(items: StockView[]): VendorStockGroup[] {
  // 1. Build map of vendor → items
  const groups = new Map<string, VendorStockGroup>();
  
  // 2. Aggregate per vendor
  for (const item of items) {
    const key = item.vendorName || 'Unassigned vendor';
    // Update count, quantity, names
  }
  
  // 3. Convert to array & sort
  return Array.from(groups.values())
    .map(group => ({
      ...group,
      itemNames: group.itemNames.sort()  // Sort items within vendor
    }))
    .sort((a, b) => a.vendorName.localeCompare(b.vendorName));  // Sort vendors
}
```

---

## Advantages of This Pattern

✅ **Decoupled Concerns**
- Backend: Manages authoritative item data
- Frontend: Manages user preferences
- No tight coupling

✅ **Offline Capability**
- Thresholds work without internet
- View still functional with cached data
- Graceful degradation

✅ **No Backend Changes Needed**
- Thresholds stored client-side
- No API changes to ERPNext
- Custom fields (aas_*) optional

✅ **Performance**
- Threshold save is instant (localStorage)
- No HTTP latency
- No server load

✅ **Per-Device Configuration**
- Each user/device has own thresholds
- Not synced across devices
- Independent preferences

✅ **Simplicity**
- No complex state management (Redux, NgRx)
- Single service with clear methods
- Easy to understand and maintain

---

## Disadvantages of This Pattern

❌ **No Data Sync Across Devices**
- Thresholds don't sync to other devices
- If user switches device, thresholds reset
- Mobile & desktop have separate settings

❌ **No Multi-User Sharing**
- Each user has own localStorage
- Can't share thresholds with team
- No collaborative threshold setting

❌ **Data Loss Risk**
- localStorage cleared → thresholds lost
- Browser data cleared → thresholds lost
- No backup or recovery

❌ **No Audit Trail**
- When user changed threshold: unknown
- Why threshold changed: unknown
- No historical tracking

❌ **No Validation**
- No server-side validation of thresholds
- User can set invalid values
- No business rule enforcement

---

## Alternative Patterns (Not Used)

### 1. **Full Backend Storage**
```
User sets threshold
  ↓
POST /api/stock/thresholds
  ↓
Backend saves to database
  ↓
Future loads fetch from server
```
**Pros:** Sync across devices, audit trail, shared settings
**Cons:** Extra API calls, server load, backend changes

### 2. **Redux/NgRx State Management**
```
User action
  ↓
Dispatch action
  ↓
Reducer updates store
  ↓
Component subscribes to store
```
**Pros:** Centralized state, time-travel debugging
**Cons:** Boilerplate code, learning curve, complexity

### 3. **IndexedDB (Advanced LocalStorage)**
```
localStorage.setItem() → localStorage[]
IndexedDB.put() → IndexedDB{}
```
**Pros:** Larger storage, better performance, async
**Cons:** More complex API, overkill for simple use case

---

## Summary

The Stock module uses a **pragmatic hybrid approach**:

1. **Remote API** for authoritative item data (names, quantities, vendor)
2. **localStorage** for user-defined thresholds (local-only configuration)
3. **In-memory merge** on component load for unified view
4. **Reactive RxJS** for clean data flow

This is **ideal for simple per-device preferences** that don't need to be synced, shared, or audited. It keeps the architecture simple while maintaining flexibility.

If future requirements demand cross-device sync, audit trails, or team sharing, the pattern can evolve to backend storage without major refactoring—the service interface remains the same.
