package com.baixingai.voicedrop.core;

import java.util.Map;

/** Main-thread request generations: refresh/account changes invalidate all prior search results. */
public final class BookShelfSearchState {
    private long generation;
    private long nextRetryAt;
    private boolean loading;
    private boolean failed;
    private Map<String, BookShelfSearch.Entry> index;

    public long invalidate() {
        generation++;
        index = null;
        loading = false;
        failed = false;
        nextRetryAt = 0;
        return generation;
    }
    public boolean isCurrent(long request) { return request == generation; }
    public long begin(long now) {
        if (loading || index != null || now < nextRetryAt) return -1;
        loading = true;
        failed = false;
        return generation;
    }
    public boolean complete(long request, Map<String, BookShelfSearch.Entry> result, long now) {
        if (!isCurrent(request) || !loading) return false;
        loading = false;
        index = result;
        failed = result == null;
        nextRetryAt = failed ? now + 2000 : 0;
        return true;
    }
    public long retryDelay(long now) { return Math.max(0, nextRetryAt - now); }
    public Map<String, BookShelfSearch.Entry> index() { return index; }
    public boolean loading() { return loading; }
    public boolean failed() { return failed; }
}
