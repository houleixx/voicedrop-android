package com.baixingai.voicedrop;

import com.baixingai.voicedrop.core.ArticlePhotoInsert;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ArticlePhotoInsertTest {
    @Test
    public void buildsInstructionThatRequiresEveryPhotoMarker() {
        String instruction = ArticlePhotoInsert.instructionForKeys(Arrays.asList(
                "photos/2026-06-24-131500/23-k7p.jpg",
                "photos/2026-06-24-131500/24-a1b.jpg"));

        assertTrue(instruction.contains("这2张照片"));
        assertTrue(instruction.contains("[[photo:photos/2026-06-24-131500/23-k7p.jpg]]"));
        assertTrue(instruction.contains("[[photo:photos/2026-06-24-131500/24-a1b.jpg]]"));
        assertTrue(instruction.contains("所有照片必须全部插入，不能遗漏"));
    }

    @Test
    public void computesNonNegativeOffsetFromSessionTimestamp() {
        ZonedDateTime captureTime = ZonedDateTime.of(2026, 6, 24, 13, 15, 42, 0, ZoneId.of("Asia/Shanghai"));

        assertEquals(42, ArticlePhotoInsert.offsetSeconds("2026-06-24-131500", captureTime));
        assertEquals(0, ArticlePhotoInsert.offsetSeconds("2026-06-24-131500",
                ZonedDateTime.of(2026, 6, 24, 13, 14, 59, 0, ZoneId.of("Asia/Shanghai"))));
    }

    @Test
    public void sampleSizeDownscalesLargeCameraImagesBeforeDecode() {
        assertEquals(8, ArticlePhotoInsert.sampleSizeForBounds(8064, 6048, 1200));
        assertEquals(1, ArticlePhotoInsert.sampleSizeForBounds(900, 600, 1200));
        assertEquals(1, ArticlePhotoInsert.sampleSizeForBounds(0, 6048, 1200));
    }
}
