#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8083}"
USERNAME="${USERNAME:-Administrator}"
PASSWORD="${PASSWORD:-admin}"

BRANCH_IMAGE="${BRANCH_IMAGE:-images/branch_order_sahyadri.svg}"
VENDOR_PDF="${VENDOR_PDF:-images/vendor_invoice_freshharvest.pdf}"

CUSTOMER="${CUSTOMER:-Sahyadri All-Day Dining}"
COMPANY="${COMPANY:-Hotel Sahyadri Pune Pvt Ltd}"
VENDOR="${VENDOR:-FreshHarvest Agro Foods}"

VENDOR_BILL_TOTAL="${VENDOR_BILL_TOTAL:-500}"
VENDOR_BILL_REF="${VENDOR_BILL_REF:-VB-CLEAN-001}"
VENDOR_BILL_DATE="${VENDOR_BILL_DATE:-2026-02-19}"
MARGIN_PERCENT="${MARGIN_PERCENT:-10}"

CLEAN_ITEMS_JSON="${CLEAN_ITEMS_JSON:-[
  {\"item_code\":\"VEG-POT-50\",\"qty\":3,\"rate\":1484,\"amount\":4452,\"aas_vendor_rate\":1325},
  {\"item_code\":\"VEG-ONI-50\",\"qty\":2,\"rate\":1672.4,\"amount\":3344.8,\"aas_vendor_rate\":1480},
  {\"item_code\":\"DRY-RIC-25\",\"qty\":2,\"rate\":2686.2,\"amount\":5372.4,\"aas_vendor_rate\":2420}
]}"

if [[ ! -f "$BRANCH_IMAGE" ]]; then
  echo "Branch image not found: $BRANCH_IMAGE"
  exit 1
fi

if [[ ! -f "$VENDOR_PDF" ]]; then
  echo "Vendor PDF not found: $VENDOR_PDF"
  exit 1
fi

echo "Logging in to MW at $BASE_URL ..."
TOKEN="$(curl -sS "${BASE_URL%/}/api/auth/login" \
  -H 'content-type: application/json' \
  --data "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')"

echo "Ensuring setup defaults ..."
curl -sS -X POST "${BASE_URL%/}/api/setup/ensure" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'content-type: application/json' \
  --data '{}' >/dev/null

echo "Creating order from branch image ..."
ORDER_RESPONSE="$(curl -sS "${BASE_URL%/}/api/orders/branch-image" \
  -H "authorization: Bearer ${TOKEN}" \
  -F "file=@${BRANCH_IMAGE}" \
  -F "customer=${CUSTOMER}" \
  -F "company=${COMPANY}")"

ORDER_ID="$(echo "$ORDER_RESPONSE" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("name") or d.get("data",{}).get("name") or "")')"
if [[ -z "$ORDER_ID" ]]; then
  echo "Failed to create order:"
  echo "$ORDER_RESPONSE"
  exit 1
fi
echo "Order created: $ORDER_ID"

echo "Assigning vendor ..."
curl -sS "${BASE_URL%/}/api/orders/${ORDER_ID}/assign-vendor" \
  -H "authorization: Bearer ${TOKEN}" \
  -H 'content-type: application/json' \
  --data "{\"fields\":{\"aas_vendor\":\"${VENDOR}\"}}" >/dev/null

echo "Uploading vendor PDF ..."
curl -sS "${BASE_URL%/}/api/orders/${ORDER_ID}/vendor-pdf" \
  -H "authorization: Bearer ${TOKEN}" \
  -F "file=@${VENDOR_PDF}" >/dev/null

echo "Applying clean items override ..."
curl -sS "${BASE_URL%/}/api/orders/${ORDER_ID}/status" \
  -H "authorization: Bearer ${TOKEN}" \
  -H 'content-type: application/json' \
  --data "{\"fields\":{\"items\":${CLEAN_ITEMS_JSON},\"aas_margin_percent\":${MARGIN_PERCENT}}}" >/dev/null

echo "Capturing vendor bill ..."
curl -sS "${BASE_URL%/}/api/orders/${ORDER_ID}/vendor-bill" \
  -H "authorization: Bearer ${TOKEN}" \
  -H 'content-type: application/json' \
  --data "{\"fields\":{\"vendor_bill_total\":${VENDOR_BILL_TOTAL},\"vendor_bill_ref\":\"${VENDOR_BILL_REF}\",\"vendor_bill_date\":\"${VENDOR_BILL_DATE}\",\"margin_percent\":${MARGIN_PERCENT}}}" >/dev/null

echo "Creating sell order ..."
curl -sS -X POST "${BASE_URL%/}/api/orders/${ORDER_ID}/sell-order" \
  -H "authorization: Bearer ${TOKEN}" >/dev/null

echo "Fetching final order summary ..."
FINAL_ORDER="$(curl -sS "${BASE_URL%/}/api/orders/${ORDER_ID}" \
  -H "authorization: Bearer ${TOKEN}")"

python3 - <<'PY' "$FINAL_ORDER"
import json,sys
d=json.loads(sys.argv[1]).get("data", {})
print("")
print("Flow completed.")
print(f"Order ID: {d.get('name')}")
print(f"Status: {d.get('aas_status')}")
print(f"Customer: {d.get('customer')}")
print(f"Vendor: {d.get('aas_vendor')}")
print(f"Company: {d.get('company')}")
print(f"Vendor Bill Total: {d.get('aas_vendor_bill_total')}")
print(f"Margin Percent: {d.get('aas_margin_percent')}")
print(f"Purchase Order: {d.get('aas_po')}")
print(f"Purchase Invoice: {d.get('aas_pi_vendor')}")
print(f"Branch Sales Order: {d.get('aas_so_branch')}")
print(f"Branch Sales Invoice: {d.get('aas_si_branch')}")
print(f"Sell Total: {d.get('aas_sell_order_total')}")
items=d.get("items", [])
print(f"Items: {len(items)}")
for i,row in enumerate(items[:5], 1):
    print(f"  {i}. {row.get('item_code')} qty={row.get('qty')} rate={row.get('rate')} amount={row.get('amount')}")
PY
