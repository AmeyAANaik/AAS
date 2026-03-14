#!/usr/bin/env node

import { OCR_FIXTURE, generateOcrArtifacts } from './generate-ocr-artifacts.mjs';

const DEFAULT_MW_BASE_URL = 'http://localhost:8083';
const DEFAULT_ERP_BASE_URL = 'http://localhost:8080';
const MW_BASE_URL = (process.env.MW_BASE_URL || DEFAULT_MW_BASE_URL).replace(/\/$/, '');
const ERP_BASE_URL = (process.env.ERP_BASE_URL || process.env.ERPNEXT_BASE_URL || DEFAULT_ERP_BASE_URL).replace(/\/$/, '');
const USERNAME = process.env.MW_USERNAME || process.env.ERP_USERNAME || 'Administrator';
const PASSWORD = process.env.MW_PASSWORD || process.env.ERP_PASSWORD || 'admin';
const DRY_RUN = process.env.DRY_RUN === '1';
const FORCE_TRANSACTIONS = process.env.SEED_TRANSACTIONS_ALWAYS === '1';
const SEED_SALES_PDF_ITEMS = process.env.SEED_SALES_PDF_ITEMS !== '0';
const TODAY = new Date('2026-03-03T00:00:00Z');
const RUN_DATE = formatDate(new Date());

const companies = [
  {
    name: 'Hotel Sahyadri Pune Pvt Ltd',
    abbr: 'HSP',
    city: 'Pune',
    state: 'Maharashtra',
    pincode: '411001',
    addressLine1: '118 Bhandarkar Road',
    country: 'India',
    currency: 'INR'
  },
  {
    name: 'Blue Lagoon Restaurant Mumbai LLP',
    abbr: 'BLR',
    city: 'Mumbai',
    state: 'Maharashtra',
    pincode: '400050',
    addressLine1: '22 Carter Road, Bandra West',
    country: 'India',
    currency: 'INR'
  },
  {
    name: 'Fortune Hills Resort Lonavala Pvt Ltd',
    abbr: 'FHR',
    city: 'Lonavala',
    state: 'Maharashtra',
    pincode: '410401',
    addressLine1: 'Survey 44, Old Mumbai Pune Highway',
    country: 'India',
    currency: 'INR'
  }
];

const suppliers = [
  {
    supplier_name: 'Vendor A',
    supplier_type: 'Company',
    supplier_group: 'All Supplier Groups',
    branch_name: 'Vendor A Pune Market Desk',
    address: 'Shop 7, Nana Peth Produce Market, Pune 411002',
    phone: '+91 98221 11007',
    gst: '27AABCV1007K1Z9',
    pan: 'AABCV1007K',
    food_license_no: 'FSSAI-11522011000781',
    aas_priority: 9,
    disabled: 0,
    invoice_template_enabled: 0,
    invoice_template_key: '',
    credit_days: 15,
    credit_limit: 150000
  },
  {
    supplier_name: 'FreshHarvest Agro Foods',
    supplier_type: 'Company',
    supplier_group: 'All Supplier Groups',
    branch_name: 'FreshHarvest Gultekdi',
    address: 'Plot 14, Market Yard Annex, Gultekdi, Pune 411037',
    phone: '+91 98220 44187',
    gst: '27AAECF4832M1ZU',
    pan: 'AAECF4832M',
    food_license_no: 'FSSAI-11522013001426',
    aas_priority: 1,
    disabled: 0,
    invoice_template_enabled: 1,
    invoice_template_key: 'table_v1',
    credit_days: 7,
    credit_limit: 350000
  },
  {
    supplier_name: 'SpiceRoute Traders',
    supplier_type: 'Company',
    supplier_group: 'All Supplier Groups',
    branch_name: 'SpiceRoute Crawford Market',
    address: 'Unit 42, Crawford Market, South Mumbai 400001',
    phone: '+91 99301 77231',
    gst: '27AAKCS6734Q1ZH',
    pan: 'AAKCS6734Q',
    food_license_no: 'FSSAI-11521022001966',
    aas_priority: 2,
    disabled: 0,
    invoice_template_enabled: 0,
    invoice_template_key: '',
    credit_days: 15,
    credit_limit: 500000
  },
  {
    supplier_name: 'PrimeDairy Distributors',
    supplier_type: 'Company',
    supplier_group: 'All Supplier Groups',
    branch_name: 'PrimeDairy Pimpri',
    address: 'Gate 3, Dapodi Dairy Hub, Pune 411012',
    phone: '+91 97671 66042',
    gst: '27AAHCP7842B1Z7',
    pan: 'AAHCP7842B',
    food_license_no: 'FSSAI-11522016002219',
    aas_priority: 3,
    disabled: 0,
    credit_days: 7,
    credit_limit: 280000
  },
  {
    supplier_name: 'OceanCatch Seafood Supplies',
    supplier_type: 'Company',
    supplier_group: 'All Supplier Groups',
    branch_name: 'OceanCatch Sassoon Dock',
    address: 'Jetty 4, Sassoon Dock, Colaba, Mumbai 400005',
    phone: '+91 98924 66118',
    gst: '27AAECO5127F1ZT',
    pan: 'AAECO5127F',
    food_license_no: 'FSSAI-11521044001482',
    aas_priority: 4,
    disabled: 0,
    credit_days: 7,
    credit_limit: 260000
  },
  {
    supplier_name: 'Deccan Poultry & Meats',
    supplier_type: 'Company',
    supplier_group: 'All Supplier Groups',
    branch_name: 'Deccan Poultry Kondhwa',
    address: 'Warehouse 8, NIBM Link Road, Kondhwa, Pune 411048',
    phone: '+91 98817 11452',
    gst: '27AAGCD9451C1ZJ',
    pan: 'AAGCD9451C',
    food_license_no: 'FSSAI-11522012001837',
    aas_priority: 5,
    disabled: 0,
    credit_days: 7,
    credit_limit: 300000
  },
  {
    supplier_name: 'CrystalClean Housekeeping Supplies',
    supplier_type: 'Company',
    supplier_group: 'All Supplier Groups',
    branch_name: 'CrystalClean Bhiwandi',
    address: 'Gala 12, Kalyan Bhiwandi Road, Bhiwandi 421302',
    phone: '+91 98193 24864',
    gst: '27AACCC3348R1ZM',
    pan: 'AACCC3348R',
    food_license_no: 'N/A',
    aas_priority: 6,
    disabled: 0,
    credit_days: 30,
    credit_limit: 450000
  },
  {
    supplier_name: 'LinenLux Textiles',
    supplier_type: 'Company',
    supplier_group: 'All Supplier Groups',
    branch_name: 'LinenLux Ichalkaranji',
    address: 'Plot 71, Textile Park, Ichalkaranji 416115',
    phone: '+91 98908 77524',
    gst: '27AACCL2738A1ZY',
    pan: 'AACCL2738A',
    food_license_no: 'N/A',
    aas_priority: 7,
    disabled: 0,
    credit_days: 30,
    credit_limit: 650000
  },
  {
    supplier_name: 'BrewMasters Beverages Pvt Ltd',
    supplier_type: 'Company',
    supplier_group: 'All Supplier Groups',
    branch_name: 'BrewMasters Taloja',
    address: 'Plot 52, MIDC Taloja, Navi Mumbai 410208',
    phone: '+91 98191 66584',
    gst: '27AACCB2851M1ZE',
    pan: 'AACCB2851M',
    food_license_no: 'FSSAI-21521012002845',
    aas_priority: 8,
    disabled: 0,
    credit_days: 15,
    credit_limit: 550000
  },
  {
    supplier_name: 'Regal Spirits Distributors',
    supplier_type: 'Company',
    supplier_group: 'All Supplier Groups',
    branch_name: 'Regal Spirits Saki Naka',
    address: 'Unit 9, Marol Pipeline Road, Andheri East, Mumbai 400072',
    phone: '+91 98205 77126',
    gst: '27AALCR4472P1ZL',
    pan: 'AALCR4472P',
    food_license_no: 'EXCISE-MH-26-04472',
    aas_priority: 10,
    disabled: 0,
    credit_days: 15,
    credit_limit: 750000
  },
  {
    supplier_name: 'Metro Packaging Solutions',
    supplier_type: 'Company',
    supplier_group: 'All Supplier Groups',
    branch_name: 'Metro Packaging Chakan',
    address: 'Shed C-18, Chakan Industrial Area, Pune 410501',
    phone: '+91 98606 11835',
    gst: '27AAGCM9318K1ZQ',
    pan: 'AAGCM9318K',
    food_license_no: 'N/A',
    aas_priority: 11,
    disabled: 0,
    credit_days: 30,
    credit_limit: 320000
  }
];

const customers = [
  {
    customer_name: 'Shop A',
    company: 'Hotel Sahyadri Pune Pvt Ltd',
    customer_type: 'Company',
    customer_group: 'All Customer Groups',
    territory: 'All Territories',
    whatsapp_group: 'AAS-Shop-A-Pune',
    gstin: '27AAECS5001Q1ZX',
    billing: { line1: '14 JM Road', city: 'Pune', state: 'Maharashtra', pincode: '411005', country: 'India' },
    shipping: { line1: 'Receiving Bay, 14 JM Road', city: 'Pune', state: 'Maharashtra', pincode: '411005', country: 'India' }
  },
  {
    customer_name: 'Sahyadri Rooftop Bar',
    company: 'Hotel Sahyadri Pune Pvt Ltd',
    customer_type: 'Company',
    customer_group: 'All Customer Groups',
    territory: 'All Territories',
    whatsapp_group: 'HSP-Rooftop-Bar-Orders',
    gstin: '27AAECS5002Q1ZW',
    billing: { line1: '118 Bhandarkar Road', city: 'Pune', state: 'Maharashtra', pincode: '411001', country: 'India' },
    shipping: { line1: 'Level 7 Service Entry, 118 Bhandarkar Road', city: 'Pune', state: 'Maharashtra', pincode: '411001', country: 'India' }
  },
  {
    customer_name: 'Sahyadri All-Day Dining',
    company: 'Hotel Sahyadri Pune Pvt Ltd',
    customer_type: 'Company',
    customer_group: 'All Customer Groups',
    territory: 'All Territories',
    whatsapp_group: 'HSP-ADD-Procurement',
    gstin: '27AAECS5003Q1ZV',
    billing: { line1: '118 Bhandarkar Road', city: 'Pune', state: 'Maharashtra', pincode: '411001', country: 'India' },
    shipping: { line1: 'Receiving Dock, Bhandarkar Road', city: 'Pune', state: 'Maharashtra', pincode: '411001', country: 'India' }
  },
  {
    customer_name: 'Sahyadri Banquet Deck',
    company: 'Hotel Sahyadri Pune Pvt Ltd',
    customer_type: 'Company',
    customer_group: 'All Customer Groups',
    territory: 'All Territories',
    whatsapp_group: 'HSP-Banquets-Events',
    gstin: '27AAECS5004Q1ZU',
    billing: { line1: '118 Bhandarkar Road', city: 'Pune', state: 'Maharashtra', pincode: '411001', country: 'India' },
    shipping: { line1: 'Banquet Loading Bay, Bhandarkar Road', city: 'Pune', state: 'Maharashtra', pincode: '411001', country: 'India' }
  },
  {
    customer_name: 'Blue Lagoon Takeaway - Bandra',
    company: 'Blue Lagoon Restaurant Mumbai LLP',
    customer_type: 'Company',
    customer_group: 'All Customer Groups',
    territory: 'All Territories',
    whatsapp_group: 'BLR-Bandra-Takeaway',
    gstin: '27AADCB3001L1ZT',
    billing: { line1: '22 Carter Road, Bandra West', city: 'Mumbai', state: 'Maharashtra', pincode: '400050', country: 'India' },
    shipping: { line1: 'Rear Service Entry, Carter Road', city: 'Mumbai', state: 'Maharashtra', pincode: '400050', country: 'India' }
  },
  {
    customer_name: 'Blue Lagoon Sea Lounge',
    company: 'Blue Lagoon Restaurant Mumbai LLP',
    customer_type: 'Company',
    customer_group: 'All Customer Groups',
    territory: 'All Territories',
    whatsapp_group: 'BLR-Sea-Lounge-Orders',
    gstin: '27AADCB3002L1ZS',
    billing: { line1: '22 Carter Road, Bandra West', city: 'Mumbai', state: 'Maharashtra', pincode: '400050', country: 'India' },
    shipping: { line1: 'Level 2 Beverage Dock, Carter Road', city: 'Mumbai', state: 'Maharashtra', pincode: '400050', country: 'India' }
  },
  {
    customer_name: 'Fortune Hills Poolside Bar',
    company: 'Fortune Hills Resort Lonavala Pvt Ltd',
    customer_type: 'Company',
    customer_group: 'All Customer Groups',
    territory: 'All Territories',
    whatsapp_group: 'FHR-Poolside-Bar',
    gstin: '27AACCF6001A1ZP',
    billing: { line1: 'Survey 44, Old Mumbai Pune Highway', city: 'Lonavala', state: 'Maharashtra', pincode: '410401', country: 'India' },
    shipping: { line1: 'Poolside Service Corridor', city: 'Lonavala', state: 'Maharashtra', pincode: '410401', country: 'India' }
  },
  {
    customer_name: 'Fortune Hills Banquet Hall',
    company: 'Fortune Hills Resort Lonavala Pvt Ltd',
    customer_type: 'Company',
    customer_group: 'All Customer Groups',
    territory: 'All Territories',
    whatsapp_group: 'FHR-Banquets-Orders',
    gstin: '27AACCF6002A1ZO',
    billing: { line1: 'Survey 44, Old Mumbai Pune Highway', city: 'Lonavala', state: 'Maharashtra', pincode: '410401', country: 'India' },
    shipping: { line1: 'Banquet Receiving Bay', city: 'Lonavala', state: 'Maharashtra', pincode: '410401', country: 'India' }
  },
  {
    customer_name: 'Fortune Hills Room Service',
    company: 'Fortune Hills Resort Lonavala Pvt Ltd',
    customer_type: 'Company',
    customer_group: 'All Customer Groups',
    territory: 'All Territories',
    whatsapp_group: 'FHR-RoomService-Dispatch',
    gstin: '27AACCF6003A1ZN',
    billing: { line1: 'Survey 44, Old Mumbai Pune Highway', city: 'Lonavala', state: 'Maharashtra', pincode: '410401', country: 'India' },
    shipping: { line1: 'Room Service Basement Store', city: 'Lonavala', state: 'Maharashtra', pincode: '410401', country: 'India' }
  },
  {
    customer_name: 'Cafe Sunrise – Koregaon Park',
    company: 'Hotel Sahyadri Pune Pvt Ltd',
    customer_type: 'Company',
    customer_group: 'All Customer Groups',
    territory: 'All Territories',
    whatsapp_group: 'Cafe-Sunrise-KP',
    gstin: '27AAECS5005Q1ZT',
    billing: { line1: 'Lane 6, Koregaon Park', city: 'Pune', state: 'Maharashtra', pincode: '411001', country: 'India' },
    shipping: { line1: 'Back Entry, Lane 6, Koregaon Park', city: 'Pune', state: 'Maharashtra', pincode: '411001', country: 'India' }
  },
  {
    customer_name: 'Bistro Central – Andheri',
    company: 'Blue Lagoon Restaurant Mumbai LLP',
    customer_type: 'Company',
    customer_group: 'All Customer Groups',
    territory: 'All Territories',
    whatsapp_group: 'Bistro-Central-Andheri',
    gstin: '27AADCB3003L1ZR',
    billing: { line1: 'MIDC Central Road, Andheri East', city: 'Mumbai', state: 'Maharashtra', pincode: '400093', country: 'India' },
    shipping: { line1: 'Dock 2, MIDC Central Road', city: 'Mumbai', state: 'Maharashtra', pincode: '400093', country: 'India' }
  }
];

const salesPdfItems = [
  { name: 'SFK SAMRAT ATTA 50KG', hsn: '11010000', rate: 35.5 },
  { name: 'MAIDA SAMRAT', hsn: '11010000', rate: 37 },
  { name: 'SFK MAKKA ATTA', hsn: '10059000', rate: 50 },
  { name: 'Talod Dhokla Mix Yellow 1kg', hsn: '21069099', rate: 190 },
  { name: 'TOORDAL PRESIDENT', hsn: '07139010', rate: 115 },
  { name: 'DOUBLE BEE', hsn: '07132000', rate: 110 },
  { name: 'SFK CHAWLI', hsn: '07132000', rate: 85 },
  { name: 'SFK GREEN MOONG', hsn: '07132000', rate: 104 },
  { name: 'SFK RAJ MA BIG', hsn: '07132000', rate: 90 },
  { name: 'SFK BROWN CHANA', hsn: '07132000', rate: 70 },
  { name: 'SFK MASOOR WHOLE', hsn: '07132000', rate: 72 },
  { name: 'SFK MATKI', hsn: '07134000', rate: 72 },
  { name: 'BASMATI RICE HEAVEN HARVEST', hsn: '10063010', rate: 95 },
  { name: 'STAFF RICE CHOTU', hsn: '10063010', rate: 34 },
  { name: 'STAFF RICE YESS BOSS', hsn: '10063010', rate: 44 },
  { name: 'AMBEMOHAR RICE', hsn: '10063010', rate: 150 },
  { name: 'SFK SUGAR', hsn: '17011490', rate: 42 },
  { name: 'GUD BLACK', hsn: '17011390', rate: 51 },
  { name: 'TATA SALT 1KG', hsn: '25010020', rate: 26.5 },
  { name: 'SFK KIRTI GOLD SUNFLOWER OIL 13kg', hsn: '15121910', rate: 2380 },
  { name: 'SF MAHAVIR MUSTARD OIL 1LTR', hsn: '15149910', rate: 161 },
  { name: 'SF TILL OIL', hsn: '15151910', rate: 110 },
  { name: 'SFK SABUDANA', hsn: '07149010', rate: 57 },
  { name: 'SFK SHINGDANA', hsn: '12024210', rate: 134 },
  { name: 'SUJI RAVA (BADA)', hsn: '11010000', rate: 39 },
  { name: 'PRAVIN AMBARI MIRCHI POWDER', hsn: '09103090', rate: 400 },
  { name: 'PRAVIN AMBARI HALDI POWDER', hsn: '09103090', rate: 390 },
  { name: 'PRAVIN AMBARI DHANIYA POWDER', hsn: '09103090', rate: 330 },
  { name: 'EVEREST CHAT MASALA 500GM', hsn: '21039040', rate: 310 },
  { name: 'LG HING 50GM', hsn: '13019013', rate: 75 },
  { name: 'UTSAV KASTURI METHI', hsn: '09109990', rate: 240 },
  { name: 'SFK JEERA', hsn: '09093129', rate: 290 },
  { name: 'MOHARI', hsn: '12075010', rate: 84 },
  { name: 'OVA AJ WAIN', hsn: '09109914', rate: 220 },
  { name: 'ENO', hsn: '30049011', rate: 140 },
  { name: 'SFK KHOBRA KHIS', hsn: '08011100', rate: 240 },
  { name: 'SF CITRIC ACID', hsn: '29181400', rate: 150 },
  { name: 'SFK BLACK SALT', hsn: '250100', rate: 20 },
  { name: 'KASHMIRI CHILLY WHOLE', hsn: '09042110', rate: 420 },
  { name: 'SAMOSA PAPAD 1', hsn: '19059030', rate: 80 },
  { name: 'NACHNI PAPAD', hsn: '19059030', rate: 90 },
  { name: 'SFK KHAJUR SEEDLESS', hsn: '08041020', rate: 150 },
  { name: 'KAJ UKANI BHARI', hsn: '08013210', rate: 460.01 },
  { name: 'SFK ANJEER', hsn: '08042090', rate: 1000 },
  { name: 'SFK KALINGAD MAGAJ 1KG', hsn: '12077090', rate: 525 },
  { name: 'SFK BADISHEP', hsn: '09096139', rate: 190 },
  { name: 'SFK KHADHI SHAKAR RAVALGAON', hsn: '17019990', rate: 60 },
  { name: 'SF NIRMA 500GM', hsn: '34029019', rate: 31 },
  { name: 'HABIT PANKO BREAD CRUMB', hsn: '19054000', rate: 120 },
  { name: 'TATA TEA AGNI 1KG', hsn: '09023020', rate: 220 },
  { name: 'PRAVIN PICKLE TOOFAN 5KG MANGO', hsn: '20011000', rate: 320 },
  { name: 'SF ZERO SEV', hsn: '21069099', rate: 130 },
  { name: 'FARSAN', hsn: '21069099', rate: 130 },
  { name: 'KISSAN DIP SAUCE POUCH 930GM', hsn: '21032000', rate: 75 },
  { name: 'MANAMA ROSE SYRUP 750ML', hsn: '21069011', rate: 135 },
  { name: 'MANAMA KALA KHATTA CORDIAL 750ML', hsn: '20089919', rate: 140 },
  { name: 'MANAMA KIWI CRUSH 1LTR', hsn: '20089919', rate: 320 },
  { name: 'SF LI J AT PAPAD SMALL', hsn: '19059040', rate: 320 },
  { name: 'AAMCHOOR POWDER', hsn: '09109100', rate: 80 }
];

function salesPdfItemGroup(name) {
  const value = String(name).toUpperCase();
  if (/(JEERA|HING|METHI|MASALA|CHILLI|HALDI|DHANIYA|AJ\\s*WAIN|AMCHOOR|CHAT|CITRIC)/.test(value)) {
    return 'Spices & Seasoning';
  }
  if (/(SYRUP|CORDIAL|CRUSH)/.test(value)) {
    return 'Packaged Juices';
  }
  if (/(NIRMA)/.test(value)) {
    return 'Cleaning Chemicals';
  }
  if (/(PANKO)/.test(value)) {
    return 'Bakery Inputs';
  }
  return 'Dry Goods & Staples';
}

function salesPdfStockUom(name) {
  const value = String(name).toUpperCase();
  if (/(TIN)/.test(value)) {
    return 'Tin';
  }
  if (/(KG|1KG|50KG|25KG|30KG|5KG)/.test(value)) {
    return 'Kg';
  }
  if (/(500GM|930GM|750ML|1LTR|LTR|ML)/.test(value)) {
    return 'Nos';
  }
  return 'Nos';
}

const extraUsers = {
  admin: [
    { email: 'ananya.kulkarni@aas.local', fullName: 'Ananya Kulkarni' },
    { email: 'rohan.mehta@aas.local', fullName: 'Rohan Mehta' },
    { email: 'farah.siddiqui@aas.local', fullName: 'Farah Siddiqui' }
  ],
  vendor: [
    { email: 'vivek.jagtap@freshharvest.local', fullName: 'Vivek Jagtap', supplier: 'FreshHarvest Agro Foods' },
    { email: 'sana.shaikh@spiceroute.local', fullName: 'Sana Shaikh', supplier: 'SpiceRoute Traders' },
    { email: 'neeraj.nair@brewmasters.local', fullName: 'Neeraj Nair', supplier: 'BrewMasters Beverages Pvt Ltd' }
  ],
  shop: [
    { email: 'arpita.deshmukh@hsp.local', fullName: 'Arpita Deshmukh', customer: 'Sahyadri All-Day Dining' },
    { email: 'karan.fernandes@blr.local', fullName: 'Karan Fernandes', customer: 'Blue Lagoon Takeaway - Bandra' },
    { email: 'madhura.patil@fhr.local', fullName: 'Madhura Patil', customer: 'Fortune Hills Banquet Hall' }
  ],
  helper: [
    { email: 'deepak.waghmare@aas.local', fullName: 'Deepak Waghmare' },
    { email: 'nisha.thorat@aas.local', fullName: 'Nisha Thorat' },
    { email: 'pratik.more@aas.local', fullName: 'Pratik More' }
  ]
};

const itemFamilies = [
  {
    parent: 'Food',
    leaf: 'Vegetables – Fresh',
    supplier: 'FreshHarvest Agro Foods',
    items: [
      ['VEG-POT-50', 'Potato - 50 kg sack', 'Bag', 'Kg', 1325, 12],
      ['VEG-ONI-50', 'Onion - 50 kg sack', 'Bag', 'Kg', 1480, 13],
      ['VEG-TOM-20', 'Tomato - 20 kg crate', 'Crate', 'Kg', 620, 18],
      ['VEG-CUC-10', 'Cucumber - 10 kg carton', 'Carton', 'Kg', 420, 16],
      ['VEG-CAB-15', 'Cabbage - 15 kg crate', 'Crate', 'Kg', 360, 14]
    ]
  },
  {
    parent: 'Food',
    leaf: 'Fruits – Fresh',
    supplier: 'FreshHarvest Agro Foods',
    items: [
      ['FRT-BAN-12', 'Banana Robusta - 12 dozen lot', 'Crate', 'Nos', 780, 14],
      ['FRT-APP-18', 'Apple Royal Gala - 18 kg carton', 'Carton', 'Kg', 1850, 18],
      ['FRT-ORA-15', 'Orange Nagpur - 15 kg crate', 'Crate', 'Kg', 1125, 15],
      ['FRT-PAP-10', 'Papaya Red Lady - 10 kg crate', 'Crate', 'Kg', 640, 16],
      ['FRT-PIN-6', 'Pineapple Queen - 6 pcs crate', 'Crate', 'Nos', 510, 18]
    ]
  },
  {
    parent: 'Food',
    leaf: 'Herbs & Aromatics',
    supplier: 'FreshHarvest Agro Foods',
    items: [
      ['HER-COR-1', 'Coriander Leaves - 1 kg', 'Bundle', 'Kg', 85, 26],
      ['HER-MIN-1', 'Mint Leaves - 1 kg', 'Bundle', 'Kg', 96, 28],
      ['HER-CUR-1', 'Curry Leaves - 1 kg', 'Bundle', 'Kg', 74, 24],
      ['HER-LEM-2', 'Lemongrass - 2 kg bundle', 'Bundle', 'Kg', 155, 25],
      ['HER-CHI-1', 'Green Chilli - 1 kg', 'Bag', 'Kg', 96, 22]
    ]
  },
  {
    parent: 'Food',
    leaf: 'Meat & Poultry',
    supplier: 'Deccan Poultry & Meats',
    items: [
      ['MET-CHK-1', 'Broiler Chicken - 1 kg', 'Crate', 'Kg', 176, 12],
      ['MET-MTN-1', 'Mutton Curry Cut - 1 kg', 'Crate', 'Kg', 720, 14],
      ['MET-KEB-1', 'Chicken Seekh Kebab Mix - 1 kg', 'Pack', 'Kg', 290, 18],
      ['MET-SAU-1', 'Chicken Sausage - 1 kg pack', 'Pack', 'Kg', 325, 18],
      ['MET-BCN-1', 'Smoked Bacon Strips - 1 kg', 'Pack', 'Kg', 640, 20]
    ]
  },
  {
    parent: 'Food',
    leaf: 'Seafood',
    supplier: 'OceanCatch Seafood Supplies',
    items: [
      ['SEA-PRA-1', 'Prawns Medium - 1 kg', 'Crate', 'Kg', 540, 16],
      ['SEA-BAS-1', 'Basa Fillet - 1 kg', 'Pack', 'Kg', 310, 15],
      ['SEA-POM-1', 'Pomfret Whole - 1 kg', 'Crate', 'Kg', 890, 17],
      ['SEA-SUR-1', 'Surmai Steak - 1 kg', 'Crate', 'Kg', 760, 16],
      ['SEA-CAL-1', 'Calamari Rings - 1 kg', 'Pack', 'Kg', 450, 18]
    ]
  },
  {
    parent: 'Food',
    leaf: 'Dairy & Eggs',
    supplier: 'PrimeDairy Distributors',
    items: [
      ['DAI-MLK-1', 'Full Cream Milk - 1 L tetra pack', 'Case', 'Litre', 61, 12],
      ['DAI-BTR-500', 'Amul Butter - 500 g pack', 'Case', 'Nos', 268, 15],
      ['DAI-PNR-1', 'Paneer - 1 kg block', 'Crate', 'Kg', 355, 14],
      ['DAI-CHS-2', 'Mozzarella Cheese - 2 kg loaf', 'Pack', 'Kg', 760, 18],
      ['DAI-EGG-30', 'Table Eggs - 30 egg tray', 'Tray', 'Nos', 168, 10]
    ]
  },
  {
    parent: 'Food',
    leaf: 'Dry Goods & Staples',
    supplier: 'SpiceRoute Traders',
    items: [
      ['DRY-RIC-25', 'Basmati Rice 1121 - 25 kg bag', 'Bag', 'Kg', 2420, 11],
      ['DRY-FLR-30', 'Maida - 30 kg bag', 'Bag', 'Kg', 1140, 10],
      ['DRY-ATA-30', 'Whole Wheat Flour - 30 kg bag', 'Bag', 'Kg', 1165, 10],
      ['DRY-SUG-50', 'Sugar M30 - 50 kg bag', 'Bag', 'Kg', 2280, 9],
      ['DRY-DAL-30', 'Toor Dal - 30 kg bag', 'Bag', 'Kg', 3480, 11]
    ]
  },
  {
    parent: 'Food',
    leaf: 'Spices & Seasoning',
    supplier: 'SpiceRoute Traders',
    items: [
      ['SPC-TUR-1', 'Turmeric Powder - 1 kg pouch', 'Case', 'Kg', 188, 22],
      ['SPC-CHI-1', 'Kashmiri Chilli Powder - 1 kg pouch', 'Case', 'Kg', 310, 24],
      ['SPC-GAR-1', 'Garam Masala - 1 kg pouch', 'Case', 'Kg', 440, 24],
      ['SPC-PEP-1', 'Black Pepper Whole - 1 kg pouch', 'Case', 'Kg', 620, 18],
      ['SPC-SAL-25', 'Iodised Salt - 25 kg bag', 'Bag', 'Kg', 265, 8]
    ]
  },
  {
    parent: 'Food',
    leaf: 'Bakery Inputs',
    supplier: 'PrimeDairy Distributors',
    items: [
      ['BAK-YEA-500', 'Instant Dry Yeast - 500 g pack', 'Box', 'Nos', 210, 26],
      ['BAK-CRE-1', 'Whipping Cream - 1 L pack', 'Case', 'Litre', 235, 20],
      ['BAK-CHO-1', 'Dark Compound Chocolate - 1 kg', 'Box', 'Kg', 295, 22],
      ['BAK-COC-1', 'Desiccated Coconut - 1 kg', 'Box', 'Kg', 210, 18],
      ['BAK-VAN-500', 'Vanilla Essence - 500 ml', 'Bottle', 'Nos', 165, 24]
    ]
  },
  {
    parent: 'Beverages',
    leaf: 'Soft Drinks',
    supplier: 'BrewMasters Beverages Pvt Ltd',
    items: [
      ['BEV-COK-125', 'Coca-Cola PET - 1.25 L', 'Case', 'Nos', 67, 18],
      ['BEV-SPR-125', 'Sprite PET - 1.25 L', 'Case', 'Nos', 65, 18],
      ['BEV-THU-750', 'Thums Up PET - 750 ml', 'Case', 'Nos', 44, 18],
      ['BEV-SOD-300', 'Club Soda - 300 ml', 'Case', 'Nos', 19, 20],
      ['BEV-TON-300', 'Tonic Water - 300 ml', 'Case', 'Nos', 34, 22]
    ]
  },
  {
    parent: 'Beverages',
    leaf: 'Packaged Juices',
    supplier: 'BrewMasters Beverages Pvt Ltd',
    items: [
      ['JUI-ORN-1', 'Orange Juice - 1 L tetra pack', 'Case', 'Litre', 112, 18],
      ['JUI-MNG-1', 'Mango Juice - 1 L tetra pack', 'Case', 'Litre', 118, 18],
      ['JUI-APP-1', 'Apple Juice - 1 L tetra pack', 'Case', 'Litre', 126, 18],
      ['JUI-CRN-1', 'Cranberry Juice - 1 L bottle', 'Case', 'Litre', 184, 22],
      ['JUI-MIX-1', 'Mixed Fruit Juice - 1 L tetra pack', 'Case', 'Litre', 108, 18]
    ]
  },
  {
    parent: 'Beverages',
    leaf: 'Bottled Water',
    supplier: 'BrewMasters Beverages Pvt Ltd',
    items: [
      ['WAT-500', 'Packaged Drinking Water - 500 ml', 'Case', 'Nos', 9, 25],
      ['WAT-1000', 'Packaged Drinking Water - 1 L', 'Case', 'Nos', 13, 22],
      ['WAT-20L', 'Water Jar - 20 L', 'Jar', 'Nos', 62, 18],
      ['WAT-SPK-750', 'Sparkling Water - 750 ml', 'Case', 'Nos', 72, 24],
      ['WAT-GLS-300', 'Premium Glass Water - 300 ml', 'Case', 'Nos', 28, 24]
    ]
  },
  {
    parent: 'Beverages',
    leaf: 'Beer',
    supplier: 'Regal Spirits Distributors',
    items: [
      ['BER-KFP-650', 'Kingfisher Premium Beer - 650 ml', 'Case', 'Nos', 118, 16],
      ['BER-H8000-500', 'Haywards 5000 - 500 ml can', 'Case', 'Nos', 92, 16],
      ['BER-BUD-330', 'Budweiser Magnum - 330 ml', 'Case', 'Nos', 108, 18],
      ['BER-HOEG-330', 'Hoegaarden - 330 ml', 'Case', 'Nos', 176, 20],
      ['BER-COR-330', 'Corona Extra - 330 ml', 'Case', 'Nos', 198, 22]
    ]
  },
  {
    parent: 'Beverages',
    leaf: 'Spirits',
    supplier: 'Regal Spirits Distributors',
    items: [
      ['SPI-BLW-750', 'Blenders Pride Reserve - 750 ml', 'Case', 'Nos', 840, 18],
      ['SPI-JW-750', 'Johnnie Walker Red Label - 750 ml', 'Case', 'Nos', 1640, 20],
      ['SPI-BAC-750', 'Bacardi Superior - 750 ml', 'Case', 'Nos', 1015, 18],
      ['SPI-SMR-750', 'Smirnoff Vodka - 750 ml', 'Case', 'Nos', 980, 18],
      ['SPI-GIN-750', 'Greater Than Gin - 750 ml', 'Case', 'Nos', 1220, 20]
    ]
  },
  {
    parent: 'Housekeeping',
    leaf: 'Linen',
    supplier: 'LinenLux Textiles',
    items: [
      ['LIN-BED-K', 'Cotton Bed Sheet - 300TC - King', 'Pack', 'Nos', 1180, 24],
      ['LIN-BED-Q', 'Cotton Bed Sheet - 300TC - Queen', 'Pack', 'Nos', 1040, 24],
      ['LIN-PIL-STD', 'Microfiber Pillow - Standard', 'Pack', 'Nos', 420, 26],
      ['LIN-DUV-K', 'Duvet Cover - King', 'Pack', 'Nos', 1260, 24],
      ['LIN-BTH-WHT', 'Bath Towel - White - 600 GSM', 'Pack', 'Nos', 285, 24]
    ]
  },
  {
    parent: 'Housekeeping',
    leaf: 'Cleaning Chemicals',
    supplier: 'CrystalClean Housekeeping Supplies',
    items: [
      ['CLN-GLS-5', 'Glass Cleaner - 5 L can', 'Can', 'Nos', 340, 22],
      ['CLN-FLR-5', 'Floor Cleaner Lemon - 5 L can', 'Can', 'Nos', 295, 22],
      ['CLN-DIS-5', 'Surface Disinfectant - 5 L can', 'Can', 'Nos', 510, 20],
      ['CLN-DGR-5', 'Kitchen Degreaser - 5 L can', 'Can', 'Nos', 465, 20],
      ['CLN-WAS-20', 'Laundry Detergent - 20 L drum', 'Drum', 'Nos', 1720, 18]
    ]
  },
  {
    parent: 'Housekeeping',
    leaf: 'Guest Amenities',
    supplier: 'CrystalClean Housekeeping Supplies',
    items: [
      ['GST-SHP-30', 'Guest Shampoo - 30 ml bottle', 'Case', 'Nos', 8.5, 30],
      ['GST-BTH-30', 'Guest Bath Gel - 30 ml bottle', 'Case', 'Nos', 8.8, 30],
      ['GST-LOT-30', 'Guest Body Lotion - 30 ml bottle', 'Case', 'Nos', 9.4, 30],
      ['GST-SOAP-18', 'Guest Soap - 18 g bar', 'Case', 'Nos', 6.2, 28],
      ['GST-DNT-KIT', 'Dental Kit - sealed', 'Case', 'Nos', 11.5, 32]
    ]
  },
  {
    parent: 'Miscellaneous',
    leaf: 'Packaging Material',
    supplier: 'Metro Packaging Solutions',
    items: [
      ['PKG-CNT-750', 'Paper Food Container - 750 ml', 'Case', 'Nos', 8.2, 24],
      ['PKG-CNT-1000', 'Paper Food Container - 1000 ml', 'Case', 'Nos', 9.6, 24],
      ['PKG-CUP-300', 'Ripple Coffee Cup - 300 ml', 'Case', 'Nos', 4.6, 24],
      ['PKG-LID-300', 'Cup Lid - 300 ml', 'Case', 'Nos', 1.9, 22],
      ['PKG-BAG-M', 'Kraft Carry Bag - Medium', 'Case', 'Nos', 6.1, 20]
    ]
  }
];

const outlets = [
  { customer: 'Shop A', company: 'Hotel Sahyadri Pune Pvt Ltd', segment: 'all-day', size: 0.85 },
  { customer: 'Sahyadri All-Day Dining', company: 'Hotel Sahyadri Pune Pvt Ltd', segment: 'all-day', size: 1.15 },
  { customer: 'Sahyadri Rooftop Bar', company: 'Hotel Sahyadri Pune Pvt Ltd', segment: 'bar', size: 1.0 },
  { customer: 'Sahyadri Banquet Deck', company: 'Hotel Sahyadri Pune Pvt Ltd', segment: 'banquet', size: 1.45 },
  { customer: 'Blue Lagoon Takeaway - Bandra', company: 'Blue Lagoon Restaurant Mumbai LLP', segment: 'all-day', size: 1.0 },
  { customer: 'Blue Lagoon Sea Lounge', company: 'Blue Lagoon Restaurant Mumbai LLP', segment: 'bar', size: 1.15 },
  { customer: 'Fortune Hills Poolside Bar', company: 'Fortune Hills Resort Lonavala Pvt Ltd', segment: 'bar', size: 1.1, resort: true },
  { customer: 'Fortune Hills Banquet Hall', company: 'Fortune Hills Resort Lonavala Pvt Ltd', segment: 'banquet', size: 1.55, resort: true },
  { customer: 'Fortune Hills Room Service', company: 'Fortune Hills Resort Lonavala Pvt Ltd', segment: 'all-day', size: 1.0, resort: true },
  { customer: 'Cafe Sunrise – Koregaon Park', company: 'Hotel Sahyadri Pune Pvt Ltd', segment: 'external', size: 0.75 },
  { customer: 'Bistro Central – Andheri', company: 'Blue Lagoon Restaurant Mumbai LLP', segment: 'external', size: 0.82 }
];

const taxRates = {
  'Vegetables – Fresh': 0.05,
  'Fruits – Fresh': 0.05,
  'Herbs & Aromatics': 0.05,
  'Meat & Poultry': 0.05,
  Seafood: 0.05,
  'Dairy & Eggs': 0.05,
  'Dry Goods & Staples': 0.05,
  'Spices & Seasoning': 0.12,
  'Bakery Inputs': 0.12,
  'Soft Drinks': 0.18,
  'Packaged Juices': 0.12,
  'Bottled Water': 0.12,
  Beer: 0.18,
  Spirits: 0.18,
  Linen: 0.12,
  'Cleaning Chemicals': 0.18,
  'Guest Amenities': 0.18,
  'Packaging Material': 0.12
};

const uoms = ['Kg', 'Litre', 'Nos', 'Bag', 'Bundle', 'Box', 'Bottle', 'Can', 'Carton', 'Case', 'Crate', 'Drum', 'Jar', 'Pack', 'Tray'];

function slug(value) {
  return String(value ?? '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function pad(value) {
  return String(value).padStart(2, '0');
}

function formatDate(date) {
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

function addDays(date, days) {
  const copy = new Date(date.getTime());
  copy.setUTCDate(copy.getUTCDate() + days);
  return copy;
}

function maxDate(left, right) {
  return left >= right ? left : right;
}

function weeksBetween(start, end) {
  const dates = [];
  for (let cursor = new Date(start.getTime()); cursor <= end; cursor = addDays(cursor, 7)) {
    dates.push(new Date(cursor.getTime()));
  }
  return dates;
}

function round(value) {
  return Math.round(Number(value) * 100) / 100;
}

function unwrap(body) {
  if (body && typeof body === 'object' && body.data && typeof body.data === 'object') {
    return body.data;
  }
  return body;
}

class HttpError extends Error {
  constructor(message, status, body) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.body = body;
  }
}

class JsonClient {
  constructor(baseUrl) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.cookie = '';
    this.token = '';
  }

  setCookie(cookie) {
    this.cookie = cookie || '';
  }

  setToken(token) {
    this.token = token || '';
  }

  async request(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.json !== undefined) {
      headers.set('content-type', 'application/json');
    }
    if (this.cookie && !headers.has('cookie')) {
      headers.set('cookie', this.cookie);
    }
    if (this.token && !headers.has('authorization')) {
      headers.set('authorization', `Bearer ${this.token}`);
    }
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: options.method || (options.json !== undefined ? 'POST' : 'GET'),
      headers,
      body: options.json !== undefined ? JSON.stringify(options.json) : options.body
    });
    const text = await response.text();
    if (!response.ok) {
      throw new HttpError(`${options.method || 'GET'} ${path} failed: ${response.status}`, response.status, text);
    }
    if (!text) {
      return null;
    }
    try {
      return JSON.parse(text);
    } catch {
      return text;
    }
  }
}

const mw = new JsonClient(MW_BASE_URL);
const erp = new JsonClient(ERP_BASE_URL);

async function loginMiddleware() {
  const response = await mw.request('/api/auth/login', {
    method: 'POST',
    json: { username: USERNAME, password: PASSWORD }
  });
  if (!response?.accessToken) {
    throw new Error('MW login failed: accessToken missing.');
  }
  mw.setToken(response.accessToken);
  return response;
}

async function loginErp() {
  const form = new URLSearchParams({ usr: USERNAME, pwd: PASSWORD });
  const response = await fetch(`${ERP_BASE_URL}/api/method/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: form
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`ERP login failed: ${response.status} ${text}`);
  }
  const cookie = response.headers.get('set-cookie') || '';
  const sid = cookie
    .split(',')
    .map(entry => entry.trim())
    .find(entry => entry.startsWith('sid='));
  if (!sid) {
    throw new Error('ERP login failed: sid cookie missing.');
  }
  erp.setCookie(sid.split(';')[0]);
}

function encodeSegment(value) {
  return encodeURIComponent(String(value));
}

function toFilterJson(filters) {
  return JSON.stringify(filters);
}

async function erpList(doctype, params = {}) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value));
    }
  }
  const suffix = query.toString() ? `?${query}` : '';
  const body = await erp.request(`/api/resource/${encodeSegment(doctype)}${suffix}`);
  return Array.isArray(body?.data) ? body.data : [];
}

async function erpGet(doctype, name) {
  try {
    const body = await erp.request(`/api/resource/${encodeSegment(doctype)}/${encodeSegment(name)}`);
    return unwrap(body);
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

async function erpCreate(doctype, payload) {
  if (DRY_RUN) {
    return payload;
  }
  const body = await erp.request(`/api/resource/${encodeSegment(doctype)}`, {
    method: 'POST',
    json: payload
  });
  return unwrap(body);
}

async function erpUpdate(doctype, name, payload) {
  if (DRY_RUN) {
    return payload;
  }
  const body = await erp.request(`/api/resource/${encodeSegment(doctype)}/${encodeSegment(name)}`, {
    method: 'PUT',
    json: payload
  });
  return unwrap(body);
}

async function erpDelete(doctype, name) {
  if (DRY_RUN) {
    return null;
  }
  await erp.request(`/api/resource/${encodeSegment(doctype)}/${encodeSegment(name)}`, {
    method: 'DELETE'
  });
  return null;
}

async function erpEnsure(doctype, name, payload, updateFields = []) {
  const existing = await erpGet(doctype, name);
  if (!existing) {
    const record = { ...payload };
    if (doctype === 'Company') {
      record.company_name = name;
    }
    if (doctype === 'Supplier') {
      record.supplier_name = name;
    }
    if (doctype === 'Customer') {
      record.customer_name = name;
    }
    if (doctype === 'Item Group') {
      record.item_group_name = name;
    }
    if (doctype === 'Item') {
      record.item_code = name;
    }
    if (doctype === 'UOM') {
      record.uom_name = name;
    }
    return erpCreate(doctype, record);
  }
  if (updateFields.length) {
    const patch = {};
    for (const field of updateFields) {
      if (payload[field] !== undefined && payload[field] !== existing[field]) {
        patch[field] = payload[field];
      }
    }
    if (Object.keys(patch).length) {
      return erpUpdate(doctype, name, patch);
    }
  }
  return existing;
}

async function erpFindOne(doctype, filters, fields = ['name']) {
  const rows = await erpList(doctype, {
    fields: JSON.stringify(fields),
    filters: toFilterJson(filters),
    limit_page_length: '1'
  });
  return rows[0] || null;
}

async function ensureSetup() {
  await mw.request('/api/setup/ensure', {
    method: 'POST',
    json: {}
  });
}

async function ensureUoms() {
  for (const uom of uoms) {
    await erpEnsure('UOM', uom, { must_be_whole_number: 0 });
  }
}

async function ensureCompanies() {
  const byName = new Map();
  for (const company of companies) {
    const record = await erpEnsure(
      'Company',
      company.name,
      {
        abbr: company.abbr,
        default_currency: company.currency,
        country: company.country
      },
      ['abbr', 'default_currency', 'country']
    );
    byName.set(company.name, record);
  }
  return byName;
}

async function ensureWarehousesAndCostCenters(companyIndex) {
  for (const company of companies) {
    const companyDoc = companyIndex.get(company.name) || {};
    const abbr = companyDoc.abbr || company.abbr;
    const rootWarehouse = await erpFindOne('Warehouse', [
      ['company', '=', company.name],
      ['is_group', '=', '1']
    ]);
    const parentWarehouse = rootWarehouse?.name || 'All Warehouses';
    for (const warehouseName of ['Main Store', 'Kitchen Store', 'Bar Store']) {
      const docName = `${warehouseName} - ${abbr}`;
      await erpEnsure(
        'Warehouse',
        docName,
        {
          warehouse_name: warehouseName,
          company: company.name,
          parent_warehouse: parentWarehouse,
          is_group: 0
        },
        ['warehouse_name', 'company', 'parent_warehouse', 'is_group']
      );
    }

    const rootCostCenter = await erpFindOne('Cost Center', [
      ['company', '=', company.name],
      ['is_group', '=', '1']
    ]);
    const parentCostCenter = rootCostCenter?.name || `Main - ${abbr}`;
    for (const costCenterName of ['F&B Operations', 'Banquets', 'Room Service']) {
      const docName = `${costCenterName} - ${abbr}`;
      await erpEnsure(
        'Cost Center',
        docName,
        {
          cost_center_name: costCenterName,
          company: company.name,
          parent_cost_center: parentCostCenter,
          is_group: 0
        },
        ['cost_center_name', 'company', 'parent_cost_center', 'is_group']
      );
    }
  }
}

async function ensureSupplierAddresses() {
  for (const supplier of suppliers) {
    await erpEnsure(
      'Supplier',
      supplier.supplier_name,
      {
        supplier_type: supplier.supplier_type,
        supplier_group: supplier.supplier_group,
        disabled: supplier.disabled,
        aas_branch_name: supplier.branch_name,
        aas_address: supplier.address,
        aas_phone: supplier.phone,
        aas_gst_no: supplier.gst,
        aas_pan_no: supplier.pan,
        aas_food_license_no: supplier.food_license_no,
        aas_priority: supplier.aas_priority,
        aas_invoice_template_enabled: supplier.invoice_template_enabled ?? 0,
        aas_invoice_template_key: supplier.invoice_template_key ?? ''
      },
      [
        'supplier_type',
        'supplier_group',
        'disabled',
        'aas_branch_name',
        'aas_address',
        'aas_phone',
        'aas_gst_no',
        'aas_pan_no',
        'aas_food_license_no',
        'aas_priority',
        'aas_invoice_template_enabled',
        'aas_invoice_template_key'
      ]
    );

    const addressTitle = `${supplier.supplier_name} Billing`;
    const existing = await erpFindOne('Address', [['address_title', '=', addressTitle]]);
    if (!existing) {
      await erpCreate('Address', {
        address_title: addressTitle,
        address_type: 'Billing',
        address_line1: supplier.address,
        city: supplier.address.includes('Mumbai') ? 'Mumbai' : supplier.address.includes('Pune') ? 'Pune' : 'Bhiwandi',
        state: 'Maharashtra',
        pincode: supplier.address.match(/\b\d{6}\b/)?.[0] || '400001',
        country: 'India',
        links: [{ link_doctype: 'Supplier', link_name: supplier.supplier_name }]
      });
    }
  }
}

async function ensureCustomerAddresses() {
  for (const customer of customers) {
    await erpEnsure(
      'Customer',
      customer.customer_name,
      {
        customer_type: customer.customer_type,
        customer_group: customer.customer_group,
        territory: customer.territory,
        aas_whatsapp_group_name: customer.whatsapp_group
      },
      ['customer_type', 'customer_group', 'territory', 'aas_whatsapp_group_name']
    );

    for (const [addressType, details] of [
      ['Billing', customer.billing],
      ['Shipping', customer.shipping]
    ]) {
      const addressTitle = `${customer.customer_name} ${addressType}`;
      const existing = await erpFindOne('Address', [['address_title', '=', addressTitle]]);
      if (!existing) {
        await erpCreate('Address', {
          address_title: addressTitle,
          address_type: addressType,
          address_line1: details.line1,
          city: details.city,
          state: details.state,
          pincode: details.pincode,
          country: details.country,
          gstin: customer.gstin,
          links: [{ link_doctype: 'Customer', link_name: customer.customer_name }]
        });
      }
    }
  }
}

async function ensureItemGroups() {
  for (const parent of ['Food', 'Beverages', 'Housekeeping', 'Miscellaneous']) {
    await erpEnsure('Item Group', parent, { parent_item_group: 'All Item Groups', is_group: 1 }, ['parent_item_group', 'is_group']);
  }
  for (const family of itemFamilies) {
    await erpEnsure(
      'Item Group',
      family.leaf,
      {
        parent_item_group: family.parent,
        is_group: 0
      },
      ['parent_item_group', 'is_group']
    );
  }
}

function buildItems() {
  return itemFamilies.flatMap(family =>
    family.items.map(([itemCode, itemName, packaging, uom, vendorRate, margin]) => ({
      item_code: itemCode,
      item_name: itemName,
      item_group: family.leaf,
      stock_uom: uom,
      aas_packaging_unit: packaging,
      aas_vendor_rate: vendorRate,
      aas_margin_percent: margin,
      default_supplier: family.supplier,
      is_stock_item: 1,
      is_sales_item: 1,
      is_purchase_item: 1
    }))
  );
}

async function ensureItems() {
  const items = buildItems();
  for (const item of items) {
    await erpEnsure(
      'Item',
      item.item_code,
      item,
      [
        'item_name',
        'item_group',
        'stock_uom',
        'aas_packaging_unit',
        'aas_vendor_rate',
        'aas_margin_percent',
        'default_supplier',
        'is_stock_item',
        'is_sales_item',
        'is_purchase_item'
      ]
    );
  }
  return items;
}

function ocrItemCode(name) {
  const base = `OCR-${slug(name).toUpperCase()}`;
  return base.length > 48 ? base.slice(0, 48) : base;
}

async function cleanupOcrItems() {
  const items = await erpList('Item', {
    fields: JSON.stringify(['name', 'item_code']),
    filters: toFilterJson([['item_code', 'like', 'OCR-%']]),
    limit_page_length: '1000'
  });
  for (const item of items) {
    if (!item?.name) {
      continue;
    }
    await erpDelete('Item', item.name);
  }
}

async function ensureSalesPdfItems() {
  await erpEnsure('Item Group', 'All Item Groups', { is_group: 1 });
  const groups = new Set(salesPdfItems.map(item => salesPdfItemGroup(item.name)));
  for (const group of groups) {
    await erpEnsure(
      'Item Group',
      group,
      { parent_item_group: 'All Item Groups', is_group: 0 },
      ['parent_item_group', 'is_group']
    );
  }
  const uoms = new Set(salesPdfItems.map(item => salesPdfStockUom(item.name)));
  for (const uom of uoms) {
    await erpEnsure('UOM', uom, { must_be_whole_number: 0 });
  }
  await cleanupOcrItems();

  const usedCodes = new Set();
  for (const item of salesPdfItems) {
    const baseCode = String(item.hsn || '').trim();
    let code = baseCode;
    if (usedCodes.has(code)) {
      let index = 2;
      while (usedCodes.has(`${code}-${index}`)) {
        index += 1;
      }
      code = `${code}-${index}`;
    }
    usedCodes.add(code);
    const itemGroup = salesPdfItemGroup(item.name);
    const stockUom = salesPdfStockUom(item.name);
    await erpEnsure(
      'Item',
      code,
      {
        item_code: code,
        item_name: item.name,
        item_group: itemGroup,
        stock_uom: stockUom,
        is_stock_item: 1,
        is_sales_item: 1,
        is_purchase_item: 1,
        aas_vendor_rate: item.rate,
        aas_margin_percent: 0
      },
      [
        'item_name',
        'item_group',
        'stock_uom',
        'is_stock_item',
        'is_sales_item',
        'is_purchase_item',
        'aas_vendor_rate',
        'aas_margin_percent'
      ]
    );
  }
}

async function ensureUsers() {
  const defaultUsers = [
    {
      email: 'vendor@example.com',
      fullName: 'Vendor User',
      password: 'VendorAAS!2026',
      role: 'Supplier',
      supplier: 'Vendor A'
    },
    {
      email: 'shop@example.com',
      fullName: 'Shop User',
      password: 'ShopAAS!2026',
      role: 'Customer',
      customer: 'Shop A'
    },
    {
      email: 'helper@example.com',
      fullName: 'Helper User',
      password: 'HelperAAS!2026',
      role: 'Stock User'
    }
  ];

  const merged = [
    ...defaultUsers,
    ...extraUsers.admin.map(user => ({ ...user, password: 'AdminAAS!2026', role: 'Administrator' })),
    ...extraUsers.vendor.map(user => ({ ...user, password: 'VendorOps!2026', role: 'Supplier' })),
    ...extraUsers.shop.map(user => ({ ...user, password: 'ShopOps!2026', role: 'Customer' })),
    ...extraUsers.helper.map(user => ({ ...user, password: 'HelperOps!2026', role: 'Stock User' }))
  ];

  for (const user of merged) {
    const existing = await erpGet('User', user.email);
    if (existing) {
      continue;
    }
    const [firstName, ...rest] = user.fullName.split(' ');
    await erpCreate('User', {
      email: user.email,
      first_name: firstName,
      last_name: rest.join(' '),
      enabled: 1,
      send_welcome_email: 0,
      new_password: user.password,
      user_type: 'System User',
      roles: [{ role: user.role }],
      supplier: user.supplier,
      customer: user.customer
    });
  }
}

function buildItemIndex(items) {
  const byCode = new Map();
  const byGroup = new Map();
  for (const item of items) {
    byCode.set(item.item_code, item);
    if (!byGroup.has(item.item_group)) {
      byGroup.set(item.item_group, []);
    }
    byGroup.get(item.item_group).push(item);
  }
  return { byCode, byGroup };
}

function warehouseForCompany(companyName) {
  const company = companies.find(entry => entry.name === companyName);
  return company ? `Kitchen Store - ${company.abbr}` : '';
}

function computeSellRate(item, uplift = 1) {
  return round(item.aas_vendor_rate * (1 + (item.aas_margin_percent * uplift) / 100));
}

function daysSinceSeedDate(date) {
  const diff = TODAY.getTime() - date.getTime();
  return Math.floor(diff / (24 * 60 * 60 * 1000));
}

function seedOrderStatus(date) {
  const age = daysSinceSeedDate(date);
  if (age >= 18) {
    return 'Delivered';
  }
  if (age >= 12) {
    return 'Accepted';
  }
  if (age >= 8) {
    return 'SELL_ORDER_CREATED';
  }
  if (age >= 5) {
    return 'VENDOR_BILL_CAPTURED';
  }
  if (age >= 3) {
    return 'VENDOR_PDF_RECEIVED';
  }
  if (age >= 1) {
    return 'VENDOR_ASSIGNED';
  }
  return 'DRAFT';
}

function normalizeSeedQty(item, qty) {
  const rawQty = qty < 1 ? 1 : qty;
  return item?.stock_uom === 'Nos' ? Math.ceil(rawQty) : rawQty;
}

function buildOrderRecord(seedKey, date, customerName, companyName, vendorName, chosenItems, sizeMultiplier, status, notes = '') {
  const transactionDate = formatDate(date);
  const deliveryDate = formatDate(addDays(date, 1));
  const warehouse = warehouseForCompany(companyName);
  const items = chosenItems.map((item, index) => {
    const qty = round((index + 1) * sizeMultiplier);
    const safeQty = normalizeSeedQty(item, qty);
    const rate = computeSellRate(item, vendorName === 'Regal Spirits Distributors' ? 0.9 : 1);
    return {
      item_code: item.item_code,
      qty: safeQty,
      rate,
      amount: round(rate * safeQty),
      aas_vendor_rate: item.aas_vendor_rate,
      aas_margin_percent: item.aas_margin_percent,
      warehouse
    };
  });
  const itemsTotal = round(items.reduce((sum, item) => sum + Number(item.amount || 0), 0));
  const hasVendorPdf = ['VENDOR_PDF_RECEIVED', 'VENDOR_BILL_CAPTURED', 'SELL_ORDER_CREATED'].includes(status);
  const hasVendorBill = ['VENDOR_PDF_RECEIVED', 'VENDOR_BILL_CAPTURED', 'SELL_ORDER_CREATED'].includes(status);
  const hasSellOrder = ['SELL_ORDER_CREATED'].includes(status);
  return {
    seedKey,
    customer: customerName,
    company: companyName,
    vendor: vendorName,
    transaction_date: transactionDate,
    delivery_date: deliveryDate,
    status,
    set_warehouse: warehouse,
    note: notes,
    items,
    vendor_pdf: hasVendorPdf ? `/files/${seedKey.toLowerCase()}.pdf` : '',
    vendor_bill_total: hasVendorBill ? itemsTotal : 0,
    vendor_bill_ref: hasVendorBill ? `BILL-${seedKey.replace(/^AAS-SEED-/, '')}` : '',
    vendor_bill_date: hasVendorBill ? deliveryDate : '',
    sell_order_total: hasSellOrder ? itemsTotal : 0
  };
}

function buildHistoricalOrders(itemIndex) {
  const orders = [];
  const mondays = weeksBetween(new Date('2026-01-05T00:00:00Z'), TODAY);
  let counter = 1;
  const banquetDates = new Set(['2026-01-18', '2026-02-14', '2026-02-28']);

  const pick = group => itemIndex.byGroup.get(group) || [];

  for (const monday of mondays) {
    const weekLabel = `${monday.getUTCFullYear()}W${pad(Math.ceil((monday.getUTCDate() + monday.getUTCDay()) / 7))}`;
    for (const outlet of outlets) {
      const weekendFactor = outlet.resort ? 1.25 : 1;
      const pantrySize = outlet.size * (outlet.segment === 'external' ? 0.7 : 1);
      const vegSize = outlet.size * (outlet.segment === 'banquet' ? 1.3 : 1);
      const proteinSize = outlet.size * (outlet.segment === 'bar' ? 0.75 : 1);

      orders.push(
        buildOrderRecord(
          `AAS-SEED-SO-${weekLabel}-${pad(counter++)}`,
          monday,
          outlet.customer,
          outlet.company,
          'SpiceRoute Traders',
          pick('Dry Goods & Staples').slice(0, 3),
          pantrySize,
          seedOrderStatus(monday),
          'Weekly pantry replenishment'
        )
      );

      orders.push(
        buildOrderRecord(
          `AAS-SEED-SO-${weekLabel}-${pad(counter++)}`,
          addDays(monday, 1),
          outlet.customer,
          outlet.company,
          'FreshHarvest Agro Foods',
          [...pick('Vegetables – Fresh').slice(0, 3), ...pick('Herbs & Aromatics').slice(0, 2)],
          vegSize,
          seedOrderStatus(addDays(monday, 1)),
          'Fresh produce cycle'
        )
      );

      orders.push(
        buildOrderRecord(
          `AAS-SEED-SO-${weekLabel}-${pad(counter++)}`,
          addDays(monday, 2),
          outlet.customer,
          outlet.company,
          'PrimeDairy Distributors',
          [...pick('Dairy & Eggs').slice(0, 3), ...pick('Bakery Inputs').slice(0, 1)],
          outlet.size,
          seedOrderStatus(addDays(monday, 2)),
          'Mid-week dairy and bakery top-up'
        )
      );

      const proteinVendor = outlet.segment === 'bar' ? 'OceanCatch Seafood Supplies' : 'Deccan Poultry & Meats';
      const proteinGroup = proteinVendor === 'OceanCatch Seafood Supplies' ? 'Seafood' : 'Meat & Poultry';
      orders.push(
        buildOrderRecord(
          `AAS-SEED-SO-${weekLabel}-${pad(counter++)}`,
          addDays(monday, 3),
          outlet.customer,
          outlet.company,
          proteinVendor,
          pick(proteinGroup).slice(0, 4),
          proteinSize,
          seedOrderStatus(addDays(monday, 3)),
          'Protein dispatch aligned to menu plan'
        )
      );

      orders.push(
        buildOrderRecord(
          `AAS-SEED-SO-${weekLabel}-${pad(counter++)}`,
          addDays(monday, 4),
          outlet.customer,
          outlet.company,
          'FreshHarvest Agro Foods',
          [...pick('Fruits – Fresh').slice(0, 3), ...pick('Vegetables – Fresh').slice(2, 4)],
          vegSize * 0.95,
          seedOrderStatus(addDays(monday, 4)),
          'Weekend mise en place'
        )
      );

      if (outlet.segment === 'bar' || outlet.segment === 'banquet') {
        const beverageVendor = outlet.segment === 'bar' ? 'Regal Spirits Distributors' : 'BrewMasters Beverages Pvt Ltd';
        const beverageGroup = outlet.segment === 'bar' ? 'Beer' : 'Soft Drinks';
        orders.push(
          buildOrderRecord(
            `AAS-SEED-SO-${weekLabel}-${pad(counter++)}`,
            addDays(monday, 5),
            outlet.customer,
            outlet.company,
            beverageVendor,
            [...pick(beverageGroup).slice(0, 3), ...pick('Bottled Water').slice(0, 1)],
            outlet.size * weekendFactor,
            seedOrderStatus(addDays(monday, 5)),
            'Weekend bar and banquet beverage load'
          )
        );
      }

      if ((counter + outlet.customer.length) % 2 === 0) {
        orders.push(
          buildOrderRecord(
            `AAS-SEED-SO-${weekLabel}-${pad(counter++)}`,
            addDays(monday, 6),
            outlet.customer,
            outlet.company,
            outlet.segment === 'banquet' ? 'LinenLux Textiles' : 'CrystalClean Housekeeping Supplies',
            outlet.segment === 'banquet' ? pick('Linen').slice(0, 3) : [...pick('Cleaning Chemicals').slice(0, 3), ...pick('Guest Amenities').slice(0, 2)],
            outlet.size * 0.65,
            seedOrderStatus(addDays(monday, 6)),
            'Housekeeping and service inventory cycle'
          )
        );
      }
    }
  }

  for (const dateText of banquetDates) {
    const date = new Date(`${dateText}T00:00:00Z`);
    for (const outlet of outlets.filter(entry => entry.segment === 'banquet')) {
      orders.push(
        buildOrderRecord(
          `AAS-SEED-SPIKE-${slug(outlet.customer)}-${dateText}`,
          date,
          outlet.customer,
          outlet.company,
          'FreshHarvest Agro Foods',
          [...pick('Vegetables – Fresh').slice(0, 4), ...pick('Herbs & Aromatics').slice(0, 2)],
          outlet.size * 2.2,
          seedOrderStatus(date),
          'Wedding and banquet volume spike'
        )
      );
      orders.push(
        buildOrderRecord(
          `AAS-SEED-SPIKE-PACK-${slug(outlet.customer)}-${dateText}`,
          addDays(date, 0),
          outlet.customer,
          outlet.company,
          'Metro Packaging Solutions',
          pick('Packaging Material').slice(0, 4),
          outlet.size * 1.9,
          seedOrderStatus(date),
          'Banquet service and takeaway packaging load'
        )
      );
    }
  }

  return orders;
}

async function createSalesOrders(orderRecords) {
  const created = [];
  for (const record of orderRecords) {
    const payload = {
      customer: record.customer,
      company: record.company,
      transaction_date: record.transaction_date,
      delivery_date: record.delivery_date,
      set_warehouse: record.set_warehouse,
      selling_price_list: 'Standard Selling',
      price_list_currency: 'INR',
      plc_conversion_rate: 1,
      po_no: record.seedKey,
      aas_vendor: record.vendor,
      aas_status: record.status,
      aas_vendor_pdf: record.vendor_pdf,
      aas_vendor_bill_total: record.vendor_bill_total,
      aas_vendor_bill_ref: record.vendor_bill_ref,
      aas_vendor_bill_date: record.vendor_bill_date,
      aas_sell_order_total: record.sell_order_total,
      remarks: record.note,
      items: record.items
    };
    const existing = await erpFindOne('Sales Order', [['po_no', '=', record.seedKey]], ['name']);
    if (existing) {
      await erpUpdate('Sales Order', existing.name, payload);
      created.push({ ...record, name: existing.name });
      continue;
    }
    let createdDoc;
    try {
      createdDoc = await erpCreate('Sales Order', payload);
    } catch (error) {
      if (error instanceof HttpError) {
        throw new Error(
          `Sales Order seed failed for ${record.seedKey} (${record.customer} / ${record.company}) `
          + `txn=${record.transaction_date} delivery=${record.delivery_date}: ${error.body}`
        );
      }
      throw error;
    }
    created.push({ ...record, name: createdDoc.name || createdDoc?.data?.name });
  }
  return created;
}

function createInvoicePlan(orders, itemIndex) {
  return orders
    .filter(order => order.status === 'Delivered' || order.status === 'Accepted')
    .filter(order => order.transaction_date <= '2026-02-25')
    .slice(0, 120)
    .map((order, index) => {
      const postingDate = maxDate(order.delivery_date, RUN_DATE);
      const dueDate = formatDate(addDays(new Date(`${postingDate}T00:00:00Z`), [7, 15, 30][index % 3]));
      const discount = index % 5 === 0 ? 2.5 : index % 7 === 0 ? 1.25 : 0;
      const lineItems = order.items.map(row => {
        const item = itemIndex.byCode.get(row.item_code);
        return {
          item_code: row.item_code,
          qty: row.qty,
          rate: row.rate,
          amount: row.amount,
          warehouse: row.warehouse,
          aas_vendor_rate: row.aas_vendor_rate,
          aas_margin_percent: row.aas_margin_percent,
          item_group: item?.item_group
        };
      });
      const baseTotal = round(lineItems.reduce((sum, row) => sum + Number(row.amount || 0), 0));
      const taxRate = taxRates[itemIndex.byCode.get(lineItems[0].item_code)?.item_group] || 0.12;
      const discountAmount = round(baseTotal * (discount / 100));
      const taxableTotal = round(baseTotal - discountAmount);
      const grandTotal = round(taxableTotal * (1 + taxRate));
      return {
        seedKey: `AAS-SEED-SI-${order.seedKey}`,
        customer: order.customer,
        company: order.company,
        posting_date: postingDate,
        due_date: dueDate,
        set_warehouse: order.set_warehouse,
        aas_source_sales_order: order.name,
        additional_discount_percentage: discount,
        remarks: `AAS-SEED Invoice for ${order.seedKey} | GST profile ${(taxRate * 100).toFixed(0)}%`,
        baseTotal,
        invoiceTotal: taxableTotal,
        grandTotal,
        taxRate,
        items: lineItems
      };
    });
}

async function createSalesInvoices(invoicePlans) {
  const created = [];
  for (const plan of invoicePlans) {
    const existing = await erpFindOne('Sales Invoice', [['remarks', '=', plan.remarks]], ['name', 'grand_total']);
    if (existing) {
      created.push({ ...plan, name: existing.name, grand_total: Number(existing.grand_total || plan.invoiceTotal) });
      continue;
    }
    const payload = {
      customer: plan.customer,
      company: plan.company,
      posting_date: plan.posting_date,
      due_date: plan.due_date,
      set_warehouse: plan.set_warehouse,
      selling_price_list: 'Standard Selling',
      price_list_currency: 'INR',
      plc_conversion_rate: 1,
      aas_source_sales_order: plan.aas_source_sales_order,
      remarks: plan.remarks,
      additional_discount_percentage: plan.additional_discount_percentage,
      items: plan.items
    };
    let createdDoc;
    try {
      createdDoc = await erpCreate('Sales Invoice', payload);
    } catch (error) {
      if (error instanceof HttpError) {
        throw new Error(
          `Sales Invoice seed failed for ${plan.seedKey} (${plan.customer} / ${plan.company}) `
          + `posting=${plan.posting_date} due=${plan.due_date}: ${error.body}`
        );
      }
      throw error;
    }
    created.push({ ...plan, name: createdDoc.name || createdDoc?.data?.name, grand_total: plan.invoiceTotal });
  }
  return created;
}

async function findPaymentEntryByReference(referenceNo) {
  return erpFindOne('Payment Entry', [['reference_no', '=', referenceNo]], ['name']);
}

async function createCustomerPayments(invoices, companyIndex) {
  for (let index = 0; index < invoices.length; index += 1) {
    const invoice = invoices[index];
    const companyDoc = companyIndex.get(invoice.company) || {};
    const receivable = companyDoc.default_receivable_account;
    const cash = companyDoc.default_cash_account;
    if (!receivable || !cash) {
      continue;
    }
    const behavior = index % 6;
    const paidAmount = behavior === 0 ? round(invoice.grand_total * 0.55) : round(invoice.grand_total);
    const delayDays = behavior === 1 ? 3 : behavior === 2 ? 10 : behavior === 3 ? 18 : behavior === 4 ? 28 : 5;
    const candidatePostingDate = formatDate(addDays(new Date(`${invoice.due_date}T00:00:00Z`), delayDays));
    const postingDate = candidatePostingDate > RUN_DATE ? RUN_DATE : candidatePostingDate;
    const referenceNo = `AAS-SEED-COL-${slug(invoice.customer)}-${index + 1}`;
    if (!FORCE_TRANSACTIONS) {
      const existing = await findPaymentEntryByReference(referenceNo);
      if (existing) {
        continue;
      }
    }
    await erpCreate('Payment Entry', {
      payment_type: 'Receive',
      party_type: 'Customer',
      party: invoice.customer,
      company: invoice.company,
      posting_date: postingDate,
      paid_amount: paidAmount,
      received_amount: paidAmount,
      paid_from: receivable,
      paid_to: cash,
      reference_no: referenceNo,
      reference_date: postingDate
    });
  }
}

function aggregateVendorAmounts(orders) {
  const map = new Map();
  for (const order of orders.filter(entry => entry.transaction_date <= '2026-02-26')) {
    const amount = round(order.items.reduce((sum, row) => sum + Number(row.aas_vendor_rate || 0) * Number(row.qty || 0), 0));
    const key = `${order.vendor}::${order.company}`;
    if (!map.has(key)) {
      map.set(key, { vendor: order.vendor, company: order.company, amount: 0, lastDate: order.delivery_date });
    }
    const bucket = map.get(key);
    bucket.amount = round(bucket.amount + amount);
    if (bucket.lastDate < order.delivery_date) {
      bucket.lastDate = order.delivery_date;
    }
  }
  return [...map.values()].sort((left, right) =>
    `${left.vendor}::${left.company}`.localeCompare(`${right.vendor}::${right.company}`)
  );
}

async function createSupplierPayments(orders, companyIndex) {
  const aggregates = aggregateVendorAmounts(orders);
  let counter = 1;
  for (const aggregate of aggregates) {
    const companyDoc = companyIndex.get(aggregate.company) || {};
    const cash = companyDoc.default_cash_account;
    const payable = companyDoc.default_payable_account;
    if (!cash || !payable) {
      continue;
    }
    const full = counter % 4 !== 0;
    const amount = full ? aggregate.amount : round(aggregate.amount * 0.62);
    const postingDate = formatDate(addDays(new Date(`${aggregate.lastDate}T00:00:00Z`), counter % 3 === 0 ? 18 : 7));
    if (postingDate > formatDate(TODAY)) {
      counter += 1;
      continue;
    }
    const referenceNo = `AAS-SEED-VPAY-${slug(aggregate.vendor)}-${counter}`;
    if (!FORCE_TRANSACTIONS) {
      const existing = await findPaymentEntryByReference(referenceNo);
      if (existing) {
        counter += 1;
        continue;
      }
    }
    await erpCreate('Payment Entry', {
      payment_type: 'Pay',
      party_type: 'Supplier',
      party: aggregate.vendor,
      company: aggregate.company,
      posting_date: postingDate,
      paid_amount: amount,
      received_amount: amount,
      paid_from: cash,
      paid_to: payable,
      reference_no: referenceNo,
      reference_date: postingDate
    });
    counter += 1;
  }
}

async function refreshCompanyIndex() {
  const index = new Map();
  for (const company of companies) {
    const doc = await erpGet('Company', company.name);
    if (doc) {
      index.set(company.name, doc);
    }
  }
  const aasCore = await erpGet('Company', 'AAS Core');
  if (aasCore) {
    index.set('AAS Core', aasCore);
  }
  return index;
}

export async function main() {
  console.log(`Seeding mature AAS data via MW=${MW_BASE_URL} and ERP=${ERP_BASE_URL}`);
  if (DRY_RUN) {
    console.log('DRY_RUN=1 enabled; no ERP or MW writes will be performed.');
  }

  await generateOcrArtifacts();
  await loginMiddleware();
  await loginErp();
  await ensureSetup();
  await ensureUoms();

  let companyIndex = await ensureCompanies();
  await ensureWarehousesAndCostCenters(companyIndex);
  await ensureSupplierAddresses();
  await ensureCustomerAddresses();
  await ensureItemGroups();
  const items = await ensureItems();
  if (SEED_SALES_PDF_ITEMS) {
    await ensureSalesPdfItems();
  }
  await ensureUsers();
  companyIndex = await refreshCompanyIndex();

  const itemIndex = buildItemIndex(items);
  const orders = buildHistoricalOrders(itemIndex);

  console.log(`Ensuring ${orders.length} seeded sales orders...`);
  const seededOrders = await createSalesOrders(orders);

  const invoicePlans = createInvoicePlan(seededOrders, itemIndex);
  console.log(`Ensuring ${invoicePlans.length} seeded sales invoices...`);
  const seededInvoices = await createSalesInvoices(invoicePlans);

  await createCustomerPayments(seededInvoices, companyIndex);
  await createSupplierPayments(seededOrders, companyIndex);

  console.log('Seed complete.');
  console.log(`OCR fixture company: ${OCR_FIXTURE.company}`);
  console.log(`OCR fixture customer: ${OCR_FIXTURE.customer}`);
  console.log(`OCR fixture vendor: ${OCR_FIXTURE.vendor}`);
}

if (process.argv[1] && new URL(`file://${process.argv[1]}`).pathname === new URL(import.meta.url).pathname) {
  main().catch(error => {
    console.error(error?.stack || error?.message || error);
    process.exitCode = 1;
  });
}
