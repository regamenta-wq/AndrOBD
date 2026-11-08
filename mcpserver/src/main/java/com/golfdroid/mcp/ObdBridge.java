package com.golfdroid.mcp;

import java.util.Collections;
import java.util.List;

/**
 * Bridge between the MCP HTTP API and the AndrOBD OBD-II stack (ElmProt,
 * CommService, DTC lists). Implemented by the Android app (MainActivity or a
 * dedicated bridge class) so :mcpserver stays a pure Java module without
 * Android dependencies.
 */
public interface ObdBridge {

    boolean isConnected();

    /** VIN of the connected vehicle, or empty string if unknown. */
    String getVin();

    /** Human-readable active protocol name, e.g. "ISO 9141-2" or "KWP2000".
     *  Never hardcoded — read from ElmProt status. */
    String getProtocolName();

    /** Adapter type label: "BT" | "WiFi" | "USB" | "demo". */
    String getAdapterType();

    /** Supported PIDs as hex ints, or empty array if unknown. */
    int[] getSupportedPids();

    /** Start a full OBD-II scan identified by {@code scanId}. Bridge must write
     *  results into ScanRegistry when the scan finishes. */
    void startScan(String scanId);

    boolean isScanRunning();

    ScanResult getScanResult(String scanId);

    Diagnostics getDiagnostics();

    /** Clear DTCs from the ECU (Mode 04). Returns true on success. */
    boolean clearFaultCodes();

    /* ---------- data classes ---------- */

    final class Dtc {
        public final String code;
        public final String desc;
        public Dtc(String code, String desc) { this.code = code; this.desc = desc; }
    }

    final class Diagnostics {
        public final List<Dtc> dtc;
        public final boolean mil;
        public final java.util.Map<String,Boolean> readyMonitors;
        public Diagnostics(List<Dtc> dtc, boolean mil,
                           java.util.Map<String,Boolean> readyMonitors) {
            this.dtc = dtc == null ? Collections.emptyList() : dtc;
            this.mil = mil;
            this.readyMonitors = readyMonitors == null
                ? Collections.emptyMap() : readyMonitors;
        }
    }

    final class ScanResult {
        public final String status;   // "running" | "done" | "error" | "unknown"
        public final List<Dtc> dtc;
        public final List<String> live;
        public final long elapsedS;
        public final String reason;   // set on error/timeout

        public static ScanResult running(long elapsedS) {
            return new ScanResult("running", Collections.emptyList(),
                Collections.emptyList(), elapsedS, null);
        }

        public static ScanResult done(List<Dtc> dtc, List<String> live,
                                      long elapsedS) {
            return new ScanResult("done", dtc, live, elapsedS, null);
        }

        public static ScanResult error(String reason, long elapsedS) {
            return new ScanResult("error", Collections.emptyList(),
                Collections.emptyList(), elapsedS, reason);
        }

        public static ScanResult unknown(long elapsedS) {
            return new ScanResult("unknown", Collections.emptyList(),
                Collections.emptyList(), elapsedS, null);
        }

        private ScanResult(String status, List<Dtc> dtc, List<String> live,
                           long elapsedS, String reason) {
            this.status = status;
            this.dtc = dtc == null ? Collections.emptyList() : dtc;
            this.live = live == null ? Collections.emptyList() : live;
            this.elapsedS = elapsedS;
            this.reason = reason;
        }
    }

}
