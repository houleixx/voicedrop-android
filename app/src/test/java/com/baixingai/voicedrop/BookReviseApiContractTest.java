package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Locks Android to the live backend's owner-only asynchronous book revision contract. */
public final class BookReviseApiContractTest {
    @Test public void bottomSheetUsesHistoryRevisionPollingAndReaderReloadContract() throws Exception {
        String revise = read("BookReviseBottomSheet.java");
        String reader = read("BookReaderActivity.java");
        String dialog = readUi("IosDialog.java");
        Path manifestPath = Paths.get("src/main/AndroidManifest.xml");
        if (!Files.exists(manifestPath)) manifestPath = Paths.get("app/src/main/AndroidManifest.xml");
        String manifest = new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8);

        assertTrue(revise.contains("HISTORY_API = \"https://lab.jianshuo.dev/api/book/history\""));
        assertTrue(revise.contains("REVISE_API = \"https://lab.jianshuo.dev/api/book/revise\""));
        assertTrue(revise.contains("POLL_INTERVAL_MS = 6_000L"));
        assertTrue(revise.contains("IosDialog.showBottomSheetFixedContent"));
        assertTrue(dialog.contains("showBottomSheetFixedContent"));
        assertTrue(dialog.contains("if (wrapContentInScrollView)"));
        assertTrue(revise.contains("Math.round(screenHeightDp * 0.84f) - 74"));
        assertTrue(revise.contains("composer.setVisibility(View.GONE)"));
        assertTrue(revise.contains("sendParams.gravity = Gravity.BOTTOM"));
        assertTrue(revise.contains("row.setBaselineAligned(false)"));
        assertTrue(revise.contains("DialogWindowDefaults.hideNavigationBar(dialog.getWindow())"));
        assertTrue(revise.contains("sendIcon.setIcon(RemixIconGlyph.ARROW_UP_LINE)"));
        assertTrue(dialog.contains("bottomSheet && wrapContentInScrollView ? dp(ctx, 20) : 0"));
        assertTrue(revise.contains("event.getActionMasked() == MotionEvent.ACTION_MOVE"));
        assertTrue(revise.contains("hideKeyboard();"));
        assertTrue(revise.contains("loading.setTranslationY(-dp(180))"));
        assertTrue(revise.contains("deniedContent.setTranslationY(-dp(180))"));
        assertTrue(revise.contains("deniedIcon.setIcon(RemixIconGlyph.LOCK)"));
        assertTrue(!revise.contains("🔒"));
        assertTrue(revise.contains("dialog.setOnDismissListener(ignored -> destroy())"));
        assertTrue(revise.contains("started = true;"));
        assertTrue(revise.contains("started = false;"));
        assertTrue(revise.contains("if (!started || destroyed) return;"));
        assertTrue(revise.contains("if (running && started && !destroyed && !activity.isFinishing())"));
        assertTrue(revise.contains("if (started && !destroyed) handler.postDelayed(poll, POLL_INTERVAL_MS);"));
        assertTrue(revise.contains("if (destroyed) return;"));
        assertTrue(revise.contains("destroyed = true;"));
        assertTrue(revise.contains("new AuthStore(activity).bearer()"));
        assertTrue(revise.contains(".put(\"slug\", slug).put(\"instruction\", instruction)"));
        assertTrue(revise.contains("response.code == 401"));
        assertTrue(revise.contains("response.code == 403"));
        assertTrue(revise.contains("response.code == 404"));
        assertTrue(revise.contains("result.code == 409"));
        assertTrue(revise.contains("MobclickAgent.onEvent"));
        assertTrue(reader.contains("BookReviseBottomSheet.show"));
        assertTrue(reader.contains("web.reload()"));
        assertTrue(!manifest.contains("android:name=\".BookReviseActivity\""));
    }

    private static String read(String name) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop", name);
        if (!Files.exists(path)) path = Paths.get("app/src/main/java/com/baixingai/voicedrop", name);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String readUi(String name) throws Exception {
        Path path = Paths.get("src/main/java/com/baixingai/voicedrop/ui", name);
        if (!Files.exists(path)) path = Paths.get("app/src/main/java/com/baixingai/voicedrop/ui", name);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
