package com.baixingai.voicedrop.data;

public final class MinedArticle {
    public final String title;
    public final String body;
    public final Integer style;
    public final String wechatMediaId;

    public MinedArticle(String title, String body, String wechatMediaId) {
        this(title, body, null, wechatMediaId);
    }

    public MinedArticle(String title, String body, Integer style, String wechatMediaId) {
        this.title = title == null || title.isEmpty() ? "(无题)" : title;
        this.body = body == null ? "" : body;
        this.style = style;
        this.wechatMediaId = wechatMediaId;
    }
}
