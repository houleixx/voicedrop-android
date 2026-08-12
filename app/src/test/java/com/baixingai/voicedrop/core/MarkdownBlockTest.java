package com.baixingai.voicedrop.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class MarkdownBlockTest {
    @Test public void classifiesBlockMarkdownWithoutExposingSyntaxMarkers() {
        assertBlock("# 一级", MarkdownBlock.Kind.H1, "一级", "");
        assertBlock("## 二级", MarkdownBlock.Kind.H2, "二级", "");
        assertBlock("###### 六级", MarkdownBlock.Kind.H3, "六级", "");
        assertBlock("- 条目", MarkdownBlock.Kind.BULLET, "条目", "");
        assertBlock("12) 条目", MarkdownBlock.Kind.ORDERED, "条目", "12");
        assertBlock(">> 引用", MarkdownBlock.Kind.QUOTE, "引用", "");
        assertBlock("- - -", MarkdownBlock.Kind.DIVIDER, "", "");
    }

    @Test public void avoidsCommonFalsePositives() {
        assertBlock("#话题", MarkdownBlock.Kind.PLAIN, "#话题", "");
        assertBlock("####### 太深", MarkdownBlock.Kind.PLAIN, "####### 太深", "");
        assertBlock("-负号", MarkdownBlock.Kind.PLAIN, "-负号", "");
        assertBlock("2026. 年份", MarkdownBlock.Kind.PLAIN, "2026. 年份", "");
        assertBlock("1.5 倍速", MarkdownBlock.Kind.PLAIN, "1.5 倍速", "");
        assertBlock("-*-", MarkdownBlock.Kind.PLAIN, "-*-", "");
    }

    private static void assertBlock(String source, MarkdownBlock.Kind kind,
                                    String content, String marker) {
        MarkdownBlock block = MarkdownBlock.classify(source);
        assertEquals(kind, block.kind);
        assertEquals(content, block.content);
        assertEquals(marker, block.marker);
    }
}
