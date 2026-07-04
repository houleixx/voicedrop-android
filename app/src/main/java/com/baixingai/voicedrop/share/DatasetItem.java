package com.baixingai.voicedrop.share;

public final class DatasetItem {
    public final String id;
    public final String type;
    public final String title;
    public final String source;
    public final String collectedAt;
    public final int chars;

    public DatasetItem(String id, String type, String title, String source, String collectedAt, int chars) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.source = source;
        this.collectedAt = collectedAt;
        this.chars = chars;
    }
}
