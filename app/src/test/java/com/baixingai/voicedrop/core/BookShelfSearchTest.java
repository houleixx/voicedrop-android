package com.baixingai.voicedrop.core;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public final class BookShelfSearchTest {
    private final List<BookShelfIndex.Book> books = BookShelfIndex.parse("{\"books\":["
            + "{\"slug\":\"story\",\"title\":\"旅途\",\"main\":\"远方\",\"sub\":\"途中\",\"author\":\"Alice\",\"category\":\"故事\"},"
            + "{\"slug\":\"ai\",\"title\":\"机器学习\",\"category\":\"AI\",\"mine\":true,\"hidden\":true},"
            + "{\"slug\":\"unknown\",\"title\":\"未分类\",\"category\":\"其他\"}]}");
    private final Map<String, BookShelfSearch.Entry> index = BookShelfSearch.parse("{\"books\":["
            + "{\"slug\":\"story\",\"sub\":\"目录副题\",\"intro\":\"世界各地\",\"toc\":[{\"t\":\"港口\",\"b\":\"清晨的潮汐\"},{\"t\":\"夜航\",\"b\":\"潮汐\"}]},"
            + "{\"slug\":\"ai\",\"toc\":[{\"t\":\"模型\",\"b\":\"LEARNING examples\"}]},"
            + "{\"slug\":\"private-other-account\",\"sub\":\"潮汐\"}]}");

    @Test public void filtersUseCanonicalOrderAndOnlyPresentKnownCategories() {
        assertEquals(Arrays.asList("全部", "我的", "AI", "故事"), BookShelfSearch.filters(books));
        assertEquals(3, BookShelfSearch.select(books, "全部", "", null).size());
        assertEquals("ai", BookShelfSearch.select(books, "我的", "", null).get(0).book.slug);
    }

    @Test public void metadataMatchesChineseAndCaseInsensitiveTrimmedEnglishWithoutIndex() {
        for (String q : Arrays.asList("旅途", "远方", "途中", " alice \n", "故事")) {
            assertEquals("", BookShelfSearch.hit(books.get(0), q, null));
        }
        assertNull(BookShelfSearch.hit(books.get(0), "不存在", null));
        assertEquals(3, BookShelfSearch.select(books, "全部", " \t\n", null).size());
        assertEquals("", BookShelfSearch.query(" \n\t"));
    }

    @Test public void searchesSupplementalSubtitleIntroAndFirstMatchingChapterBrief() {
        assertEquals("", BookShelfSearch.hit(books.get(0), "目录副题", index.get("story")));
        assertEquals("", BookShelfSearch.hit(books.get(0), "各地", index.get("story")));
        assertEquals("港口", BookShelfSearch.hit(books.get(0), "潮汐", index.get("story")));
        assertEquals("港口", BookShelfSearch.hit(books.get(0), "港口", index.get("story")));
        assertEquals("模型", BookShelfSearch.hit(books.get(1), "learning", index.get("ai")));
        assertEquals("", BookShelfSearch.hit(books.get(0), "旅途", index.get("story")));
    }

    @Test public void searchAndFilterIntersectAndNeverAddBooksFromSearchIndex() {
        assertTrue(BookShelfSearch.select(books, "我的", "潮汐", index).isEmpty());
        assertTrue(BookShelfSearch.select(books, "故事", "learning", index).isEmpty());
        List<BookShelfSearch.Match> results = BookShelfSearch.select(books, "全部", "潮汐", index);
        assertEquals(1, results.size());
        assertEquals("story", results.get(0).book.slug);
        assertEquals("港口", results.get(0).chapter);
        assertEquals(1, BookShelfSearch.select(books, "我的", "learning", index).size());
    }

    @Test public void malformedPayloadIsRetryableAndLegacyMissingOptionalFieldsAreSafe() {
        assertNull(BookShelfSearch.parse("<html>old backend</html>"));
        assertNull(BookShelfSearch.parse("{\"error\":\"unauthorized\"}"));
        assertNull(BookShelfSearch.parse("{\"books\":null}"));
        assertTrue(BookShelfSearch.parse("{\"books\":[]}").isEmpty());
        Map<String, BookShelfSearch.Entry> legacy = BookShelfSearch.parse("{\"books\":[null,{},"
                + "{\"slug\":\"story\",\"sub\":null,\"toc\":[null,{\"b\":\"海浪\"}]},"
                + "{\"slug\":\"story\",\"sub\":\"duplicate\"}]}");
        assertEquals(1, legacy.size());
        assertEquals("", BookShelfSearch.hit(books.get(0), "海浪", legacy.get("story")));
        assertNull(BookShelfSearch.hit(books.get(0), "duplicate", legacy.get("story")));
    }
}
