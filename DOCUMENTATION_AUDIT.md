# Documentation Audit & Consolidation Plan

**Date:** 2026-04-04
**Status:** Analysis Complete

---

## Current Documentation Inventory

### Active Documentation (Keep & Maintain)

#### ✅ **ARCHITECTURE.md** (666 lines)
- **Purpose:** Complete system architecture overview
- **Content:** Three-tier architecture, controllers, services, API endpoints, data flows
- **Status:** Current & Comprehensive
- **Keep?:** YES - Essential reference

#### ✅ **STOCK_ITEM_MAINTENANCE.md** (770 lines)
- **Purpose:** Complete item lifecycle and storage
- **Content:** Item creation, retrieval, updates, deletion, categorization, vendor linking
- **Status:** Current & Detailed
- **Keep?:** YES - Critical for item operations

#### ✅ **ITEM_MARGIN_FLOW.md** (820 lines)
- **Purpose:** Item margin system (default 7% + MRP constraints)
- **Content:** Margin resolution, calculation, MRP capping, per-vendor pricing
- **Status:** Recently Updated (corrected to single 7% margin)
- **Keep?:** YES - Core business logic

#### ✅ **CATALOG_STORAGE_SYSTEM.md** (621 lines)
- **Purpose:** How catalog is stored and managed
- **Content:** ERPNext storage, item code generation, retrieval, CRUD operations
- **Status:** Recently Created
- **Keep?:** YES - Important for understanding data persistence

#### ✅ **STOCK_PATTERN_ANALYSIS.md** (397 lines)
- **Purpose:** Stock threshold management pattern
- **Content:** Hybrid state (API + localStorage), quantity parsing, vendor grouping
- **Status:** Current & Complete
- **Keep?:** YES - Inventory management pattern

---

### Redundant/Outdated Documentation (Remove or Consolidate)

#### ❌ **DOCUMENTATION_SUMMARY.md** (106 lines)
- **Purpose:** Summary of PDF generation script
- **Content:** Outdated - references old approach to documentation
- **Issue:** Redundant with COMPLETE_DOCUMENTATION_SUMMARY.md
- **Action:** **DELETE** - Superseded

#### ❌ **COMPLETE_DOCUMENTATION_SUMMARY.md** (242 lines)
- **Purpose:** Summary of ZIP package with screenshots and flows
- **Content:** References PDF files that may not be up-to-date
- **Issue:** More of a packaging summary than technical documentation
- **Action:** **CONSOLIDATE** - Merge into master README

#### ❌ **UI_SCREENSHOTS_README.md** (262 lines)
- **Purpose:** Instructions for capturing UI screenshots
- **Content:** Playwright script details, screenshot capture guide
- **Issue:** Infrastructure/tooling docs, not application documentation
- **Action:** **ARCHIVE** - Move to /docs/tools/ folder

#### ⚠️ **PROJECT_CONTEXT.md** (287 lines)
- **Purpose:** High-level project overview
- **Content:** Architecture layers, stack overview, module listing
- **Issue:** Overlaps heavily with ARCHITECTURE.md, less detailed
- **Action:** **CONSOLIDATE** - Merge critical parts into ARCHITECTURE.md

#### ❌ **SYSTEM_DESIGN.md** (221 lines)
- **Purpose:** System design principles
- **Content:** Generic design patterns, less specific to AAS
- **Issue:** Too generic, better covered in ARCHITECTURE.md
- **Action:** **DELETE** - Content better in ARCHITECTURE.md

#### ❌ **SCM_UI_REFACTOR_GUIDE.md** (269 lines)
- **Purpose:** SCM (Supply Chain Management) UI refactor guide
- **Content:** Specific refactor instructions, likely outdated
- **Issue:** Appears to be past refactor work, not current system docs
- **Action:** **ARCHIVE** - Move to /docs/completed-tasks/

#### ❌ **STARTUP_COMMANDS.md** (611 lines)
- **Purpose:** Development environment setup
- **Content:** Docker commands, build scripts, dev instructions
- **Issue:** Operations/setup guide, not application documentation
- **Action:** **ARCHIVE** - Move to README or /docs/setup/

---

### Missing Documentation (Need to Create)

#### 🔴 **ORDER WORKFLOW GUIDE** (Missing)
- **Should Cover:** Order creation (image/item modes) → vendor assignment → PDF upload → bill capture → sell order → invoice
- **Status:** NEEDED - Core business process
- **Priority:** CRITICAL

#### 🔴 **VENDOR OPERATIONS GUIDE** (Missing)
- **Should Cover:** Vendor summary → drill-down → ledger tracking → settlement states
- **Status:** NEEDED - Key operational feature
- **Priority:** HIGH

#### 🔴 **BRANCH OPERATIONS GUIDE** (Missing)
- **Should Cover:** Branch summary → receivables tracking → payment health → settlement
- **Status:** NEEDED - Key operational feature
- **Priority:** HIGH

#### 🔴 **BILLING & INVOICE GUIDE** (Missing)
- **Should Cover:** Invoice creation modes → GST handling → payment recording → allocation
- **Status:** NEEDED - Financial workflow
- **Priority:** HIGH

#### 🔴 **DASHBOARD & REPORTING GUIDE** (Missing)
- **Should Cover:** KPI calculation, month-scoped data, summary metrics
- **Status:** NEEDED - User-facing feature
- **Priority:** MEDIUM

#### 🔴 **GST & TAX HANDLING** (Missing)
- **Should Cover:** GST calculation, Item Tax Templates, tax on items/orders
- **Status:** NEEDED - Compliance critical
- **Priority:** MEDIUM

#### 🔴 **VENDOR INVOICE PARSING (OCR/AI)** (Missing)
- **Should Cover:** PDF parsing, AI template generation, field mapping
- **Status:** NEEDED - Key automation feature
- **Priority:** MEDIUM

#### 🔴 **ERROR HANDLING & VALIDATION** (Missing)
- **Should Cover:** Margin validation, MRP constraints, field constraints
- **Status:** NEEDED - Important for correctness
- **Priority:** LOW

---

## Documentation Consolidation Plan

### Phase 1: Cleanup (Remove Non-Relevant Docs)

**To Delete:**
1. ❌ DOCUMENTATION_SUMMARY.md
2. ❌ SYSTEM_DESIGN.md

**To Archive (move to /docs/archived/):**
1. ➡️ UI_SCREENSHOTS_README.md
2. ➡️ STARTUP_COMMANDS.md
3. ➡️ SCM_UI_REFACTOR_GUIDE.md

**To Consolidate:**
1. 🔄 PROJECT_CONTEXT.md → Merge into ARCHITECTURE.md
2. 🔄 COMPLETE_DOCUMENTATION_SUMMARY.md → Create comprehensive README.md

---

### Phase 2: Create Missing Documentation

**CRITICAL (Do First):**
1. 📝 ORDER_WORKFLOW.md - Complete order lifecycle
2. 📝 VENDOR_OPERATIONS.md - Vendor KPIs, ledger, settlement
3. 📝 BRANCH_OPERATIONS.md - Branch receivables, payment tracking
4. 📝 BILLING_INVOICE.md - Invoice creation and payment recording

**HIGH PRIORITY:**
5. 📝 DASHBOARD_GUIDE.md - KPI explanations and data scoping
6. 📝 GST_TAX_SYSTEM.md - GST calculation and compliance
7. 📝 VENDOR_PDF_PARSING.md - PDF extraction and template generation

**MEDIUM PRIORITY:**
8. 📝 VALIDATION_RULES.md - Field constraints and business rules
9. 📝 API_ENDPOINTS.md - Complete endpoint reference with examples

---

### Phase 3: Create Master Index

**Create:** README.md
- **Purpose:** Entry point for all documentation
- **Content:**
  - System overview
  - Feature matrix
  - Module breakdown
  - Links to detailed docs
  - Setup instructions (from STARTUP_COMMANDS)
  - API documentation link

---

## Recommended File Structure

```
/Users/roshninaik/Projects/AAS/
├─ README.md (NEW - Master index)
│
├─ ARCHITECTURE.md (KEEP - Updated with PROJECT_CONTEXT content)
│
├─ CORE_FLOWS/
│  ├─ ORDER_WORKFLOW.md (NEW)
│  ├─ VENDOR_OPERATIONS.md (NEW)
│  ├─ BRANCH_OPERATIONS.md (NEW)
│  ├─ BILLING_INVOICE.md (NEW)
│  └─ DASHBOARD_GUIDE.md (NEW)
│
├─ SYSTEM_DESIGN/
│  ├─ STOCK_ITEM_MAINTENANCE.md (KEEP)
│  ├─ ITEM_MARGIN_FLOW.md (KEEP)
│  ├─ CATALOG_STORAGE_SYSTEM.md (KEEP)
│  ├─ STOCK_PATTERN_ANALYSIS.md (KEEP)
│  ├─ GST_TAX_SYSTEM.md (NEW)
│  ├─ VENDOR_PDF_PARSING.md (NEW)
│  └─ VALIDATION_RULES.md (NEW)
│
├─ API_REFERENCE/ (NEW)
│  └─ API_ENDPOINTS.md (NEW)
│
└─ docs/ (for setup & tools)
   ├─ SETUP.md (from STARTUP_COMMANDS)
   ├─ archived/
   │  ├─ UI_SCREENSHOTS_README.md
   │  ├─ SCM_UI_REFACTOR_GUIDE.md
   │  └─ COMPLETE_DOCUMENTATION_SUMMARY.md
   └─ tools/
      └─ SCREENSHOT_CAPTURE.md
```

---

## Summary of Actions

| File | Action | Reason |
|------|--------|--------|
| DOCUMENTATION_SUMMARY.md | DELETE | Redundant, outdated |
| SYSTEM_DESIGN.md | DELETE | Generic content better in ARCHITECTURE.md |
| PROJECT_CONTEXT.md | MERGE INTO ARCHITECTURE.md | Consolidate overlapping content |
| COMPLETE_DOCUMENTATION_SUMMARY.md | ARCHIVE | Tooling/packaging guide, not app docs |
| UI_SCREENSHOTS_README.md | ARCHIVE | Tooling guide, not app docs |
| SCM_UI_REFACTOR_GUIDE.md | ARCHIVE | Completed task, historical reference |
| STARTUP_COMMANDS.md | ARCHIVE | Setup/ops guide, move to /docs/SETUP.md |
| ARCHITECTURE.md | UPDATE | Add PROJECT_CONTEXT content, expand |
| STOCK_ITEM_MAINTENANCE.md | KEEP | Critical and current |
| ITEM_MARGIN_FLOW.md | KEEP | Critical and current |
| CATALOG_STORAGE_SYSTEM.md | KEEP | Critical and current |
| STOCK_PATTERN_ANALYSIS.md | KEEP | Critical and current |

---

## Priority Execution Order

### Week 1: Cleanup & Consolidation
1. Delete: DOCUMENTATION_SUMMARY.md, SYSTEM_DESIGN.md
2. Archive: COMPLETE_DOCUMENTATION_SUMMARY.md, UI_SCREENSHOTS_README.md, SCM_UI_REFACTOR_GUIDE.md, STARTUP_COMMANDS.md
3. Merge: PROJECT_CONTEXT.md → ARCHITECTURE.md
4. Create: README.md (master index)

### Week 2: Missing Documentation (Critical)
1. Create: ORDER_WORKFLOW.md
2. Create: VENDOR_OPERATIONS.md
3. Create: BRANCH_OPERATIONS.md
4. Create: BILLING_INVOICE.md

### Week 3: Missing Documentation (High Priority)
1. Create: DASHBOARD_GUIDE.md
2. Create: GST_TAX_SYSTEM.md
3. Create: VENDOR_PDF_PARSING.md

### Week 4: Missing Documentation (Medium Priority)
1. Create: VALIDATION_RULES.md
2. Create: API_ENDPOINTS.md
3. Create: /docs/SETUP.md (from STARTUP_COMMANDS)
4. Create: Folder structure and organize docs

---

## Benefits of This Consolidation

✅ **Cleaner Repository** - Remove outdated and redundant docs
✅ **Better Organization** - Logical grouping of related documentation
✅ **Comprehensive Coverage** - All major features documented
✅ **Easier Navigation** - Master README.md as entry point
✅ **Clear Priority** - Focus on critical workflows first
✅ **Future Maintainability** - Organized structure for adding new docs

---

## What Will Be Ready After Consolidation

1. **Core Documentation** (5 critical docs)
   - Orders, Vendor Ops, Branch Ops, Billing, Dashboard

2. **System Design Docs** (7 detailed docs)
   - Stock, Items, Margins, Catalog, Patterns, GST, PDF Parsing

3. **Master Index** (1 entry point)
   - README.md linking all documentation

4. **Setup & Tools** (Archived but accessible)
   - Setup instructions, screenshot tools, past refactors

**Total: 13 active documentation files, well-organized**

---

## Next Steps

Ready to proceed with:
1. Phase 1: Cleanup & delete/archive non-relevant docs?
2. Phase 2: Create the 4 critical workflow documents?
3. Phase 3: Organize final structure with master README?

Let me know which phase to start with!
