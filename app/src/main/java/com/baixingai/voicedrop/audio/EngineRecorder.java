package com.baixingai.voicedrop.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;

import androidx.core.content.ContextCompat;

import com.baixingai.voicedrop.core.RecordingName;

import java.io.File;
import java.nio.ByteBuffer;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EngineRecorder implements RecordingBackend {
    private static final int SAMPLE_RATE = 16000;
    private static final int BIT_RATE = 32000;
    private static final String MIME = "audio/mp4a-latm";

    private final Context context;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private Thread worker;
    private File currentFile;
    private ZonedDateTime start;
    private long startedAtMs;
    private volatile int peakAmplitude;
    private volatile int currentAmplitude;
    private volatile PcmListener pcmListener;
    private volatile CountDownLatch finishedLatch;

    public EngineRecorder(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setPcmListener(PcmListener pcmListener) {
        this.pcmListener = pcmListener;
    }

    @Override public void start() throws Exception {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("RECORD_AUDIO permission is required");
        }
        if (!recording.compareAndSet(false, true)) return;
        AudioRecorder.cleanupStaleStaging(context);
        start = ZonedDateTime.now();
        currentFile = AudioRecorder.stagingFile(context, start);
        startedAtMs = System.currentTimeMillis();
        peakAmplitude = 0;
        currentAmplitude = 0;
        CountDownLatch ready = new CountDownLatch(1);
        finishedLatch = new CountDownLatch(1);
        worker = new Thread(() -> captureAndEncode(ready), "voicedrop-engine-recorder");
        worker.start();
        if (!ready.await(2, TimeUnit.SECONDS)) {
            cancel();
            throw new IllegalStateException("录音引擎启动超时");
        }
        if (!recording.get()) {
            cancel();
            throw new IllegalStateException("录音引擎启动失败");
        }
    }

    @Override public AudioRecorder.Take stop(String place) {
        if (!recording.get() || currentFile == null || start == null) return null;
        recording.set(false);
        waitFinished();
        double duration = Math.max(0, (System.currentTimeMillis() - startedAtMs) / 1000.0);
        String finalName = RecordingName.make(start, duration, place);
        File finalFile = new File(AudioRecorder.documentsDir(context), finalName);
        if (!currentFile.renameTo(finalFile)) {
            finalFile = currentFile;
        }
        AudioRecorder.Take take = new AudioRecorder.Take(finalFile, start, duration, peakAmplitude);
        currentFile = null;
        start = null;
        startedAtMs = 0;
        currentAmplitude = 0;
        peakAmplitude = 0;
        return take;
    }

    @Override public void cancel() {
        recording.set(false);
        waitFinished();
        if (currentFile != null && currentFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            currentFile.delete();
        }
        currentFile = null;
        start = null;
        startedAtMs = 0;
        currentAmplitude = 0;
        peakAmplitude = 0;
    }

    @Override public boolean isRecording() {
        return recording.get();
    }

    @Override public long elapsedSeconds() {
        return isRecording() ? Math.max(0, (System.currentTimeMillis() - startedAtMs) / 1000) : 0;
    }

    @Override public ZonedDateTime startDate() {
        return start;
    }

    @Override public int sampleAmplitude() {
        return peakAmplitude;
    }

    @Override public int sampleCurrentAmplitude() {
        return currentAmplitude;
    }

    @SuppressWarnings("MissingPermission")
    private void captureAndEncode(CountDownLatch ready) {
        AudioRecord audioRecord = null;
        MediaCodec codec = null;
        MediaMuxer muxer = null;
        boolean muxerStarted = false;
        int trackIndex = -1;
        try {
            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufferSize = Math.max(min, SAMPLE_RATE / 5 * 2);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                recording.set(false);
                ready.countDown();
                return;
            }

            MediaFormat format = MediaFormat.createAudioFormat(MIME, SAMPLE_RATE, 1);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            codec = MediaCodec.createEncoderByType(MIME);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            muxer = new MediaMuxer(currentFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            codec.start();
            audioRecord.startRecording();
            ready.countDown();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            byte[] pcmBuffer = new byte[bufferSize];
            long presentationTimeUs = 0;
            boolean inputDone = false;
            while (!inputDone) {
                int inputIndex = codec.dequeueInputBuffer(10_000);
                if (inputIndex >= 0) {
                    ByteBuffer input = codec.getInputBuffer(inputIndex);
                    if (input == null) continue;
                    input.clear();
                    if (recording.get()) {
                        int n = audioRecord.read(pcmBuffer, 0, Math.min(pcmBuffer.length, input.remaining()));
                        if (n > 0) {
                            input.put(pcmBuffer, 0, n);
                            updateAmplitude(pcmBuffer, n);
                            PcmListener listener = pcmListener;
                            if (listener != null) {
                                byte[] copy = new byte[n];
                                System.arraycopy(pcmBuffer, 0, copy, 0, n);
                                listener.onPcm16(copy, SAMPLE_RATE);
                            }
                            codec.queueInputBuffer(inputIndex, 0, n, presentationTimeUs, 0);
                            presentationTimeUs += samplesToUs(n / 2);
                        }
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    }
                }
                DrainResult drained = drain(codec, muxer, info, muxerStarted, trackIndex);
                muxerStarted = drained.muxerStarted;
                trackIndex = drained.trackIndex;
            }

            boolean outputDone = false;
            while (!outputDone) {
                DrainResult drained = drain(codec, muxer, info, muxerStarted, trackIndex);
                muxerStarted = drained.muxerStarted;
                trackIndex = drained.trackIndex;
                outputDone = drained.endOfStream;
            }
        } catch (Exception ignored) {
            recording.set(false);
        } finally {
            try {
                if (audioRecord != null) audioRecord.stop();
            } catch (Exception ignored) {
            }
            if (audioRecord != null) audioRecord.release();
            try {
                if (codec != null) codec.stop();
            } catch (Exception ignored) {
            }
            if (codec != null) codec.release();
            try {
                if (muxer != null && muxerStarted) muxer.stop();
            } catch (Exception ignored) {
            }
            if (muxer != null) muxer.release();
            recording.set(false);
            ready.countDown();
            CountDownLatch latch = finishedLatch;
            if (latch != null) latch.countDown();
        }
    }

    private DrainResult drain(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo info,
                              boolean muxerStarted, int trackIndex) {
        boolean endOfStream = false;
        while (true) {
            int outputIndex = codec.dequeueOutputBuffer(info, 0);
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                trackIndex = muxer.addTrack(codec.getOutputFormat());
                muxer.start();
                muxerStarted = true;
            } else if (outputIndex >= 0) {
                ByteBuffer output = codec.getOutputBuffer(outputIndex);
                if (output != null && info.size > 0 && muxerStarted) {
                    output.position(info.offset);
                    output.limit(info.offset + info.size);
                    muxer.writeSampleData(trackIndex, output, info);
                }
                endOfStream = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(outputIndex, false);
                if (endOfStream) break;
            }
        }
        return new DrainResult(muxerStarted, trackIndex, endOfStream);
    }

    private void updateAmplitude(byte[] pcm, int length) {
        int max = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            int lo = pcm[i] & 0xff;
            int hi = pcm[i + 1];
            int sample = Math.abs((short) (lo | (hi << 8)));
            if (sample > max) max = sample;
        }
        currentAmplitude = max;
        if (max > peakAmplitude) peakAmplitude = max;
    }

    private long samplesToUs(int samples) {
        return samples * 1_000_000L / SAMPLE_RATE;
    }

    private void waitFinished() {
        CountDownLatch latch = finishedLatch;
        if (latch == null) return;
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class DrainResult {
        final boolean muxerStarted;
        final int trackIndex;
        final boolean endOfStream;

        DrainResult(boolean muxerStarted, int trackIndex, boolean endOfStream) {
            this.muxerStarted = muxerStarted;
            this.trackIndex = trackIndex;
            this.endOfStream = endOfStream;
        }
    }
}
