package com.baixingai.voicedrop;

import com.baixingai.voicedrop.update.UpdatePrefs;

import org.junit.Test;

import static org.junit.Assert.*;

public class UpdatePrefsTest {
    @Test
    public void allowsAutoCheckWhenInstalledVersionChanged() {
        long now = 1_000_000L;
        assertTrue(UpdatePrefs.shouldAutoCheck(now, now - 1_000L, "0.0.1", "0.1.0"));
    }

    @Test
    public void skipsAutoCheckWithinIntervalForSameVersion() {
        long now = 1_000_000L;
        assertFalse(UpdatePrefs.shouldAutoCheck(now, now - 1_000L, "0.1.0", "0.1.0"));
    }
}
