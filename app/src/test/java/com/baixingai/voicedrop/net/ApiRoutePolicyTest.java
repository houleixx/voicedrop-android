package com.baixingai.voicedrop.net;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ApiRoutePolicyTest {
    @Test public void bothFailuresKeepIncumbentAndSingleSurvivorWins() {
        assertEquals(Api.CN_HOST, ApiRoutePolicy.pick(Api.CN_HOST, null, null));
        assertEquals(Api.CF_HOST, ApiRoutePolicy.pick(Api.CF_HOST, null, null));
        assertEquals(Api.CN_HOST, ApiRoutePolicy.pick(Api.CF_HOST, 300L, null));
        assertEquals(Api.CF_HOST, ApiRoutePolicy.pick(Api.CN_HOST, null, 300L));
    }

    @Test public void clearWinnerSwitchesInBothDirections() {
        assertEquals(Api.CF_HOST, ApiRoutePolicy.pick(Api.CN_HOST, 1_200L, 300L));
        assertEquals(Api.CN_HOST, ApiRoutePolicy.pick(Api.CF_HOST, 300L, 1_200L));
    }

    @Test public void challengerMustBeatIncumbentByMoreThan150Ms() {
        assertEquals(Api.CN_HOST, ApiRoutePolicy.pick(Api.CN_HOST, 500L, 400L));
        assertEquals(Api.CF_HOST, ApiRoutePolicy.pick(Api.CF_HOST, 400L, 500L));
        assertEquals(Api.CN_HOST, ApiRoutePolicy.pick(Api.CN_HOST, 550L, 400L));
        assertEquals(Api.CF_HOST, ApiRoutePolicy.pick(Api.CN_HOST, 551L, 400L));
    }

    @Test public void publicWebPathPrefixMatchesEdgeOneContract() {
        assertEquals("https://voicedrop.cn", Api.publicWebBaseForHost(Api.CN_HOST));
        assertEquals("https://jianshuo.dev/voicedrop", Api.publicWebBaseForHost(Api.CF_HOST));
    }
}
