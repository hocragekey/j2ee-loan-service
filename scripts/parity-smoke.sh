#!/usr/bin/env bash
# Black-box parity transcript for the legacy JSP/servlet endpoints.
# Usage: scripts/parity-smoke.sh [base-url] > transcript.txt
# Run against the legacy build and the new build, then diff the two transcripts.
# Generated ids (LA<millis>), dates and session cookies are normalized.
set -euo pipefail
BASE="${1:-http://localhost:8080}"
CTX="$BASE/loan-service"

req() {
  local label="$1"; shift
  echo "=== $label"
  curl -sS -i "$@" \
    | tr -d '\r' \
    | grep -v -i -E '^(date|content-length|keep-alive|connection|set-cookie):' \
    | sed -E 's/LA[0-9]{13}/LA<id>/g; s/EL[0-9]{13}/EL<id>/g; s/;jsessionid=[A-Za-z0-9]+//g; s/[0-9]{4}-[0-9]{2}-[0-9]{2}/<date>/g'
  echo
}

form='customerId=C10001&productCode=AUTO&amount=20000&termMonths=48&collateralType=AUTO&collateralValue=28000'
req "GET context root (no slash)" "$CTX"
req "GET /" "$CTX/"
req "GET /index.jsp" "$CTX/index.jsp"
req "GET /apply" "$CTX/apply"
req "GET /eligibility" "$CTX/eligibility"
for c in C10001 C10002 C10003 C10004 C99999; do
  req "POST /eligibility $c" -X POST -d "${form/C10001/$c}" "$CTX/eligibility"
done
req "POST /eligibility missing customer" -X POST -d 'productCode=AUTO' "$CTX/eligibility"
req "POST /eligibility bad term" -X POST -d "${form/termMonths=48/termMonths=forty}" "$CTX/eligibility"
req "POST /eligibility xss customer" -X POST --data-urlencode 'customerId=<script>x</script>' -d 'productCode=AUTO&amount=1&termMonths=12' "$CTX/eligibility"
req "POST /apply C10001 AUTO" -X POST -d "$form&purposeCode=PURCHASE" "$CTX/apply"
req "POST /apply C10003 PERSONAL" -X POST -d 'customerId=C10003&productCode=PERSONAL&amount=10000&termMonths=36&collateralType=NONE&collateralValue=0' "$CTX/apply"
req "POST /apply C10004 watchlist" -X POST -d "${form/C10001/C10004}" "$CTX/apply"
req "POST /apply bad amount" -X POST -d "${form/amount=20000/amount=lots}" "$CTX/apply"
req "GET /customer C10001" "$CTX/customer?customerId=C10001"
req "GET /customer unknown" "$CTX/customer?customerId=C99999"
req "GET /customer xss" "$CTX/customer?customerId=%3Cscript%3E"
req "GET /underwrite unknown" "$CTX/underwrite?applId=LA123"
req "GET /underwrite missing" "$CTX/underwrite"
req "GET /admin/reload (removed)" "$CTX/admin/reload"
req "GET /health (not yet implemented)" "$CTX/health"
