package com.baixingai.voicedrop.data;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WechatMiniProgramShareSourceTest {
    @Test
    public void adaptiveApplicationIconIsRenderedIntoTheCardThumbnail() throws Exception {
        String source = readSource("src/main/java/com/baixingai/voicedrop/data/WechatMiniProgramShare.java");

        assertTrue(source.contains("loadIcon(context.getPackageManager())"));
        assertTrue(source.contains("THUMBNAIL_SIDE_PX = 256"));
        assertTrue(source.contains("Bitmap.createBitmap(THUMBNAIL_SIDE_PX"));
        assertTrue(source.contains("icon.draw(canvas)"));
        assertFalse(source.contains("BitmapFactory.decodeResource"));
        assertTrue(source.contains("Bitmap articleImage"));
        assertTrue(source.contains("new RectF("));
        assertTrue(source.contains("canvas.drawBitmap(articleImage, null,"));
        assertTrue(source.contains("float scale = Math.min"));
        assertTrue(source.contains("canvas.drawColor(Color.WHITE)"));
        assertTrue(source.contains("new WXWebpageObject()"));
        assertTrue(source.contains("SendMessageToWX.Req.WXSceneSession, Result.FRIEND_SENT"));
        assertTrue(source.contains("SendMessageToWX.Req.WXSceneTimeline, Result.TIMELINE_SENT"));
        assertTrue(source.contains("request.scene = scene"));
        assertTrue(source.contains("message.thumbData = thumbnail(context, thumbnailImage)"));
    }

    private static String readSource(String moduleRelative) throws Exception {
        Path path = Paths.get(moduleRelative);
        if (!Files.exists(path)) path = Paths.get("app", moduleRelative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
