package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class WritingStyleActivityI18nSourceTest {
    @Test
    public void localizesWritingStyleMetadataAndEditorCopy() throws Exception {
        String activity = readSource("src/main/java/com/baixingai/voicedrop/WritingStyleActivity.java");
        String i18n = readSource("src/main/java/com/baixingai/voicedrop/ui/I18n.java");

        assertTrue(activity.contains("I18n.format(this, \"%d 字\","));
        assertTrue(activity.contains("I18n.format(this,\n                        \"正在基于 v%d 编辑。修改后会保存为新版本，并自动设为默认。\", version)"));
        assertTrue(activity.contains("input.setHint(com.baixingai.voicedrop.ui.I18n.text(this,"));
        assertTrue(activity.contains("I18n.format(this, \"v%d 写作风格\", version)"));
        assertTrue(activity.contains("I18n.text(this,\n                WritingStylePresentation.actionLabel(action))"));
        assertTrue(i18n.contains("copy.put(\"%d 字\", \"%d characters\");"));
        assertTrue(i18n.contains("copy.put(\"v%d 写作风格\", \"Writing style v%d\");"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
