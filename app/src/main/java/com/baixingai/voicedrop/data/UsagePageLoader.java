package com.baixingai.voicedrop.data;

/** Loads each section independently so a failed refresh never replaces good data with zeroes. */
public final class UsagePageLoader {
    public interface Source {
        UsageStore.Balance balance() throws Exception;
        UsageStore.Summary summary() throws Exception;
        UsageStore.LedgerPage ledger() throws Exception;
        WechatSubscriptionStore.Status subscription() throws Exception;
    }

    private final Source source;

    public UsagePageLoader(Source source) { this.source = source; }

    public Result load() {
        Result result = new Result();
        try { result.subscription = source.subscription(); }
        catch (Exception e) { result.subscriptionFailed = true; }
        try {
            UsageStore.Balance balance = source.balance();
            UsageStore.Summary summary = source.summary();
            UsageStore.LedgerPage ledger = source.ledger();
            result.balance = balance;
            result.summary = summary;
            result.ledger = ledger;
        } catch (Exception e) { result.usageFailed = true; }
        return result;
    }

    public static final class Result {
        public UsageStore.Balance balance;
        public UsageStore.Summary summary;
        public UsageStore.LedgerPage ledger;
        public WechatSubscriptionStore.Status subscription;
        public boolean usageFailed;
        public boolean subscriptionFailed;
    }
}
