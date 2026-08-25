package com.baixingai.voicedrop.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BookWritingSeedTest {
    @Test public void articleSeedAllowsAnEmptySupplementalRequirement() {
        assertTrue(BookWritingSeed.canSubmit("   ", true));
        assertFalse(BookWritingSeed.canSubmit("   ", false));
    }

    @Test public void articleSeedKeepsTheRequirementAndArticleStructure() {
        assertEquals("写书要求：写给孩子\n\n以下这篇文章是种子素材，把它扩展成一本完整的书：\n\n《星空》\n\n正文",
                BookWritingSeed.fromArticle(" 写给孩子 ", " 星空 ", "正文"));
    }

    @Test public void articleSeedIsCappedAtTheSharedServerLimit() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < BookWritingSeed.MAX_CHARS + 10; i++) body.append('文');
        assertEquals(BookWritingSeed.MAX_CHARS,
                BookWritingSeed.fromArticle("", "题", body.toString()).length());
    }
}
