package com.baixingai.voicedrop;

import com.baixingai.voicedrop.core.ArticleSharePayload;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ArticleSharePayloadTest {
    @Test public void normalTargetsReceiveArticleTextPlusPublicLink() {
        assertEquals("标题\n\n正文\n\nhttps://jianshuo.dev/voicedrop/abc?s=1",
                ArticleSharePayload.textForTarget(
                        "标题\n\n正文",
                        "https://jianshuo.dev/voicedrop/abc?s=1",
                        "com.example.notes"));
    }

    @Test public void wechatTargetsReceiveStableTextInsteadOfBarePreviewUrl() {
        assertFalse(ArticleSharePayload.wantsLinkCard("com.tencent.mm"));
        assertFalse(ArticleSharePayload.wantsLinkCard("com.tencent.mm.ui.tools.ShareImgUI"));
        assertFalse(ArticleSharePayload.wantsLinkCard("com.twitter.android"));
        assertEquals("标题\n\n正文\n\nhttps://jianshuo.dev/voicedrop/abc?s=1",
                ArticleSharePayload.textForTarget(
                        "标题\n\n正文",
                        "https://jianshuo.dev/voicedrop/abc?s=1",
                        "com.tencent.mm"));
    }
}
