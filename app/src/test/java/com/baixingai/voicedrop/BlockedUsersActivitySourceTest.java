package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockedUsersActivitySourceTest {
    @Test
    public void aboutOpensDedicatedBlockedUsersPage() throws Exception {
        String about = readSource("src/main/java/com/baixingai/voicedrop/AboutActivity.java");
        String manifest = readSource("src/main/AndroidManifest.xml");

        assertTrue(about.contains("new Intent(this, BlockedUsersActivity.class)"));
        assertFalse(about.contains("IosDialog.show(this, \"已屏蔽用户\""));
        assertTrue(manifest.contains("android:name=\".BlockedUsersActivity\""));
    }

    @Test
    public void pageRendersSortedAuthorsAndRemovesThemLocally() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/BlockedUsersActivity.java");

        assertTrue(source.contains("blockStore.blockedList()"));
        assertTrue(source.contains("没有已屏蔽的作者"));
        assertTrue(source.contains("blockStore.unblock(author)"));
        assertTrue(source.contains("SimpleToast.show(this, \"已取消屏蔽\")"));
        assertTrue(source.contains("text(\"取消屏蔽\""));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
