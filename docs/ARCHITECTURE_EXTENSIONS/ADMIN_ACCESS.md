# Admin Access & User Management - Complete Guide

## Overview

Admin Access is a **permission and feature management system** that allows administrators to control which features each user can access. It implements role-based access control (RBAC) through a catalog of features and per-user allow/deny lists.

---

## Admin Access Dashboard

### Access Admin Console

```
User navigates to: Settings > Admin Access (admin-only)
    ↓
Requires: Admin role authorization
    ↓
GET /api/admin/access
    ↓
Backend: AdminAccessController.accessOverview()
  ├─ Load feature catalog (all available features)
  ├─ Load user access profiles (all users + their access)
  └─ Return structured data
    ↓
Display:
├─ Feature Catalog (left panel)
│  ├─ List of all feature names
│  ├─ Feature descriptions
│  └─ Toggles for each feature
│
└─ User Access Matrix (main panel)
   ├─ User Name
   ├─ Role
   ├─ Features Allowed (list)
   ├─ Features Denied (list)
   └─ Edit button per user
```

### Admin Overview Data

```
AccessOverviewResponse {
  features: [
    {
      key: "FEATURE_ORDERS",
      name: "Orders Module",
      description: "Create, manage, and track orders"
    },
    {
      key: "FEATURE_VENDOR_OPS",
      name: "Vendor Operations",
      description: "Vendor-centric dashboard and KPIs"
    },
    {
      key: "FEATURE_BRANCH_OPS",
      name: "Branch Operations",
      description: "Branch receivables and payment tracking"
    },
    // ... more features
  ],
  
  users: [
    {
      userId: "user123",
      name: "John Admin",
      role: "ADMIN",
      allowFeatures: [
        "FEATURE_ORDERS",
        "FEATURE_VENDOR_OPS",
        "FEATURE_BRANCH_OPS"
        // ... all features (admins can see everything)
      ],
      denyFeatures: []
    },
    {
      userId: "user456",
      name: "Sarah Vendor Ops",
      role: "USER",
      allowFeatures: [
        "FEATURE_VENDOR_OPS",
        "FEATURE_REPORTS"
      ],
      denyFeatures: [
        "FEATURE_INVOICES",
        "FEATURE_PAYMENTS"
      ]
    },
    // ... more users
  ]
}
```

---

## Feature Catalog

### Available Features

```
Core Feature Flags:

1. FEATURE_ORDERS
   ├─ Allows: Create orders, upload vendor PDFs, capture bills
   ├─ Controls: Orders module menu item
   └─ Required for: Order workflow users

2. FEATURE_VENDOR_OPS
   ├─ Allows: View vendor dashboard, ledger, exceptions
   ├─ Controls: Vendor Operations menu item
   └─ Required for: Operations/procurement teams

3. FEATURE_BRANCH_OPS
   ├─ Allows: View branch dashboard, receivables, ledger
   ├─ Controls: Branch Operations menu item
   └─ Required for: Finance/collections teams

4. FEATURE_INVOICES
   ├─ Allows: Create invoices (from order or manual)
   ├─ Controls: Bills > Create Invoice
   └─ Required for: Finance teams

5. FEATURE_PAYMENTS
   ├─ Allows: Record payment entries against invoices
   ├─ Controls: Bills > Record Payment
   └─ Required for: Finance/accounting teams

6. FEATURE_REPORTS
   ├─ Allows: View and export all reports
   ├─ Controls: Reports menu and all report types
   └─ Required for: Management/analytics

7. FEATURE_MASTER_DATA
   ├─ Allows: Manage items, vendors, branches, categories
   ├─ Controls: Master Data menu and all CRUD operations
   └─ Required for: Data administrators

8. FEATURE_STOCK
   ├─ Allows: View stock levels and manage thresholds
   ├─ Controls: Stock module and inventory tracking
   └─ Required for: Inventory managers

9. FEATURE_DASHBOARD
   ├─ Allows: View KPI dashboard and snapshots
   ├─ Controls: Dashboard module
   └─ Required for: All users (usually enabled for everyone)

10. FEATURE_COMPANY_SETTINGS
    ├─ Allows: View and edit company configuration
    ├─ Controls: Company Settings menu
    └─ Required for: Admins only

11. FEATURE_ADMIN_ACCESS
    ├─ Allows: Access admin console, modify user permissions
    ├─ Controls: Admin Access menu
    └─ Required for: Super admins only
```

---

## User Access Management

### View User Access Profile

```
User clicks: Admin Access > Select User
    ↓
User details shown:
├─ User ID
├─ Name
├─ Email
├─ Role
├─ Features Allowed (list)
├─ Features Denied (list)
└─ Edit button
```

### Update User Access

```
Admin clicks: Edit User Access
    ↓
Modal/Page shows:
├─ User Name (read-only)
├─ Feature Catalog (checkboxes)
│  ├─ Checked = Allowed
│  ├─ Unchecked = Denied
│  └─ Indeterminate = Default (inherited from role)
│
└─ Buttons: Save / Cancel

Admin selects/deselects features:
├─ Check features user should access
└─ Uncheck features user should NOT access
    ↓
Clicks: "Save Changes"
    ↓
PUT /api/admin/access/users/{userId}
{
  "allowFeatures": ["FEATURE_ORDERS", "FEATURE_VENDOR_OPS"],
  "denyFeatures": ["FEATURE_INVOICES", "FEATURE_PAYMENTS"]
}
    ↓
Backend: AdminAccessController.updateUserAccess()
  ├─ Validate user exists
  ├─ Update allow list
  ├─ Update deny list
  ├─ Persist to database (User entity)
  └─ Return updated access profile
    ↓
UI: Confirmation message
└─ Changes applied immediately
    ↓
User's next login uses new permissions
```

### Access Update Request DTO

```
UserAccessUpdateRequest {
  allowFeatures: string[],   // Features to allow
  denyFeatures: string[]     // Features to explicitly deny
}

Example:
{
  "allowFeatures": [
    "FEATURE_ORDERS",
    "FEATURE_VENDOR_OPS",
    "FEATURE_DASHBOARD",
    "FEATURE_REPORTS"
  ],
  "denyFeatures": [
    "FEATURE_INVOICES",
    "FEATURE_PAYMENTS",
    "FEATURE_ADMIN_ACCESS"
  ]
}
```

---

## Feature Guard Implementation

### Frontend Feature Guards

```
FeatureGuard (Angular):

Purpose: Control route access based on user's feature permissions

Implementation:
├─ UserAccessService stores allowed features
├─ FeatureGuard.canActivate() checks route requirements
├─ If feature allowed: Allow navigation
└─ If feature denied: Redirect to unauthorized page

Example:
routes: [
  {
    path: 'orders',
    component: OrdersComponent,
    canActivate: [FeatureGuard],
    data: { feature: 'FEATURE_ORDERS' }
  },
  {
    path: 'vendor-ops',
    component: VendorOpsComponent,
    canActivate: [FeatureGuard],
    data: { feature: 'FEATURE_VENDOR_OPS' }
  }
]

Guard Logic:
const feature = route.data['feature'];
const isAllowed = userAccessService.hasFeature(feature);
return isAllowed ? true : this.router.navigate(['/unauthorized']);
```

### Sidebar Dynamic Menu

```
AppShellComponent builds sidebar menu dynamically:

for each feature in allFeatures:
  if user.allowFeatures.includes(feature):
    Show menu item
  else:
    Hide menu item (not displayed)

Example Menu:
├─ Dashboard (always shown)
├─ Procure
│  ├─ Orders [if FEATURE_ORDERS allowed]
│  └─ Vendor Ops [if FEATURE_VENDOR_OPS allowed]
├─ Finance
│  ├─ Bills [if FEATURE_INVOICES allowed]
│  ├─ Payments [if FEATURE_PAYMENTS allowed]
│  └─ Branch Ops [if FEATURE_BRANCH_OPS allowed]
├─ Inventory
│  ├─ Stock [if FEATURE_STOCK allowed]
│  └─ Master Data [if FEATURE_MASTER_DATA allowed]
├─ Reports [if FEATURE_REPORTS allowed]
├─ Settings
│  ├─ Company Settings [if FEATURE_COMPANY_SETTINGS allowed]
│  └─ Admin Access [if FEATURE_ADMIN_ACCESS allowed]
└─ User Settings (always shown)
```

---

## Access Control Flow

```
┌────────────────────────────────────────────────────────────┐
│ USER LOGIN                                                 │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ POST /api/auth/login                                       │
│ ├─ Validate credentials                                   │
│ ├─ Generate JWT token                                     │
│ └─ Return user profile
│                                                            │
│ Frontend stores token in localStorage                      │
└────────────────────────────────────────────────────────────┘
                        ↓
┌────────────────────────────────────────────────────────────┐
│ LOAD USER PROFILE                                          │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ GET /api/me                                                │
│ ├─ Fetch user details from database                       │
│ ├─ Fetch user.allowFeatures (from User entity)            │
│ ├─ Fetch user.denyFeatures (from User entity)             │
│ └─ Return user context with features
│                                                            │
│ Frontend: UserAccessService stores feature list           │
└────────────────────────────────────────────────────────────┘
                        ↓
┌────────────────────────────────────────────────────────────┐
│ ROUTE NAVIGATION                                           │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ User clicks menu item or navigates to route                │
│   ↓                                                        │
│ Angular FeatureGuard.canActivate() triggered              │
│   ├─ Get feature requirement from route data             │
│   ├─ Call userAccessService.hasFeature(feature)          │
│   │  └─ Check: feature in allowFeatures?                 │
│   │                                                        │
│   ├─ If allowed: Continue navigation                      │
│   └─ If denied: Redirect to /unauthorized                │
└────────────────────────────────────────────────────────────┘
                        ↓
┌────────────────────────────────────────────────────────────┐
│ COMPONENT RENDERING                                        │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ If route allows: Component renders                        │
│                                                            │
│ Component can also check feature programmatically:        │
│   if (userAccessService.hasFeature('FEATURE_INVOICES')) {│
│     show invoice creation button                          │
│   } else {                                                 │
│     hide invoice creation button                          │
│   }                                                        │
└────────────────────────────────────────────────────────────┘
                        ↓
┌────────────────────────────────────────────────────────────┐
│ API CALL GUARD (Backend)                                   │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ User makes API call (POST /api/invoices)                  │
│   ↓                                                        │
│ Spring Security checks:                                    │
│   ├─ User is authenticated (valid JWT)                    │
│   ├─ User role can invoke endpoint                        │
│   └─ (Optional: Check against feature flags)              │
│                                                            │
│ If authorized: Execute request                            │
│ If unauthorized: Return 403 Forbidden                     │
└────────────────────────────────────────────────────────────┘
```

---

## Admin Workflows

### Workflow 1: Grant New Feature Access

```
1. Open Settings > Admin Access
2. Find user to grant access
3. Click "Edit User Access"
4. Check features to allow
5. Uncheck features to deny
6. Click "Save Changes"
7. Confirmation message displays
8. User can access feature on next login
```

### Workflow 2: Revoke Feature Access

```
1. Open Settings > Admin Access
2. Find user to revoke access from
3. Click "Edit User Access"
4. Uncheck features to disable
5. Click "Save Changes"
6. Feature menu items disappear for that user
7. If user is still logged in, they'll see feature removed from menu on page refresh
```

### Workflow 3: Set Up Role-Based Access

```
1. Open Settings > Admin Access
2. Create user with specific role (e.g., "Vendor Ops Manager")
3. Grant features:
   - FEATURE_VENDOR_OPS (required)
   - FEATURE_REPORTS (for analysis)
   - FEATURE_DASHBOARD (for overview)
4. Deny features:
   - FEATURE_INVOICES (not needed)
   - FEATURE_PAYMENTS (finance only)
   - FEATURE_ADMIN_ACCESS (admin only)
5. Save
6. User can now only access vendor operations
```

### Workflow 4: Audit User Permissions

```
1. Open Settings > Admin Access
2. Review all users and their feature lists
3. Identify over-privileged users
4. Identify under-privileged users
5. Adjust access as needed
6. Document changes for audit trail
```

---

## Data Structures

### User Entity (Extended)

```
User {
  userId: string;
  username: string;
  email: string;
  passwordHash: string;
  role: 'ADMIN' | 'USER' | 'VENDOR' | 'FINANCE';
  
  // Access control
  allowFeatures: string[];    // Explicitly allowed features
  denyFeatures: string[];     // Explicitly denied features
  
  // Metadata
  createdAt: Date;
  updatedAt: Date;
  lastLogin: Date;
  active: boolean;
}
```

### UserAccessUpdateRequest

```
{
  allowFeatures: [
    "FEATURE_ORDERS",
    "FEATURE_VENDOR_OPS"
  ],
  denyFeatures: [
    "FEATURE_INVOICES",
    "FEATURE_PAYMENTS"
  ]
}
```

### UserAccessProfile

```
{
  userId: "user456",
  name: "Sarah Vendor Ops",
  email: "sarah@company.com",
  role: "USER",
  allowFeatures: [
    "FEATURE_VENDOR_OPS",
    "FEATURE_REPORTS",
    "FEATURE_DASHBOARD"
  ],
  denyFeatures: [
    "FEATURE_INVOICES",
    "FEATURE_PAYMENTS",
    "FEATURE_ADMIN_ACCESS"
  ]
}
```

---

## Error Scenarios

| Scenario | Error | Resolution |
|----------|-------|-----------|
| User not found | User Not Found | Verify user ID exists |
| Admin not authenticated | Unauthorized | Must be logged in as admin |
| Invalid feature name | Invalid Feature | Use correct feature key from catalog |
| Feature already allowed | Already Allowed | Remove from allow list first |
| No access changes made | No Changes | Select features to allow/deny |

---

## Security Considerations

### Role-Based Access Control (RBAC)

```
Roles:
├─ ADMIN: Full access to all features + admin console
├─ FINANCE: Bills, Payments, Reports, Branch Ops
├─ PROCUREMENT: Orders, Vendor Ops, Reports
├─ INVENTORY: Stock, Master Data
└─ VIEWER: Dashboard, Reports (read-only)

Each role has default feature set, customizable per user
```

### Principle of Least Privilege

```
Best Practices:
├─ Grant only necessary features
├─ Deny features by default
├─ Regularly audit access
├─ Remove unused features immediately
└─ Document all access changes
```

### Audit Trail

```
Track:
├─ Who made access changes
├─ When changes were made
├─ What features were added/removed
├─ For which users
└─ Maintain in audit log for compliance
```

---

## Summary

Admin Access is a **flexible permission management system** that enables administrators to control feature visibility and API access on a per-user basis. It combines feature catalog definitions, per-user allow/deny lists, frontend guards, and sidebar menu filtering to create a comprehensive role-based access control system. This allows organizations to grant precise permissions tailored to individual user roles and responsibilities.

