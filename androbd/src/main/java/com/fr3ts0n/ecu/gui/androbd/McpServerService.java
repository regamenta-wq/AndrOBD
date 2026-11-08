package com.fr3ts0n.ecu.gui.androbd;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.golfdroid.mcp.Api;
import com.golfdroid.mcp.TokenStore;

/**
 * Android service that hosts the GolfDroid MCP-over-HTTP server on port 8080.
 *
 * <p>Started via {@link Context#startService} after a successful OBD-II
 * connection. Runs in the foreground-less mode: a plain started Service,
 * NanoHTTPD binds to :8080 and serves the diagnostic JSON API.
 *
 * <p>The {@link ObdBridge} is supplied by {@link MainActivity} through a
 * static holder so the service (different process boundary aside, same app)
 * can call into the live connection state.
 */
public class McpServerService extends Service {

    private static final String TAG = "McpServerService";
    private static final int PORT = 8080;

    private Api api;
    private boolean started = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!started) {
            startServer();
            started = true;
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    private void startServer() {
        try {
            String token = TokenStore.get(this).get();
            ObdBridge bridge = ObdBridgeHolder.get();
            if (bridge == null) {
                Log.w(TAG, "ObdBridge not set — MCP server not started");
                return;
            }
            api = new Api(PORT, token, bridge);
            api.start(NanoHttpdConstants.SOCKET_READ_TIMEOUT, false);
            Log.i(TAG, "MCP server listening on :" + PORT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start MCP server", e);
        }
    }

    private void stopServer() {
        if (api != null) {
            api.stop();
            api = null;
        }
        started = false;
        Log.i(TAG, "MCP server stopped");
    }

    /** NanoHTTPD start() timeout constant (avoids pulling NanoHTTPD into the manifest). */
    interface NanoHttpdConstants {
        int SOCKET_READ_TIMEOUT = 5000;
    }
}
