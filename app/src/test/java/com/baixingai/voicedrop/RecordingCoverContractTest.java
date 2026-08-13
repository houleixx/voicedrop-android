package com.baixingai.voicedrop;

import com.baixingai.voicedrop.core.RecordingName;
import com.baixingai.voicedrop.data.Recording;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RecordingCoverContractTest {
    @Test public void dedicatedCoverUsesSharedPhotoDirectoryContract() {
        assertEquals("photos/2026-08-13-091500/cover.jpg",
                RecordingName.coverKey("2026-08-13-091500"));
        Recording rec = new Recording(
                "VoiceDrop-2026-08-13-091500-3m20s-Wed-Morning-Shanghai.m4a", "", true, false);
        assertEquals("photos/2026-08-13-091500/cover.jpg", rec.coverJpgKey());
        assertNull(new Recording("random.m4a", "", true, false).coverJpgKey());
    }
}
