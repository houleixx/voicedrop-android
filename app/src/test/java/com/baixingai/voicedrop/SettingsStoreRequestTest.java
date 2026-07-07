package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.SettingsStore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class SettingsStoreRequestTest {
    @Test
    public void styleSelectionBodyMatchesIosProfileStylesContract() throws Exception {
        JSONObject body = SettingsStore.styleSelectionBody(Arrays.asList(12, 9, 3));
        JSONArray styles = body.getJSONArray("styles");

        assertEquals(3, styles.length());
        assertEquals(12, styles.getInt(0));
        assertEquals(9, styles.getInt(1));
        assertEquals(3, styles.getInt(2));
    }

    @Test
    public void emptyStyleSelectionClearsMultiStyle() throws Exception {
        JSONObject body = SettingsStore.styleSelectionBody(Collections.emptyList());

        assertEquals(0, body.getJSONArray("styles").length());
    }

    @Test
    public void appConfigBodyStoresNoFollowupsOnlyWhenDisabled() throws Exception {
        JSONObject enabled = SettingsStore.appConfigBody(true, true);
        JSONObject disabled = SettingsStore.appConfigBody(false, false);

        assertTrue(enabled.getBoolean("autoShareCommunity"));
        assertFalse(enabled.has("noFollowups"));
        assertFalse(disabled.getBoolean("autoShareCommunity"));
        assertTrue(disabled.getBoolean("noFollowups"));
    }
}
