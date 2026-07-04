package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.LibraryStore;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class LibraryStoreRequestTest {
    @Test
    public void restyleBodyOmitsStyleVWhenUsingCurrentHeadStyle() throws Exception {
        JSONObject body = LibraryStore.restyleRequestBody("VoiceDrop-2026-07-01-120000-0m1s", null);

        assertEquals("VoiceDrop-2026-07-01-120000-0m1s", body.getString("stem"));
        assertFalse(body.has("styleV"));
    }

    @Test
    public void restyleBodyIncludesExplicitStyleVersionOnlyWhenProvided() throws Exception {
        JSONObject body = LibraryStore.restyleRequestBody("VoiceDrop-2026-07-01-120000-0m1s", 8);

        assertEquals(8, body.getInt("styleV"));
    }
}
