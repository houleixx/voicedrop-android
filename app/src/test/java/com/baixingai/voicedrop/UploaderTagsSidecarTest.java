package com.baixingai.voicedrop;

import com.baixingai.voicedrop.audio.Uploader;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.*;

public class UploaderTagsSidecarTest {
    @Test
    public void writesAndReadsTagsSidecarNextToAudioFile() throws Exception {
        File dir = Files.createTempDirectory("voicedrop-tags").toFile();
        File audio = new File(dir, "VoiceDrop-2026-07-05-120000-0m1s.m4a");
        assertTrue(audio.createNewFile());

        Uploader.writeTagsSidecar(audio, Arrays.asList("创业", "产品"));

        File sidecar = Uploader.tagsSidecarFile(audio);
        assertEquals("VoiceDrop-2026-07-05-120000-0m1s.tags.json", sidecar.getName());
        assertEquals("[\"创业\",\"产品\"]", new String(Files.readAllBytes(sidecar.toPath()), StandardCharsets.UTF_8));
        assertEquals(Arrays.asList("创业", "产品"), Uploader.readTagsSidecar(audio));
    }
}
