package com.baixingai.voicedrop.ui;

import com.baixingai.voicedrop.core.BookShelfIndex;
import com.baixingai.voicedrop.data.CommunityStore;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class CommunityFeedBookTest {
    @Test public void recommendationBookBuildsTheExistingReaderModel() throws Exception {
        CommunityStore.Post post = CommunityStore.Post.from(new JSONObject("{"
                + "\"kind\":\"book\",\"shareId\":\"book-a-great-book\","
                + "\"title\":\"A Great Book\",\"preview\":\"A short description\","
                + "\"author\":\"Ada\",\"count\":4,\"coverPhotoKey\":\"books/a-great-book/cover.jpg\"}"));

        BookShelfIndex.Book book = CommunityFeedPresentation.book(post);

        assertNotNull(book);
        assertEquals("a-great-book", book.slug);
        assertEquals("A Great Book", book.main);
        assertEquals("A short description", book.sub);
        assertEquals("Ada", book.author);
        assertEquals(4, book.chapters);
        assertTrue(book.cover);
        assertFalse(book.mine);
    }

    @Test public void onlyWellFormedBookFeedItemsOpenTheReader() throws Exception {
        assertNull(CommunityFeedPresentation.book(CommunityStore.Post.from(
                new JSONObject("{\"kind\":\"article\",\"shareId\":\"book-a-great-book\"}"))));
        assertNull(CommunityFeedPresentation.book(CommunityStore.Post.from(
                new JSONObject("{\"kind\":\"book\",\"shareId\":\"article-a-great-book\"}"))));
        assertNull(CommunityFeedPresentation.book(CommunityStore.Post.from(
                new JSONObject("{\"kind\":\"book\",\"shareId\":\"book-not/a-route\"}"))));
    }
}
