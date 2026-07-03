package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.LibraryStore;

import org.junit.Test;

import static org.junit.Assert.*;

public class WechatPublishResultTest {
    @Test
    public void mapsWechatConfigErrorsToChineseMessage() {
        assertEquals("公众号配置有误，检查 AppID/Secret 或 IP 白名单",
                LibraryStore.wechatMessage(40164, "invalid ip"));
        assertEquals("摘要太短，正文写长一点再发",
                LibraryStore.wechatMessage(45004, "invalid digest"));
        assertEquals("今天发布次数到上限了，明天再试",
                LibraryStore.wechatMessage(45110, "api freq out"));
    }

    @Test
    public void identifiesConfigFailures() {
        assertTrue(LibraryStore.PublishResult.notConfigured().isConfigError());
        assertTrue(LibraryStore.PublishResult.failed("bad config", 40013).isConfigError());
        assertFalse(LibraryStore.PublishResult.failed("too often", 45009).isConfigError());
    }
}
