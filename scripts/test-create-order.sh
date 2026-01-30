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
  DATA='{"fields":{"customer":"Shop A","company":"AAS Core","transaction_date":"2026-01-30","delivery_date":"2026-02-05","items":[{"item_code":"BX001","qty":1,"rate":13.2,"aas_vendor_rate":12,"aas_margin_percent":10}]}}'
fi

curl -i "${BASE_URL%/}/api/orders" \
  -H 'accept: application/json, text/plain, */*' \
  -H "authorization: Bearer ${TOKEN}" \
  -H 'content-type: application/json' \
  --data-raw "$DATA"
