package com.baixingai.voicedrop;

import com.baixingai.voicedrop.update.AppVersion;

import org.junit.Test;

import static org.junit.Assert.*;

public class AppVersionTest {
    @Test
    public void comparesMultiSegmentVersionsNumerically() {
        assertTrue(AppVersion.isNewer("0.1.10", "0.1.9"));
        assertTrue(AppVersion.isNewer("1.0.0", "0.9.99"));
        assertFalse(AppVersion.isNewer("0.1.0", "0.1"));
    }

    @Test
    public void ignoresPrefixAndPreviewSuffixForStableComparison() {
        assertTrue(AppVersion.isNewer("v0.2.0", "0.1.9"));
        assertFalse(AppVersion.isNewer("0.2.0-beta", "0.2.0"));
        assertFalse(AppVersion.isNewer("0.2.0-ci-test.3", "0.2.0"));
    }
}
