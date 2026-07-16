package com.baixingai.voicedrop;

import com.baixingai.voicedrop.core.ArticleBody;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ArticleBodyTest {
    @Test
    public void segmentsStripMetaCommentsAndPreservePhotoTokens() {
        List<ArticleBody.Segment> segments = ArticleBody.segments(
                "<!-- style: 风格 v8 -->\n第一段\n\n[[photo:photos/2026-06-18-143052/3-abc.jpg]]\n\n第二段");

        assertEquals(3, segments.size());
        assertEquals(ArticleBody.Segment.Type.TEXT, segments.get(0).type);
        assertEquals("第一段", segments.get(0).value);
        assertEquals(ArticleBody.Segment.Type.PHOTO, segments.get(1).type);
        assertEquals("photos/2026-06-18-143052/3-abc.jpg", segments.get(1).value);
        assertEquals("第二段", segments.get(2).value);
    }

    @Test
    public void resolvePhotoKeySupportsLegacyNumericAndNewKeyTokens() {
        List<String> photos = Arrays.asList("photos/session/1-a1b.jpg", "photos/session/2-c3d.jpg");

        assertEquals("photos/session/2-c3d.jpg", ArticleBody.resolvePhotoKey("2", photos));
        assertNull(ArticleBody.resolvePhotoKey("3", photos));
        assertEquals("photos/new/9-xyz.jpg", ArticleBody.resolvePhotoKey("photos/new/9-xyz.jpg", photos));
    }

    @Test
    public void stripMarkersRemovesPhotosAndHtmlComments() {
        String stripped = ArticleBody.stripMarkers("<!-- style: 风格 v2 -->\n标题\n[[photo:1]]\n正文");

        assertEquals("标题\n\n正文", stripped);
    }

    @Test
    public void styleMetadataMatchesIosFormat() {
        String body = "<!-- style: 风格 v12 -->\n正文";

        assertEquals("风格 v12", ArticleBody.styleLabel(body));
        assertEquals(Integer.valueOf(12), ArticleBody.styleVersion(body));
        assertEquals("风格 v5", ArticleBody.styleLabelForVersion(5));
    }

    @Test
    public void firstPhotoKeyAndShareTextMatchIosSemantics() {
        String body = "A\n[[photo:1]]\nB";

        assertEquals("photos/session/1-a1b.jpg",
                ArticleBody.firstPhotoKey(body, Collections.singletonList("photos/session/1-a1b.jpg")));

        String share = ArticleBody.shareText(Arrays.asList(
                new ArticleBody.Article("第一篇", "正文一\n[[photo:1]]"),
                new ArticleBody.Article("第二篇", "正文二")));

        assertEquals("【第一篇】\n\n正文一\n\n---\n\n【第二篇】\n\n正文二", share);
    }

    @Test
    public void replacingLineOnlyChangesTheSelectedVisibleParagraph() throws Exception {
        Method replacingLine = ArticleBody.class.getMethod(
                "replacingLine", int.class, String.class, String.class);
        String body = "<!-- style: 风格 v3 -->\n  第一段  \n\n[[photo:photos/s/1-a.jpg]]\n\n第三段";

        String result = (String) replacingLine.invoke(null, 3, "第三段（已修改）", body);

        assertEquals("<!-- style: 风格 v3 -->\n  第一段  \n\n[[photo:photos/s/1-a.jpg]]\n\n第三段（已修改）", result);
    }

    @Test
    public void replacingLineDoesNotReplacePhotoRowsOrOutOfRangeRows() throws Exception {
        Method replacingLine = ArticleBody.class.getMethod(
                "replacingLine", int.class, String.class, String.class);
        String body = "第一段\n[[photo:photos/s/1-a.jpg]]\n第三段";

        assertEquals(body, replacingLine.invoke(null, 2, "不能替换图片", body));
        assertEquals(body, replacingLine.invoke(null, 9, "越界", body));
    }

    @Test
    public void replacingRenderedLineSkipsTheDuplicateTitleHiddenByTheDetailScreen() throws Exception {
        Method replacingRenderedLine = ArticleBody.class.getMethod(
                "replacingRenderedLine", int.class, String.class, String.class, String.class);
        String original = "<!-- style: 风格 v2 -->\n标题\n\n第一段\n[[photo:photos/s/1-a.jpg]]\n第二段";
        String rendered = "第一段\n[[photo:photos/s/1-a.jpg]]\n第二段";

        String result = (String) replacingRenderedLine.invoke(
                null, 1, "第一段（已修改）", rendered, original);

        assertEquals("<!-- style: 风格 v2 -->\n标题\n\n第一段（已修改）\n[[photo:photos/s/1-a.jpg]]\n第二段", result);
    }
}
