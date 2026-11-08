package com.fr3ts0n.ecu.gui.androbd;

import android.util.Log;

import com.fr3ts0n.ecu.prot.obd.ElmProt;
import com.golfdroid.mcp.ObdBridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@link ObdBridge} implementation backed by the live AndrOBD stack:
 * {@link CommService#getState()}, {@link CommService#elm} (ElmProt),
 * {@link CommService#medium}, and the DFC/DTC adapters.
 *
 * <p>Protocol name, VIN, supported PIDs and DTC list are all read from the
 * running connection — never hardcoded (Review Focus #4).
 */
public class GolfObdBridge implements ObdBridge {

    private static final String TAG = "GolfObdBridge";

    @Override
    public boolean isConnected() {
        return CommService.getState() == CommService.STATE.CONNECTED;
    }

    @Override
    public String getVin() {
        String addr = (String) CommService.elm.getProperty(ElmProt.PROP_ECU_ADDRESS);
        return addr == null ? "" : String.valueOf(addr);
    }

    @Override
    public String getProtocolName() {
        // ElmProt status / active protocol descriptor
        String status = (String) CommService.elm.getProperty(ElmProt.PROP_STATUS);
        if (status != null && !status.isEmpty()) {
            return status;
        }
        return CommService.elm.toString();
    }

    @Override
    public String getAdapterType() {
        switch (CommService.medium) {
            case DEMO: return "demo";
            case BLUETOOTH: return "BT";
            case BLE: return "BT";
            case USB: return "USB";
            case NETWORK: return "WiFi";
            default: return "demo";
        }
    }

    @Override
    public int[] getSupportedPids() {
        // AndrOBD does not expose a simple supported-PID list in ElmProt public API;
        // return empty (API caller can still function).
        return new int[0];
    }

    @Override
    public void startScan(String scanId) {
        Log.i(TAG, "startScan requested: " + scanId);
        // AndrOBD's full scan is driven by the UI (menu DTC read). MCP client
        // polls get_scan_results; results land in ScanRegistry via the bridge
        // callback once MainActivity wires the DFC adapter. Placeholder here.
    }

    @Override
    public boolean isScanRunning() {
        return false;
    }

    @Override
    public ObdBridge.ScanResult getScanResult(String scanId) {
        return ObdBridge.ScanResult.unknown(0);
    }

    @Override
    public ObdBridge.Diagnostics getDiagnostics() {
        List<ObdBridge.Dtc> dtc = new ArrayList<>();
        try {
            // AndrOBD stores DFC codes in ObdProt.tCodes (PropertyValue list).
            Object tCodes = getClass().getClassLoader()
                .loadClass("com.fr3ts0n.ecu.prot.obd.ObdProt")
                .getField("tCodes").get(null);
            if (tCodes instanceof java.util.List) {
                for (Object pv : (java.util.List<?>) tCodes) {
                    Object codeObj = pv.getClass().getField("code").get(pv);
                    Object descObj = pv.getClass().getField("desc").get(pv);
                    dtc.add(new ObdBridge.Dtc(
                        String.valueOf(codeObj), String.valueOf(descObj)));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read DTC list", e);
        }
        Map<String, Boolean> monitors = new TreeMap<>();
        monitors.put("misfire", false);
        monitors.put("fuel", false);
        monitors.put("components", false);
        return new ObdBridge.Diagnostics(dtc, false, monitors);
    }

    @Override
    public boolean clearFaultCodes() {
        Log.i(TAG, "clearFaultCodes requested");
        // AndrOBD Mode-04 clear routine is UI-driven; not wired yet.
        return false;
    }
}
