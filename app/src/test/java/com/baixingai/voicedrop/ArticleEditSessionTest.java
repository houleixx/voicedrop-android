package com.baixingai.voicedrop;

import com.baixingai.voicedrop.net.ArticleEditSession;

import org.junit.Test;

import static org.junit.Assert.*;

public class ArticleEditSessionTest {
    @Test
    public void buildsIosCompatibleInstructionPayload() throws Exception {
        ArticleEditSession.EditRequest request = new ArticleEditSession.EditRequest(
                "edit-1",
                "把第2行写得更温柔",
                1);

        String payload = ArticleEditSession.payloadFor(request);

        assertTrue(payload.contains("\"type\":\"instruct\""));
        assertTrue(payload.contains("\"id\":\"edit-1\""));
        assertTrue(payload.contains("\"text\":\"把第2行写得更温柔\""));
        assertTrue(payload.contains("\"articleIndex\":1"));
    }

    @Test
    public void includesImagesInInstructionPayload() throws Exception {
        ArticleEditSession.EditRequest request = new ArticleEditSession.EditRequest(
                "edit-2",
                "插入这张照片",
                0);
        request.images.add(new ArticleEditSession.AgentImage("photos/a.jpg", "abc"));

        String payload = ArticleEditSession.payloadFor(request);

        assertTrue(payload.contains("\"images\""));
        assertTrue(payload.contains("\"key\":\"photos/a.jpg\""));
        assertTrue(payload.contains("\"data\":\"abc\""));
        assertTrue(payload.contains("\"mediaType\":\"image/jpeg\""));
    }
}
