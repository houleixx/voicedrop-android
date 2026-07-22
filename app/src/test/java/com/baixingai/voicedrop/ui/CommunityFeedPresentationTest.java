package com.baixingai.voicedrop.ui;

import com.baixingai.voicedrop.data.CommunityStore;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class CommunityFeedPresentationTest {
    @Test
    public void tabsExposeRecommendedLatestAndReplyPosts() throws Exception {
        CommunityStore.Post latest = post("latest", null, 3);
        CommunityStore.Post reply = post("reply", "root", 2);
        CommunityStore.Post root = post("root", null, 1);
        CommunityStore.Post prompt = prompt("prompt", 0); // Historical rows remain decodable for old links/caches.
        CommunityStore.Feed feed = CommunityStore.Feed.fromLegacy(
                Arrays.asList(latest, reply, root, prompt),
                new CommunityStore.Ranking(Arrays.asList("root", "latest", "reply", "prompt"),
                        Arrays.asList(), java.util.Collections.emptyMap()));

        assertEquals(Arrays.asList("root", "latest", "reply", "prompt"),
                CommunityFeedPresentation.ids(CommunityFeedPresentation.posts(feed,
                        CommunityFeedPresentation.Tab.RECOMMENDED)));
        assertEquals(Arrays.asList("latest", "reply", "root", "prompt"),
                CommunityFeedPresentation.ids(CommunityFeedPresentation.posts(feed,
                        CommunityFeedPresentation.Tab.LATEST)));
        assertEquals(Arrays.asList("reply"),
                CommunityFeedPresentation.ids(CommunityFeedPresentation.posts(feed,
                        CommunityFeedPresentation.Tab.REPLIES)));
    }

    @Test
    public void paletteIsStableForTheSameShareId() {
        assertEquals(CommunityFeedPresentation.paletteIndex("share-42"),
                CommunityFeedPresentation.paletteIndex("share-42"));
    }

    private CommunityStore.Post post(String id, String replyTo, double time) throws Exception {
        JSONObject json = new JSONObject().put("shareId", id).put("firstSharedAt", time);
        if (replyTo != null) json.put("replyTo", replyTo);
        return CommunityStore.Post.from(json);
    }

    private CommunityStore.Post prompt(String id, double time) throws Exception {
        return CommunityStore.Post.from(new JSONObject().put("shareId", id).put("firstSharedAt", time)
                .put("kind", "prompt"));
    }
}
