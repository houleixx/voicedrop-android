package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Keeps the title fixed, resizes content above the IME, and dismisses it on a real drag. */
public final class BookWritingKeyboardContractTest {
    @Test public void writingPageResizesAndDismissesKeyboardOnScroll() throws Exception {
        String manifest = read("src/main/AndroidManifest.xml");
        String activity = read("src/main/java/com/baixingai/voicedrop/BookWritingActivity.java");

        assertTrue(manifest.contains("android:name=\".BookWritingActivity\""));
        assertTrue(manifest.contains("android:windowSoftInputMode=\"adjustResize\""));
        assertTrue(activity.contains("applyImeAvoidance(page)"));
        assertTrue(activity.contains("WindowInsetsCompat.Type.ime()"));
        assertTrue(activity.contains("target.setPadding(0, 0, 0, ime.bottom)"));
        assertTrue(activity.contains("dismissKeyboardWhenDragging(scroll)"));
        assertTrue(activity.contains("ViewConfiguration.get(this).getScaledTouchSlop()"));
        assertTrue(activity.contains("MotionEvent.ACTION_MOVE"));
        assertTrue(activity.contains("hideKeyboard();"));
        assertTrue(activity.contains("return false;"));
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.exists(path)) path = Paths.get("app", relative.replaceFirst("^src/main/", "src/main/"));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
