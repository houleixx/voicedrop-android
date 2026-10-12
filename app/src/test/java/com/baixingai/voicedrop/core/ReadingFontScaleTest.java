package com.baixingai.voicedrop.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReadingFontScaleTest {
    @Test public void unsupportedStoredValuesFallBackToStandard() {
        for (float value : new float[]{0f, -1f, 2f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertEquals(1f, ReadingFontScale.normalize(value), 0f);
            assertEquals(1, ReadingFontScale.index(value));
        }
    }
    @Test public void webTextZoomUsesTheSameFiveLevelsAndSafeDefault() {
        int[] percentages = {90, 100, 115, 130, 150};
        for (int i = 0; i < percentages.length; i++) {
            assertEquals(percentages[i], ReadingFontScale.percent(ReadingFontScale.value(i)));
        }
        assertEquals(100, ReadingFontScale.percent(Float.NaN));
        assertEquals(100, ReadingFontScale.percent(10f));
    }
    @Test public void allFiveLevelsRoundTripThroughThePreferenceModel() {
        assertEquals(5, ReadingFontScale.count());
        for (int i = 0; i < ReadingFontScale.count(); i++) {
            assertEquals(i, ReadingFontScale.index(ReadingFontScale.value(i)));
            assertFalse(ReadingFontScale.label(i).isEmpty());
        }
        assertEquals(0.9f, ReadingFontScale.value(0), 0f);
        assertEquals(1.5f, ReadingFontScale.value(4), 0f);
    }
}
