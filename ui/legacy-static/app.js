const roleButtons = document.querySelectorAll("[data-role-btn]");
const roleName = document.getElementById("role-name");
const navItems = document.querySelectorAll(".nav-item");
const apiBaseInput = document.getElementById("api-base");
const authStatus = document.getElementById("auth-status");
const apiStatus = document.getElementById("api-status");
const loginBtn = document.getElementById("login-btn");
const logoutBtn = document.getElementById("logout-btn");
const pingBtn = document.getElementById("ping-btn");
const loadCategoriesBtn = document.getElementById("load-categories");
const loadVendorsBtn = document.getElementById("load-vendors");
const loadItemsBtn = document.getElementById("load-items");
const createOrderBtn = document.getElementById("create-order");
const createInvoiceBtn = document.getElementById("create-invoice");
const assignVendorBtn = document.getElementById("assign-vendor");

const categoriesList = document.getElementById("categories-list");
const vendorsList = document.getElementById("vendors-list");
const itemsList = document.getElementById("items-list");
const orderResponse = document.getElementById("order-response");
const invoiceResponse = document.getElementById("invoice-response");
const assignResponse = document.getElementById("assign-response");

const authUsername = document.getElementById("auth-username");
const authPassword = document.getElementById("auth-password");
const orderJson = document.getElementById("order-json");
const invoiceJson = document.getElementById("invoice-json");
const assignOrderId = document.getElementById("assign-order-id");
const assignJson = document.getElementById("assign-json");

let accessToken = "";

roleButtons.forEach((button) => {
  button.addEventListener("click", () => {
    const role = button.getAttribute("data-role-btn");
    document.body.setAttribute("data-role", role);
    roleButtons.forEach((btn) => btn.classList.remove("active"));
    button.classList.add("active");
    roleName.textContent = role.charAt(0).toUpperCase() + role.slice(1);
  });
});

navItems.forEach((item) => {
  item.addEventListener("click", () => {
    navItems.forEach((link) => link.classList.remove("active"));
    item.classList.add("active");
  });
});

const setStatus = (element, message, isError = false) => {
  element.textContent = message;
  element.style.color = isError ? "#8b2a2a" : "";
};

const getApiBase = () => (apiBaseInput?.value || "").trim().replace(/\/$/, "");

const apiFetch = async (path, options = {}) => {
  const base = getApiBase();
  if (!base) {
    throw new Error("Set the API base URL first.");
  }
  const headers = new Headers(options.headers || {});
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }
  if (!headers.has("Content-Type") && options.body) {
    headers.set("Content-Type", "application/json");
  }
  const response = await fetch(`${base}${path}`, {
    ...options,
    headers,
  });
  const payload = await response.json();
  if (!response.ok) {
    const message = payload?.message || "Request failed";
    throw new Error(message);
  }
  return payload;
};

const renderList = (listElement, items, labelKey = "name") => {
  listElement.innerHTML = "";
  if (!Array.isArray(items) || items.length === 0) {
    const empty = document.createElement("li");
    empty.textContent = "No data yet.";
    listElement.appendChild(empty);
    return;
  }
  items.slice(0, 6).forEach((item) => {
    const li = document.createElement("li");
    const label = document.createElement("span");
    const value = document.createElement("span");
    label.textContent = item[labelKey] || item.name || item.item_name || item.category || "Unknown";
    value.textContent = item.status || item.supplier_name || item.item_code || item.variant_of || "";
    li.append(label, value);
    listElement.appendChild(li);
  });
};

loginBtn?.addEventListener("click", async () => {
  try {
    setStatus(authStatus, "Signing in...");
    const payload = await apiFetch("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({
        username: authUsername.value.trim(),
        password: authPassword.value,
      }),
    });
    accessToken = payload.accessToken || "";
    setStatus(authStatus, accessToken ? "Connected and authenticated." : "Logged in, no token returned.");
  } catch (error) {
    setStatus(authStatus, error.message, true);
  }
});

logoutBtn?.addEventListener("click", () => {
  accessToken = "";
  setStatus(authStatus, "Logged out.");
});

pingBtn?.addEventListener("click", async () => {
  try {
    setStatus(apiStatus, "Pinging...");
    await apiFetch("/api/categories");
    setStatus(apiStatus, "API reachable.");
  } catch (error) {
    setStatus(apiStatus, error.message, true);
  }
});

loadCategoriesBtn?.addEventListener("click", async () => {
  try {
    const data = await apiFetch("/api/categories");
    renderList(categoriesList, data, "category_name");
  } catch (error) {
    renderList(categoriesList, []);
    setStatus(apiStatus, error.message, true);
  }
});

loadVendorsBtn?.addEventListener("click", async () => {
  try {
    const data = await apiFetch("/api/vendors");
    renderList(vendorsList, data, "supplier_name");
  } catch (error) {
    renderList(vendorsList, []);
    setStatus(apiStatus, error.message, true);
  }
});

loadItemsBtn?.addEventListener("click", async () => {
  try {
    const data = await apiFetch("/api/items");
    renderList(itemsList, data, "item_name");
  } catch (error) {
    renderList(itemsList, []);
    setStatus(apiStatus, error.message, true);
  }
});

createOrderBtn?.addEventListener("click", async () => {
  try {
    orderResponse.textContent = "Sending order...";
    const body = JSON.parse(orderJson.value || "{}");
    const data = await apiFetch("/api/orders", {
      method: "POST",
      body: JSON.stringify(body),
    });
    orderResponse.textContent = JSON.stringify(data, null, 2);
  } catch (error) {
    orderResponse.textContent = error.message;
  }
});

createInvoiceBtn?.addEventListener("click", async () => {
  try {
    invoiceResponse.textContent = "Sending invoice...";
    const body = JSON.parse(invoiceJson.value || "{}");
    const data = await apiFetch("/api/invoices", {
      method: "POST",
      body: JSON.stringify(body),
    });
    invoiceResponse.textContent = JSON.stringify(data, null, 2);
  } catch (error) {
    invoiceResponse.textContent = error.message;
  }
});

assignVendorBtn?.addEventListener("click", async () => {
  try {
    assignResponse.textContent = "Assigning vendor...";
    const id = assignOrderId.value.trim();
    const body = JSON.parse(assignJson.value || "{}");
    const data = await apiFetch(`/api/orders/${id}/assign-vendor`, {
      method: "POST",
      body: JSON.stringify(body),
    });
    assignResponse.textContent = JSON.stringify(data, null, 2);
  } catch (error) {
    assignResponse.textContent = error.message;
  }
});
