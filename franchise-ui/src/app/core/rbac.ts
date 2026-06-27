// Role-based access control catalog for the Franchise Console (mock).
// Mirrors the AAS `{domain}.{action}` feature-key convention so a real
// backend can later resolve these the same way.

export type Role = 'SUPER_ADMIN' | 'FRANCHISE_OWNER' | 'MANAGER' | 'STAFF';

export interface RoleDef {
  value: Role;
  label: string;
  description: string;
}

export const ROLES: RoleDef[] = [
  { value: 'SUPER_ADMIN', label: 'Super Admin', description: 'Brand owner — every franchise, all modules and configuration.' },
  { value: 'FRANCHISE_OWNER', label: 'Franchise Owner', description: 'Own franchise — all operational and finance modules.' },
  { value: 'MANAGER', label: 'Manager', description: 'Day-to-day operations — inventory, vendors, sales, expenses.' },
  { value: 'STAFF', label: 'Staff', description: 'Data entry only — record purchases and daily consumption.' }
];

export type FeatureKey =
  | 'dashboard.view'
  | 'inventory.view'
  | 'inventory.manage'
  | 'inventory.purchase'
  | 'inventory.consume'
  | 'vendors.view'
  | 'vendors.manage'
  | 'sales.view'
  | 'expenses.view'
  | 'salary.view'
  | 'royalty.view'
  | 'pnl.view'
  | 'admin.modules'
  | 'admin.access';

export interface FeatureDef {
  key: FeatureKey;
  label: string;
  group: string;
}

export const FEATURES: FeatureDef[] = [
  { key: 'dashboard.view', label: 'Dashboard', group: 'Home' },
  { key: 'inventory.view', label: 'View stock & reports', group: 'Inventory' },
  { key: 'inventory.manage', label: 'Manage products', group: 'Inventory' },
  { key: 'inventory.purchase', label: 'Record purchases', group: 'Inventory' },
  { key: 'inventory.consume', label: 'Record consumption', group: 'Inventory' },
  { key: 'vendors.view', label: 'View vendors', group: 'Vendors' },
  { key: 'vendors.manage', label: 'Manage vendors', group: 'Vendors' },
  { key: 'sales.view', label: 'Daily Sales', group: 'Operations' },
  { key: 'expenses.view', label: 'Expenses', group: 'Operations' },
  { key: 'salary.view', label: 'Salary', group: 'Operations' },
  { key: 'royalty.view', label: 'Royalty', group: 'Operations' },
  { key: 'pnl.view', label: 'Profit & Loss', group: 'Operations' },
  { key: 'admin.modules', label: 'Module Settings', group: 'Administration' },
  { key: 'admin.access', label: 'Access Control', group: 'Administration' }
];

const ALL_FEATURES = FEATURES.map(f => f.key);

const OWNER_FEATURES: FeatureKey[] = [
  'dashboard.view',
  'inventory.view', 'inventory.manage', 'inventory.purchase', 'inventory.consume',
  'vendors.view', 'vendors.manage',
  'sales.view', 'expenses.view', 'salary.view', 'royalty.view', 'pnl.view'
];

const MANAGER_FEATURES: FeatureKey[] = [
  'dashboard.view',
  'inventory.view', 'inventory.manage', 'inventory.purchase', 'inventory.consume',
  'vendors.view', 'vendors.manage',
  'sales.view', 'expenses.view'
];

const STAFF_FEATURES: FeatureKey[] = [
  'inventory.purchase', 'inventory.consume'
];

const ROLE_DEFAULTS: Record<Role, FeatureKey[]> = {
  SUPER_ADMIN: ALL_FEATURES,
  FRANCHISE_OWNER: OWNER_FEATURES,
  MANAGER: MANAGER_FEATURES,
  STAFF: STAFF_FEATURES
};

export function featuresForRole(role: Role): FeatureKey[] {
  return [...(ROLE_DEFAULTS[role] ?? [])];
}

export function roleLabel(role: Role): string {
  return ROLES.find(r => r.value === role)?.label ?? role;
}

export function homeRouteForFeatures(features: FeatureKey[]): string {
  // Send users to the first module they can actually see.
  const preferred: Array<[FeatureKey, string]> = [
    ['dashboard.view', '/dashboard'],
    ['inventory.view', '/inventory/reports'],
    ['inventory.purchase', '/inventory/purchases'],
    ['inventory.consume', '/inventory/consumption'],
    ['vendors.view', '/vendors'],
    ['admin.modules', '/admin/modules']
  ];
  const match = preferred.find(([feature]) => features.includes(feature));
  return match ? match[1] : '/no-access';
}

// ---- Toggleable modules (admin enable/disable) -----------------------------
// Each module owns a set of feature keys; disabling a module removes those
// features from every user's effective access. Admin features are not
// toggleable (the Super Admin always keeps configuration access).

export interface ModuleDef {
  key: string;
  label: string;
  description: string;
  features: FeatureKey[];
}

export const TOGGLEABLE_MODULES: ModuleDef[] = [
  { key: 'dashboard', label: 'Dashboard', description: "Today's sales, stock value, outstanding & profit snapshot.", features: ['dashboard.view'] },
  { key: 'inventory', label: 'Inventory Management', description: 'Products, opening stock, purchases, consumption, stock reports.', features: ['inventory.view', 'inventory.manage', 'inventory.purchase', 'inventory.consume'] },
  { key: 'vendors', label: 'Vendor Management', description: 'Vendors, purchase bills, payments and outstanding.', features: ['vendors.view', 'vendors.manage'] },
  { key: 'sales', label: 'Daily Sales', description: 'Manual POS sales entry (Petpooja) with cash/UPI/card split.', features: ['sales.view'] },
  { key: 'expenses', label: 'Expense Management', description: 'Rent, electricity, GST, marketing and other expenses.', features: ['expenses.view'] },
  { key: 'salary', label: 'Salary Tracking', description: 'Employees and monthly salary entries.', features: ['salary.view'] },
  { key: 'royalty', label: 'Royalty Management', description: 'Percentage-based royalty calculation and collection.', features: ['royalty.view'] },
  { key: 'pnl', label: 'Profit & Loss', description: 'Operational P&L: revenue minus consumption, salary, expenses, royalty.', features: ['pnl.view'] }
];

const ADMIN_FEATURES: FeatureKey[] = ['admin.modules', 'admin.access'];

export function isAdminFeature(feature: FeatureKey): boolean {
  return ADMIN_FEATURES.includes(feature);
}
