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

    @Test
    public void parsesFollowupQuestionsSidecarFromArticleDoc() throws Exception {
        ArticleDoc doc = ArticleDoc.fromJson("{"
                + "\"questions\":[{\"id\":\"q1\",\"articleIndex\":0,\"text\":\"还有一个例子吗？\",\"status\":\"pending\",\"createdAt\":1234}],"
                + "\"articles\":[{\"title\":\"标题\",\"body\":\"正文\"}]"
                + "}");

        assertEquals(1, doc.questions.size());
        assertEquals("q1", doc.questions.get(0).id);
        assertEquals(Integer.valueOf(0), doc.questions.get(0).articleIndex);
        assertEquals("还有一个例子吗？", doc.questions.get(0).text);
        assertEquals("pending", doc.questions.get(0).status);
        assertEquals(Double.valueOf(1234), doc.questions.get(0).createdAt);
    }
}
