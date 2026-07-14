# Android Prompt Manager Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Android's removed UI-config integration with the complete Prompt Manager behavior shipped by iOS TestFlight 240.

**Architecture:** A pure Java recursive `PromptTree` owns schema conversion and tree mutations. `PromptStore` owns HTTP, cache, optimistic snapshots, mutation locking, and share state. Native Activities render the store without duplicating contract logic; the existing long-press menu consumes an adapter generated from the prompt tree.

**Tech Stack:** Java 17, Android SDK 23–36 native View APIs, OkHttp, `org.json`, SharedPreferences, JUnit 4.

## Global Constraints

- Preserve `VoiceDrop-...m4a` names and `[[photo:photos/<sessionTs>/<offset>-<rand>.jpg]]` markers.
- Use only `/agent/prompts*`, `/agent/prompt-share*`, and `/agent/prompt-shares`; no runtime `/agent/ui-config*` request may remain.
- Prompt trees have at most two levels; groups cannot contain groups.
- System nodes serialize as refs; system groups must include raw children.
- All production changes follow red-green-refactor and retain user changes in other repositories.
- Before delivery run `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`.

---

### Task 1: Prompt node model and resolved/raw schema conversion

**Files:**
- Create: `app/src/main/java/com/baixingai/voicedrop/core/PromptNode.java`
- Create: `app/src/main/java/com/baixingai/voicedrop/core/PromptTree.java`
- Create: `app/src/test/java/com/baixingai/voicedrop/PromptTreeTest.java`

**Interfaces:**
- Produces: `PromptNode.fromResolved(JSONObject)`, `PromptNode.copy()`, `PromptTree.decode(String)`, `PromptTree.rawItems(List<PromptNode>)`, `PromptTree.encodeRaw(List<PromptNode>)`.

- [ ] **Step 1: Write failing schema tests**

```java
@Test public void systemGroupSerializesAsRefWithChildren() throws Exception {
    List<PromptNode> nodes = PromptTree.decode(Fixtures.RESOLVED_PROMPTS);
    JSONObject raw = new JSONObject(PromptTree.encodeRaw(nodes));
    JSONObject group = raw.getJSONArray("items").getJSONObject(0);
    assertEquals("sys_rewrite", group.getString("ref"));
    assertEquals("sys_concise", group.getJSONArray("children").getJSONObject(0).getString("ref"));
    assertFalse(group.has("origin"));
}

@Test public void unknownResolvedFieldsAreIgnored() throws Exception {
    PromptNode node = PromptNode.fromResolved(new JSONObject(
        "{\"id\":\"p_12345678\",\"type\":\"action\",\"label\":\"A\",\"origin\":\"user\",\"prompt\":\"P\",\"appliesTo\":[\"text\"],\"future\":1}"));
    assertEquals("p_12345678", node.id);
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew testDebugUnitTest --tests '*PromptTreeTest'`
Expected: compilation fails because `PromptNode` and `PromptTree` do not exist.

- [ ] **Step 3: Implement the immutable model and conversion**

```java
public final class PromptNode {
    public String id, type, label, origin, prompt, kind, forkedFrom;
    public final List<String> appliesTo = new ArrayList<>();
    public final List<PromptNode> children = new ArrayList<>();
    public boolean isGroup() { return "group".equals(type); }
    public boolean isSystem() { return "system".equals(origin); }
    public PromptNode copy() {
        PromptNode out = new PromptNode();
        out.id = id; out.type = type; out.label = label; out.origin = origin;
        out.prompt = prompt; out.kind = kind; out.forkedFrom = forkedFrom;
        out.appliesTo.addAll(appliesTo);
        for (PromptNode child : children) out.children.add(child.copy());
        return out;
    }
    public static PromptNode fromResolved(JSONObject json) throws JSONException {
        PromptNode out = new PromptNode();
        out.id = json.getString("id"); out.type = json.getString("type");
        out.label = json.getString("label"); out.origin = json.getString("origin");
        out.prompt = json.optString("prompt", null); out.kind = json.optString("kind", null);
        out.forkedFrom = json.optString("forkedFrom", null);
        JSONArray applies = json.optJSONArray("appliesTo");
        if (applies != null) for (int i = 0; i < applies.length(); i++) out.appliesTo.add(applies.getString(i));
        JSONArray children = json.optJSONArray("children");
        if (children != null) for (int i = 0; i < children.length(); i++) out.children.add(fromResolved(children.getJSONObject(i)));
        return out;
    }
}

public final class PromptTree {
    public static List<PromptNode> copy(List<PromptNode> nodes) {
        List<PromptNode> out = new ArrayList<>();
        for (PromptNode node : nodes) out.add(node.copy());
        return out;
    }
    public static List<PromptNode> decode(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optInt("schema", 1) > 1) throw new JSONException("unsupported prompt schema");
        JSONArray array = root.getJSONArray("items");
        List<PromptNode> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) out.add(PromptNode.fromResolved(array.getJSONObject(i)));
        return out;
    }
    public static JSONArray rawItems(List<PromptNode> nodes) throws JSONException {
        JSONArray out = new JSONArray();
        for (PromptNode node : nodes) {
            JSONObject item = new JSONObject();
            if (node.isSystem()) {
                item.put("ref", node.id);
                if (node.isGroup()) item.put("children", rawItems(node.children));
            } else {
                item.put("id", node.id).put("type", node.type).put("label", node.label);
                if (node.forkedFrom != null) item.put("forkedFrom", node.forkedFrom);
                if (node.isGroup()) item.put("children", rawItems(node.children));
                else {
                    item.put("prompt", node.prompt).put("appliesTo", new JSONArray(node.appliesTo));
                    if (node.kind != null) item.put("kind", node.kind);
                }
            }
            out.put(item);
        }
        return out;
    }
    public static String encodeRaw(List<PromptNode> nodes) throws JSONException {
        return new JSONObject().put("items", rawItems(nodes)).toString();
    }
}
```

- [ ] **Step 4: Verify GREEN and commit**

Run: `./gradlew testDebugUnitTest --tests '*PromptTreeTest'`
Expected: all PromptTree schema tests pass.

Commit: `git commit -am "feat(android): add prompt tree contract"` after staging the three files.

### Task 2: Tree mutations, menus, share-code parsing, and conflict baselines

**Files:**
- Modify: `app/src/main/java/com/baixingai/voicedrop/core/PromptTree.java`
- Modify: `app/src/test/java/com/baixingai/voicedrop/PromptTreeTest.java`

**Interfaces:**
- Produces: `fork`, `replace`, `remove`, `append`, `move`, `flattenIds`, `menu`, `extractShareCode`, `mergeCodeInput`, `newUserId`.

- [ ] **Step 1: Add failing mutation and boundary tests**

```java
@Test public void editingSystemNodeForksInPlace() {
    PromptNode fork = PromptTree.fork(systemAction, () -> "p_abcdefgh");
    assertEquals("p_abcdefgh", fork.id);
    assertEquals("sys_concise", fork.forkedFrom);
    assertEquals("custom", fork.origin);
}

@Test public void pastedEightDigitsAreRejected() {
    assertNull(PromptTree.extractShareCode("12345678"));
    assertEquals("12", PromptTree.mergeCodeInput("12", "12345678"));
}

@Test public void imageMenuContainsOnlyImageActions() {
    UIConfigStore.MenuConfig menu = PromptTree.menu(nodes, "image");
    assertEquals("sys_cartoon", menu.groups.get(0).get(0).children.get(0).id);
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew testDebugUnitTest --tests '*PromptTreeTest'`
Expected: missing mutation and code-input methods.

- [ ] **Step 3: Implement recursive pure functions**

```java
public static PromptNode fork(PromptNode node, Supplier<String> ids) {
    PromptNode out = node.copy(); out.forkedFrom = node.id; out.id = ids.get(); out.origin = "custom"; return out;
}
public static final class MutationResult {
    public final List<PromptNode> items;
    public final boolean changed;
    public final PromptNode removed;
    MutationResult(List<PromptNode> items, boolean changed) { this(items, changed, null); }
    MutationResult(List<PromptNode> items, boolean changed, PromptNode removed) {
        this.items = items; this.changed = changed; this.removed = removed;
    }
}
public static MutationResult replace(List<PromptNode> source, String id, PromptNode replacement) {
    List<PromptNode> out = new ArrayList<>(); boolean changed = false;
    for (PromptNode node : source) {
        if (node.id.equals(id)) { out.add(replacement.copy()); changed = true; continue; }
        PromptNode copy = node.copy();
        if (node.isGroup()) { MutationResult nested = replace(node.children, id, replacement); copy.children.clear(); copy.children.addAll(nested.items); changed |= nested.changed; }
        out.add(copy);
    }
    return new MutationResult(out, changed);
}
public static MutationResult remove(List<PromptNode> source, String id) {
    List<PromptNode> out = new ArrayList<>(); boolean changed = false;
    for (PromptNode node : source) {
        if (node.id.equals(id)) { changed = true; continue; }
        PromptNode copy = node.copy();
        if (node.isGroup()) { MutationResult nested = remove(node.children, id); copy.children.clear(); copy.children.addAll(nested.items); changed |= nested.changed; }
        out.add(copy);
    }
    return new MutationResult(out, changed);
}
public static List<String> flattenIds(List<PromptNode> source) {
    List<String> out = new ArrayList<>();
    for (PromptNode node : source) { out.add(node.id); out.addAll(flattenIds(node.children)); }
    return out;
}
public static String extractShareCode(String text) {
    Matcher matcher = Pattern.compile("(?<![0-9])[1-9][0-9]{6}(?![0-9])").matcher(text == null ? "" : text);
    return matcher.find() ? matcher.group() : null;
}
public static String mergeCodeInput(String previous, String incoming) {
    String old = previous == null ? "" : previous;
    String next = incoming == null ? "" : incoming;
    boolean typing = Math.abs(next.length() - old.length()) <= 1 && (next.startsWith(old) || old.startsWith(next));
    if (typing) { String digits = next.replaceAll("[^0-9]", ""); return digits.substring(0, Math.min(7, digits.length())); }
    String code = extractShareCode(next); return code == null ? old : code;
}
```

Implement `move` by calling `remove`, retaining the removed node in `MutationResult.removed`, rejecting a removed group when `groupId` is non-null, and inserting the node at `max(0,min(index,target.size()))` in either the top-level list or the selected group's children.

- [ ] **Step 4: Verify GREEN and commit**

Run: `./gradlew testDebugUnitTest --tests '*PromptTreeTest'`
Expected: schema, mutation, menu, and code boundary tests pass.

Commit: `git commit -am "feat(android): add prompt tree operations"`.

### Task 3: HTTP/cache store and mutation transaction discipline

**Files:**
- Create: `app/src/main/java/com/baixingai/voicedrop/data/PromptStore.java`
- Create: `app/src/test/java/com/baixingai/voicedrop/PromptStoreTest.java`
- Modify: `app/src/main/java/com/baixingai/voicedrop/net/HttpClient.java` to expose the existing GET/PUT/POST/DELETE operations through `PromptStore.Transport`.

**Interfaces:**
- Produces: `loadCached`, `refresh`, `save`, `replace`, `remove`, `add`, `applyReorder`, `restoreDefaults`, `preview`, `importCode`, `shareStates`, `setSharing`, `textMenu`, `imageMenu`.

- [ ] **Step 1: Write failing request and rollback tests**

```java
@Test public void refreshUsesPromptsAndCachesResolvedResponse() throws Exception {
    transport.enqueue(200, Fixtures.RESOLVED_PROMPTS);
    store.refresh();
    assertEquals("GET /agent/prompts", transport.lastRequestLine());
    assertEquals(Fixtures.RESOLVED_PROMPTS, cache.getString("promptsCache.v1", ""));
}

@Test public void failedDeleteRestoresWholeSnapshot() throws Exception {
    transport.enqueue(500, "{}");
    List<String> before = PromptTree.flattenIds(store.items());
    assertNotNull(store.remove("sys_concise"));
    assertEquals(before, PromptTree.flattenIds(store.items()));
}

@Test public void staleReorderBaselineDoesNotPut() {
    assertEquals(PromptStore.CONFLICT, store.applyReorder(draft, Arrays.asList("old")));
    assertEquals(0, transport.putCount());
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew testDebugUnitTest --tests '*PromptStoreTest'`
Expected: `PromptStore` is missing.

- [ ] **Step 3: Implement store and transport seam**

```java
public final class PromptStore {
    public interface Transport {
        HttpClient.Response get(String url, String bearer) throws Exception;
        HttpClient.Response put(String url, String bearer, byte[] json) throws Exception;
        HttpClient.Response post(String url, String bearer, byte[] json) throws Exception;
        HttpClient.Response delete(String url, String bearer) throws Exception;
    }
    private final Object lock = new Object();
    private List<PromptNode> items;
    private boolean mutating;
    public String saveDraft(List<PromptNode> draft) {
        List<PromptNode> snapshot;
        synchronized (lock) { if (mutating) return BUSY; mutating = true; snapshot = PromptTree.copy(items); items = PromptTree.copy(draft); }
        try {
            HttpClient.Response response = transport.put(Api.agentBase() + "/prompts", auth.bearer(), PromptTree.encodeRaw(items).getBytes(StandardCharsets.UTF_8));
            if (!response.ok()) throw new IllegalStateException("prompt save HTTP " + response.code);
            List<PromptNode> fresh = PromptTree.decode(response.text());
            synchronized (lock) { items = fresh; cache(response.text()); }
            return null;
        } catch (Exception error) {
            synchronized (lock) { items = snapshot; }
            return "保存失败，请重试";
        } finally { synchronized (lock) { mutating = false; } }
    }
    public String applyReorder(List<PromptNode> draft, List<String> baseline) {
        synchronized (lock) { if (!PromptTree.flattenIds(items).equals(baseline)) return CONFLICT; }
        return saveDraft(draft);
    }
}
```

- [ ] **Step 4: Add endpoint tests for restore/import/share and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests '*PromptStoreTest'`
Expected: all request, cache, rollback, lock, import, and share tests pass.

Commit: `git commit -am "feat(android): add prompt store and transactions"`.

### Task 4: Replace long-press menu data source

**Files:**
- Modify: `app/src/main/java/com/baixingai/voicedrop/data/UIConfigStore.java`
- Modify: `app/src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java`
- Modify: `app/src/test/java/com/baixingai/voicedrop/UIConfigStoreTest.java`
- Modify: `app/src/test/java/com/baixingai/voicedrop/PromptShareSourceTest.java`

**Interfaces:**
- Consumes: `PromptStore.textMenu/imageMenu`.
- Produces: no runtime request containing `/ui-config`.

- [ ] **Step 1: Change source-contract tests to require PromptStore and forbid old paths**

```java
assertFalse(readSource("src/main/java/com/baixingai/voicedrop/data/UIConfigStore.java").contains("/ui-config"));
assertTrue(readSource("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java").contains("PromptStore"));
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew testDebugUnitTest --tests '*UIConfigStoreTest' --tests '*PromptShareSourceTest'`
Expected: old paths are still present.

- [ ] **Step 3: Delegate menu and refresh calls to PromptStore**

Keep `MenuConfig` and `MenuNode` as the existing rendering boundary, but remove old document parsing and custom-item HTTP methods.

- [ ] **Step 4: Verify GREEN and commit**

Run: `./gradlew testDebugUnitTest --tests '*UIConfigStoreTest' --tests '*PromptShareSourceTest'`
Expected: no legacy request remains and menu integration tests pass.

Commit: `git commit -am "fix(android): migrate prompt menus to new API"`.

### Task 5: Native manager list, edit/create/group, delete, restore, and error banner

**Files:**
- Replace: `app/src/main/java/com/baixingai/voicedrop/InstructionSettingsActivity.java`
- Create: `app/src/main/java/com/baixingai/voicedrop/PromptEditActivity.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/java/com/baixingai/voicedrop/PromptManagerSourceTest.java`

**Interfaces:**
- Consumes: PromptStore transaction methods.
- Produces: list/edit/create/group/delete/restore UI and cached-data error banner.

- [ ] **Step 1: Write failing UI contract tests**

Assert that manager source contains actions for `新建提示词`, `新建分组`, `导入`, `排序`, `恢复默认`, deletion confirmation, `store.refresh`, and a non-blocking error banner; edit source must call `PromptTree.fork` for system nodes and expose text/image applicability controls.

- [ ] **Step 2: Verify RED**

Run: `./gradlew testDebugUnitTest --tests '*PromptManagerSourceTest'`
Expected: missing manager actions and edit Activity.

- [ ] **Step 3: Implement manager and editor with native Views**

Use nested LinearLayouts for normal mode, bottom sheets for create/group actions, and explicit save callbacks. Disable mutation controls while `PromptStore.isMutating()` is true. A refresh failure with cached rows sets the banner text without clearing rows.

- [ ] **Step 4: Verify GREEN and commit**

Run: `./gradlew testDebugUnitTest --tests '*PromptManagerSourceTest'`
Expected: manager and editor contracts pass.

Commit: `git commit -am "feat(android): add complete prompt manager UI"`.

### Task 6: Drag reorder and cross-group movement

**Files:**
- Create: `app/src/main/java/com/baixingai/voicedrop/ui/PromptDragController.java`
- Modify: `app/src/main/java/com/baixingai/voicedrop/InstructionSettingsActivity.java`
- Create: `app/src/test/java/com/baixingai/voicedrop/PromptDragControllerTest.java`

**Interfaces:**
- Produces: `begin(items)`, `move(id,targetGroup,index)`, `draft()`, `baseline()`, `cancel()`.

- [ ] **Step 1: Write failing controller tests**

Cover top-level reorder, child reorder, move into group, move out of group, group-into-group rejection, and cancel restoring the initial draft.

- [ ] **Step 2: Verify RED**

Run: `./gradlew testDebugUnitTest --tests '*PromptDragControllerTest'`
Expected: controller missing.

- [ ] **Step 3: Implement controller and touch drag handles**

The controller delegates structural changes to `PromptTree.move`; the Activity only maps pointer target rectangles to `(targetGroup,index)` and submits through `applyReorder`.

- [ ] **Step 4: Verify GREEN and commit**

Run: `./gradlew testDebugUnitTest --tests '*PromptDragControllerTest' --tests '*PromptManagerSourceTest'`
Expected: reorder and UI contract tests pass.

Commit: `git commit -am "feat(android): add prompt drag reordering"`.

### Task 7: Import preview, sharing, and deep links

**Files:**
- Create: `app/src/main/java/com/baixingai/voicedrop/PromptImportActivity.java`
- Modify: `app/src/main/java/com/baixingai/voicedrop/PromptEditActivity.java`
- Modify: `app/src/main/java/com/baixingai/voicedrop/AppRouter.java`
- Modify: `app/src/main/java/com/baixingai/voicedrop/RecordingsActivity.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/test/java/com/baixingai/voicedrop/AppRouterTest.java`
- Modify: `app/src/test/java/com/baixingai/voicedrop/PromptManagerSourceTest.java`

**Interfaces:**
- Produces: `AppRouter.Kind.PROMPT_IMPORT` with `shareCode`; import and share screens.

- [ ] **Step 1: Add failing deep-link and source tests**

```java
assertEquals(AppRouter.Kind.PROMPT_IMPORT, AppRouter.parse("https://voicedrop.cn/1234567").kind);
assertEquals("1234567", AppRouter.parse("voicedrop://prompt-import?code=1234567").shareCode);
assertEquals(AppRouter.Kind.SHARE_LINK, AppRouter.parse("https://voicedrop.cn/12345678").kind);
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew testDebugUnitTest --tests '*AppRouterTest' --tests '*PromptManagerSourceTest'`
Expected: prompt import route and screens are missing.

- [ ] **Step 3: Implement import/share flows**

Import Activity uses `mergeCodeInput`, calls preview only for a valid code, preserves input on failure, and calls `importCode` after confirmation. Edit Activity loads `/prompt-shares`, maps 429, copies both code and `https://voicedrop.cn/<code>`, and sends only the URL via `ACTION_SEND`.

- [ ] **Step 4: Verify GREEN and commit**

Run: `./gradlew testDebugUnitTest --tests '*AppRouterTest' --tests '*PromptManagerSourceTest' --tests '*PromptStoreTest'`
Expected: route, import, and share contracts pass.

Commit: `git commit -am "feat(android): add prompt import and sharing"`.

### Task 8: Android full verification and contract cleanup

**Files:**
- Verify: all files changed by Tasks 1–7; production changes in this task are prohibited unless a failing full-suite test identifies a regression attributable to those tasks.

- [ ] **Step 1: Search forbidden endpoints**

Run: `rg -n '/agent/ui-config|/ui-config/custom' app/src/main app/src/test`
Expected: no runtime match; migration assertions may mention the strings only as forbidden values.

- [ ] **Step 2: Run full unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL with zero failed tests.

- [ ] **Step 3: Build debug APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL and debug APK produced.

- [ ] **Step 4: Record manual checks**

Check on emulator/device: drag targets, deep-link import, clipboard, Android share sheet, cached error banner, system fork, restore defaults, and long-press text/image menus.
