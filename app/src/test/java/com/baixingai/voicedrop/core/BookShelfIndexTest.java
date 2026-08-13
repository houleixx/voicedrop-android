package com.baixingai.voicedrop.core;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BookShelfIndexTest {
    @Test public void parsesIosShelfContractAndSkipsMissingSlug() {
        java.util.List<BookShelfIndex.Book> books = BookShelfIndex.parse("{\"books\":[" +
                "{\"slug\":\"a-book\",\"main\":\"主标题\",\"sub\":\"副标题\",\"c\":\"#111111\",\"c2\":\"#222222\",\"cover\":true,\"chapters\":7,\"author\":\"作者\",\"createdAt\":123},{}]}");
        assertEquals(1, books.size());
        assertEquals("主标题", books.get(0).main);
        assertEquals(7, books.get(0).chapters);
        assertEquals("作者", books.get(0).author);
        assertEquals(123L, books.get(0).createdAt);
        assertEquals("https://voicedrop.cn/books/a-book/", books.get(0).readerUrl());
    }
}
