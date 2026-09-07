package com.baixingai.voicedrop.core;

/** Main-thread polling budget; generations discard replies from a paused or replaced page. */
public final class SubscriptionRefreshSession {
    public static final long DELAY_MS = 3000;
    public static final int MAX_ATTEMPTS = 10;
    private int generation;
    private int attempts;
    private boolean active;

    public int start() {
        active = true;
        attempts = 0;
        return ++generation;
    }

    public void stop() { active = false; generation++; }
    public boolean accepts(int id) { return active && id == generation; }

    public boolean completed(int id, boolean waiting) {
        if (!accepts(id)) return false;
        attempts++;
        return waiting && attempts < MAX_ATTEMPTS;
    }
}
