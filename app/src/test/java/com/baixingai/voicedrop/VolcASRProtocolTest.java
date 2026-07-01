package com.baixingai.voicedrop;

import com.baixingai.voicedrop.net.VolcASRProtocol;

import org.junit.Test;

import static org.junit.Assert.*;

public class VolcASRProtocolTest {
    @Test
    public void buildsFullClientAndAudioFrames() throws Exception {
        byte[] full = VolcASRProtocol.buildFullClientPayload("voicedrop-edit", 16000);
        byte[] audio = VolcASRProtocol.buildAudioPayload(new byte[]{1, 2, 3, 4}, 2, false);
        byte[] last = VolcASRProtocol.buildAudioPayload(new byte[0], 3, true);

        assertEquals(0x11, full[0] & 0xff);
        assertEquals(0x11, full[1] & 0xff);
        assertEquals(0x11, full[2] & 0xff);
        assertEquals(0x21, audio[1] & 0xff);
        assertEquals(0x23, last[1] & 0xff);
    }
}
