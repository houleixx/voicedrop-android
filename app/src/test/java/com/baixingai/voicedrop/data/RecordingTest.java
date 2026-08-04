package com.baixingai.voicedrop.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RecordingTest {
    @Test
    public void savingStatePrecedesUploadingState() {
        Recording recording = new Recording("VoiceDrop-2026-08-04-120000-0m8s-Mon-Noon.m4a", "", false, false);

        recording.saving = true;
        recording.uploading = true;
        assertEquals("正在保存", recording.statusLabel());

        recording.saving = false;
        assertEquals("正在上传", recording.statusLabel());
    }
}
