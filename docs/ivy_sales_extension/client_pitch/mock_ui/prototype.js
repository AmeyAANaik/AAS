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

const roleScope = document.getElementById('role-scope');
const hierarchySummary = document.getElementById('hierarchy-summary');
const subordinateList = document.getElementById('subordinate-list');

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

roleScope?.addEventListener('change', renderHierarchyScope);
document.getElementById('territory-filter')?.addEventListener('change', renderHierarchyScope);

renderSuggestions();
renderRetailerSuggestions();
renderHierarchyScope();
