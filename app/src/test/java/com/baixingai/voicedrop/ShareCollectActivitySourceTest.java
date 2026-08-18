package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class ShareCollectActivitySourceTest {
    @Test
    public void datasetShareMatchesIosAndHarmonyInteractionContract() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ShareCollectActivity.java");

        assertTrue(source.contains("startDatasetFlow()"));
        assertTrue(source.contains("collectIncoming(item)"));
        assertTrue(source.contains("本次新增"));
        assertTrue(source.contains("继续收集"));
        assertTrue(source.contains("提取后清空数据集"));
        assertTrue(source.contains("retryIncoming(item)"));
        assertTrue(source.contains("runOnUiThread(this::finishAndRemoveTask)"));
        assertTrue(source.contains("正在提取文章风格，可在“我的录音”查看进度"));
        assertTrue(source.contains("root.postDelayed(this::finishAndRemoveTask"));
        assertFalse(source.contains("View grabber"));
        assertTrue(source.contains("datasetExtractAction("));
        assertTrue(source.contains("String displayType = done ? \"text\" : item.type"));
        assertTrue(source.contains("done ? ShareDatasetUi.formatChars(item.chars) : item.meta"));
    }

    @Test
    public void systemShareTargetAcceptsSingleAndMultipleContent() throws Exception {
        String manifest = readSource("src/main/AndroidManifest.xml");

        assertTrue(manifest.contains("android:name=\".ShareCollectActivity\""));
        assertTrue(manifest.contains("android.intent.action.SEND"));
        assertTrue(manifest.contains("android.intent.action.SEND_MULTIPLE"));
        assertTrue(manifest.contains("android:mimeType=\"*/*\""));
        assertTrue(manifest.contains("android:theme=\"@style/ShareCollectTheme\""));

        String styles = readSource("src/main/res/values/styles.xml");
        assertTrue(styles.contains("<style name=\"ShareCollectTheme\""));
        assertTrue(styles.contains("<item name=\"android:windowIsTranslucent\">true</item>"));
    }

    @Test
    public void datasetTitlesUseEndEllipsisInsteadOfHardClipping() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/ShareCollectActivity.java");
        String existing = methodBody(source, "private View existingRow");
        String incoming = methodBody(source, "private View incomingRow");

        assertTrue(existing.contains("setEllipsize(TextUtils.TruncateAt.END)"));
        assertTrue(incoming.contains("setEllipsize(TextUtils.TruncateAt.END)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path cwd = Paths.get("").toAbsolutePath();
        Path file = cwd.resolve(moduleRelative);
        if (!Files.exists(file)) file = cwd.resolve("app").resolve(moduleRelative);
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) return "";
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '{') depth++;
            if (value == '}' && --depth == 0) return source.substring(open + 1, index);
        }
        return "";
    }
}
