package com.baixingai.voicedrop;

import com.baixingai.voicedrop.core.PromptNode;
import com.baixingai.voicedrop.core.PromptTree;
import com.baixingai.voicedrop.data.PromptStore;
import com.baixingai.voicedrop.data.UIConfigStore;
import com.baixingai.voicedrop.net.HttpClient;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PromptStoreTest {
    private static final String RESOLVED = "{\"schema\":1,\"items\":[{\"id\":\"sys_concise\",\"type\":\"action\",\"label\":\"精简\",\"origin\":\"system\",\"prompt\":\"P\",\"appliesTo\":[\"text\"]}]}";
    private static final String IMPORTED_GROUPED = "{\"schema\":1,\"items\":[{\"id\":\"g_shared\",\"type\":\"group\",\"label\":\"共享组\",\"origin\":\"user\",\"children\":[{\"id\":\"p_12345678\",\"type\":\"action\",\"label\":\"共享\",\"origin\":\"user\",\"prompt\":\"P\",\"appliesTo\":[\"text\"],\"importedFrom\":\"1234567\"}]}]}";
    private FakeTransport transport;
    private FakeCache cache;
    private PromptStore store;

    @Before public void setUp() throws Exception {
        transport = new FakeTransport();
        cache = new FakeCache();
        cache.put(PromptStore.CACHE_KEY, RESOLVED);
        store = new PromptStore(transport, cache, "token", "https://example.test/agent", new ArrayList<>());
    }

    @Test public void refreshUsesPromptsAndCachesResolvedResponse() {
        transport.enqueue(200, RESOLVED);
        assertNull(store.refresh());
        assertEquals("GET /agent/prompts", transport.lastRequestLine());
        assertEquals(RESOLVED, cache.get(PromptStore.CACHE_KEY));
    }

    @Test public void failedDeleteRestoresWholeSnapshot() {
        transport.enqueue(500, "{}");
        List<String> before = PromptTree.flattenIds(store.items());
        assertNotNull(store.remove("sys_concise"));
        assertEquals(before, PromptTree.flattenIds(store.items()));
    }

    @Test public void addAndReplacePersistPromptDrafts() {
        PromptNode created = new PromptNode();
        created.id = "p_12345678"; created.type = "action"; created.label = "新提示词"; created.origin = "user";
        created.prompt = "提示词"; created.appliesTo.add("text");
        transport.enqueue(200, RESOLVED);
        assertNull(store.add(created, null));
        assertEquals("PUT /agent/prompts", transport.lastRequestLine());

        PromptNode changed = store.items().get(0).copy();
        changed.label = "已修改";
        transport.enqueue(200, RESOLVED);
        assertNull(store.replace("sys_concise", changed));
        assertEquals("PUT /agent/prompts", transport.lastRequestLine());
    }

    @Test public void staleReorderBaselineDoesNotPut() {
        assertEquals(PromptStore.CONFLICT, store.applyReorder(store.items(), Arrays.asList("old")));
        assertEquals(0, transport.putCount);
    }

    @Test public void restoreImportPreviewAndShareUseExactEndpoints() {
        transport.enqueue(200, RESOLVED);
        assertNull(store.restoreDefaults());
        assertEquals("POST /agent/prompts/restore-defaults", transport.lastRequestLine());

        transport.enqueue(200, "{\"label\":\"共享\",\"prompt\":\"P\",\"appliesTo\":[\"text\"],\"author\":\"A\",\"importCount\":2}");
        assertEquals("共享", store.preview("1234567").label);
        assertEquals("GET /agent/prompt-share/1234567", transport.lastRequestLine());

        transport.enqueue(200, "{\"item\":{\"id\":\"p_12345678\",\"type\":\"action\",\"label\":\"共享\",\"origin\":\"user\",\"prompt\":\"P\",\"appliesTo\":[\"text\"],\"importedFrom\":\"1234567\"}}");
        transport.enqueue(200, IMPORTED_GROUPED);
        assertNull(store.importCode("1234567"));
        assertTrue(transport.requests.contains("POST /agent/prompts/import"));
        assertEquals("GET /agent/prompts", transport.lastRequestLine());
        assertTrue(cache.get(PromptStore.CACHE_KEY).contains("\"importedFrom\":\"1234567\""));
        assertEquals("g_shared", store.items().get(0).id);

        transport.enqueue(200, "{\"code\":\"1234567\",\"sharing\":true}");
        PromptStore.ShareState state = store.setSharing("p_12345678", true);
        assertEquals("1234567", state.code);
        assertEquals("POST /agent/prompt-share", transport.lastRequestLine());
    }

    @Test public void sharingUsesSignedSessionWhilePromptDataKeepsAnonymousBearer() {
        PromptStore signedStore = new PromptStore(transport, cache, "anon-token", "wechat.session.token",
                "https://example.test/agent", new ArrayList<>());
        transport.enqueue(200, RESOLVED);
        assertNull(signedStore.refresh());
        assertEquals("anon-token", transport.lastBearer);

        transport.enqueue(200, "{\"code\":\"1234567\",\"sharing\":true}");
        assertNull(signedStore.setSharing("p_12345678", true).error);
        assertEquals("wechat.session.token", transport.lastBearer);
    }

    @Test public void sharingWithoutSignedSessionShowsWechatLoginGuidance() {
        PromptStore unsignedStore = new PromptStore(transport, cache, "anon-token", "",
                "https://example.test/agent", new ArrayList<>());
        PromptStore.ShareState result = unsignedStore.setSharing("p_12345678", true);
        assertEquals("请先登录微信后分享提示词", result.error);
    }

    @Test public void idempotentImportDoesNotAppendDuplicateLocalItem() {
        transport.enqueue(200, "{\"item\":{\"id\":\"sys_concise\",\"type\":\"action\",\"label\":\"精简\",\"origin\":\"system\",\"prompt\":\"P\",\"appliesTo\":[\"text\"]},\"already\":true}");
        transport.enqueue(200, RESOLVED);
        assertNull(store.importCode("1234567"));
        assertEquals(1, PromptTree.flattenIds(store.items()).size());
    }

    @Test public void importedPromptIsImmediatelyVisibleToRecordingDetailMenus() {
        transport.enqueue(200, "{\"item\":{\"id\":\"p_imported1\",\"type\":\"action\","
                + "\"label\":\"社区导入\",\"origin\":\"user\",\"prompt\":\"按导入提示词处理\","
                + "\"appliesTo\":[\"text\",\"image\"],\"importedFrom\":\"3295225\"}}");
        transport.enqueue(200, "{\"schema\":1,\"items\":[{\"id\":\"p_imported1\",\"type\":\"action\","
                + "\"label\":\"社区导入\",\"origin\":\"user\",\"prompt\":\"按导入提示词处理\","
                + "\"appliesTo\":[\"text\",\"image\"],\"importedFrom\":\"3295225\"}]}");

        assertNull(store.importCode("3295225"));

        PromptStore recordingDetailStore = new PromptStore(
                new FakeTransport(), cache, "token", "https://example.test/agent", new ArrayList<>());
        assertTrue(menuContains(recordingDetailStore.textMenu(), "社区导入"));
        assertTrue(menuContains(recordingDetailStore.imageMenu(), "社区导入"));
    }

    @Test public void sharingPreservesServerBusinessError() {
        PromptStore signedStore = new PromptStore(transport, cache, "anon-token", "wechat.session.token",
                "https://example.test/agent", new ArrayList<>());
        transport.enqueue(403, "{\"error\":\"content_flagged\"}");
        assertEquals("提示词未通过社区审核，暂时不能分享",
                signedStore.setSharing("p_12345678", true).error);
    }

    private static boolean menuContains(UIConfigStore.MenuConfig menu, String label) {
        for (List<UIConfigStore.MenuNode> group : menu.groups) {
            for (UIConfigStore.MenuNode node : group) {
                if (label.equals(node.label)) return true;
            }
        }
        return false;
    }

    private static final class FakeCache implements PromptStore.Cache {
        private final Map<String, String> values = new HashMap<>();
        @Override public String get(String key) { return values.get(key); }
        @Override public void put(String key, String value) { values.put(key, value); }
    }

    private static final class FakeTransport implements PromptStore.Transport {
        private final ArrayDeque<HttpClient.Response> responses = new ArrayDeque<>();
        private final List<String> requests = new ArrayList<>();
        int putCount;
        String lastBearer;
        void enqueue(int status, String body) { responses.add(new HttpClient.Response(status, body.getBytes(StandardCharsets.UTF_8))); }
        String lastRequestLine() { return requests.get(requests.size() - 1); }
        private HttpClient.Response response(String method, String url, String bearer) {
            lastBearer = bearer;
            requests.add(method + " " + url.substring(url.indexOf("/agent")));
            return responses.remove();
        }
        @Override public HttpClient.Response get(String url, String bearer) { return response("GET", url, bearer); }
        @Override public HttpClient.Response put(String url, String bearer, byte[] json) { putCount++; return response("PUT", url, bearer); }
        @Override public HttpClient.Response post(String url, String bearer, byte[] json) { return response("POST", url, bearer); }
        @Override public HttpClient.Response delete(String url, String bearer) { return response("DELETE", url, bearer); }
    }
}
