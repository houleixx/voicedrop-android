package com.baixingai.voicedrop.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class WechatShareContentTest {
    @Test public void excerptStripsMarkersCollapsesWhitespaceAndTruncatesByCodePoint() {
        String body = "<!-- origin:x -->\n[[photo:photos/a.jpg]]  开头\n\n"
                + "字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字";
        assertEquals("开头 字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字字……",
                WechatShareContent.excerpt(body, "fallback"));
        assertEquals("fallback", WechatShareContent.excerpt("[[photo:x]]", "fallback"));
    }

    @Test public void extractsPublicIdAndBuildsTheMiniProgramRoute() {
        assertEquals("Abc_123-xy", WechatShareContent.publicShareId(
                "https://voicedrop.cn/a/Abc_123-xy?s=2"));
        assertEquals("", WechatShareContent.publicShareId("https://voicedrop.cn/a/bad.id?s=2"));
        assertEquals("pages/shared-article/index?shareId=Abc_123-xy&section=2&fromShare=1",
                WechatShareContent.sharedArticlePath("Abc_123-xy", 2));
    }

    @Test public void addsCurrentSectionToCommunityLink() {
        assertEquals("https://voicedrop.cn/voicedrop/abc123?s=3",
                WechatShareContent.communityUrl("https://voicedrop.cn/voicedrop/abc123", 3));
    }
}
