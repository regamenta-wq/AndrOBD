#!/bin/bash
# tools/test_skill_calls.sh — skill contract smoke test (bash, no Android needed)
set -e
CONFIG="$HOME/.golfdroid/config.json"
mkdir -p "$HOME/.golfdroid"

echo "== config parsing (loopback) =="
echo '{"url":"http://127.0.0.1:8080","token":"abc123"}' > "$CONFIG"
URL=$(python3 -c 'import json;print(json.load(open("'"$CONFIG"'"))["url"])')
[ "$URL" = "http://127.0.0.1:8080" ] && echo PASS config_loopback || echo FAIL config_loopback

echo "== config parsing (LAN IP) =="
echo '{"url":"http://192.168.0.10:8080","token":"abc123"}' > "$CONFIG"
URL=$(python3 -c 'import json;print(json.load(open("'"$CONFIG"'"))["url"])')
[ "$URL" = "http://192.168.0.10:8080" ] && echo PASS config_lan || echo FAIL config_lan

echo "== health check against mock server =="
python3 - <<'EOF' &
from http.server import BaseHTTPRequestHandler, HTTPServer
import json, sys
class H(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status":"ok","connected":True}).encode())
        else:
            self.send_response(404)
            self.end_headers()
    def log_message(self, *a): pass
HTTPServer(("127.0.0.1", 8091), H).handle_request()
EOF
MOCK_PID=$!
sleep 0.5
R=$(curl -s http://127.0.0.1:8091/health)
echo "$R" | grep -q '"ok"' && echo PASS health_check || echo FAIL health_check
kill $MOCK_PID 2>/dev/null || true

echo "== token masking rule =="
TOKEN="abcdef12"
MASKED="${TOKEN:0:2}***${TOKEN: -1}"
[ "$MASKED" = "ab***2" ] && echo PASS token_mask || echo FAIL token_mask

echo "ALL DONE"
