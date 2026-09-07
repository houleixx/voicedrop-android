package com.baixingai.voicedrop.core;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SubscriptionRefreshSessionTest {
    @Test public void delayedPaymentIsPolledWithAFiniteBudget() {
        SubscriptionRefreshSession session = new SubscriptionRefreshSession();
        int id = session.start();
        for (int i = 1; i < SubscriptionRefreshSession.MAX_ATTEMPTS; i++) assertTrue(session.completed(id, true));
        assertFalse(session.completed(id, true));
    }

    @Test public void pauseDiscardsInflightReplyAndResumeStartsANewBudget() {
        SubscriptionRefreshSession session = new SubscriptionRefreshSession();
        int old = session.start();
        session.stop();
        assertFalse(session.accepts(old));
        assertFalse(session.completed(old, true));
        int fresh = session.start();
        assertFalse(session.accepts(old));
        assertTrue(session.accepts(fresh));
        assertTrue(session.completed(fresh, true));
    }

    @Test public void settledPageStopsPollingAndManualRefreshSupersedesOldReplies() {
        SubscriptionRefreshSession session = new SubscriptionRefreshSession();
        int old = session.start();
        assertFalse(session.completed(old, false));
        int fresh = session.start();
        assertFalse(session.accepts(old));
        assertTrue(session.accepts(fresh));
    }
}
