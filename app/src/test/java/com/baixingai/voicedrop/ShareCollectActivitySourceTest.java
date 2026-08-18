package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

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
    }

    @Test
    public void systemShareTargetAcceptsSingleAndMultipleContent() throws Exception {
        String manifest = readSource("src/main/AndroidManifest.xml");

        assertTrue(manifest.contains("android:name=\".ShareCollectActivity\""));
        assertTrue(manifest.contains("android.intent.action.SEND"));
        assertTrue(manifest.contains("android.intent.action.SEND_MULTIPLE"));
        assertTrue(manifest.contains("android:mimeType=\"*/*\""));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path cwd = Paths.get("").toAbsolutePath();
        Path file = cwd.resolve(moduleRelative);
        if (!Files.exists(file)) file = cwd.resolve("app").resolve(moduleRelative);
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
