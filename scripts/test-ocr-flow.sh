#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-}"
TOKEN="${TOKEN:-}"
USERNAME="${USERNAME:-}"
PASSWORD="${PASSWORD:-}"
CUSTOMER="${CUSTOMER:-Shop A}"
COMPANY="${COMPANY:-AAS Core}"
VENDOR="${VENDOR:-Vendor A}"
BRANCH_IMAGE="${BRANCH_IMAGE:-images/branch_order.jpeg}"
VENDOR_PDF="${VENDOR_PDF:-images/vendor_order.pdf}"
TRANSACTION_DATE="${TRANSACTION_DATE:-}"
DELIVERY_DATE="${DELIVERY_DATE:-}"

if [[ -z "$BASE_URL" ]]; then
  echo "BASE_URL is required (e.g. http://localhost:8083)"
  exit 1
fi

if [[ ! -f "$BRANCH_IMAGE" ]]; then
  echo "Branch image not found: $BRANCH_IMAGE"
  exit 1
fi

if [[ ! -f "$VENDOR_PDF" ]]; then
  echo "Vendor PDF not found: $VENDOR_PDF"
  exit 1
fi

if [[ -z "$TOKEN" ]]; then
  if [[ -z "$USERNAME" || -z "$PASSWORD" ]]; then
    echo "TOKEN or USERNAME/PASSWORD are required."
    exit 1
  fi
  TOKEN="$(curl -sS "${BASE_URL%/}/api/auth/login" \
    -H 'content-type: application/json' \
    --data-raw "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')"
fi

FORM_ARGS=(-F "file=@${BRANCH_IMAGE}" -F "customer=${CUSTOMER}" -F "company=${COMPANY}")
if [[ -n "$TRANSACTION_DATE" ]]; then
  FORM_ARGS+=(-F "transaction_date=${TRANSACTION_DATE}")
fi
if [[ -n "$DELIVERY_DATE" ]]; then
  FORM_ARGS+=(-F "delivery_date=${DELIVERY_DATE}")
fi

ORDER_RESPONSE="$(curl -sS "${BASE_URL%/}/api/orders/branch-image" \
  -H "authorization: Bearer ${TOKEN}" \
  "${FORM_ARGS[@]}")"

ORDER_ID="$(echo "$ORDER_RESPONSE" | python3 -c 'import json,sys; data=json.load(sys.stdin); print(data["name"] if isinstance(data, dict) and "name" in data else (data.get("data", {}).get("name", "") if isinstance(data, dict) else ""))')"

if [[ -z "$ORDER_ID" ]]; then
  echo "Unable to resolve order id from response:"
  echo "$ORDER_RESPONSE"
  exit 1
fi

curl -sS "${BASE_URL%/}/api/orders/${ORDER_ID}/assign-vendor" \
  -H "authorization: Bearer ${TOKEN}" \
  -H 'content-type: application/json' \
  --data-raw "{\"fields\":{\"aas_vendor\":\"${VENDOR}\"}}"

VENDOR_PDF_RESPONSE="$(curl -sS "${BASE_URL%/}/api/orders/${ORDER_ID}/vendor-pdf" \
  -H "authorization: Bearer ${TOKEN}" \
  -F "file=@${VENDOR_PDF}")"

echo
echo "Order ID: ${ORDER_ID}"
echo "Vendor PDF Response:"
echo "$VENDOR_PDF_RESPONSE"
