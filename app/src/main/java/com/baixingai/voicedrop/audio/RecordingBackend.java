package com.baixingai.voicedrop.audio;

import java.time.ZonedDateTime;

public interface RecordingBackend {
    void start() throws Exception;
    AudioRecorder.Take stop(String place);
    void cancel();
    boolean isRecording();
    long elapsedSeconds();
    ZonedDateTime startDate();
    int sampleAmplitude();
    int sampleCurrentAmplitude();

    interface PcmListener {
        void onPcm16(byte[] pcm16le, int sampleRate);
    }
}
