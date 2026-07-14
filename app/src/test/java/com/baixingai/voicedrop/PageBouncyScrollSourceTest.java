package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class PageBouncyScrollSourceTest {
    private static final String[] BOUNCY_PAGE_SOURCES = {
            "src/main/java/com/baixingai/voicedrop/AboutActivity.java",
            "src/main/java/com/baixingai/voicedrop/AccountActivity.java",
            "src/main/java/com/baixingai/voicedrop/CommunityDetailActivity.java",
            "src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java",
            "src/main/java/com/baixingai/voicedrop/SettingsActivity.java",
            "src/main/java/com/baixingai/voicedrop/ShareCollectActivity.java",
            "src/main/java/com/baixingai/voicedrop/UsageActivity.java",
            "src/main/java/com/baixingai/voicedrop/WechatSettingsActivity.java"
    };

    @Test
    public void nonRefreshPagesUseBouncyScrollView() throws Exception {
        for (String path : BOUNCY_PAGE_SOURCES) {
            String source = readSource(path);

            assertTrue(path, source.contains("import com.baixingai.voicedrop.ui.BouncyScrollView;"));
            assertTrue(path, source.contains("BouncyScrollView scroll = new BouncyScrollView(this);"));
            assertFalse(path, source.contains("import android.widget.ScrollView;"));
            assertFalse(path, source.contains("ScrollView scroll = new ScrollView(this);"));
        }
    }

    @Test
    public void refreshListsAndDialogsKeepRegularScrollView() throws Exception {
        assertKeepsRegularScrollInstance("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        assertCommunityUsesRefreshableRecyclerFeed("src/main/java/com/baixingai/voicedrop/CommunityActivity.java");
        assertKeepsRegularScrollInstance("src/main/java/com/baixingai/voicedrop/ui/IosDialog.java");
        assertDoesNotUseBouncyScroll("src/main/java/com/baixingai/voicedrop/update/AppUpdateManager.java");
    }

    private static void assertCommunityUsesRefreshableRecyclerFeed(String path) throws Exception {
        String source = readSource(path);

        assertTrue(path, source.contains("new CommunityFeedView"));
        assertTrue(path, source.contains("PullRefreshLayout refresher"));
        assertFalse(path, source.contains("import com.baixingai.voicedrop.ui.BouncyScrollView;"));
    }

    private static void assertKeepsRegularScrollInstance(String path) throws Exception {
        String source = readSource(path);

        assertTrue(path, source.contains("import android.widget.ScrollView;"));
        assertTrue(path, source.contains("ScrollView scroll = new ScrollView("));
        assertFalse(path, source.contains("import com.baixingai.voicedrop.ui.BouncyScrollView;"));
    }

    private static void assertDoesNotUseBouncyScroll(String path) throws Exception {
        String source = readSource(path);

        assertFalse(path, source.contains("import com.baixingai.voicedrop.ui.BouncyScrollView;"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
