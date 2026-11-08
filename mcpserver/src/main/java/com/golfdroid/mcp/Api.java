package com.golfdroid.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
/**
 * MCP-over-HTTP JSON handler for the GolfDroid diagnostics server.
 *
 * <p>Routes:
 * <ul>
 *   <li>GET /health              -> 200 {"status":"ok","connected":bool} (no token)</li>
 *   <li>POST /tools/get_vehicle_info   -> 200 {...} (token required)</li>
 *   <li>POST /tools/run_full_scan      -> 200 {"scan_id","status":"started"}</li>
 *   <li>POST /tools/get_scan_results   -> 200 {"status","dtc","live","elapsed_s"}</li>
 *   <li>POST /tools/get_diagnostics    -> 200 {"dtc":[{"code","desc"}],"mil","ready_monitors"}</li>
 *   <li>POST /tools/clear_fault_codes  -> 200 {"cleared":true} only when body {"confirm":true}</li>
 * </ul>
 *
 * <p>Every /tools/* endpoint requires a valid bearer token (Authorization
 * header or ?token= query param). Missing/wrong token -> 401.
 * clear_fault_codes without confirm:true -> 403.
 */
public class Api extends NanoHTTPD {

    private final String token;
    private final ObdBridge bridge;
    private final ScanRegistry registry = new ScanRegistry();

    /** Max time a scan may run before being reported as timed out (ms). */
    private static final long SCAN_TIMEOUT_MS = 90_000;

    public Api(int port, String token, ObdBridge bridge) {
        super(port);
        this.token = token;
        this.bridge = bridge;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String method = session.getMethod().toString();
        String uri = session.getUri();
        Map<String, String> parms = session.getParms();

        try {
            // --- health: no token required, no vehicle data ---
            if ("GET".equals(method) && "/health".equals(uri)) {
                boolean connected = bridge.isConnected();
                return json(200, "{\"status\":\"ok\",\"connected\":"
                    + connected + "}");
            }

            // --- token gate for all /tools/* ---
            String provided = session.getHeaders().get("authorization");
            if (provided == null) {
                provided = session.getHeaders().get("Authorization");
            }
            if (provided == null && parms.containsKey("token")) {
                provided = "Bearer " + parms.get("token");
            }
            if (!tokenEquals(provided)) {
                return json(401, "{\"error\":\"unauthorized\"}");
            }

            if ("POST".equals(method) && uri.startsWith("/tools/")) {
                String body = readBody(session);
                String tool = uri.substring("/tools/".length());

                switch (tool) {
                    case "get_vehicle_info":
                        return handleVehicleInfo();
                    case "run_full_scan":
                        return handleRunFullScan();
                    case "get_scan_results":
                        return handleGetScanResults(body);
                    case "get_diagnostics":
                        return handleGetDiagnostics();
                    case "clear_fault_codes":
                        return handleClearFaultCodes(body);
                    default:
                        return json(404, "{\"error\":\"unknown_tool\"}");
                }
            }

            return json(404, "{\"error\":\"not_found\"}");
        } catch (Exception e) {
            return json(500, "{\"error\":\"internal\",\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    /* ---------------- token ---------------- */

    private boolean tokenEquals(String provided) {
        if (provided == null) return false;
        String t = provided.trim();
        if (t.startsWith("Bearer ")) t = t.substring(7).trim();
        return token != null && token.equals(t);
    }

    /* ---------------- body ---------------- */

    private String readBody(IHTTPSession session) throws IOException {
        Map<String, String> files = new java.util.HashMap<>();
        // body is in parms for POST form; for raw JSON we read the input stream
        long size = -1;
        String cl = session.getHeaders().get("content-length");
        if (cl != null) {
            try { size = Long.parseLong(cl.trim()); } catch (NumberFormatException ignored) {}
        }
        if (size > 0) {
            byte[] buf = new byte[(int) Math.min(size, 65536)];
            java.io.InputStream in = session.getInputStream();
            int total = 0;
            while (total < buf.length) {
                int r = in.read(buf, total, buf.length - total);
                if (r < 0) break;
                total += r;
            }
            return new String(buf, 0, total, StandardCharsets.UTF_8);
        }
        return "";
    }

    /* ---------------- tool handlers ---------------- */

    private Response handleVehicleInfo() {
        ObdBridge.Diagnostics diag = bridge.getDiagnostics();
        String pidList = arrayJson(bridge.getSupportedPids());
        return json(200, "{"
            + "\"vin\":\"" + esc(bridge.getVin()) + "\","
            + "\"protocol\":\"" + esc(bridge.getProtocolName()) + "\","
            + "\"adapter\":\"" + esc(bridge.getAdapterType()) + "\","
            + "\"supported_pids\":" + pidList
            + "}");
    }

    private Response handleRunFullScan() {
        if (registry.hasRunning()) {
            String id = registry.getRunningId();
            return json(409, "{\"error\":\"scan_in_progress\",\"scan_id\":\"" + esc(id) + "\"}");
        }
        String id = registry.create();
        bridge.startScan(id);
        return json(200, "{\"scan_id\":\"" + esc(id) + "\",\"status\":\"started\"}");
    }

    private Response handleGetScanResults(String body) {
        String id = jsonString(body, "scan_id");
        ScanRegistry.Entry entry = registry.get(id);
        if (entry == null || entry.result == null) {
            return json(404, "{\"status\":\"unknown\",\"scan_id\":\"" + esc(id) + "\"}");
        }
        long elapsed = (System.currentTimeMillis() - entry.createdAt) / 1000;
        ObdBridge.ScanResult r = entry.result;
        if ("done".equals(r.status) || "error".equals(r.status)) {
            return json(200, scanResultJson(r.status, r.dtc, r.live, r.elapsedS, r.reason));
        }
        // still running
        if (System.currentTimeMillis() - entry.createdAt > SCAN_TIMEOUT_MS) {
            return json(200, scanResultJson("error", Collections.emptyList(),
                Collections.emptyList(), elapsed, "timeout"));
        }
        return json(200, scanResultJson("running", Collections.emptyList(),
            Collections.emptyList(), elapsed, null));
    }

    private Response handleGetDiagnostics() {
        ObdBridge.Diagnostics diag = bridge.getDiagnostics();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"dtc\":[");
        boolean first = true;
        for (ObdBridge.Dtc d : diag.dtc) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"code\":\"").append(esc(d.code)).append("\",")
              .append("\"desc\":\"").append(esc(d.desc)).append("\"}");
        }
        sb.append("],\"mil\":").append(diag.mil).append(",\"ready_monitors\":{");
        first = true;
        for (Map.Entry<String, Boolean> e : diag.readyMonitors.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(esc(e.getKey())).append("\":").append(e.getValue());
        }
        sb.append("}}");
        return json(200, sb.toString());
    }

    private Response handleClearFaultCodes(String body) {
        boolean confirm = jsonBool(body, "confirm");
        if (!confirm) {
            return json(403, "{\"error\":\"confirmation_required\"}");
        }
        boolean ok = bridge.clearFaultCodes();
        return json(200, "{\"cleared\":" + ok + "}");
    }

    /* ---------------- json helpers ---------------- */

    private Response json(int status, String body) {
        return newFixedLengthResponse(Status.lookup(status),
            "application/json; charset=utf-8", body);
    }

    private String arrayJson(int[] pids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < pids.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("0x%02X", pids[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    private String scanResultJson(String status, java.util.List<ObdBridge.Dtc> dtc,
                                  java.util.List<String> live, long elapsedS,
                                  String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"status\":\"").append(status).append("\",");
        sb.append("\"dtc\":[");
        boolean first = true;
        for (ObdBridge.Dtc d : dtc) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"code\":\"").append(esc(d.code)).append("\",")
              .append("\"desc\":\"").append(esc(d.desc)).append("\"}");
        }
        sb.append("],\"live\":[");
        first = true;
        for (String s : live) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(esc(s)).append("\"");
        }
        sb.append("],\"elapsed_s\":").append(elapsedS);
        if (reason != null) sb.append(",\"reason\":\"").append(esc(reason)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private String jsonString(String body, String key) {
        // minimal extract: "key":"value"
        String pat = "\"" + key + "\"";
        int i = body.indexOf(pat);
        if (i < 0) return "";
        int j = body.indexOf(':', i + pat.length());
        if (j < 0) return "";
        int k = body.indexOf('"', j + 1);
        if (k < 0) return "";
        int end = body.indexOf('"', k + 1);
        if (end < 0) return "";
        return body.substring(k + 1, end);
    }

    private boolean jsonBool(String body, String key) {
        String pat = "\"" + key + "\"";
        int i = body.indexOf(pat);
        if (i < 0) return false;
        int j = body.indexOf(':', i + pat.length());
        if (j < 0) return false;
        int k = j + 1;
        while (k < body.length() && Character.isWhitespace(body.charAt(k))) k++;
        return k < body.length() && body.charAt(k) == 't';
    }

    private String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    /** Expose entries for internal use (409 scan_in_progress lookup). */
    Map<String, ScanRegistry.Entry> entries() { return registry.entries(); }
}
