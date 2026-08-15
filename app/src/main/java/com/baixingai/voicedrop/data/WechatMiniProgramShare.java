package com.baixingai.voicedrop.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import com.baixingai.voicedrop.BuildConfig;
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX;
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage;
import com.tencent.mm.opensdk.modelmsg.WXMiniProgramObject;
import com.tencent.mm.opensdk.modelmsg.WXWebpageObject;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** Sends a VoiceDrop article as a WeChat Mini Program card. */
public final class WechatMiniProgramShare {
    private static final int THUMBNAIL_LIMIT_BYTES = 128 * 1024;
    private static final int THUMBNAIL_SIDE_PX = 256;

    public enum Result {
        SENT("已打开微信，请选择好友发送小程序卡片"),
        FRIEND_SENT("已打开微信，请选择好友"),
        TIMELINE_SENT("已打开微信，请分享到朋友圈"),
        CONFIGURATION_REQUIRED("未配置小程序原始 ID，无法分享卡片"),
        WECHAT_NOT_INSTALLED("未安装微信"),
        SEND_FAILED("微信未能发起分享");

        private final String message;

        Result(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private WechatMiniProgramShare() {
    }

    public static Result send(Context context, String title, String webpageUrl, String miniProgramPath) {
        return send(context, title, webpageUrl, miniProgramPath, null);
    }

    /** Uses the first article image when one is available; otherwise uses the app icon. */
    public static Result send(Context context, String title, String webpageUrl, String miniProgramPath,
                              Bitmap articleImage) {
        return send(context, title, webpageUrl, miniProgramPath, articleImage,
                "打开 VoiceDrop 小程序阅读全文");
    }

    public static Result send(Context context, String title, String webpageUrl, String miniProgramPath,
                              Bitmap articleImage, String description) {
        if (!isOriginalId(BuildConfig.VOICEDROP_MINI_PROGRAM_ORIGINAL_ID)) {
            return Result.CONFIGURATION_REQUIRED;
        }
        if (!WechatLogin.api(context).isWXAppInstalled()) return Result.WECHAT_NOT_INSTALLED;

        WXMiniProgramObject miniProgram = new WXMiniProgramObject();
        miniProgram.webpageUrl = webpageUrl;
        miniProgram.userName = BuildConfig.VOICEDROP_MINI_PROGRAM_ORIGINAL_ID.trim();
        miniProgram.path = miniProgramPath;
        miniProgram.miniprogramType = WXMiniProgramObject.MINIPTOGRAM_TYPE_RELEASE;

        WXMediaMessage message = new WXMediaMessage(miniProgram);
        message.title = trim(title, WXMediaMessage.TITLE_LENGTH_LIMIT, "VoiceDrop 文章");
        message.description = trim(description, WXMediaMessage.DESCRIPTION_LENGTH_LIMIT,
                "打开 VoiceDrop 小程序阅读全文");
        message.thumbData = thumbnail(context, articleImage);

        SendMessageToWX.Req request = new SendMessageToWX.Req();
        request.transaction = "voicedrop-mini-program-" + System.currentTimeMillis();
        request.message = message;
        request.scene = SendMessageToWX.Req.WXSceneSession;
        return WechatLogin.api(context).sendReq(request) ? Result.SENT : Result.SEND_FAILED;
    }

    static boolean isOriginalId(String value) {
        return value != null && value.trim().matches("gh_[0-9A-Za-z]+$");
    }

    public static String communityPath(String shareId) {
        String id = shareId == null ? "" : shareId.trim();
        try {
            return "pages/community-detail/index?shareId=" + URLEncoder.encode(id, "UTF-8").replace("+", "%20") + "&fromShare=1";
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static Result sendFriend(Context context, String title, String webpageUrl,
                                    Bitmap thumbnailImage) {
        return sendWebpage(context, title, webpageUrl, thumbnailImage,
                "打开 VoiceDrop 阅读这本书",
                SendMessageToWX.Req.WXSceneSession, Result.FRIEND_SENT);
    }

    public static Result sendTimeline(Context context, String title, String webpageUrl,
                                      Bitmap thumbnailImage) {
        return sendWebpage(context, title, webpageUrl, thumbnailImage,
                "打开 VoiceDrop 阅读这本书",
                SendMessageToWX.Req.WXSceneTimeline, Result.TIMELINE_SENT);
    }

    public static Result sendTimeline(Context context, String title, String webpageUrl,
                                      Bitmap thumbnailImage, String description) {
        return sendWebpage(context, title, webpageUrl, thumbnailImage, description,
                SendMessageToWX.Req.WXSceneTimeline, Result.TIMELINE_SENT);
    }

    private static Result sendWebpage(Context context, String title, String webpageUrl,
                                      Bitmap thumbnailImage, String description,
                                      int scene, Result sentResult) {
        if (!WechatLogin.api(context).isWXAppInstalled()) return Result.WECHAT_NOT_INSTALLED;

        WXWebpageObject webpage = new WXWebpageObject();
        webpage.webpageUrl = webpageUrl;
        WXMediaMessage message = new WXMediaMessage(webpage);
        message.title = trim(title, WXMediaMessage.TITLE_LENGTH_LIMIT, "VoiceDrop 分享");
        message.description = trim(description, WXMediaMessage.DESCRIPTION_LENGTH_LIMIT,
                "打开 VoiceDrop 查看内容");
        message.thumbData = thumbnail(context, thumbnailImage);

        SendMessageToWX.Req request = new SendMessageToWX.Req();
        request.transaction = "voicedrop-webpage-" + System.currentTimeMillis();
        request.message = message;
        request.scene = scene;
        return WechatLogin.api(context).sendReq(request) ? sentResult : Result.SEND_FAILED;
    }

    private static String trim(String value, int maxLength, String fallback) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) clean = fallback;
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
    }

    private static byte[] thumbnail(Context context, Bitmap articleImage) {
        Bitmap bitmap = Bitmap.createBitmap(THUMBNAIL_SIDE_PX, THUMBNAIL_SIDE_PX, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            if (articleImage != null && articleImage.getWidth() > 0 && articleImage.getHeight() > 0) {
                canvas.drawColor(Color.WHITE);
                float scale = Math.min(bitmap.getWidth() / (float) articleImage.getWidth(),
                        bitmap.getHeight() / (float) articleImage.getHeight());
                float width = articleImage.getWidth() * scale;
                float height = articleImage.getHeight() * scale;
                float left = (bitmap.getWidth() - width) / 2f;
                float top = (bitmap.getHeight() - height) / 2f;
                canvas.drawBitmap(articleImage, null, new RectF(left, top, left + width, top + height), null);
            } else {
                Drawable icon = context.getApplicationInfo().loadIcon(context.getPackageManager());
                if (icon == null) return null;
                icon.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                icon.draw(canvas);
            }
            for (int quality = 90; quality >= 30; quality -= 15) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
                byte[] bytes = output.toByteArray();
                if (bytes.length <= THUMBNAIL_LIMIT_BYTES) return bytes;
            }
            return null;
        } finally {
            bitmap.recycle();
        }
    }
}
