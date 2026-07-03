package com.baixingai.voicedrop;

import com.baixingai.voicedrop.ui.Theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ThemeColorTest {
    @Test
    public void androidThemeMatchesIosAccentTokens() {
        assertEquals(0xffd8593b, Theme.ACCENT);
        assertEquals(0xffe5392e, Theme.RED);
        assertEquals(0xff8a8175, Theme.SECONDARY);
        assertEquals(0xffb8ae9e, Theme.FAINT);
    }
}
