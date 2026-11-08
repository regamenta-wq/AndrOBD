package com.golfdroid.mcp;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ApiTest {

    static final String TOKEN = "abc123"; // 6 char
    static Api server;

    // Mock ObdBridge — bez Androida, bez ELM-a
    static class MockBridge implements ObdBridge {
        public boolean isConnected() { return true; }
        public String getVin() { return "WVWZZZ1JZ2W123456"; }
        public String getProtocolName() { return "ISO 9141-2"; }
        public String getAdapterType() { return "demo"; }
        public int[] getSupportedPids() { return new int[]{0x04, 0x0C}; }
        public void startScan(String scanId) { }
        public boolean isScanRunning() { return false; }
        public ObdBridge.ScanResult getScanResult(String scanId) {
            return ObdBridge.ScanResult.done(Collections.emptyList(),
                Collections.emptyList(), 0);
        }
        public ObdBridge.Diagnostics getDiagnostics() {
            Map<String,Boolean> monitors = new HashMap<>();
            monitors.put("misfire", true);
            monitors.put("fuel", false);
            return new ObdBridge.Diagnostics(
                java.util.Collections.singletonList(
                    new ObdBridge.Dtc("P0100", "MAF Circuit")), false, monitors);
        }
        public boolean clearFaultCodes() { return true; }
    }

    @Before public void setUp() throws Exception {
        server = new Api(8081, TOKEN, new MockBridge());
        server.start(1000);
    }
    @After public void tearDown() {
        server.stop();
    }

    static String http(String method, String path, String token, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8081" + path).openConnection();
        c.setRequestMethod(method);
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            c.setDoOutput(true);
            c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        return code + "|" + (c.getErrorStream() != null
            ? new String(c.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)
            : new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test public void healthWithoutTokenIsOkAndSilent() throws Exception {
        String r = http("GET", "/health", null, null);
        assertTrue(r.startsWith("200|"));
        assertFalse(r.contains("vin"));
    }

    @Test public void vehicleInfoWithoutTokenIs401() throws Exception {
        assertTrue(http("POST", "/tools/get_vehicle_info", null, "{}").startsWith("401|"));
    }

    @Test public void vehicleInfoWithBadTokenIs401() throws Exception {
        assertTrue(http("POST", "/tools/get_vehicle_info", "XXXXXX", "{}").startsWith("401|"));
    }

    @Test public void vehicleInfoWithTokenReturnsVinAndProtocol() throws Exception {
        String r = http("POST", "/tools/get_vehicle_info", TOKEN, "{}");
        assertTrue(r.startsWith("200|"));
        assertTrue(r.contains("WVWZZZ1JZ2W123456"));
        assertTrue(r.contains("ISO 9141-2"));
        assertTrue(r.contains("\"supported_pids\":[0x04,0x0C]"));
    }

    @Test public void runScanReturnsScanId() throws Exception {
        String r = http("POST", "/tools/run_full_scan", TOKEN, "{}");
        assertTrue(r.startsWith("200|"));
        assertTrue(r.contains("scan_id"));
    }

    @Test public void scanResultsUnknownIdIsUnknown() throws Exception {
        String r = http("POST", "/tools/get_scan_results", TOKEN, "{\"scan_id\":\"nope\"}");
        assertTrue(r.contains("\"unknown\""));
    }

    @Test public void clearWithoutConfirmIs403() throws Exception {
        assertTrue(http("POST", "/tools/clear_fault_codes", TOKEN, "{}").startsWith("403|"));
        assertTrue(http("POST", "/tools/clear_fault_codes", TOKEN,
            "{\"confirm\":false}").startsWith("403|"));
    }

    @Test public void clearWithConfirmIs200() throws Exception {
        assertTrue(http("POST", "/tools/clear_fault_codes", TOKEN,
            "{\"confirm\":true}").startsWith("200|"));
    }

    // Review Focus #4: protokol dolazi iz bridge-a, nije hardkodiran
    @Test public void protocolComesFromBridgeNotHardcoded() throws Exception {
        Api s2 = new Api(8082, TOKEN, new FakeBridge());
        s2.start(1000);
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8082/tools/get_vehicle_info").openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Authorization", "Bearer " + TOKEN);
            c.setDoOutput(true);
            c.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
            String body = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("14230-4"));
            assertFalse(body.contains("ISO 9141-2"));
        } finally {
            s2.stop();
        }
    }

    static class FakeBridge implements ObdBridge {
        public boolean isConnected() { return true; }
        public String getVin() { return "WVWZZZ1JZ2W999999"; }
        public String getProtocolName() { return "ISO 14230-4 KWP (fast Init)"; }
        public String getAdapterType() { return "BT"; }
        public int[] getSupportedPids() { return new int[0]; }
        public void startScan(String scanId) { }
        public boolean isScanRunning() { return false; }
        public ObdBridge.ScanResult getScanResult(String scanId) {
            return ObdBridge.ScanResult.unknown(0);
        }
        public ObdBridge.Diagnostics getDiagnostics() {
            return new ObdBridge.Diagnostics(Collections.emptyList(), false, null);
        }
        public boolean clearFaultCodes() { return false; }
    }
}
