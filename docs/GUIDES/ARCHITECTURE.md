# AAS - Complete Architecture Analysis

## Executive Summary

AAS is a **three-tier, full-stack web application** for supply chain and financial management:
- **Frontend:** Angular 17 (Modern SPA)
- **Middleware:** Spring Boot 3.4 (REST APIs)
- **Backend:** ERPNext (ERP System)

The system handles orders, invoicing, vendor management, inventory, and financial operations for multi-branch businesses.

---

## 1. ARCHITECTURE LAYERS

### Layer 1: Client Layer (Angular 17 Frontend)

**Location:** `/Users/roshninaik/Projects/AAS/ui/`

**Technology Stack:**
- Angular 17.3.0
- Angular Material (UI Components)
- RxJS (Reactive Programming)
- TypeScript
- Lazy-loaded modules (reduces initial bundle size)

**Modules:**
1. **Core Modules** (Always loaded)
   - Dashboard Module
   - Shared Services & Components
   - Authentication & Guards

2. **Lazy-loaded Feature Modules**
   - Orders Module
   - Vendors Module
   - Branches Module
   - Categories Module
   - Items Module
   - Stock Module
   - Bills Module
   - Vendor-Ops Module
   - Branch-Ops Module
   - Company-Settings Module
   - User-Settings Module

**Key Features:**
- Feature-based folder structure
- Lazy loading with code splitting
- Centralized state in services (no Redux/NgRx)
- Shared service layer for HTTP communication
- Auth token persistence in localStorage
- Material Design UI

---

### Layer 2: Middleware (Spring Boot 3.4)

**Location:** `/Users/roshninaik/Projects/AAS/mw/`

**Technology Stack:**
- Spring Boot 3.4.1
- Spring Web (REST Controllers)
- Spring Security with JWT
- Spring Data (if using relational DB)
- Spring Cloud Feign (HTTP clients)
- Maven (Build tool)

**Controllers (REST API Endpoints):**

| Controller | Purpose | Routes |
|-----------|---------|--------|
| AuthController | Authentication | POST /api/auth/login |
| OrdersController | Order CRUD | GET/POST /api/orders |
| VendorOpsController | Vendor dashboard | GET /api/vendor-ops/* |
| BranchOpsController | Branch dashboard | GET /api/branch-ops/* |
| InvoiceController | Billing | GET/POST /api/invoices |
| PaymentsController | Payment tracking | POST /api/payments |
| VendorPdfController | PDF parsing | POST /api/orders/{id}/vendor-pdf |
| MasterDataController | Master data | GET /api/vendors, /api/shops, /api/items |
| ReportsController | Reporting | GET /api/reports/* |

**Service Layer:**

```
OrderService
  ├── Order creation & management
  ├── Vendor assignment
  ├── Status updates
  └── Sell order creation

VendorOpsService
  ├── Vendor aggregations
  ├── KPI calculations
  ├── Ledger generation
  └── Performance analytics

InvoiceService
  ├── Invoice creation
  ├── GST handling
  ├── Tax template management
  └── PDF generation

VendorPdfService
  ├── PDF extraction
  ├── OCR processing
  ├── AI-powered parsing
  └── Item matching

PaymentService
  ├── Payment recording
  ├── Allocation logic
  └── Ledger updates

UserService
  ├── User authentication
  ├── Role management
  ├── Feature flags
  └── Access control
```

**External Integrations:**

1. **ERPNext Client (Feign)**
   - HTTP client for ERPNext REST API
   - Handles all data persistence
   - Document management
   - GL posting
   - Report generation

2. **AI Service (Gemma/LLM)**
   - Invoice template generation
   - PDF parsing assistance
   - Item recognition
   - Data extraction

3. **Camelot/OCR Service**
   - Table extraction from PDFs
   - Text recognition
   - Document processing

**Security:**

```
Authentication Flow:
  Client Login
    ↓
  POST /api/auth/login (username, password)
    ↓
  Backend validates against ERPNext
    ↓
  JWT Token generated
    ↓
  Token returned to client
    ↓
  Client stores in localStorage
    ↓
  All subsequent requests include: Authorization: Bearer <token>

Authorization:
  JwtAuthenticationFilter validates token
    ↓
  UserAccessService fetches user profile
    ↓
  Feature flags checked against route requirements
    ↓
  If unauthorized → 403 Forbidden
```

---

### Layer 3: Backend (ERPNext)

**Location:** ERPNext cloud/on-premise instance

**Technology:**
- Frappe Framework (Python)
- MySQL/PostgreSQL (Database)
- Document-based data model

**Document Types (Doctypes):**

| Doctype | Purpose | Key Fields |
|---------|---------|-----------|
| Sales Order | Customer orders | customer, items, qty, rates |
| Purchase Order | Vendor orders | supplier, items, qty |
| Purchase Invoice | Vendor bills | supplier, items, qty, amount |
| Sales Invoice | Customer invoices | customer, items, qty, amount, tax |
| Payment Entry | Payment records | party, amount, reference |
| Supplier | Vendor master | name, credit_days, contact |
| Customer | Branch/Shop | name, credit_days, outstanding |
| Item | Product catalog | code, name, category, rate |
| Account | GL accounts | name, type, parent |
| Company | Organization | name, currency, default_accounts |

**Custom Fields (aas_*):**

```
Sales Order:
  - aas_status (DRAFT, VENDOR_ASSIGNED, etc)
  - aas_vendor (Assigned vendor)
  - aas_po (PO number)

Purchase Invoice:
  - aas_vendor_pdf (PDF file link)
  - aas_bill_date (Parsed bill date)
  - aas_rounding_adjustment (Rounding)

Item:
  - aas_vendor (Default vendor)
  - aas_gst_rate (GST percentage)

Customer/Supplier:
  - aas_credit_days (Credit period)
```

---

## 2. DATA FLOW ARCHITECTURE

### Complete Order-to-Invoice Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ STEP 1: Order Creation (Angular UI)                             │
└────────┬────────────────────────────────────────────────────────┘
         │
         │ POST /api/orders (with items list or image)
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 2: Middleware Processing                                   │
│ - OrderService.createOrder()                                    │
│ - If image: OCR parsing via Camelot                             │
│ - Extract items, quantities, rates                              │
└────────┬────────────────────────────────────────────────────────┘
         │
         │ ERPNext Client (HTTP REST)
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 3: ERPNext Storage                                         │
│ - Create Sales Order document                                   │
│ - Set status = Draft                                            │
│ - Store items, customer, company                                │
└────────┬────────────────────────────────────────────────────────┘
         │
         │ Return Order ID to UI
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 4: Vendor Assignment (Angular UI)                          │
│ - User selects vendor from dropdown                             │
└────────┬────────────────────────────────────────────────────────┘
         │
         │ POST /api/orders/{id}/assign-vendor
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 5: Middleware Update                                       │
│ - OrderService.assignVendor()                                   │
│ - Update aas_vendor field in ERPNext                            │
│ - Set status = VENDOR_ASSIGNED                                  │
└────────┬────────────────────────────────────────────────────────┘
         │
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 6: PDF Upload & Parsing (Angular UI)                       │
│ - User uploads vendor bill PDF                                  │
└────────┬────────────────────────────────────────────────────────┘
         │
         │ POST /api/orders/{id}/vendor-pdf (multipart)
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 7: PDF Processing in Middleware                            │
│ - VendorPdfService.parseVendorPdf()                             │
│ - Camelot extracts tables                                       │
│ - OCR reads text                                                │
│ - AI (LLM) matches items to order                               │
│ - Returns: { items[], accuracy, mismatches }                    │
└────────┬────────────────────────────────────────────────────────┘
         │
         │ Return parsed data to UI
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 8: Bill Capture (Angular UI)                               │
│ - User reviews parsed data                                      │
│ - Enters/confirms: bill total, reference, date, transport      │
└────────┬────────────────────────────────────────────────────────┘
         │
         │ POST /api/orders/{id}/vendor-bill
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 9: Middleware Validation                                   │
│ - OrderService.captureVendorBill()                              │
│ - Validate amounts match                                        │
│ - Set status = VENDOR_BILL_CAPTURED                             │
└────────┬────────────────────────────────────────────────────────┘
         │
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 10: Sell Preview (Angular UI)                              │
│ - GET /api/orders/{id}/sell-preview                             │
│ - User reviews margin %                                         │
│ - Approves sell pricing                                         │
└────────┬────────────────────────────────────────────────────────┘
         │
         │ POST /api/orders/{id}/sell-order
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 11: Sell Order Creation in Middleware                      │
│ - OrderService.createSellOrder()                                │
│ - Create Purchase Invoice in ERPNext (cost side)                │
│ - Create Sales Order in ERPNext (sell side)                     │
│ - Set status = SELL_ORDER_CREATED                               │
└────────┬────────────────────────────────────────────────────────┘
         │
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 12: Invoice Creation (Angular UI)                          │
│ - Bills > Create Invoice                                        │
│ - Select order (auto-fills data)                                │
│ - Apply GST, submit                                             │
└────────┬────────────────────────────────────────────────────────┘
         │
         │ POST /api/invoices
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 13: Invoice Processing in Middleware                       │
│ - InvoiceService.createInvoice()                                │
│ - Handle GST: auto-create Item Tax Template                     │
│ - Create Sales Invoice in ERPNext                               │
│ - Set due_date = invoice_date + customer.credit_days            │
│ - Update order status = INVOICED                                │
└────────┬────────────────────────────────────────────────────────┘
         │
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 14: Payment Recording (Angular UI)                         │
│ - Bills > Record Payment                                        │
│ - Enter payment amount                                          │
│ - Submit                                                        │
└────────┬────────────────────────────────────────────────────────┘
         │
         │ POST /api/payments
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 15: Payment Processing in Middleware                       │
│ - PaymentService.recordPayment()                                │
│ - Allocate to invoice                                           │
│ - Create Payment Entry in ERPNext                               │
│ - Post to GL (Debit Cash, Credit Receivable)                    │
│ - Update outstanding amount                                     │
└────────┴────────────────────────────────────────────────────────┘
```

---

## 3. ARCHITECTURAL PATTERNS

### 1. **Service-Oriented Architecture (SOA)**
- Clear separation of concerns
- Each service handles specific domain
- Services communicate via REST APIs
- Stateless middleware

### 2. **MVC Pattern (Frontend)**
- Model: Data models (order.model.ts, bill.model.ts)
- View: Components (HTML templates)
- Controller: Services (order.service.ts)

### 3. **Layered Architecture**
```
Presentation Layer (Components)
         ↓
Service Layer (HTTP services)
         ↓
HTTP/REST APIs
         ↓
Business Logic Layer (Middleware)
         ↓
Data Access Layer (ERP Client)
         ↓
Database Layer (ERPNext)
```

### 4. **Repository Pattern**
- Services encapsulate data access
- ERPNext as remote repository
- Single point of data access

### 5. **Guard Pattern (Angular)**
- authGuard: Check if user is authenticated
- featureGuard: Check if user has feature access
- Guards prevent unauthorized navigation

### 6. **Lazy Loading**
- Modules load on-demand
- Reduces initial bundle size
- Faster app startup

### 7. **Observable Pattern (RxJS)**
- Services expose observables
- Components subscribe
- Reactive data flow
- Automatic cleanup on unsubscribe

### 8. **Factory Pattern**
- Service creation & injection
- Angular DI container
- Singleton pattern for services

---

## 4. SECURITY ARCHITECTURE

### Authentication Flow
1. User enters credentials
2. POST /api/auth/login
3. Middleware validates against ERPNext
4. JWT token generated (HS256 or RS256)
5. Token returned to client
6. Client stores in localStorage
7. All API requests include token in Authorization header

### Authorization Flow
1. authGuard checks if token exists
2. featureGuard fetches user profile (GET /api/me)
3. Checks if user has required feature
4. If not → redirected to homeRoute
5. If yes → component loads

### Token Management
- Stored in localStorage (aas_auth_token)
- Sent as Bearer token in Authorization header
- No token refresh logic (single session)
- Manual logout clears localStorage

### CORS & Security Headers
- Spring Security configured
- CORS policies for frontend
- HTTPS enforced in production
- SQL Injection prevention (parameterized queries)
- XSS prevention (Angular sanitization)

---

## 5. DATA PERSISTENCE ARCHITECTURE

### No Local Database
- **No local DB** (MySQL, PostgreSQL, MongoDB)
- **All data goes to ERPNext**
- Middleware is **stateless**
- Scaling: Horizontal scaling possible

### ERPNext as Source of Truth
- Single source of truth
- Audit trails maintained
- GL posting for compliance
- Version control of documents

### Data Sync Pattern
1. Frontend → Middleware (REST)
2. Middleware → ERPNext (HTTP Client)
3. ERPNext stores & validates
4. Middleware caches response (if needed)
5. Return to Frontend

---

## 6. MODULE DEPENDENCIES

```
Dashboard Module
  ├── Depends: OrderService, InvoiceService, StockService
  └── Uses: Dashboard API endpoints

Orders Module
  ├── Depends: OrderService, VendorService, ItemService
  ├── Uses: /api/orders/* endpoints
  └── Contains: OrderCreateComponent, OrderPageComponent

Vendor-Ops Module
  ├── Depends: VendorOpsService
  └── Uses: /api/vendor-ops/* endpoints

Branch-Ops Module
  ├── Depends: BranchOpsService
  └── Uses: /api/branch-ops/* endpoints

Bills Module
  ├── Depends: BillsService, OrderService
  ├── Uses: /api/invoices, /api/payments endpoints
  └── Contains: InvoiceCreateComponent, PaymentFormComponent

Stock Module
  ├── Depends: StockService
  └── Uses: /api/stock endpoints

Master Data Modules (Vendors, Branches, Items, Categories)
  ├── Depend: VendorService, ItemService, CategoryService
  └── Use: /api/vendors, /api/items, /api/categories endpoints
```

---

## 7. ERROR HANDLING ARCHITECTURE

### Frontend Error Handling
- Try-catch in components
- Observable error operators
- Error toast messages
- Graceful degradation

### Middleware Error Handling
- Controller exception handlers
- Service-level validation
- Meaningful error messages
- HTTP status codes (400, 404, 500)

### Backend (ERPNext) Error Handling
- Document validation
- Permission checks
- GL posting failures handled
- Cascading operations

### Common Error Scenarios
```
InvalidCredentials → 401 Unauthorized → Redirect to login
InsufficientPermissions → 403 Forbidden → Redirect to homeRoute
DocumentNotFound → 404 Not Found → Show error message
ValidationFailed → 400 Bad Request → Display validation errors
ServerError → 500 Internal Server Error → Log & show generic message
```

---

## 8. PERFORMANCE ARCHITECTURE

### Frontend Optimization
- Lazy loading modules
- OnPush change detection (where applicable)
- TrackBy functions in *ngFor
- Pagination for large lists
- Client-side caching of dropdown data

### Middleware Optimization
- Connection pooling to ERPNext
- Response caching (Redis or in-memory)
- Batch API calls where possible
- Async processing for heavy operations (PDF parsing)

### Database Optimization
- ERPNext indexes
- Query optimization
- Full-text search where needed
- Archiving old records

---

## 9. KEY ARCHITECTURAL DECISIONS

### Why Three Tiers?
- **Separation of concerns**: Each layer has specific responsibility
- **Scalability**: Middleware can scale independently
- **Security**: Credentials never reach frontend
- **Flexibility**: Can swap ERPNext without changing frontend
- **Maintainability**: Clear boundaries between layers

### Why ERPNext?
- **Financial compliance**: GL posting, audit trails
- **Standard documents**: Sales Order, Invoice, Payment Entry
- **Multi-currency & tax**: Built-in support
- **Reporting**: Pre-built reports & dashboards
- **Extensibility**: Custom doctype fields (aas_*)

### Why Spring Boot Middleware?
- **Lightweight**: Only REST APIs, no heavy framework overhead
- **Fast**: High throughput, low latency
- **Flexible**: Can integrate with multiple services (OCR, AI, Camelot)
- **Secure**: Spring Security with JWT
- **Testable**: Clear service layer for unit testing

### Why Angular?
- **Modern SPA**: Fast, responsive UI
- **Material Design**: Professional look & feel
- **Type-safe**: TypeScript catches errors early
- **Reactive**: RxJS for complex user interactions
- **Lazy loading**: Only load what's needed

---

## 10. DEPLOYMENT ARCHITECTURE

### Frontend Deployment
```
npm run build → dist/ folder
     ↓
Upload to: nginx/Apache/CDN
     ↓
Served as static files
```

### Middleware Deployment
```
mvn clean package → .jar file
     ↓
Docker container or direct Java
     ↓
Exposed on port 8080 (or configured)
     ↓
Reverse proxy (nginx) forwards /api/* to middleware
```

### Backend Deployment
```
ERPNext cloud or on-premise
     ↓
Accessible via HTTPS
     ↓
Middleware connects via HTTP client
```

---

## 11. TECHNOLOGY STACK SUMMARY

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Frontend** | Angular 17, Material, RxJS | User Interface |
| **Middleware** | Spring Boot 3.4, Maven | REST APIs, Business Logic |
| **Backend** | ERPNext (Frappe/Python) | Data, GL, Documents |
| **Build Tools** | ng CLI, Maven | Compilation |
| **Package Manager** | npm, Maven | Dependencies |
| **Testing** | Jasmine, JUnit | Quality Assurance |
| **Authentication** | JWT | Security |
| **External** | Camelot, LLM, OCR | PDF Processing |

---

## 12. SCALABILITY & EXTENSIBILITY

### Horizontal Scalability
- **Frontend**: Deploy to multiple servers behind load balancer
- **Middleware**: Deploy multiple instances, use load balancer
- **Backend**: ERPNext can handle multi-tenancy

### Adding New Features
1. **Frontend**: Create new module → Add route → Create components
2. **Middleware**: Create controller → Service → Wire in Spring
3. **Backend**: Create doctype or add custom field in ERPNext

### Performance Under Load
- Connection pooling for DB
- Response caching
- Async operations for heavy tasks
- Load balancing across middleware instances

---

## Conclusion

AAS is a **well-architected, three-tier enterprise application** with:
- Clear separation of concerns
- Scalable and maintainable design
- Secure authentication & authorization
- Flexible integration with ERPNext
- Modern frontend with Angular
- Robust middleware with Spring Boot

The architecture supports growth, maintenance, and extensibility for multi-branch supply chain management operations.
