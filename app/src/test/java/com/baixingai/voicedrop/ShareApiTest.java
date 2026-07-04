package com.baixingai.voicedrop;

import com.baixingai.voicedrop.share.ShareApi;
import com.baixingai.voicedrop.share.ShareExtraction;

import org.json.JSONObject;
import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.*;

public class ShareApiTest {
    @Test
    public void collectStyleBodyMatchesFilesApiContract() throws Exception {
        JSONObject body = ShareApi.collectStyleBody("web", "标题", "正文", "mp.weixin.qq.com");

        assertEquals("web", body.getString("type"));
        assertEquals("标题", body.getString("title"));
        assertEquals("正文", body.getString("text"));
        assertEquals("mp.weixin.qq.com", body.getString("source"));
    }

    @Test
    public void styleExtractTaskNameUsesMiningTaskTag() {
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 4, 9, 30, 0, 0, ZoneId.of("Asia/Shanghai"));

        assertTrue(ShareApi.styleExtractTaskName(true, now).contains("TaskStyleExtract"));
        assertFalse(ShareApi.styleExtractTaskName(true, now).contains("Keep"));
        assertTrue(ShareApi.styleExtractTaskName(false, now).contains("TaskStyleExtractKeep"));
        assertTrue(ShareApi.styleExtractTaskName(true, now).startsWith("VoiceDrop-2026-07-04-093000-0m0s-"));
    }

    @Test
    public void firstLineTitleTrimsAndCapsToFortyChars() {
        assertEquals("第一行标题", ShareExtraction.firstLineTitle("\n 第一行标题 \n正文", "fallback"));
        assertEquals("fallback", ShareExtraction.firstLineTitle("\n\n", "fallback"));
        assertEquals(40, ShareExtraction.firstLineTitle("123456789012345678901234567890123456789012345", "fallback").length());
    }
}
