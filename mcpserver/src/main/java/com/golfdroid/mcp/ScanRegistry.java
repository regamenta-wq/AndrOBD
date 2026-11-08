package com.golfdroid.mcp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of in-flight and completed scans: scan_id -> status +
 * result + creation time. Accessed from the HTTP handler thread(s) and the
 * bridge callback thread — all operations are thread-safe.
 */
public class ScanRegistry {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /** Create a new scan entry and return its id. */
    public String create() {
        String id = UUID.randomUUID().toString();
        entries.put(id, new Entry());
        return id;
    }

    /** Return the entry for {@code id}, or null if unknown. */
    public Entry get(String id) {
        return entries.get(id);
    }

    /** All entries (package-visible for Api). */
    Map<String, Entry> entries() { return entries; }

    /** Mark an entry complete with its result. */
    public void setResult(String id, ObdBridge.ScanResult result) {
        Entry e = entries.get(id);
        if (e != null) {
            e.result = result;
            e.done = true;
        }
    }

    /** Is there any scan currently running (created, not done)? */
    public boolean hasRunning() {
        return entries.values().stream().anyMatch(e -> !e.done);
    }

    /** Id of a running scan, or null if none. */
    public String getRunningId() {
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            if (!e.getValue().done) return e.getKey();
        }
        return null;
    }

    public static final class Entry {
        public final String id;
        public final long createdAt = System.currentTimeMillis();
        public volatile boolean done = false;
        public volatile ObdBridge.ScanResult result = null;

        private Entry() { this.id = null; } // id set by registry
        Entry(String id) { this.id = id; }
    }
}
