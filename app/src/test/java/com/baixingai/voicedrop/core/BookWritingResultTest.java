package com.baixingai.voicedrop.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BookWritingResultTest {
    @Test
    public void acceptsOnlyTheCurrentAcceptedStatus() {
        assertTrue(BookWritingResult.from(202, "{}").accepted);
        assertFalse(BookWritingResult.from(401, "{}").accepted);
    }

    @Test
    public void insufficientCreditUsesTheServerAmounts() {
        BookWritingResult result = BookWritingResult.from(402,
                "{\"error\":\"no-credit\",\"need_suanli\":320,\"suanli\":12.5}");
        assertTrue(result.message.contains("要 320 算力"));
        assertTrue(result.message.contains("现在有 12.5"));
        assertTrue(result.message.contains("设置 → 算力"));
    }

    @Test
    public void invalidTokenDoesNotClaimThatAnArticleIsRequired() {
        String message = BookWritingResult.from(401, "{}").message;
        assertTrue(message.contains("身份校验"));
        assertFalse(message.contains("文章"));
    }
}
