package com.baixingai.voicedrop.core;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BookShareTargetTest {
    private static final String ROOT = "https://voicedrop.cn/books/demo/";

    @Test public void sharesTheCurrentTrustedChapterAndItsPageTitle() {
        BookShareTarget.Target target = BookShareTarget.resolve(ROOT,
                ROOT + "chapter-2.html", "第二章", "书名", "作者");
        assertEquals(ROOT + "chapter-2.html", target.url);
        assertEquals("第二章", target.title);
        assertTrue(target.chapter);
    }

    @Test public void fallsBackForExternalOrMissingLocations() {
        for (String unsafe : new String[]{null, "https://example.com/books/demo/chapter.html",
                "http://voicedrop.cn/books/demo/chapter.html", "https://voicedrop.cn/about",
                "https://voicedrop.cn/books/another-book/chapter.html",
                "https://voicedrop.cn/books/demo/../another-book/chapter.html"}) {
            BookShareTarget.Target target = BookShareTarget.resolve(ROOT, unsafe, "外页", "书名", "作者");
            assertEquals(ROOT, target.url);
            assertEquals("《书名》 — 作者", target.title);
            assertFalse(target.chapter);
        }
    }

    @Test public void cloudflareReaderChapterIsSharedThroughCn() {
        BookShareTarget.Target target = BookShareTarget.resolve(ROOT,
                "https://jianshuo.dev/voicedrop/books/demo/chapter-2.html?from=reader",
                "第二章", "书名", "作者");
        assertEquals(ROOT + "chapter-2.html?from=reader", target.url);
        assertEquals("第二章", target.title);
        assertTrue(target.chapter);
    }
}
