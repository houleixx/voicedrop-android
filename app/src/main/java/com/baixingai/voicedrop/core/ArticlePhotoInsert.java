package com.baixingai.voicedrop.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class ArticlePhotoInsert {
    private static final DateTimeFormatter SESSION_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss", Locale.US);

    private ArticlePhotoInsert() {}

    public static String instructionForKeys(List<String> relKeys) {
        if (relKeys == null || relKeys.isEmpty()) return "";
        StringBuilder markers = new StringBuilder();
        for (String key : relKeys) {
            if (markers.length() > 0) markers.append("、");
            markers.append("[[photo:").append(key).append("]]");
        }
        String countWord = relKeys.size() == 1 ? "这张照片" : "这" + relKeys.size() + "张照片";
        String pronoun = relKeys.size() == 1 ? "它" : "每一张都";
        return "我刚拍了" + countWord
                + "，请把" + pronoun
                + "插入文章里最合适的位置。每张照片用它自己的标记（原样写进正文，放在和场景最相符的段落附近）："
                + markers
                + "。所有照片必须全部插入，不能遗漏。";
    }

    public static int offsetSeconds(String sessionTs, ZonedDateTime captureTime) {
        if (sessionTs == null || sessionTs.isEmpty() || captureTime == null) return 0;
        try {
            LocalDateTime sessionLocal = LocalDateTime.parse(sessionTs, SESSION_TIMESTAMP);
            ZonedDateTime sessionStart = sessionLocal.atZone(captureTime.getZone() == null
                    ? ZoneId.systemDefault()
                    : captureTime.getZone());
            return Math.max(0, (int) Duration.between(sessionStart, captureTime).getSeconds());
        } catch (Exception e) {
            return 0;
        }
    }

    public static String thumbnailBase64(byte[] jpegBytes, int sidePx) {
        if (jpegBytes == null || jpegBytes.length == 0) return null;
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
        if (bitmap == null) return null;
        Bitmap thumb = Bitmap.createScaledBitmap(bitmap, sidePx, sidePx, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        thumb.compress(Bitmap.CompressFormat.JPEG, 70, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }
}
