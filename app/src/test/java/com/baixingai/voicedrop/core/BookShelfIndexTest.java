package com.baixingai.voicedrop.core;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BookShelfIndexTest {
    @Test public void ownershipRequiresTrueAndCategorySupportsLegacyAndNullValues() {
        java.util.List<BookShelfIndex.Book> books = BookShelfIndex.parse("{\"books\":[" +
                "{\"slug\":\"a\",\"mine\":true,\"category\":\"AI\"}," +
                "{\"slug\":\"b\",\"mine\":\"true\",\"category\":null}," +
                "{\"slug\":\"c\",\"mine\":1},{\"slug\":\"d\"}]}");
        assertTrue(books.get(0).mine);
        assertEquals("AI", books.get(0).category);
        for (int i = 1; i < books.size(); i++) {
            assertFalse(books.get(i).mine);
            assertEquals("", books.get(i).category);
        }
    }

    @Test public void parsesIosShelfContractAndSkipsMissingSlug() {
        java.util.List<BookShelfIndex.Book> books = BookShelfIndex.parse("{\"books\":[" +
                "{\"slug\":\"a-book\",\"main\":\"主标题\",\"sub\":\"副标题\",\"c\":\"#111111\",\"c2\":\"#222222\",\"cover\":true,\"coverAt\":456,\"chapters\":7,\"author\":\"作者\",\"createdAt\":123},{}]}");
        assertEquals(1, books.size());
        assertEquals("主标题", books.get(0).main);
        assertEquals(7, books.get(0).chapters);
        assertEquals("作者", books.get(0).author);
        assertEquals(123L, books.get(0).createdAt);
        assertEquals("https://voicedrop.cn/books/a-book/", books.get(0).readerUrl("https://voicedrop.cn"));
        assertEquals(456L, books.get(0).coverAt);
        assertEquals("https://voicedrop.cn/books/a-book/cover.jpg?v=456", books.get(0).coverUrl("https://voicedrop.cn"));
        assertEquals("https://jianshuo.dev/voicedrop/books/a-book/cover.jpg?v=456",
                books.get(0).coverUrl("https://jianshuo.dev/voicedrop"));
    }

    @Test public void preservesServerOrderInsteadOfSortingByCreatedAt() {
        java.util.List<BookShelfIndex.Book> books = BookShelfIndex.parse("{\"books\":[" +
                "{\"slug\":\"server-first\",\"title\":\"服务端第一本\",\"createdAt\":100}," +
                "{\"slug\":\"server-second\",\"title\":\"服务端第二本\",\"createdAt\":200}]}");

        assertEquals(2, books.size());
        assertEquals("server-first", books.get(0).slug);
        assertEquals("server-second", books.get(1).slug);
    }

    @Test public void parsesLegacyCacheWithoutCreatedAt() {
        java.util.List<BookShelfIndex.Book> books = BookShelfIndex.parse("{\"books\":[" +
                "{\"slug\":\"legacy\",\"title\":\"主标题：副标题\"}]}");

        assertEquals(1, books.size());
        assertEquals("主标题：副标题", books.get(0).title);
        assertEquals(0L, books.get(0).createdAt);
        assertEquals(0L, books.get(0).coverAt);
        assertFalse(books.get(0).hidden);
        assertEquals("https://voicedrop.cn/books/legacy/cover.jpg",
                books.get(0).coverUrl("https://voicedrop.cn"));
    }

    @Test public void parsesOwnerOnlyHiddenMarker() {
        java.util.List<BookShelfIndex.Book> books = BookShelfIndex.parse("{\"books\":[" +
                "{\"slug\":\"private-draft\",\"hidden\":true,\"mine\":true}," +
                "{\"slug\":\"public-book\"}]}");

        assertEquals(2, books.size());
        assertTrue(books.get(0).hidden);
        assertTrue(books.get(0).mine);
        assertFalse(books.get(1).hidden);
        assertFalse(books.get(1).mine);
    }
}
