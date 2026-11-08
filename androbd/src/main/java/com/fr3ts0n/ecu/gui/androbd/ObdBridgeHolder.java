package com.fr3ts0n.ecu.gui.androbd;

import com.golfdroid.mcp.ObdBridge;

/**
 * Holds the live {@link ObdBridge} implementation (set by {@link MainActivity}
 * or a dedicated bridge class) so {@link McpServerService} can reach it.
 */
public class ObdBridgeHolder {
    private static ObdBridge bridge;

    private ObdBridgeHolder() {}

    public static ObdBridge get() {
        return bridge;
    }

    public static void set(ObdBridge bridge) {
        ObdBridgeHolder.bridge = bridge;
    }
}
