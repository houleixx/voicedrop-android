package com.baixingai.voicedrop.data;

public final class MinedArticle {
    public final String title;
    public final String body;
    public final String wechatMediaId;

    public MinedArticle(String title, String body, String wechatMediaId) {
        this.title = title == null || title.isEmpty() ? "(无题)" : title;
        this.body = body == null ? "" : body;
        this.wechatMediaId = wechatMediaId;
    }
}
