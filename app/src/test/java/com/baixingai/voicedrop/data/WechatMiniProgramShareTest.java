package com.baixingai.voicedrop.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WechatMiniProgramShareTest {
    @Test
    public void acceptsOnlyWechatMiniProgramOriginalIds() {
        assertTrue(WechatMiniProgramShare.isOriginalId("gh_123Abc"));
        assertFalse(WechatMiniProgramShare.isOriginalId("wx" + "1234567890abcdef"));
        assertFalse(WechatMiniProgramShare.isOriginalId("mini-program"));
    }

    @Test
    public void buildsTheExistingMiniProgramCommunityRoute() {
        assertEquals("pages/community-detail/index?shareId=share%20id%2F1&section=0&fromShare=1",
                WechatMiniProgramShare.communityPath("share id/1"));
        assertEquals("pages/community-detail/index?shareId=abc123&section=2&fromShare=1",
                WechatMiniProgramShare.communityPath("abc123", 2));
    }

    @Test
    public void buildsTheMiniProgramBookReaderRouteAndOptionalChapter() {
        assertEquals("pages/book-reader/index?slug=sample-book&title=%E4%B8%80%E6%9C%AC%E4%B9%A6"
                        + "&main=%E4%B8%BB%E6%A0%87%E9%A2%98&author=%E5%BC%A0%20%E4%B8%89"
                        + "&cover=1&coverAt=456",
                WechatMiniProgramShare.bookReaderPath(
                        "sample-book", "一本书", "主标题", "张 三", true, 456L, null));
        assertEquals("pages/book-reader/index?slug=sample-book&title=%E4%B8%80%E6%9C%AC%E4%B9%A6"
                        + "&main=%E4%B8%BB%E6%A0%87%E9%A2%98&author=&cover=0&coverAt=0"
                        + "&page=https%3A%2F%2Fvoicedrop.cn%2Fbooks%2Fsample-book%2F02.html%3Fx%3D1",
                WechatMiniProgramShare.bookReaderPath(
                        "sample-book", "一本书", "主标题", "", false, -1L,
                        "https://voicedrop.cn/books/sample-book/02.html?x=1"));
    }
}
