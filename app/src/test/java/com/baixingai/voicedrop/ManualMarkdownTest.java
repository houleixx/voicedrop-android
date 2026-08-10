package com.baixingai.voicedrop;

import com.baixingai.voicedrop.core.ManualMarkdown;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ManualMarkdownTest {
    @Test public void parsesTheSameBlockKindsAsIos() {
        List<ManualMarkdown.Block> blocks = ManualMarkdown.parse(
                "# 标题\n\n## 第一章\n### 小节\n正文\n\n- 一项\n- 二项\n\n1. 一步\n2. 二步"
                        + "\n\n| 状态 | 意思 |\n|---|---|\n| 完成 | 好 |\n\n```\n代码\n```");

        assertEquals(8, blocks.size());
        assertEquals(ManualMarkdown.Kind.TITLE, blocks.get(0).kind);
        assertEquals(ManualMarkdown.Kind.CHAPTER, blocks.get(1).kind);
        assertEquals(ManualMarkdown.Kind.SECTION, blocks.get(2).kind);
        assertEquals(ManualMarkdown.Kind.PARAGRAPH, blocks.get(3).kind);
        assertEquals(List.of("一项", "二项"), blocks.get(4).items);
        assertEquals(List.of("一步", "二步"), blocks.get(5).items);
        assertEquals(List.of("状态", "意思"), blocks.get(6).items);
        assertEquals(List.of("完成", "好"), blocks.get(6).rows.get(0));
        assertEquals("代码", blocks.get(7).text);
    }

    @Test public void convertsSupportedInlineMarkdownForNativeTextViews() {
        String html = ManualMarkdown.inlineHtml("**重点** `代码` [链接](https://example.com)");
        assertTrue(html.contains("<strong>重点</strong>"));
        assertTrue(html.contains("<code>代码</code>"));
        assertTrue(html.contains("href='https://example.com'"));
    }
}
