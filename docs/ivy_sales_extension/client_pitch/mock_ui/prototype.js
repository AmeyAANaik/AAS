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

const menuItems = document.querySelectorAll('.menu-item');
menuItems.forEach((item) => {
  item.addEventListener('click', () => {
    menuItems.forEach((m) => m.classList.remove('active'));
    item.classList.add('active');
  });
});

const suggestions = [
  {
    sku: 'IVY-LASSI-200ML',
    type: 'replenishment',
    segment: 'gold',
    reason: 'Outlet reorder cycle reached (14 days).',
    confidence: 86,
    qty: 24,
  },
  {
    sku: 'IVY-GHEE-500ML',
    type: 'cross-sell',
    segment: 'silver',
    reason: 'High conversion with milk + curd basket.',
    confidence: 72,
    qty: 6,
  },
  {
    sku: 'IVY-BUTTERMILK-180ML',
    type: 'scheme',
    segment: 'platinum',
    reason: 'Active summer scheme unlocks margin uplift.',
    confidence: 79,
    qty: 30,
  },
  {
    sku: 'IVY-PANEER-200G',
    type: 'cross-sell',
    segment: 'gold',
    reason: 'Demand trend up 18% in nearby outlets.',
    confidence: 74,
    qty: 10,
  },
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
  retailerBox.innerHTML = retailerRows
    .map((s) => `
      <article class="suggestion-item">
        <div>
          <strong>${s.sku}</strong>
          <p>${s.reason}</p>
        </div>
        <div class="suggestion-meta">
          <span class="badge blue">${s.type}</span>
          <span class="badge amber">${s.confidence}%</span>
        </div>
        <div class="suggestion-actions">
          <button>Add to Cart (${s.qty})</button>
        </div>
      </article>
    `)
    .join('');
}

document.getElementById('retailer-refresh')?.addEventListener('click', renderRetailerSuggestions);

renderSuggestions();
renderRetailerSuggestions();
