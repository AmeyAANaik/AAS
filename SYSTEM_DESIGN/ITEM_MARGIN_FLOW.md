# Item Margin Flow - Complete Analysis

## Overview

The item margin system is **single-tier** - every item has a margin:

1. **Item with Margin Set** - Uses the item's specific margin (e.g., 8%, 10%, 12%)
2. **Item without Margin (or missing from catalog)** - Uses the **default 7% margin** applied uniformly

The flow converts vendor invoice costs into selling prices using the item's margin (or 7% if not specified), respecting MRP caps where applicable.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Item Master (Catalog)                                          │
│  └─ aas_margin_percent (default 7% or item-specific)           │
└──────────────────────┬──────────────────────────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        │                             │
        ↓                             ↓
┌────────────────────┐      ┌────────────────────┐
│ Vendor Rate        │      │ MRP (if set)       │
│ (Cost per unit)    │      │ (Max selling price)│
└────────────────────┘      └────────────────────┘
        │                             │
        └──────────────┬──────────────┘
                       ↓
        ┌──────────────────────────────┐
        │ OrderPricingService          │
        │ applyMrpCap()                │
        │ ├─ Calculate: rate × (1+m%)  │
        │ └─ Cap at MRP if needed      │
        └──────────────┬───────────────┘
                       ↓
        ┌──────────────────────────────┐
        │ Selling Rate (Price)         │
        │ (capped or calculated)       │
        │ Effective Margin %           │
        │ (actual achieved margin)     │
        └──────────────────────────────┘
```

---

## Margin Sources & Resolution

### 1. Item-Level Default Margin (7%)

**Source: Item Master Data**

```typescript
// Frontend: item-form.component.ts
private readonly defaultMarginPercent = 7;

form: FormGroup = this.fb.group({
  marginPercent: [this.defaultMarginPercent, [Validators.required, Validators.min(0), Validators.max(100)]]
});
```

**Backend: Item Creation (MasterDataService.createItem)**

```typescript
// When creating item, margin defaults to 7%
// If user doesn't specify, uses 7%
// If user specifies custom value, uses that value
// Stored in: aas_margin_percent field
```

**Storage:**
- Field: `aas_margin_percent` (on Item doctype)
- Default: 7.0 (7%)
- Range: 0-100
- Type: Double (decimal)

### 2. Default Margin for Missing Items (Same 7%)

**Source: Application Configuration**

```java
// Backend: VendorPdfService.java
@Value("${app.order.margin.default-percent:7}") 
double defaultMarginPercent;

// Backend: OrderService.java
@Value("${app.order.margin.default-percent:7}") 
double defaultMarginPercent;
```

**Default Value:** 7%
**When Used:** 
- Item doesn't have a margin set (aas_margin_percent is null or 0)
- Item is not found in catalog
- Item data is incomplete
**Fallback Chain:**
1. Item's `aas_margin_percent` (if > 0)
2. Default margin from config (7%)
3. Never 0% - always has some margin

### 3. Margin Resolution Logic (Single Margin Rule)

```java
// VendorPdfService.java
private double resolveMarginPercent(Object value) {
  double margin = asDouble(value);
  
  // If value is null, empty, or zero → use default 7%
  if (value == null || value.toString().trim().isEmpty() || margin == 0.0) {
    margin = defaultMarginPercent;  // Always 7% (same margin as for items)
  }
  
  // Validate: no negative margins
  if (margin < 0) {
    throw new IllegalArgumentException("Margin percent must be non-negative.");
  }
  
  return margin;
}

// Sequence (applies to ALL items):
// 1. IF item has aas_margin_percent > 0:
//    → Use that item's specific margin
// 2. ELSE (item margin not set or item missing from catalog):
//    → Apply default 7% margin (same margin for all new/missing items)
// 3. NEVER allow negative margins
```

---

## Margin Calculation Flow (In Orders)

### Step 1: Item Selection in Order

When user selects an item in order creation:

```typescript
// Frontend: order-page.component.ts
interface ItemOption {
  id: string;
  name: string;
  code: string;
  category?: string;
  unit?: string;
  marginPercent?: number | null;  // ← Item's margin
}

// Load item with its margin
const itemOptions = items.map(item => ({
  ...item,
  marginPercent: item.aas_margin_percent
}));
```

### Step 2: Margin Resolution (Order Creation)

**Backend: OrderService.createOrderFromItems**

```java
private Map<String, Object> resolveItemLine(
    Map<String, Object> line,
    String itemCode,
    List<Map<String, Object>> items,
    CatalogRoutingService.VendorCategoryResolution vendorResolution) {
    
  Map<String, Object> copy = new HashMap<>(line);
  
  // 1. Try to get margin from parsed line
  double margin = resolveMarginPercent(line.get("aas_margin_percent"), itemCode);
  //    └─ Looks up item master if margin not in line
  
  // 2. Apply pricing logic (MRP cap, etc.)
  OrderPricingService.LinePricing pricing = orderPricingService.applyMrpCap(
    vendorRate,
    margin,
    mrp,
    itemLabel
  );
  
  // 3. Store effective margin (may differ from input if MRP capped)
  copy.put("aas_margin_percent", pricing.effectiveMarginPercent());
  
  return copy;
}

private double resolveMarginPercent(Object value, String itemCode) {
  double margin = asDouble(value);
  
  // First: Check if value provided
  if (value != null && !value.toString().trim().isEmpty() && margin > 0) {
    return margin;
  }
  
  // Second: Look up item from master
  try {
    Map<String, Object> item = unwrapResource(
      erpNextClient.getResource("Item", itemCode)
    );
    double itemMargin = asDouble(item.get("aas_margin_percent"));
    if (itemMargin > 0) {
      return itemMargin;
    }
  } catch (Exception ignored) {
    // Fall through to default
  }
  
  // Third: Use company default
  return defaultMarginPercent;  // 7%
}
```

### Step 3: Margin Application (Selling Price Calculation)

**OrderPricingService.applyMrpCap**

```java
public LinePricing applyMrpCap(
    double vendorRate,
    double requestedMarginPercent,
    Double mrp,
    String itemLabel) {
  
  // Sanitize margin (ensure non-negative)
  double marginPercent = sanitizeMargin(requestedMarginPercent);
  
  // Calculate base selling rate
  double sellRate = round(vendorRate * (1 + marginPercent / 100.0));
  // Formula: Selling Rate = Vendor Rate × (1 + Margin% / 100)
  
  // Normalize MRP
  double normalizedMrp = mrp == null ? 0.0 : round(mrp);
  
  // Case 1: No MRP or invalid costs
  if (normalizedMrp <= 0 || vendorRate <= 0) {
    return new LinePricing(sellRate, marginPercent, false);  // Use calculated rate
  }
  
  // Case 2: Vendor cost exceeds MRP (ERROR)
  if (round(vendorRate) > normalizedMrp) {
    throw new IllegalArgumentException(
      "Vendor rate exceeds MRP for " + itemLabel + 
      ". Vendor rate=" + vendorRate + ", MRP=" + normalizedMrp
    );
  }
  
  // Case 3: Calculated rate within MRP (OK)
  if (sellRate <= normalizedMrp) {
    return new LinePricing(sellRate, marginPercent, false);  // Use calculated
  }
  
  // Case 4: Calculated rate exceeds MRP (CAP AT MRP)
  double cappedSellRate = normalizedMrp;
  
  // Recalculate effective margin based on capped rate
  double effectiveMarginPercent = vendorRate <= 0
    ? 0.0
    : round(((cappedSellRate - vendorRate) / vendorRate) * 100.0);
  
  // Return: capped rate, recalculated margin, flag indicating cap was applied
  return new LinePricing(cappedSellRate, effectiveMarginPercent, true);
}
```

**Key Formula:**
```
Selling Rate = Vendor Rate × (1 + Margin% / 100)

Example with 7% margin:
- Vendor Rate: 100
- Margin: 7%
- Selling Rate = 100 × (1 + 7/100) = 100 × 1.07 = 107
```

**MRP Capping Logic:**
```
If MRP exists and Calculated Selling Rate > MRP:
  1. Cap selling rate at MRP
  2. Recalculate effective margin:
     Effective Margin% = ((Capped Rate - Vendor Rate) / Vendor Rate) × 100
  
Example:
- Vendor Rate: 100
- Margin: 7%
- Calculated Selling: 107
- MRP: 105 (less than calculated)
- Capped Selling Rate: 105
- Effective Margin: ((105-100)/100) × 100 = 5%
```

---

## Margin Flow in PDF Processing (Vendor Invoice)

When a vendor PDF is uploaded and parsed:

### Flow Diagram

```
┌──────────────────────────────┐
│ Vendor PDF Upload            │
│ GET /vendor-pdf/{orderId}    │
└────────────────┬─────────────┘
                 │
                 ↓
    ┌────────────────────────┐
    │ PDF Parser             │
    │ (OCR + Template)       │
    │ Extracts:              │
    │ ├─ item_code           │
    │ ├─ qty                 │
    │ ├─ rate (vendor cost)  │
    │ ├─ aas_margin_percent  │
    │ └─ aas_mrp (if set)    │
    └────────────┬───────────┘
                 │
                 ↓
    ┌────────────────────────┐
    │ resolveItems()         │
    │ Creates base items     │
    │ with parsed data       │
    └────────────┬───────────┘
                 │
         ┌───────┴────────┐
         │                │
         ↓                ↓
   ┌──────────────┐ ┌──────────────┐
   │ withVendor   │ │ withSellMargin│
   │ Rate()       │ │ ()           │
   │ (Cost side)  │ │ (Sell side)   │
   └──────┬───────┘ └──────┬───────┘
          │                │
          ↓                ↓
   ┌─────────────────────────────┐
   │ For each item:              │
   │ ├─ resolveMarginPercent()   │
   │ ├─ applyMrpCap()            │
   │ └─ enriched items           │
   └──────────┬──────────────────┘
              │
              ↓
   ┌──────────────────────────┐
   │ calculateDerivedMargin   │
   │ Percent()                │
   │ Total margin across      │
   │ all items                │
   └──────────┬───────────────┘
              │
              ↓
   ┌──────────────────────────┐
   │ Order Updated with:      │
   │ ├─ aas_margin_percent    │
   │ ├─ items with margins    │
   │ └─ sell preview          │
   └──────────────────────────┘
```

### Code Implementation

```java
// VendorPdfService.processVendorPdf()
public Map<String, Object> processVendorPdf(
    String orderId, 
    MultipartFile pdfFile, 
    String sessionCookie) {
  
  // ... PDF parsing ...
  List<ParsedItem> parsedItems = extractionResult.items();
  
  // 1. Resolve items from parsed data
  List<Map<String, Object>> baseItems = resolveItems(parsedItems, vendorResolution);
  
  // 2. Create vendor cost view
  List<Map<String, Object>> sourceOrderItems = withVendorRate(baseItems);
  
  // 3. Create selling view
  List<Map<String, Object>> sellItems = withSellMargin(baseItems);
  
  // 4. Calculate overall margin
  double marginPercent = calculateDerivedMarginPercent(sourceOrderItems);
  
  // 5. Update order
  Map<String, Object> linkUpdate = new HashMap<>();
  linkUpdate.put("items", sourceOrderItems);
  linkUpdate.put("aas_margin_percent", marginPercent);
  // ... other fields ...
  erpNextClient.updateResource("Sales Order", orderId, linkUpdate);
  
  // 6. Return preview
  return Map.of(
    "sellPreview", Map.of(
      "vendorTotal", sumAmount(baseItems),
      "marginPercent", marginPercent,
      "sellTotal", sumAmount(sellItems)
    )
  );
}
```

### withVendorRate() - Cost Side

```java
private List<Map<String, Object>> withVendorRate(
    List<Map<String, Object>> baseItems) {
  
  List<Map<String, Object>> enriched = new ArrayList<>();
  
  for (Map<String, Object> row : baseItems) {
    Map<String, Object> copy = new HashMap<>(row);
    double vendorRate = asDouble(row.get("rate"));  // From PDF
    
    // Resolve margin
    double marginPercent = resolveMarginPercent(row.get("aas_margin_percent"));
    // Logic: If item has margin → use it, else use 7%
    
    // Apply MRP cap if needed
    OrderPricingService.LinePricing pricing = orderPricingService.applyMrpCap(
      vendorRate,
      marginPercent,
      asNullableDouble(row.get("aas_mrp")),
      asText(row.get("item_name"))
    );
    
    // Store vendor cost details
    copy.put("aas_vendor_rate", vendorRate);        // Original vendor rate
    copy.put("aas_margin_percent", pricing.effectiveMarginPercent());  // Final margin
    
    enriched.add(copy);
  }
  return enriched;
}
```

### withSellMargin() - Selling Side

```java
private List<Map<String, Object>> withSellMargin(
    List<Map<String, Object>> baseItems) {
  
  List<Map<String, Object>> enriched = new ArrayList<>();
  
  for (Map<String, Object> row : baseItems) {
    Map<String, Object> copy = new HashMap<>(row);
    double vendorRate = asDouble(row.get("rate"));
    double qty = asDouble(row.get("qty"));
    
    // Resolve margin (same logic as cost side)
    double marginPercent = resolveMarginPercent(row.get("aas_margin_percent"));
    
    // Apply MRP cap
    OrderPricingService.LinePricing pricing = orderPricingService.applyMrpCap(
      vendorRate,
      marginPercent,
      asNullableDouble(row.get("aas_mrp")),
      asText(row.get("item_name"))
    );
    
    // Update with selling prices
    copy.put("rate", pricing.sellRate());               // Selling rate per unit
    copy.put("amount", round(pricing.sellRate() * qty)); // Total selling amount
    copy.put("aas_vendor_rate", vendorRate);             // Keep vendor cost for reference
    copy.put("aas_margin_percent", pricing.effectiveMarginPercent());
    
    enriched.add(copy);
  }
  return enriched;
}
```

### calculateDerivedMarginPercent() - Overall Margin

```java
private double calculateDerivedMarginPercent(
    List<Map<String, Object>> items) {
  
  double vendorTotal = 0.0;
  double sellTotal = 0.0;
  
  for (Map<String, Object> row : items) {
    double qty = asDouble(row.get("qty"));
    if (qty <= 0) qty = 1.0;  // Default to 1 if missing
    
    double vendorRate = asDouble(row.get("aas_vendor_rate"));
    if (vendorRate <= 0) {
      vendorRate = asDouble(row.get("rate"));  // Fallback
    }
    
    double marginPercent = resolveMarginPercent(row.get("aas_margin_percent"));
    
    // Calculate totals
    vendorTotal += vendorRate * qty;
    sellTotal += vendorRate * (1 + marginPercent / 100.0) * qty;
  }
  
  vendorTotal = round(vendorTotal);
  sellTotal = round(sellTotal);
  
  // Fallback to default if no vendor data
  if (vendorTotal <= 0) {
    return defaultMarginPercent;  // 7%
  }
  
  // Derive overall margin %
  return round(((sellTotal - vendorTotal) / vendorTotal) * 100.0);
  
  // Formula: Overall Margin% = ((Total Sell - Total Cost) / Total Cost) × 100
}
```

---

## Margin in Item Vendor Pricing

### ItemVendorPricingService (Client-Side)

Items can have **per-vendor pricing** stored in `localStorage`:

```typescript
interface ItemVendorPricingEntry {
  itemId: string;
  itemName: string;
  vendorId: string;
  vendorName: string;
  originalRate: number;      // Vendor's cost
  marginPercent: number;     // Markup %
  finalRate: number;         // Calculated selling price
}

const PRICING_KEY = 'aas_item_vendor_pricing';

calculateFinalRate(originalRate: number, marginPercent: number): number {
  // Final Rate = Original Rate × (1 + Margin% / 100)
  const margin = Number(marginPercent) || 0;
  return Number((originalRate * (1 + margin / 100)).toFixed(2));
}

// Example:
// originalRate: 100
// marginPercent: 7
// finalRate = 100 × (1 + 7/100) = 100 × 1.07 = 107
```

---

## Margin in Frontend (Item Vendor Pricing Component)

### User Interface

```typescript
// item-vendor-pricing.component.ts
private readonly defaultMarginPercent = 7;

form: FormGroup = this.fb.group({
  itemId: ['', [Validators.required]],
  vendorId: ['', [Validators.required]],
  originalRate: [null, [Validators.required, Validators.min(0)]],
  marginPercent: [this.defaultMarginPercent, [Validators.required, Validators.min(0)]]
});

finalRate = 0;

// Whenever form changes, recalculate
form.valueChanges.subscribe(() => this.updateFinalRate());

updateFinalRate(): void {
  const originalRate = Number(this.form.get('originalRate')?.value) || 0;
  const marginPercent = Number(this.form.get('marginPercent')?.value) || 0;
  this.finalRate = this.pricingService.calculateFinalRate(originalRate, marginPercent);
}

submit(): void {
  const entry: ItemVendorPricingEntry = {
    itemId: ...,
    vendorId: ...,
    originalRate: Number(value.originalRate) || 0,
    marginPercent: Number(value.marginPercent) || 0,
    finalRate: this.pricingService.calculateFinalRate(originalRate, marginPercent)
  };
  this.pricingSaved.emit(entry);
}
```

---

## Complete Data Flow Example

### Scenario: Order with Item that has 7% default margin

**Step 1: Item in Catalog**
```
Item: "Controller Unit"
Item Code: VEN001-ELEC-854430
aas_margin_percent: 7  (or not set → defaults to 7)
aas_vendor: "VEN001"
```

**Step 2: Vendor PDF Upload**
```
PDF Contains:
├─ Item: "Controller Unit"
├─ Qty: 10
└─ Rate: 100 (vendor cost per unit)

Parsing:
├─ item_code: "VEN001-ELEC-854430"
├─ qty: 10
├─ rate: 100
└─ aas_margin_percent: (empty/missing)

Resolution:
├─ Margin not in PDF → Look up item master
├─ Item master has aas_margin_percent: 7
└─ Use 7%
```

**Step 3: Margin Calculation**
```
vendorRate = 100
marginPercent = 7
mrp = null (not set)

applyMrpCap(100, 7, null):
  ├─ sellRate = 100 × (1 + 7/100) = 107
  ├─ No MRP to cap against
  └─ Return LinePricing(107, 7%, mrpApplied=false)

Per-Item:
├─ Vendor Rate: 100
├─ Selling Rate: 107
├─ Quantity: 10
└─ Margin per item: 7%

Totals:
├─ Vendor Total: 100 × 10 = 1000
├─ Selling Total: 107 × 10 = 1070
└─ Overall Margin: ((1070-1000)/1000) × 100 = 7%
```

**Step 4: Order Updated**
```
Sales Order:
├─ aas_margin_percent: 7
├─ items: [
│   {
│     item_code: "VEN001-ELEC-854430",
│     item_name: "Controller Unit",
│     qty: 10,
│     rate: 100 (vendor cost),
│     amount: 1000,
│     aas_vendor_rate: 100,
│     aas_margin_percent: 7
│   }
│ ]
├─ aas_cost_total: 1000
└─ aas_margin_total: 70
```

**Step 5: Invoice Generation**
```
Sales Invoice created with:
├─ Line Item:
│  ├─ Item: "Controller Unit"
│  ├─ Qty: 10
│  ├─ Rate: 107 (selling price)
│  ├─ Amount: 1070
│  └─ Margin: 7%
├─ Total: 1070
├─ Cost: 1000
└─ Profit: 70
```

---

## Margin with MRP (Maximum Retail Price)

### Scenario: Item with MRP constraint

**Example:**
```
Vendor Rate: 95
Item Margin: 10%
Item MRP: 100
Calculated Selling: 95 × (1 + 10/100) = 104.5

Problem: 104.5 > 100 (MRP)

Solution:
├─ Cap selling rate at MRP: 100
├─ Recalculate effective margin:
│  Effective Margin% = ((100 - 95) / 95) × 100 = 5.26%
└─ Store: Margin 5.26%, Rate 100, Flag: mrpApplied=true
```

**MRP Logic in applyMrpCap():**
```
if (sellRate > MRP):
  cappedSellRate = MRP
  effectiveMarginPercent = ((cappedSellRate - vendorRate) / vendorRate) × 100
  mrpApplied = true
```

---

## Configuration & Defaults

### Application Configuration

```properties
# Default margin percent
app.order.margin.default-percent=7
```

### Frontend Defaults

```typescript
// All margin inputs default to 7%
private readonly defaultMarginPercent = 7;
```

### Backend Defaults

```java
@Value("${app.order.margin.default-percent:7}") 
double defaultMarginPercent;
```

---

## Margin Resolution Priority (Simple: One Margin for All)

The system follows a simple **priority chain** - there's only ONE margin (7%):

```
PRIORITY 1: Item's specific margin (if set in item master)
  │ Does item exist with aas_margin_percent > 0?
  ├─ YES → Use that item's margin (e.g., 8%, 10%, 12%)
  └─ NO → Continue

PRIORITY 2: Default margin (applied to all items without specific margin)
  │ From app.order.margin.default-percent config
  └─ Default 7% (applies to new items, missing items, and items without margin)

RESULT: 
  Every item/order line has SOME margin - either specific or default 7%
```

**Examples:**
- Item "Controller" with aas_margin_percent=10 → Use 10%
- Item "Resistor" with aas_margin_percent=0 or null → Use 7% (default)
- New item (not in catalog) → Use 7% (default, same as unset items)
- Item from PDF without margin → Use 7% (default)

**Implementation:**
```java
private double resolveMarginPercent(Object value) {
  double margin = asDouble(value);
  
  // P1: Explicit value check
  if (value != null && !value.toString().trim().isEmpty() && margin > 0) {
    return margin;
  }
  
  // P2: Look up item → P3: Default
  // (handled in separate method)
  
  // Default fallback
  return margin > 0 ? margin : defaultMarginPercent;
}
```

---

## Key Properties of Margin System

### ✅ Strengths
- **Flexible** - Per-item, per-vendor, company default margins
- **Automatic Fallback** - 7% default if item margin not set
- **MRP-Aware** - Respects maximum retail price constraints
- **Non-Negative** - Prevents loss-making scenarios (margin < 0)
- **Precision** - 2 decimal rounding (0.01 accuracy)
- **Derivable** - Overall margin calculated from line items

### ⚠️ Design Notes
- **Default is 7%** - Company standard markup (configurable)
- **MRP Overrides Margin** - If calculated price exceeds MRP, reduces margin
- **Line-Level Tracking** - Each order item has its own margin %
- **Overall Margin Derived** - Calculated from total sell vs total cost
- **No Negative Margins** - Exception thrown if attempted

---

## Summary

The item margin flow implements a **simple single-tier system**:

1. **Item Margin** (if set in catalog) - Use the item's specific aas_margin_percent
2. **Default Margin** (7% - same for all missing/unset items) - Applied when item has no margin
3. **Per-Vendor Pricing** (Client) - localStorage stores vendor-specific rates
4. **MRP-Constrained** - Selling price capped at maximum retail price
5. **Derived Overall** - Calculated from aggregated line items

**The flow:**
```
Item has margin?
├─ YES → Use item's margin (e.g., 10%)
└─ NO → Use default 7% (same margin for ALL missing items)
    ↓
+ Vendor Cost
    ↓
= Calculated Selling Price
    ↓
Cap at MRP (if exists)
    ↓
= Final Selling Price
    ↓
Effective Margin% (may differ from input if MRP capped)
```

This system ensures that **every item always has a margin** - either its specific margin or the uniform 7% default - while respecting MRP constraints and precision rounding.
