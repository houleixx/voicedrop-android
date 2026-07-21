package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PageTransitionAnimationResourceTest {
    @Test
    public void sheetAnimationsDoNotRenumberEstablishedPageAnimations() throws Exception {
        Path animDir = rootPath("app/src/main/res/anim");
        String sheetEnter = "window_sheet_slide_in_bottom.xml";
        String sheetExit = "window_sheet_slide_out_bottom.xml";

        assertTrue(Files.exists(animDir.resolve(sheetEnter)));
        assertTrue(Files.exists(animDir.resolve(sheetExit)));
        assertTrue(sheetEnter.compareTo("stay.xml") > 0);
        assertTrue(sheetExit.compareTo("stay.xml") > 0);
        assertFalse(Files.exists(animDir.resolve("slide_in_bottom.xml")));
        assertFalse(Files.exists(animDir.resolve("slide_out_bottom.xml")));

        String styles = new String(Files.readAllBytes(rootPath("app/src/main/res/values/styles.xml")),
                StandardCharsets.UTF_8);
        assertTrue(styles.contains("@anim/window_sheet_slide_in_bottom"));
        assertTrue(styles.contains("@anim/window_sheet_slide_out_bottom"));
    }

    private static Path rootPath(String relative) {
        Path path = Paths.get(relative);
        return Files.exists(path) ? path : Paths.get("..", relative);
    }
}
