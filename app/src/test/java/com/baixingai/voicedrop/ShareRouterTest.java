package com.baixingai.voicedrop;

import com.baixingai.voicedrop.share.ShareKind;
import com.baixingai.voicedrop.share.ShareRouter;
import com.baixingai.voicedrop.share.SilentAudio;

import org.junit.Test;

import static org.junit.Assert.*;

public class ShareRouterTest {
    @Test
    public void classifiesSharePayloadsLikeIosShareCollect() {
        assertEquals(ShareKind.AUDIO, ShareRouter.classify("android.intent.action.SEND", "audio/m4a", 1, false, ""));
        assertEquals(ShareKind.IMAGE, ShareRouter.classify("android.intent.action.SEND_MULTIPLE", "image/jpeg", 6, false, ""));
        assertEquals(ShareKind.DOCUMENT, ShareRouter.classify("android.intent.action.SEND", "application/pdf", 1, false, ""));
        assertEquals(ShareKind.DOCUMENT, ShareRouter.classify("android.intent.action.SEND", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 1, false, ""));
        assertEquals(ShareKind.WEB, ShareRouter.classify("android.intent.action.SEND", "text/plain", 0, true, "https://mp.weixin.qq.com/s/abc"));
        assertEquals(ShareKind.TEXT, ShareRouter.classify("android.intent.action.SEND", "text/plain", 0, true, "一段普通文字"));
    }

    @Test
    public void silentAudioBytesAreEmbeddedInCode() {
        assertTrue(SilentAudio.data().length > 256);
    }
}
