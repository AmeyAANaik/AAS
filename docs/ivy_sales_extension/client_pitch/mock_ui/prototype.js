// Workspace (sidebar) navigation
const workspaceButtons = document.querySelectorAll('.menu-item[data-view]');
const workspaceViews = document.querySelectorAll('.workspace-view');
workspaceButtons.forEach((button) => {
  button.addEventListener('click', () => {
    workspaceButtons.forEach((b) => b.classList.remove('active'));
    workspaceViews.forEach((v) => v.classList.remove('active'));
    button.classList.add('active');
    document.getElementById(button.dataset.view)?.classList.add('active');
  });
});

// Persona tabs under execution flow
const personaTabs = document.querySelectorAll('.tab');
const personas = document.querySelectorAll('.persona');
personaTabs.forEach((tab) => {
  tab.addEventListener('click', () => {
    personaTabs.forEach((t) => t.classList.remove('active'));
    personas.forEach((p) => p.classList.remove('active'));
    tab.classList.add('active');
    document.getElementById(tab.dataset.persona)?.classList.add('active');
  });
});

// Suggestion System
const suggestions = [
  { sku: 'IVY-LASSI-200ML', type: 'replenishment', segment: 'gold', reason: 'Outlet reorder cycle reached (14 days).', confidence: 86, qty: 24, uplift: '₹4,200' },
  { sku: 'IVY-GHEE-500ML', type: 'cross-sell', segment: 'silver', reason: 'High conversion with milk + curd basket.', confidence: 72, qty: 6, uplift: '₹1,850' },
  { sku: 'IVY-BUTTERMILK-180ML', type: 'scheme', segment: 'platinum', reason: 'Active summer scheme unlocks margin uplift.', confidence: 79, qty: 30, uplift: '₹3,120' },
  { sku: 'IVY-PANEER-200G', type: 'cross-sell', segment: 'gold', reason: 'Demand trend up 18% in nearby outlets.', confidence: 74, qty: 10, uplift: '₹2,150' },
];

let accepted = 0;
let rejected = 0;

const suggestionList = document.getElementById('suggestion-list');
const shownCount = document.getElementById('shown-count');
const acceptedCount = document.getElementById('accepted-count');
const rejectedCount = document.getElementById('rejected-count');
const segmentFilter = document.getElementById('segment-filter');
const typeFilter = document.getElementById('type-filter');
const refreshButton = document.getElementById('refresh-suggestions');

function filterSuggestions() {
  const segment = segmentFilter?.value || 'all';
  const type = typeFilter?.value || 'all';
  return suggestions.filter((s) => (segment === 'all' || s.segment === segment) && (type === 'all' || s.type === type));
}

function updateCounts(shown) {
  if (shownCount) shownCount.textContent = String(shown);
  if (acceptedCount) acceptedCount.textContent = String(accepted);
  if (rejectedCount) rejectedCount.textContent = String(rejected);
}

function renderSuggestions() {
  if (!suggestionList) return;
  const rows = filterSuggestions();
  suggestionList.innerHTML = '';

  rows.forEach((s, index) => {
    const row = document.createElement('article');
    row.className = 'suggestion-item';
    row.innerHTML = `
      <div>
        <strong>${s.sku}</strong>
        <p>${s.reason}</p>
      </div>
      <div class="suggestion-meta">
        <span class="badge blue">Type: ${s.type}</span>
        <span class="badge amber">Confidence: ${s.confidence}%</span>
        <span>Suggested Qty: ${s.qty}</span>
        <span>Expected uplift: ${s.uplift}</span>
      </div>
      <div class="suggestion-actions">
        <button class="ghost" data-action="reject" data-index="${index}">Reject</button>
        <button data-action="accept" data-index="${index}">Accept + Add</button>
      </div>
    `;
    suggestionList.appendChild(row);
  });

  updateCounts(rows.length);
}

suggestionList?.addEventListener('click', (event) => {
  const target = event.target;
  if (!(target instanceof HTMLElement)) return;
  const action = target.dataset.action;
  if (!action) return;

  if (action === 'accept') accepted += 1;
  if (action === 'reject') rejected += 1;
  updateCounts(filterSuggestions().length);
});

refreshButton?.addEventListener('click', renderSuggestions);
segmentFilter?.addEventListener('change', renderSuggestions);
typeFilter?.addEventListener('change', renderSuggestions);

function renderRetailerSuggestions() {
  const retailerBox = document.getElementById('retailer-suggestions');
  if (!retailerBox) return;

  const retailerRows = suggestions.slice(0, 2);
  retailerBox.innerHTML = retailerRows.map((s) => `
      <article class="suggestion-item">
        <div>
          <strong>${s.sku}</strong>
          <p>${s.reason}</p>
        </div>
        <div class="suggestion-meta">
          <span class="badge blue">${s.type}</span>
          <span class="badge amber">${s.confidence}%</span>
          <span>Qty ${s.qty}</span>
        </div>
        <div class="suggestion-actions">
          <button>Add to Cart</button>
        </div>
      </article>
  `).join('');
}

document.getElementById('retailer-refresh')?.addEventListener('click', renderRetailerSuggestions);


// Salesperson-scoped beat data + shortest path mock
const salesProfiles = {
  roshni: {
    kpi: { planned: 18, completed: 11, productive: 9, value: '₹1.58L', priority: '3 high-priority outlets', route: '61% route completion', strike: 'Strike rate 81.8%', change: '+12% vs yesterday' },
    routeSummary: 'Optimized path: 18.4 km • ETA 5h 20m • 18 outlets',
    beats: [
      { code: '001', name: 'Sagar Stores', due: '09:30', segment: 'Gold', primary: 'Start Visit', secondary: 'Check-in' },
      { code: '002', name: 'Lakshmi Mart', due: '10:10', segment: 'Silver', primary: 'Capture Order', secondary: 'Audit' },
      { code: '003', name: 'SRS Super', due: '10:50', segment: 'Platinum', primary: 'Checkout', secondary: 'Collection' },
    ],
  },
  ajay: {
    kpi: { planned: 20, completed: 13, productive: 10, value: '₹1.32L', priority: '2 high-priority outlets', route: '65% route completion', strike: 'Strike rate 76.9%', change: '+6% vs yesterday' },
    routeSummary: 'Optimized path: 21.1 km • ETA 6h 05m • 20 outlets',
    beats: [
      { code: '004', name: 'Metro Bazaar', due: '09:20', segment: 'Gold', primary: 'Start Visit', secondary: 'Check-in' },
      { code: '005', name: 'Green Mart', due: '10:05', segment: 'Silver', primary: 'Capture Order', secondary: 'Audit' },
      { code: '006', name: 'City Retail', due: '10:45', segment: 'Gold', primary: 'Checkout', secondary: 'Collection' },
    ],
  },
  sana: {
    kpi: { planned: 19, completed: 15, productive: 12, value: '₹1.74L', priority: '4 high-priority outlets', route: '79% route completion', strike: 'Strike rate 80.0%', change: '+15% vs yesterday' },
    routeSummary: 'Optimized path: 16.9 km • ETA 5h 02m • 19 outlets',
    beats: [
      { code: '007', name: 'A1 Traders', due: '09:10', segment: 'Platinum', primary: 'Start Visit', secondary: 'Check-in' },
      { code: '008', name: 'Nova Stores', due: '09:55', segment: 'Gold', primary: 'Capture Order', secondary: 'Audit' },
      { code: '009', name: 'Prime Mart', due: '10:35', segment: 'Silver', primary: 'Checkout', secondary: 'Collection' },
    ],
  },
};

const salespersonFilter = document.getElementById('salesperson-filter');
const routeMode = document.getElementById('route-mode');
const beatList = document.getElementById('beat-list');
const routeSummary = document.getElementById('route-summary');

function renderSalespersonView() {
  const key = salespersonFilter?.value || 'roshni';
  const profile = salesProfiles[key] || salesProfiles.roshni;

  document.getElementById('kpi-planned').textContent = String(profile.kpi.planned);
  document.getElementById('kpi-completed').textContent = String(profile.kpi.completed);
  document.getElementById('kpi-productive').textContent = String(profile.kpi.productive);
  document.getElementById('kpi-value').textContent = profile.kpi.value;
  document.getElementById('kpi-priority').textContent = profile.kpi.priority;
  document.getElementById('kpi-route').textContent = profile.kpi.route;
  document.getElementById('kpi-strike').textContent = profile.kpi.strike;
  document.getElementById('kpi-change').textContent = profile.kpi.change;

  const mode = routeMode?.value || 'shortest';
  const modeLabel = mode === 'traffic' ? 'Traffic optimized' : mode === 'priority' ? 'Priority-first' : 'Shortest path';
  if (routeSummary) routeSummary.textContent = `${modeLabel}: ${profile.routeSummary}`;

  if (beatList) {
    beatList.innerHTML = profile.beats.map((b) => `
      <div class="list-item">
        <div><strong>${b.code} • ${b.name}</strong><p>Due ${b.due} • ${b.segment} Segment</p></div>
        <div class="actions"><button class="ghost fixed-btn">${b.secondary}</button><button class="fixed-btn">${b.primary}</button></div>
      </div>
    `).join('');
  }
}

salespersonFilter?.addEventListener('change', renderSalespersonView);
routeMode?.addEventListener('change', renderSalespersonView);
document.getElementById('optimize-route')?.addEventListener('click', renderSalespersonView);

// Territory map + hierarchy role scoping
const hierarchyProfiles = {
  salesperson: {
    visibleData: 'Own route outlets only (beat-level visibility).',
    territory: 'South Cluster 1 / Route A',
    subordinates: ['No subordinate team. Individual contributor scope.'],
  },
  asm: {
    visibleData: 'Area-level outlets and all reps under assigned area.',
    territory: 'South Cluster 1 (Bangalore + Mysore)',
    subordinates: ['Rep: Roshni N', 'Rep: Ajay P', 'Rep: Sana M'],
  },
  rsm: {
    visibleData: 'Region-level totals, area rollups, and ASM visibility.',
    territory: 'South Region (Karnataka + Tamil Nadu)',
    subordinates: ['ASM: Kiran R', 'ASM: Meera D', 'ASM: Arun S'],
  },
  nsm: {
    visibleData: 'National rollup, regional comparison, and deep drilldown.',
    territory: 'All India',
    subordinates: ['RSM South', 'RSM West', 'RSM North', 'RSM East'],
  },
};

const territoryInsights = {
  'bangalore-urban': {
    attainment: '112%',
    outlets: 324,
    monthlySales: '₹9.2L',
    gap: 'Strong performance; push premium cross-sell.',
    team: [
      { name: 'Roshni N', role: 'Sales Rep', sales: '₹2.4L', accept: '56%' },
      { name: 'Ajay P', role: 'Sales Rep', sales: '₹1.8L', accept: '49%' },
      { name: 'Sana M', role: 'Sales Rep', sales: '₹2.0L', accept: '52%' },
    ],
    outletsTop: ['Sagar Stores', 'Lakshmi Mart', 'SRS Super'],
  },
  mysore: {
    attainment: '91%',
    outlets: 188,
    monthlySales: '₹4.1L',
    gap: 'Increase productive visits and reorder compliance.',
    team: [
      { name: 'Karthik V', role: 'Sales Rep', sales: '₹1.2L', accept: '43%' },
      { name: 'Nisha R', role: 'Sales Rep', sales: '₹1.0L', accept: '41%' },
    ],
    outletsTop: ['City Traders', 'Royal Mart'],
  },
  'chennai-north': {
    attainment: '108%',
    outlets: 276,
    monthlySales: '₹8.6L',
    gap: 'Maintain current scheme-led momentum.',
    team: [
      { name: 'Arun S', role: 'ASM', sales: '₹3.1L', accept: '58%' },
      { name: 'Priya K', role: 'Sales Rep', sales: '₹1.9L', accept: '54%' },
    ],
    outletsTop: ['Metro Fresh', 'North Bazaar', 'KVR Stores'],
  },
  coimbatore: {
    attainment: '74%',
    outlets: 143,
    monthlySales: '₹2.9L',
    gap: 'Low coverage zone; ASM intervention needed.',
    team: [
      { name: 'Meera D', role: 'ASM', sales: '₹1.1L', accept: '32%' },
      { name: 'Rahul T', role: 'Sales Rep', sales: '₹0.8L', accept: '29%' },
    ],
    outletsTop: ['Coimbatore Wholesale', 'Annai Stores'],
  },
  madurai: {
    attainment: '88%',
    outlets: 169,
    monthlySales: '₹3.8L',
    gap: 'Improve fill-rate and repeat order cycle.',
    team: [
      { name: 'Vimal A', role: 'Sales Rep', sales: '₹1.3L', accept: '39%' },
      { name: 'Deepa M', role: 'Sales Rep', sales: '₹1.1L', accept: '37%' },
    ],
    outletsTop: ['Southline Retail', 'Temple Market'],
  },
  'hyderabad-east': {
    attainment: '116%',
    outlets: 301,
    monthlySales: '₹9.8L',
    gap: 'High growth; expand portfolio push.',
    team: [
      { name: 'Kiran R', role: 'ASM', sales: '₹3.4L', accept: '61%' },
      { name: 'Suman G', role: 'Sales Rep', sales: '₹2.2L', accept: '57%' },
      { name: 'Latha P', role: 'Sales Rep', sales: '₹1.9L', accept: '55%' },
    ],
    outletsTop: ['East Hyper', 'Metro Basket', 'A1 Traders'],
  },
};

const roleScope = document.getElementById('role-scope');
const hierarchySummary = document.getElementById('hierarchy-summary');
const subordinateList = document.getElementById('subordinate-list');
const territoryDetail = document.getElementById('territory-detail');
const subordinatePerformanceBody = document.getElementById('subordinate-performance-body');
const territoryOutletList = document.getElementById('territory-outlet-list');
const mapNodes = document.querySelectorAll('.map-node[data-territory]');
let selectedTerritory = 'bangalore-urban';

function renderTerritoryDetail() {
  if (!territoryDetail) return;
  const info = territoryInsights[selectedTerritory];
  if (!info) {
    territoryDetail.innerHTML = '<div class="summary-pill">No territory data.</div>';
    if (subordinatePerformanceBody) subordinatePerformanceBody.innerHTML = '';
    if (territoryOutletList) territoryOutletList.innerHTML = '';
    return;
  }
  territoryDetail.innerHTML = `
    <div class="summary-pill"><strong>Attainment:</strong> ${info.attainment}</div>
    <div class="summary-pill"><strong>Active Outlets:</strong> ${info.outlets}</div>
    <div class="summary-pill"><strong>Monthly Sales:</strong> ${info.monthlySales}</div>
    <div class="summary-pill"><strong>Insight:</strong> ${info.gap}</div>
  `;

  if (subordinatePerformanceBody) {
    subordinatePerformanceBody.innerHTML = info.team
      .map((member) => `<tr><td>${member.name}</td><td>${member.role}</td><td>${member.sales}</td><td>${member.accept}</td></tr>`)
      .join('');
  }

  if (territoryOutletList) {
    territoryOutletList.innerHTML = info.outletsTop.map((outlet) => `<li>${outlet}</li>`).join('');
  }
}

function renderHierarchyScope() {
  if (!roleScope || !hierarchySummary || !subordinateList) return;
  const key = roleScope.value;
  const profile = hierarchyProfiles[key] || hierarchyProfiles.salesperson;

  hierarchySummary.innerHTML = `
    <div class="summary-pill"><strong>Visible Data:</strong> ${profile.visibleData}</div>
    <div class="summary-pill"><strong>Territory Scope:</strong> ${profile.territory}</div>
  `;

  subordinateList.innerHTML = profile.subordinates.map((entry) => `<li>${entry}</li>`).join('');
}

mapNodes.forEach((node) => {
  node.addEventListener('click', () => {
    mapNodes.forEach((n) => n.classList.remove('active'));
    node.classList.add('active');
    selectedTerritory = node.dataset.territory || selectedTerritory;
    renderTerritoryDetail();
  });
});

roleScope?.addEventListener('change', renderHierarchyScope);
document.getElementById('territory-filter')?.addEventListener('change', renderHierarchyScope);

renderSalespersonView();
renderSuggestions();
renderRetailerSuggestions();
renderHierarchyScope();
renderTerritoryDetail();
