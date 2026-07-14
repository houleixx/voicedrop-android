# Mini Program Prompt Manager Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the mini program's removed UI-config integration with the complete iOS Prompt Manager behavior while preserving the user's current uncommitted styling and test changes.

**Architecture:** `utils/prompt-tree.js` contains deterministic schema and tree operations. `services/prompt-store.js` owns requests, cache, mutation serialization, snapshots, and share state. Pages consume this store; movable views manage reorder drafts without placing business rules in WXML handlers.

**Tech Stack:** WeChat Mini Program JavaScript/WXML/WXSS, Node.js built-in test runner, existing request/auth services.

## Global Constraints

- Work in `/Users/holly/code/BaiXingAI/voicedrop-mini` and preserve all pre-existing unstaged changes.
- Do not overwrite current edits in `components/page-header/index.wxss`, prompt page WXSS, recordings WXSS, or their tests.
- Use only the new prompt and prompt-share endpoints; no runtime `/agent/ui-config*` request may remain.
- Keep prompt depth at two levels and preserve text/image placeholder contracts.
- Every production change starts with a failing `node --test` case.
- Before delivery run `npm test` and `npm run validate:miniapp`.

---

### Task 1: Prompt-tree schema, raw conversion, and immutable mutations

**Files:**
- Create: `utils/prompt-tree.js`
- Create: `tests/prompt-tree.test.js`

**Interfaces:**
- Produces: `decodeItems`, `rawItems`, `fork`, `newUserId`, `replace`, `remove`, `append`, `move`, `flattenIds`, `menu`, `extractShareCode`, `mergeCodeInput`.

- [ ] **Step 1: Write failing pure-logic tests**

```js
test('system group serializes as a ref with children', () => {
  const [group] = tree.decodeItems(fixtures.resolved).items
  assert.deepEqual(tree.rawItems([group]), [{ ref: 'sys_rewrite', children: [{ ref: 'sys_concise' }] }])
})

test('pasted eight-digit runs are not truncated to a code', () => {
  assert.equal(tree.extractShareCode('12345678'), null)
  assert.equal(tree.mergeCodeInput('12', '12345678'), '12')
})

test('actions move into and out of groups without nesting groups', () => {
  const moved = tree.move(items, 'p_action01', 'p_group001', 0)
  assert.equal(moved[0].children[0].id, 'p_action01')
  assert.throws(() => tree.move(items, 'p_group001', 'p_group002', 0))
})
```

- [ ] **Step 2: Verify RED**

Run: `node --test tests/prompt-tree.test.js`
Expected: module `../utils/prompt-tree` is missing.

- [ ] **Step 3: Implement exported pure functions**

```js
function rawItem(node) {
  if (node.origin === 'system') return node.type === 'group'
    ? { ref: node.id, children: rawItems(node.children || []) }
    : { ref: node.id }
  const out = { id: node.id, type: node.type, label: node.label }
  if (node.forkedFrom) out.forkedFrom = node.forkedFrom
  if (node.type === 'group') out.children = rawItems(node.children || [])
  else Object.assign(out, { prompt: node.prompt, appliesTo: node.appliesTo }, node.kind ? { kind: node.kind } : {})
  return out
}
```

`decodeItems` must reject schema values above 1 and recursively normalize declared fields; `fork` deep-clones a node and sets a new `p_` ID, `forkedFrom`, and `origin:'custom'`; `replace`, `remove`, and `append` return new arrays; `move` removes one node and inserts it at a bounded target index while rejecting group nesting; `flattenIds` returns preorder IDs; `menu` retains actions whose `appliesTo` contains the requested anchor; `extractShareCode` uses `/(?<![0-9])[1-9][0-9]{6}(?![0-9])/`; `mergeCodeInput` accepts single-character tail edits but requires a boundary-valid match for paste/autofill.

- [ ] **Step 4: Verify GREEN and commit in the mini repository**

Run: `node --test tests/prompt-tree.test.js`
Expected: all prompt-tree tests pass.

Commit only new Task 1 files: `git commit -m "feat(mini): add prompt tree contract"`.

### Task 2: Prompt store, cache fallback, transactions, and endpoint contract

**Files:**
- Create: `services/prompt-store.js`
- Create: `tests/prompt-store.test.js`
- Modify: `services/request.js` to export the existing request methods as the injectable object consumed by `createStore`.

**Interfaces:**
- Produces: `createStore({request,storage,auth})`, singleton `store`, and methods matching Android `refresh/save/replace/remove/add/applyReorder/restoreDefaults/preview/importCode/shareStates/setSharing/menu`.

- [ ] **Step 1: Write failing store tests**

```js
test('refresh keeps cached items and exposes an error on network failure', async () => {
  const store = createStore({ storage: memoryWith(fixtures.resolved), request: rejectingRequest, auth })
  const result = await store.refresh()
  assert.equal(result.ok, false)
  assert.equal(store.items().length, 1)
  assert.equal(store.error(), '加载失败')
})

test('failed save restores the full snapshot', async () => {
  const before = store.items()
  request.putJson = async () => ({ statusCode: 500 })
  assert.equal((await store.remove('sys_concise')).ok, false)
  assert.deepEqual(store.items(), before)
})
```

- [ ] **Step 2: Verify RED**

Run: `node --test tests/prompt-store.test.js`
Expected: prompt-store module missing.

- [ ] **Step 3: Implement store**

```js
function createStore(deps) {
  let items = loadCache(deps.storage)
  let mutating = false
  async function transact(makeDraft) {
    if (mutating) return { ok: false, error: 'busy' }
    const snapshot = clone(items)
    mutating = true
    items = makeDraft(items)
    try {
      const res = await deps.request.putJson(`${api.agentBase()}/prompts`, deps.auth.bearer(), { items: tree.rawItems(items) })
      if (res.statusCode < 200 || res.statusCode >= 300) {
        items = snapshot
        return { ok: false, error: 'save_failed' }
      }
      items = tree.decodeItems(res.data).items
      saveCache(deps.storage, items)
      return { ok: true, items: clone(items) }
    } catch (_) {
      items = snapshot
      return { ok: false, error: 'network_error' }
    } finally {
      mutating = false
    }
  }
  return { items: () => clone(items), refresh, replace, remove, add, applyReorder, restoreDefaults, preview, importCode, shareStates, setSharing }
}
```

- [ ] **Step 4: Add exact endpoint assertions and verify GREEN**

Assert GET/PUT `/agent/prompts`, POST restore/import/share, GET shares/preview, and DELETE encoded item ID.

Run: `node --test tests/prompt-store.test.js`
Expected: cache, endpoint, rollback, busy lock, and conflict tests pass.

Commit Task 2 files: `git commit -m "feat(mini): add prompt store and transactions"`.

### Task 3: Migrate long-press menus and remove legacy runtime services

**Files:**
- Modify: `components/config-menu/index.js`
- Modify: `pages/detail/index.js`
- Modify: `tests/ui-config.test.js`
- Modify: `tests/editing.test.js`
- Delete after callers migrate: `services/ui-config.js`
- Delete after callers migrate: `services/instruction-settings.js`
- Reduce or delete after callers migrate: `utils/ui-config.js`

**Interfaces:**
- Consumes: `promptStore.menu('text'|'image')`.
- Produces: no old endpoint calls.

- [ ] **Step 1: Write failing migration assertions**

```js
test('runtime source no longer requests removed ui-config endpoints', () => {
  for (const file of runtimeJsFiles()) assert.doesNotMatch(read(file), /\/agent\/ui-config|\/ui-config\/custom/)
})
```

- [ ] **Step 2: Verify RED**

Run: `node --test tests/ui-config.test.js tests/editing.test.js`
Expected: legacy services still match.

- [ ] **Step 3: Switch menu callers to prompt-store and delete dead service code**

Keep the existing menu component input shape; generate groups via `prompt-tree.menu` so WXML rendering need not understand resolved nodes.

- [ ] **Step 4: Verify GREEN and commit only Task 3 files**

Run: `node --test tests/ui-config.test.js tests/editing.test.js`
Expected: menu behavior passes and forbidden endpoint assertion passes.

Commit: `git commit -m "fix(mini): migrate prompt menus to new API"`.

### Task 4: Manager list, create/group, delete, restore, and cached error state

**Files:**
- Modify: `pages/instruction-settings/index.js`
- Modify: `pages/instruction-settings/index.wxml`
- Carefully extend: `pages/instruction-settings/index.wxss`
- Add page: `pages/prompt-new/index.js`
- Add page: `pages/prompt-new/index.wxml`
- Add page: `pages/prompt-new/index.wxss`
- Add page config: `pages/prompt-new/index.json`
- Modify: `app.json`
- Carefully extend: `tests/instruction-settings-page.test.js`

**Interfaces:**
- Consumes: store list/add/remove/restore methods.
- Produces: nested manager rows and complete create/group/delete/restore flows.

- [ ] **Step 1: Append failing page tests without removing existing gutter assertions**

Cover nested rows, origin badges, cached rows plus error banner, new prompt/group navigation, deletion confirmation, restore-default request, and disabled mutation controls.

- [ ] **Step 2: Verify RED**

Run: `node --test tests/instruction-settings-page.test.js`
Expected: new page actions and prompt-new page are missing; existing user-added gutter test still passes.

- [ ] **Step 3: Implement pages and preserve current WXSS changes**

The manager maps store nodes to `{id,type,title,preview,origin,depth,expanded}` rows. The new page validates trimmed label/prompt and at least one applicability value, then creates a `p_` entity through the store.

- [ ] **Step 4: Verify GREEN and commit only prompt manager files**

Run: `node --test tests/instruction-settings-page.test.js`
Expected: old and new page tests pass.

Commit: `git commit -m "feat(mini): add complete prompt manager list"`.

### Task 5: Edit/fork/share behavior

**Files:**
- Modify: `pages/instruction-edit/index.js`
- Modify: `pages/instruction-edit/index.wxml`
- Carefully extend: `pages/instruction-edit/index.wxss`
- Create: `tests/prompt-edit-page.test.js`

**Interfaces:**
- Consumes: tree fork and store replace/share methods.
- Produces: action/group editing and share card.

- [ ] **Step 1: Write failing edit tests**

Test system fork-on-save, custom in-place edit, no-op save, validation, applicability toggles, group rename, failed save staying on page, share-state loading, 429 rollback, code/link copy, and share card query.

- [ ] **Step 2: Verify RED**

Run: `node --test tests/prompt-edit-page.test.js`
Expected: new store-based behavior is absent.

- [ ] **Step 3: Implement edit and share flows**

```js
const next = item.origin === 'system' ? tree.fork(item) : tree.cloneNode(item)
next.label = this.data.nameDraft.trim()
next.prompt = this.data.instructionDraft.trim()
next.appliesTo = selectedAppliesTo(this.data)
const result = await promptStore.replace(item.id, next)
if (result.ok) wx.navigateBack()
```

- [ ] **Step 4: Verify GREEN and commit**

Run: `node --test tests/prompt-edit-page.test.js tests/instruction-settings-page.test.js`
Expected: edit, fork, share, and existing style tests pass.

Commit: `git commit -m "feat(mini): add prompt editing and sharing"`.

### Task 6: Movable reorder and cross-group drag

**Files:**
- Create: `utils/prompt-drag.js`
- Create: `tests/prompt-drag.test.js`
- Modify: `pages/instruction-settings/index.js`
- Modify: `pages/instruction-settings/index.wxml`
- Carefully extend: `pages/instruction-settings/index.wxss`

**Interfaces:**
- Produces: `begin`, `move`, `draft`, `baseline`, `cancel`, `commit` controller.

- [ ] **Step 1: Write failing drag tests**

Cover top-level/child reorder, move into/out of group, illegal group nesting, cancellation, changed-store conflict, and failed PUT preserving draft.

- [ ] **Step 2: Verify RED**

Run: `node --test tests/prompt-drag.test.js`
Expected: drag module missing.

- [ ] **Step 3: Implement controller and movable-area bindings**

WXML emits item ID and target zone; controller alone updates tree structure. `finishReorder` passes baseline to `promptStore.applyReorder`; conflict keeps `reordering: true` and draft rows visible.

- [ ] **Step 4: Verify GREEN and commit**

Run: `node --test tests/prompt-drag.test.js tests/instruction-settings-page.test.js`
Expected: all drag and manager tests pass.

Commit: `git commit -m "feat(mini): add prompt drag reordering"`.

### Task 7: Magic-number preview/import and mini-program routing

**Files:**
- Add page: `pages/prompt-import/index.js`
- Add page: `pages/prompt-import/index.wxml`
- Add page: `pages/prompt-import/index.wxss`
- Add page config: `pages/prompt-import/index.json`
- Modify: `pages/instruction-settings/index.js`
- Modify: `app.js`
- Modify: `app.json`
- Modify: prompt page `onShareAppMessage` methods
- Create: `tests/prompt-import-page.test.js`
- Modify: `tests/router-share.test.js`

**Interfaces:**
- Produces: `promptCode` query routing and import page.

- [ ] **Step 1: Write failing input/routing tests**

Test typing, paste, 8-digit rejection, public preview, 404, confirm import, retry preservation, `onLaunch({query:{promptCode:'1234567'}})`, and share path `/pages/prompt-import/index?promptCode=1234567`.

- [ ] **Step 2: Verify RED**

Run: `node --test tests/prompt-import-page.test.js tests/router-share.test.js`
Expected: import page and promptCode route missing.

- [ ] **Step 3: Implement page and route**

Both App launch routing and page `onLoad` normalize through `extractShareCode`; invalid query values do not navigate. Successful import refreshes the store and returns to settings.

- [ ] **Step 4: Verify GREEN and commit**

Run: `node --test tests/prompt-import-page.test.js tests/router-share.test.js`
Expected: import and routing tests pass.

Commit: `git commit -m "feat(mini): add prompt magic-number import"`.

### Task 8: Mini-program full verification and dirty-worktree audit

**Files:**
- Verify: all files changed by Tasks 1–7; production changes in this task are prohibited unless a failing full-suite test identifies a regression attributable to those tasks.

- [ ] **Step 1: Compare pre-existing user changes**

Run: `git diff -- components/page-header/index.wxss pages/instruction-edit/index.wxss pages/instruction-settings/index.wxss pages/recordings/index.wxss tests/instruction-settings-page.test.js tests/page-header-style.test.js tests/recordings-layout.test.js`
Expected: the original gutter/loading/style changes are still present alongside additive Prompt Manager changes.

- [ ] **Step 2: Search forbidden endpoints**

Run: `rg -n '/agent/ui-config|/ui-config/custom' . --glob '!node_modules/**' --glob '!docs/**'`
Expected: no runtime request; tests may contain forbidden-string assertions only.

- [ ] **Step 3: Run all tests**

Run: `npm test`
Expected: all Node test files pass with zero failures.

- [ ] **Step 4: Validate mini-app structure**

Run: `npm run validate:miniapp`
Expected: validation succeeds with all registered pages and referenced assets present.

- [ ] **Step 5: Record real-device checks**

Use WeChat DevTools/device for movable drag, share-card query, clipboard, cold-launch import, cached refresh banner, system fork, restore defaults, and long-press text/image menu filtering.
