package com.baixingai.voicedrop;

import com.baixingai.voicedrop.share.ShareApi;
import com.baixingai.voicedrop.share.ShareExtraction;
import com.baixingai.voicedrop.share.DatasetItem;
import com.baixingai.voicedrop.share.ShareDatasetUi;

import org.json.JSONObject;
import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

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
        assertTrue(ShareApi.styleExtractTaskName(false, now).contains("TaskStyleExtract-Keep"));
        assertTrue(ShareApi.styleExtractTaskName(true, now).startsWith("VoiceDrop-2026-07-04-093000-0m0s-"));
    }

    @Test
    public void styleExtractKeepTagMatchesBackendContract() {
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 4, 9, 30, 0, 0, ZoneId.of("Asia/Shanghai"));
        assertTrue(ShareApi.styleExtractTaskName(false, now).contains("TaskStyleExtract-Keep"));
    }

    @Test
    public void datasetUiMatchesIosAndHarmonyMetadata() {
        DatasetItem text = new DatasetItem("a", "text", "文章", "分享文本", "2026-08-18T02:00:00Z", 1116);
        DatasetItem web = new DatasetItem("b", "web", "网页", "voicedrop.cn", "2026-08-18T02:00:00Z", 2232);
        assertEquals(3348, ShareDatasetUi.totalChars(Arrays.asList(text, web)));
        assertEquals("1,116 字", ShareDatasetUi.itemMeta(text));
        assertEquals("voicedrop.cn", ShareDatasetUi.itemMeta(web));
        assertEquals("8月18日", ShareDatasetUi.chineseDate(text.collectedAt));
        assertEquals("约 3,348 字", ShareDatasetUi.formatTotalChars(3348));
    }

    @Test
    public void shareTextMayContainAUrlAlongsidePreviewCopy() {
        assertEquals("https://voicedrop.cn/article",
                ShareExtraction.firstWebUrl("文章标题 https://voicedrop.cn/article"));
        String html = "<html><head><title>文章标题</title><style>x</style></head>"
                + "<body><article><p>第一段</p><p>第二段</p></article></body></html>";
        assertEquals("文章标题", ShareExtraction.htmlTitle(html, "fallback"));
        assertTrue(ShareExtraction.readableHtml(html).contains("第一段\n第二段"));
    }

    @Test
    public void webTitlePrefersOpenGraphLikeIosReadability() {
        String html = "<html><head><title>站点标题</title>"
                + "<meta property=\"og:title\" content=\"文章标题\"></head>"
                + "<body><article><p>正文内容</p></article></body></html>";

        assertEquals("文章标题", ShareExtraction.htmlTitle(html, "fallback"));
        assertEquals("正文内容", ShareExtraction.readableHtml(html));
    }

    @Test
    public void firstLineTitleTrimsAndCapsToFortyChars() {
        assertEquals("第一行标题", ShareExtraction.firstLineTitle("\n 第一行标题 \n正文", "fallback"));
        assertEquals("fallback", ShareExtraction.firstLineTitle("\n\n", "fallback"));
        assertEquals(40, ShareExtraction.firstLineTitle("123456789012345678901234567890123456789012345", "fallback").length());
    }
}
