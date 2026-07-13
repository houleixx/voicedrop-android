# VoiceDrop Android Privacy Consent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require explicit, versioned local privacy consent before VoiceDrop starts analytics, referral attribution, networking, WebSockets, update checks, or other business behavior.

**Architecture:** A `PrivacyConsent` state object owns the policy URL/version and persists acceptance in private `SharedPreferences`. A dedicated `PrivacyConsentDialog` renders an uncancelable required choice. Exported entry activities split minimal UI setup from business initialization, while `VoiceDropApplication` exposes one idempotent activation method for consented services.

**Tech Stack:** Java 17, Android native Views, SharedPreferences, DialogX/IosDialog, JUnit 4, Gradle.

## Global Constraints

- Privacy policy URL is exactly `https://voicedrop.cn/privacy/`.
- Current policy version is exactly `2026-07-12`.
- Consent is local-only and is never uploaded or resolved through an account.
- Before consent, do not initialize Umeng, read the clipboard, create an anonymous account, make HTTP requests, connect WebSockets, refresh data, or check for updates.
- Buttons are exactly “同意并继续” and “不同意并退出”.
- Keep Umeng dependencies, build configuration, release secret validation, and tests.
- Remove only the README “友盟统计” documentation section.
- Do not modify iOS, mini-program, backend, HTTP/WebSocket contracts, recording names, article schema, or photo markers.

---

### Task 1: Versioned local consent state

**Files:**
- Create: `app/src/main/java/com/baixingai/voicedrop/data/PrivacyConsent.java`
- Create: `app/src/test/java/com/baixingai/voicedrop/data/PrivacyConsentTest.java`

**Interfaces:**
- Produces: `PrivacyConsent(Context)`, `boolean isAccepted()`, `void accept()`, `String POLICY_URL`, `String CURRENT_VERSION`.
- Internal test seam: package-private `PrivacyConsent(Storage storage, Clock clock)` with `Storage` and `Clock` interfaces.

- [ ] **Step 1: Write the failing state tests**

```java
public final class PrivacyConsentTest {
    private final FakeStorage storage = new FakeStorage();

    @Test public void currentVersionStartsUnaccepted() {
        assertFalse(new PrivacyConsent(storage, () -> 1234L).isAccepted());
    }

    @Test public void acceptingStoresCurrentVersionAndTimestamp() {
        PrivacyConsent consent = new PrivacyConsent(storage, () -> 1234L);
        consent.accept();
        assertTrue(consent.isAccepted());
        assertEquals(PrivacyConsent.CURRENT_VERSION, storage.version);
        assertEquals(1234L, storage.acceptedAt);
    }

    @Test public void oldAcceptedVersionDoesNotAcceptCurrentPolicy() {
        storage.version = "2026-01-01";
        assertFalse(new PrivacyConsent(storage, () -> 1234L).isAccepted());
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.baixingai.voicedrop.data.PrivacyConsentTest`

Expected: compilation fails because `PrivacyConsent` does not exist.

- [ ] **Step 3: Implement the minimal state object**

Implement constants, a private SharedPreferences adapter using file `voicedrop.privacy`, keys `acceptedVersion` and `acceptedAt`, exact-version comparison, and a single `edit().putString(...).putLong(...).apply()` acceptance write.

```java
public static final String POLICY_URL = "https://voicedrop.cn/privacy/";
public static final String CURRENT_VERSION = "2026-07-12";

public boolean isAccepted() {
    return CURRENT_VERSION.equals(storage.acceptedVersion());
}

public void accept() {
    storage.saveAcceptance(CURRENT_VERSION, clock.now());
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests com.baixingai.voicedrop.data.PrivacyConsentTest`

Expected: all three tests pass.

- [ ] **Step 5: Commit the state component**

```bash
git add app/src/main/java/com/baixingai/voicedrop/data/PrivacyConsent.java app/src/test/java/com/baixingai/voicedrop/data/PrivacyConsentTest.java
git commit -m "feat: persist versioned privacy consent"
```

---

### Task 2: Uncancelable privacy choice dialog

**Files:**
- Create: `app/src/main/java/com/baixingai/voicedrop/ui/PrivacyConsentDialog.java`
- Modify: `app/src/main/java/com/baixingai/voicedrop/ui/IosDialog.java`
- Create: `app/src/test/java/com/baixingai/voicedrop/PrivacyConsentDialogSourceTest.java`

**Interfaces:**
- Consumes: `PrivacyConsent.POLICY_URL`.
- Produces: `PrivacyConsentDialog.show(Activity activity, Runnable onAccept, Runnable onDecline)`.
- Produces: `IosDialog.showRequiredChoice(Context, String, View, String, Runnable, String, Runnable)` returning the shown dialog.

- [ ] **Step 1: Write the failing source contract test**

```java
@Test public void dialogIsRequiredAndLinksThePublishedPolicy() throws Exception {
    String dialog = readRoot("app/src/main/java/com/baixingai/voicedrop/ui/PrivacyConsentDialog.java");
    String ios = readRoot("app/src/main/java/com/baixingai/voicedrop/ui/IosDialog.java");
    assertTrue(dialog.contains("隐私保护提示"));
    assertTrue(dialog.contains("同意并继续"));
    assertTrue(dialog.contains("不同意并退出"));
    assertTrue(dialog.contains("PrivacyConsent.POLICY_URL"));
    assertTrue(dialog.contains("LinkMovementMethod.getInstance()"));
    assertTrue(dialog.contains("IosDialog.showRequiredChoice"));
    assertTrue(ios.contains("dialog.setCancelable(false)"));
    assertTrue(ios.contains("dialog.setCanceledOnTouchOutside(false)"));
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.baixingai.voicedrop.PrivacyConsentDialogSourceTest`

Expected: failure because the dialog source file and required-choice API do not exist.

- [ ] **Step 3: Add the required-choice IosDialog API**

Create a static API that calls `buildWithView` with `dismissOnOutside=false` and `includeDefaultCancelButton=false`, then calls both `setCancelable(false)` and `setCanceledOnTouchOutside(false)` before `show()`.

- [ ] **Step 4: Implement the privacy dialog**

Build a native `TextView` with readable line spacing and a `SpannableString`. Attach a `ClickableSpan` to “《隐私政策》”; open `PrivacyConsent.POLICY_URL` with `Intent.ACTION_VIEW`. Catch `ActivityNotFoundException`/runtime failures, keep the dialog open, and call `SimpleToast.show(activity, "暂时无法打开隐私政策")`.

The summary must state that VoiceDrop processes user-provided recordings and text for transcription/article generation, and that the user can read the full policy before deciding. Pass callbacks to the exact two buttons without an extra default Cancel button.

- [ ] **Step 5: Run focused dialog tests and existing IosDialog tests**

Run: `./gradlew testDebugUnitTest --tests com.baixingai.voicedrop.PrivacyConsentDialogSourceTest --tests com.baixingai.voicedrop.IosDialogSourceTest`

Expected: both test classes pass.

- [ ] **Step 6: Commit the dialog**

```bash
git add app/src/main/java/com/baixingai/voicedrop/ui/PrivacyConsentDialog.java app/src/main/java/com/baixingai/voicedrop/ui/IosDialog.java app/src/test/java/com/baixingai/voicedrop/PrivacyConsentDialogSourceTest.java
git commit -m "feat: add required privacy consent dialog"
```

---

### Task 3: Delay Umeng and referral activation

**Files:**
- Modify: `app/src/main/java/com/baixingai/voicedrop/VoiceDropApplication.java`
- Modify: `app/src/test/java/com/baixingai/voicedrop/UmengAnalyticsSourceTest.java`

**Interfaces:**
- Consumes: `new PrivacyConsent(this).isAccepted()`.
- Produces: `public synchronized void activateConsentedServices()`; safe to call repeatedly.

- [ ] **Step 1: Change the Umeng test first**

Add assertions that `onCreate()` checks consent and calls the shared activation entry, while the activation method owns both Umeng and referral startup and has an idempotence guard.

```java
assertTrue(source.contains("new PrivacyConsent(this).isAccepted()"));
assertTrue(source.contains("activateConsentedServices();"));
assertTrue(source.contains("public synchronized void activateConsentedServices()"));
assertTrue(source.contains("if (consentedServicesActivated) return;"));
assertTrue(source.contains("new ReferralManager(this).runOnLaunch();"));
```

Also isolate the `onCreate` method body and assert it does not directly contain `UMConfigure.init` or `new ReferralManager(this).runOnLaunch()`.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.baixingai.voicedrop.UmengAnalyticsSourceTest`

Expected: new consent/idempotence assertions fail.

- [ ] **Step 3: Implement delayed activation**

Move Umeng and referral startup behind:

```java
if (new PrivacyConsent(this).isAccepted()) activateConsentedServices();
```

Implement:

```java
public synchronized void activateConsentedServices() {
    if (consentedServicesActivated) return;
    consentedServicesActivated = true;
    initUmengAnalytics();
    new ReferralManager(this).runOnLaunch();
}
```

Keep DialogX setup and system-bar callbacks unconditional because they are local UI prerequisites.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests com.baixingai.voicedrop.UmengAnalyticsSourceTest`

Expected: all Umeng source tests pass.

- [ ] **Step 5: Commit delayed activation**

```bash
git add app/src/main/java/com/baixingai/voicedrop/VoiceDropApplication.java app/src/test/java/com/baixingai/voicedrop/UmengAnalyticsSourceTest.java
git commit -m "fix: delay consented services until acceptance"
```

---

### Task 4: Gate every exported user entry before business initialization

**Files:**
- Modify: `app/src/main/java/com/baixingai/voicedrop/RecordingsActivity.java`
- Modify: `app/src/main/java/com/baixingai/voicedrop/ShareCollectActivity.java`
- Create: `app/src/test/java/com/baixingai/voicedrop/PrivacyConsentEntryGateSourceTest.java`

**Interfaces:**
- Consumes: `PrivacyConsent`, `PrivacyConsentDialog`, `VoiceDropApplication.activateConsentedServices()`.
- Produces in each Activity: a minimal `onCreate`, an idempotent `continueAfterPrivacyConsent(Bundle)` method, and a `privacyConsentPending`/`businessInitialized` lifecycle guard.

- [ ] **Step 1: Write failing entry-gate source tests**

The tests must extract `onCreate`, `continueAfterPrivacyConsent`, and `onResume` method bodies and assert:

```java
assertTrue(onCreate.contains("PrivacyConsent consent = new PrivacyConsent(this)"));
assertTrue(onCreate.contains("PrivacyConsentDialog.show"));
assertFalse(onCreate.contains("new AuthStore(this)"));
assertFalse(onCreate.contains("refreshAndDrain()"));
assertTrue(continueMethod.contains("new AuthStore(this)"));
assertTrue(continueMethod.contains("activateConsentedServices()"));
assertTrue(onResume.contains("if (!businessInitialized) return;"));
```

For `ShareCollectActivity`, additionally assert `loadPayload`, `classify`, and `render` only occur in `continueAfterPrivacyConsent`.

- [ ] **Step 2: Run the entry-gate test and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.baixingai.voicedrop.PrivacyConsentEntryGateSourceTest`

Expected: failure because both activities initialize business immediately.

- [ ] **Step 3: Gate RecordingsActivity**

Refactor `onCreate` to set a minimal background/root view, then:

```java
PrivacyConsent consent = new PrivacyConsent(this);
if (consent.isAccepted()) {
    continueAfterPrivacyConsent(savedInstanceState);
    return;
}
privacyConsentPending = true;
PrivacyConsentDialog.show(this, () -> {
    consent.accept();
    privacyConsentPending = false;
    continueAfterPrivacyConsent(savedInstanceState);
}, this::finishAndRemoveTask);
```

Move all existing `AuthStore` through `onPageCreate(getIntent())` initialization into `continueAfterPrivacyConsent`. Guard it with `if (businessInitialized) return;`, set `businessInitialized = true` before starting page behavior, and call `((VoiceDropApplication) getApplication()).activateConsentedServices()` before business networking begins.

Add `if (!businessInitialized) return;` immediately after `super.onResume()`. Guard `onNewIntent` until initialized; retain the latest Intent with `setIntent(intent)` so it can be processed after acceptance.

- [ ] **Step 4: Gate ShareCollectActivity**

Make `onCreate` install only the minimal scrim/root and run the same consent decision. Move `AuthStore`, `HttpClient`, `ShareApi`, `loadPayload`, `classify`, and `render` to idempotent `continueAfterPrivacyConsent`. On decline call `finishAndRemoveTask()`; on acceptance save first, activate consented services, then load the shared payload.

- [ ] **Step 5: Run the focused gate test and affected source tests**

Run: `./gradlew testDebugUnitTest --tests com.baixingai.voicedrop.PrivacyConsentEntryGateSourceTest --tests com.baixingai.voicedrop.RecordingsActivitySourceTest --tests com.baixingai.voicedrop.ShareRouterTest`

Expected: all selected tests pass.

- [ ] **Step 6: Commit both entry gates**

```bash
git add app/src/main/java/com/baixingai/voicedrop/RecordingsActivity.java app/src/main/java/com/baixingai/voicedrop/ShareCollectActivity.java app/src/test/java/com/baixingai/voicedrop/PrivacyConsentEntryGateSourceTest.java
git commit -m "feat: gate app business behind privacy consent"
```

---

### Task 5: Permanent policy access and README cleanup

**Files:**
- Modify: `app/src/main/java/com/baixingai/voicedrop/AboutActivity.java`
- Modify: `app/src/test/java/com/baixingai/voicedrop/AboutIconTest.java`
- Modify: `README.md`
- Create: `app/src/test/java/com/baixingai/voicedrop/PrivacyDocumentationSourceTest.java`

**Interfaces:**
- Consumes: `PrivacyConsent.POLICY_URL`.
- Changes the About privacy row to open the published page.

- [ ] **Step 1: Write failing documentation/access tests**

```java
@Test public void aboutOpensPublishedPrivacyPolicy() throws Exception {
    String source = readRoot("app/src/main/java/com/baixingai/voicedrop/AboutActivity.java");
    assertTrue(source.contains("PrivacyConsent.POLICY_URL"));
    assertTrue(source.contains("Intent.ACTION_VIEW"));
    assertFalse(source.contains("录音只上传到你自己的云端空间"));
}

@Test public void readmeDoesNotDocumentUmeng() throws Exception {
    String readme = readRoot("README.md");
    assertFalse(readme.contains("## 友盟统计"));
    assertFalse(readme.contains("umeng.appKey"));
    assertFalse(readme.contains("UMENG_APP_KEY"));
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.baixingai.voicedrop.PrivacyDocumentationSourceTest`

Expected: assertions fail against the current About row and README.

- [ ] **Step 3: Update AboutActivity**

Replace the short local dialog with a helper that launches `Intent.ACTION_VIEW` for `PrivacyConsent.POLICY_URL`. Catch launch failure and show `SimpleToast` without changing consent state.

- [ ] **Step 4: Remove the README section**

Delete from `## 友盟统计` through the divider immediately before `## 注意事项`. Do not change Gradle, workflow, secret names, or Umeng tests.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests com.baixingai.voicedrop.PrivacyDocumentationSourceTest --tests com.baixingai.voicedrop.AboutIconTest`

Expected: both test classes pass.

- [ ] **Step 6: Commit policy access and README cleanup**

```bash
git add app/src/main/java/com/baixingai/voicedrop/AboutActivity.java app/src/test/java/com/baixingai/voicedrop/AboutIconTest.java app/src/test/java/com/baixingai/voicedrop/PrivacyDocumentationSourceTest.java README.md
git commit -m "docs: link published privacy policy"
```

---

### Task 6: Full verification and manual handoff

**Files:**
- Modify only if verification reveals a defect in the files above.

**Interfaces:**
- Verifies all deliverables; produces no new runtime API.

- [ ] **Step 1: Run all JVM tests**

Run: `./gradlew testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`, zero failed tests.

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL` and a debug APK under `app/build/outputs/apk/debug/`.

- [ ] **Step 3: Inspect the final diff**

Run: `git diff HEAD~4 --check` and `git status --short`.

Expected: no whitespace errors; only planned files are changed/untracked.

- [ ] **Step 4: Record manual verification steps**

On an emulator or device with cleared App data:

1. Launch normally and confirm only the privacy dialog appears, with no home refresh, update prompt, permission prompt, or network-driven content behind it.
2. Press Back and tap outside; confirm the dialog remains.
3. Open 《隐私政策》 and confirm `https://voicedrop.cn/privacy/` loads.
4. Choose “不同意并退出”; confirm the task closes and relaunch shows the dialog again.
5. Choose “同意并继续”; confirm the home loads and normal analytics/referral/business startup proceeds.
6. Relaunch; confirm the dialog does not repeat.
7. Clear App data; confirm the dialog appears again.
8. Invoke the Android Sharesheet entry on a clean install; confirm the same gate appears before reading/rendering shared content.

- [ ] **Step 5: Report the remaining policy mismatch**

State explicitly in delivery notes that the published policy still says no third-party analytics/tracking while Android retains Umeng, and that the policy must be corrected before store submission.
