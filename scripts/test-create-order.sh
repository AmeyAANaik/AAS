#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-}"
TOKEN="${TOKEN:-}"
PAYLOAD_FILE="${PAYLOAD_FILE:-}"

if [[ -z "$BASE_URL" ]]; then
  echo "BASE_URL is required (e.g. https://your-app.app.github.dev)"
  exit 1
fi

if [[ -z "$TOKEN" ]]; then
  echo "TOKEN is required (set as env var to avoid pasting in chat)"
  exit 1
fi

if [[ -n "$PAYLOAD_FILE" ]]; then
  if [[ ! -f "$PAYLOAD_FILE" ]]; then
    echo "PAYLOAD_FILE not found: $PAYLOAD_FILE"
    exit 1
  fi
  DATA="$(cat "$PAYLOAD_FILE")"
else
  DATA='{"fields":{"customer":"Sahyadri All-Day Dining","company":"Hotel Sahyadri Pune Pvt Ltd","transaction_date":"2026-03-02","delivery_date":"2026-03-03","aas_category":"Grocery","items":[{"item_code":"FRESHHARVEST_GROCERY_11010000","qty":2,"rate":1484,"aas_vendor_rate":1325,"aas_margin_percent":12}]}}'
fi

curl -i "${BASE_URL%/}/api/orders" \
  -H 'accept: application/json, text/plain, */*' \
  -H "authorization: Bearer ${TOKEN}" \
  -H 'content-type: application/json' \
  --data-raw "$DATA"
