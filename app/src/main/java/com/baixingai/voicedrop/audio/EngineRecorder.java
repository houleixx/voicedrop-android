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
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.baixingai.voicedrop.core.RecordingName;

import java.io.File;
import java.nio.ByteBuffer;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EngineRecorder implements RecordingBackend {
    private static final int CAPTURE_SAMPLE_RATE = PcmDownsampler48To16.INPUT_SAMPLE_RATE;
    private static final int ENCODE_SAMPLE_RATE = PcmDownsampler48To16.OUTPUT_SAMPLE_RATE;
    private static final int BIT_RATE = 32000;
    private static final String MIME = "audio/mp4a-latm";
    private static final String TAG = "VoiceDropRecorder";
    private static final long CODEC_POLL_US = 10_000L;
    private static final long FINALIZE_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(4);
    private static final long OUTPUT_EOS_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(2);
    private static final long WORKER_TIMEOUT_SECONDS = 6L;

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
    private volatile boolean finalizedSuccessfully;
    private volatile String failureReason;

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
        finalizedSuccessfully = false;
        failureReason = null;
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
        double duration = Math.max(0, (System.currentTimeMillis() - startedAtMs) / 1000.0);
        recording.set(false);
        boolean finished = waitFinished();
        if (!finished || !finalizedSuccessfully || !Uploader.isUploadable(currentFile)) {
            Log.e(TAG, "Recording finalization failed: " + failureReason);
            resetTakeState();
            return null;
        }
        String finalName = RecordingName.make(start, duration, place);
        File finalFile = new File(AudioRecorder.documentsDir(context), finalName);
        if (!currentFile.renameTo(finalFile)) {
            finalFile = currentFile;
        }
        AudioRecorder.Take take = new AudioRecorder.Take(finalFile, start, duration, peakAmplitude);
        resetTakeState();
        return take;
    }

    @Override public void cancel() {
        recording.set(false);
        waitFinished();
        if (currentFile != null && currentFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            currentFile.delete();
        }
        resetTakeState();
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
        boolean outputEosReceived = false;
        boolean recoverableOutputEosTimeout = false;
        int trackIndex = -1;
        try {
            int min = AudioRecord.getMinBufferSize(CAPTURE_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufferSize = Math.max(min, CAPTURE_SAMPLE_RATE / 5 * 2);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, CAPTURE_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                recording.set(false);
                ready.countDown();
                return;
            }

            MediaFormat format = MediaFormat.createAudioFormat(MIME, ENCODE_SAMPLE_RATE, 1);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            codec = MediaCodec.createEncoderByType(MIME);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            muxer = new MediaMuxer(currentFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            codec.start();
            audioRecord.startRecording();
            ready.countDown();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            byte[] captureBuffer = new byte[bufferSize];
            byte[] encodedPcmBuffer = new byte[bufferSize];
            PcmDownsampler48To16 downsampler = new PcmDownsampler48To16();
            long presentationTimeUs = 0;
            boolean inputDone = false;
            long finalizeDeadlineNs = Long.MAX_VALUE;
            while (!inputDone) {
                if (!recording.get() && finalizeDeadlineNs == Long.MAX_VALUE) {
                    finalizeDeadlineNs = System.nanoTime() + FINALIZE_TIMEOUT_NS;
                }
                int inputIndex = codec.dequeueInputBuffer(CODEC_POLL_US);
                if (inputIndex >= 0) {
                    ByteBuffer input = codec.getInputBuffer(inputIndex);
                    if (input == null) {
                        codec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, 0);
                        throw new IllegalStateException("Codec returned a null input buffer");
                    }
                    input.clear();
                    if (recording.get()) {
                        int outputSampleCapacity = Math.min(input.remaining(), encodedPcmBuffer.length) / 2;
                        int captureSampleCapacity = outputSampleCapacity * 3 - downsampler.pendingSamples();
                        int captureBytes = Math.min(captureBuffer.length,
                                Math.max(0, captureSampleCapacity * 2));
                        int n = captureBytes == 0 ? 0 : audioRecord.read(captureBuffer, 0, captureBytes);
                        if (n <= 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, 0);
                            if (n < 0) {
                                throw new IllegalStateException("AudioRecord.read failed: " + n);
                            }
                        } else {
                            int encodedBytes = downsampler.downsample(
                                    captureBuffer, n, encodedPcmBuffer);
                            input.put(encodedPcmBuffer, 0, encodedBytes);
                            publishPcm(encodedPcmBuffer, encodedBytes);
                            codec.queueInputBuffer(inputIndex, 0, encodedBytes, presentationTimeUs, 0);
                            presentationTimeUs += samplesToUs(encodedBytes / 2);
                        }
                    } else {
                        int flushedBytes = downsampler.flush(encodedPcmBuffer);
                        input.put(encodedPcmBuffer, 0, flushedBytes);
                        publishPcm(encodedPcmBuffer, flushedBytes);
                        codec.queueInputBuffer(inputIndex, 0, flushedBytes, presentationTimeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        presentationTimeUs += samplesToUs(flushedBytes / 2);
                        inputDone = true;
                    }
                }
                DrainResult drained = drain(codec, muxer, info, muxerStarted, trackIndex, 0);
                muxerStarted = drained.muxerStarted;
                trackIndex = drained.trackIndex;
                if (!inputDone && !recording.get() && System.nanoTime() >= finalizeDeadlineNs) {
                    throw new IllegalStateException("Codec input EOS timed out");
                }
            }

            long outputDeadlineNs = System.nanoTime() + OUTPUT_EOS_TIMEOUT_NS;
            while (!outputEosReceived) {
                DrainResult drained = drain(codec, muxer, info, muxerStarted, trackIndex, CODEC_POLL_US);
                muxerStarted = drained.muxerStarted;
                trackIndex = drained.trackIndex;
                outputEosReceived = drained.endOfStream;
                if (!outputEosReceived && System.nanoTime() >= outputDeadlineNs) {
                    throw new IllegalStateException("Codec output EOS timed out");
                }
            }
        } catch (Exception e) {
            failureReason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            recoverableOutputEosTimeout = "Codec output EOS timed out".equals(failureReason);
            if (recoverableOutputEosTimeout) {
                Log.w(TAG, "Codec did not emit output EOS; validating muxer fallback", e);
            } else {
                Log.e(TAG, "Capture/finalization failed", e);
            }
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
            boolean muxerStopped = false;
            try {
                if (muxer != null && muxerStarted) {
                    muxer.stop();
                    muxerStopped = true;
                }
            } catch (Exception e) {
                if (failureReason == null) failureReason = "MediaMuxer.stop failed: " + e.getMessage();
                Log.e(TAG, "MediaMuxer.stop failed", e);
            }
            if (muxer != null) muxer.release();
            finalizedSuccessfully = muxerStopped && (outputEosReceived || recoverableOutputEosTimeout);
            recording.set(false);
            ready.countDown();
            CountDownLatch latch = finishedLatch;
            if (latch != null) latch.countDown();
        }
    }

    private DrainResult drain(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo info,
                              boolean muxerStarted, int trackIndex, long timeoutUs) {
        boolean endOfStream = false;
        long nextTimeoutUs = timeoutUs;
        while (true) {
            int outputIndex = codec.dequeueOutputBuffer(info, nextTimeoutUs);
            nextTimeoutUs = 0;
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

    private void publishPcm(byte[] pcm, int length) {
        if (length <= 0) return;
        updateAmplitude(pcm, length);
        PcmListener listener = pcmListener;
        if (listener == null) return;
        byte[] copy = new byte[length];
        System.arraycopy(pcm, 0, copy, 0, length);
        listener.onPcm16(copy, ENCODE_SAMPLE_RATE);
    }

    private long samplesToUs(int samples) {
        return samples * 1_000_000L / ENCODE_SAMPLE_RATE;
    }

    private boolean waitFinished() {
        CountDownLatch latch = finishedLatch;
        if (latch == null) return false;
        try {
            return latch.await(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void resetTakeState() {
        currentFile = null;
        start = null;
        startedAtMs = 0;
        currentAmplitude = 0;
        peakAmplitude = 0;
        finishedLatch = null;
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
