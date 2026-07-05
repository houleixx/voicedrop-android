package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.ArticleDoc;

import org.junit.Test;

import static org.junit.Assert.*;

public class ArticleDocTest {
    @Test
    public void parsesPerArticleStyleFieldFromSchemaTwoArticles() throws Exception {
        ArticleDoc doc = ArticleDoc.fromJson("{"
                + "\"articles\":[{\"title\":\"标题\",\"body\":\"正文\",\"style\":12}]"
                + "}");

        assertEquals(1, doc.articles.size());
        assertEquals(Integer.valueOf(12), doc.articles.get(0).style);
    }

    @Test
    public void parsesDocumentTagsFromArticleDoc() throws Exception {
        ArticleDoc doc = ArticleDoc.fromJson("{"
                + "\"tags\":[\"创业\",\"产品\"],"
                + "\"articles\":[{\"title\":\"标题\",\"body\":\"正文\"}]"
                + "}");

        assertEquals(2, doc.tags.size());
        assertEquals("创业", doc.tags.get(0));
        assertEquals("产品", doc.tags.get(1));
    }
}
