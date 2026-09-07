package com.baixingai.voicedrop.data;

import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;

public final class UsagePageLoaderTest {
    private static final class Source implements UsagePageLoader.Source {
        double balance = 20;
        boolean subscriptionFails;
        boolean ledgerFails;
        public UsageStore.Balance balance() { return new UsageStore.Balance(balance, 0); }
        public UsageStore.Summary summary() { return new UsageStore.Summary(new ArrayList<>(), new ArrayList<>()); }
        public UsageStore.LedgerPage ledger() throws Exception {
            if (ledgerFails) throw new Exception("offline");
            ArrayList<UsageStore.Entry> entries = new ArrayList<>();
            entries.add(new UsageStore.Entry(1, 1, "grant", "subscription", balance, balance));
            return new UsageStore.LedgerPage(entries, "");
        }
        public WechatSubscriptionStore.Status subscription() throws Exception {
            if (subscriptionFails) throw new Exception("offline");
            return WechatSubscriptionStore.Status.fromJson(new JSONObject("{\"enabled\":true}"));
        }
    }

    @Test public void paymentRefreshReadsNewBalanceAndLedgerTogether() {
        Source source = new Source();
        UsagePageLoader loader = new UsagePageLoader(source);
        assertEquals(20, loader.load().balance.suanli, 0);
        source.balance += 200;
        UsagePageLoader.Result paid = loader.load();
        assertEquals(220, paid.balance.suanli, 0);
        assertEquals(220, paid.ledger.entries.get(0).suanli, 0);
    }

    @Test public void subscriptionFailureDoesNotLoseSuccessfulUsage() {
        Source source = new Source(); source.subscriptionFails = true;
        UsagePageLoader.Result result = new UsagePageLoader(source).load();
        assertTrue(result.subscriptionFailed);
        assertFalse(result.usageFailed);
        assertEquals(20, result.balance.suanli, 0);
        assertNotNull(result.ledger);
    }

    @Test public void usageFailureDoesNotHideSubscriptionAndCannotPublishPartialBalance() {
        Source source = new Source(); source.ledgerFails = true;
        UsagePageLoader.Result result = new UsagePageLoader(source).load();
        assertTrue(result.usageFailed);
        assertFalse(result.subscriptionFailed);
        assertNotNull(result.subscription);
        assertNull(result.balance);
        assertNull(result.ledger);
    }
}
