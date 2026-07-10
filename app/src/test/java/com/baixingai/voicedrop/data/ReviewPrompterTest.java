package com.baixingai.voicedrop.data;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReviewPrompterTest {
    @Test
    public void promptsOnlyOnMilestoneArticleOpens() {
        assertFalse(ReviewPrompter.shouldPrompt(1));
        assertFalse(ReviewPrompter.shouldPrompt(2));
        assertTrue(ReviewPrompter.shouldPrompt(3));
        assertFalse(ReviewPrompter.shouldPrompt(9));
        assertTrue(ReviewPrompter.shouldPrompt(10));
        assertFalse(ReviewPrompter.shouldPrompt(29));
        assertTrue(ReviewPrompter.shouldPrompt(30));
        assertFalse(ReviewPrompter.shouldPrompt(31));
    }
}
