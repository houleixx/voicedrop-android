package com.baixingai.voicedrop.audio;

import android.content.Context;
import android.media.MediaRecorder;

import com.baixingai.voicedrop.core.RecordingName;

import java.io.File;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class AudioRecorder {
    public static final String STAGING_PREFIX = "recording-";

    private final Context context;
    private MediaRecorder recorder;
    private File currentFile;
    private ZonedDateTime start;
    private long startedAtMs;
    private int peakAmplitude;

    public AudioRecorder(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() throws Exception {
        cleanupStaleStaging(context);
        start = ZonedDateTime.now();
        currentFile = stagingFile(context, start);
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioChannels(1);
        recorder.setAudioSamplingRate(16000);
        recorder.setAudioEncodingBitRate(32000);
        recorder.setOutputFile(currentFile.getAbsolutePath());
        recorder.prepare();
        recorder.start();
        startedAtMs = System.currentTimeMillis();
        peakAmplitude = 0;
    }

    public Take stop(String place) {
        if (recorder == null || currentFile == null || start == null) return null;
        sampleAmplitude();
        try {
            recorder.stop();
        } catch (RuntimeException ignored) {
        }
        recorder.release();
        recorder = null;
        double duration = Math.max(0, (System.currentTimeMillis() - startedAtMs) / 1000.0);
        String finalName = RecordingName.make(start, duration, place);
        File finalFile = new File(documentsDir(context), finalName);
        if (!currentFile.renameTo(finalFile)) {
            finalFile = currentFile;
        }
        Take take = new Take(finalFile, start, duration, peakAmplitude);
        currentFile = null;
        start = null;
        peakAmplitude = 0;
        return take;
    }

    public void cancel() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
            }
            recorder.release();
            recorder = null;
        }
        if (currentFile != null && currentFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            currentFile.delete();
        }
        currentFile = null;
        start = null;
        startedAtMs = 0;
        peakAmplitude = 0;
    }

    public boolean isRecording() {
        return recorder != null;
    }

    public long elapsedSeconds() {
        return isRecording() ? Math.max(0, (System.currentTimeMillis() - startedAtMs) / 1000) : 0;
    }

    public ZonedDateTime startDate() {
        return start;
    }

    public int sampleAmplitude() {
        if (recorder == null) return peakAmplitude;
        try {
            peakAmplitude = Math.max(peakAmplitude, recorder.getMaxAmplitude());
        } catch (RuntimeException ignored) {
        }
        return peakAmplitude;
    }

    public static File documentsDir(Context context) {
        File dir = context.getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File stagingFile(Context context, ZonedDateTime start) {
        return new File(documentsDir(context), STAGING_PREFIX + RecordingName.timestamp(start) + ".m4a");
    }

    public static void cleanupStaleStaging(Context context) {
        File[] files = documentsDir(context).listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().startsWith(STAGING_PREFIX)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    public static final class Take {
        public final File file;
        public final ZonedDateTime start;
        public final double duration;
        public final int peakAmplitude;

        Take(File file, ZonedDateTime start, double duration, int peakAmplitude) {
            this.file = file;
            this.start = start.withZoneSameInstant(ZoneId.systemDefault());
            this.duration = duration;
            this.peakAmplitude = peakAmplitude;
        }
    }
}
