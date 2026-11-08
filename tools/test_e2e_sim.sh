#!/bin/bash
# tools/test_e2e_sim.sh — curl smoke test protiv GolfDroid MCP servera
# Potreban: GOLFDROID_TOKEN iz app Settings (MCP server token), host:port servera
set -euo pipefail

HOST="${1:-127.0.0.1:8080}"
TOKEN="${GOLFDROID_TOKEN:?export GOLFDROID_TOKEN=<token iz GolfDroid Settings>}"

http() {
    local method="$1" path="$2" body="${3:-}"
    local code
    code=$(curl -s -o /tmp/golfdroid_e2e_body -w '%{http_code}' \
        -X "$method" -H "Authorization: Bearer $TOKEN" \
        -H 'Content-Type: application/json' \
        -d "$body" "http://$HOST$path")
    echo "$code|$(cat /tmp/golfdroid_e2e_body)"
}

echo "== health =="
curl -s "http://$HOST/health" | grep -q '"ok"' && echo PASS health || { echo FAIL health; exit 1; }

echo "== vehicle info =="
R=$(http POST /tools/get_vehicle_info '{}')
echo "$R" | grep -qE 'protocol' && echo PASS vehicle_info || { echo FAIL vehicle_info; echo "$R"; exit 1; }

echo "== full scan =="
R=$(http POST /tools/run_full_scan '{}')
SCAN_ID=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("scan_id",""))')
echo "scan_id=$SCAN_ID"
DONE=0
for i in $(seq 1 30); do
    R=$(http POST /tools/get_scan_results "{\"scan_id\":\"$SCAN_ID\"}")
    echo "$R" | grep -q '"done"' && DONE=1 && break || sleep 3
done
[ "$DONE" = "1" ] && echo PASS scan || { echo FAIL scan; echo "$R"; exit 1; }

echo "== clear without confirm =="
R=$(http POST /tools/clear_fault_codes '{}')
echo "$R" | grep -q '403' && echo PASS clear_403 || { echo FAIL clear_403; echo "$R"; exit 1; }

echo "ALL E2E PASS"
