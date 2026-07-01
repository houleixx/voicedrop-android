package com.baixingai.voicedrop.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class VolcASRProtocol {
    private static final int FULL_CLIENT = 0x1;
    private static final int AUDIO_ONLY = 0x2;
    private static final int FULL_SERVER = 0x9;
    private static final int ERROR_RESPONSE = 0xf;
    private static final int POSITIVE_SEQUENCE = 0x1;
    private static final int NEGATIVE_SEQUENCE = 0x3;
    private static final int JSON_SERIALIZATION = 0x1;
    private static final int NO_SERIALIZATION = 0x0;
    private static final int GZIP_COMPRESSION = 0x1;

    private VolcASRProtocol() {}

    public static byte[] buildFullClientPayload(String appUserID, int sampleRate) throws Exception {
        String payload = "{"
                + "\"user\":{\"uid\":\"" + jsonEscape(appUserID) + "\"},"
                + "\"audio\":{\"format\":\"pcm\",\"rate\":" + sampleRate + ",\"bits\":16,\"channel\":1,\"codec\":\"raw\"},"
                + "\"request\":{\"model_name\":\"bigmodel\",\"enable_punc\":true,\"enable_itn\":true,\"show_utterances\":true}"
                + "}";
        byte[] json = payload.getBytes("UTF-8");
        return frame(FULL_CLIENT, POSITIVE_SEQUENCE, JSON_SERIALIZATION, GZIP_COMPRESSION, 1, gzip(json));
    }

    public static byte[] buildAudioPayload(byte[] pcm, int sequence, boolean isLast) throws Exception {
        int seq = isLast ? -Math.abs(sequence) : Math.abs(sequence);
        return frame(AUDIO_ONLY, isLast ? NEGATIVE_SEQUENCE : POSITIVE_SEQUENCE,
                NO_SERIALIZATION, GZIP_COMPRESSION, seq, gzip(pcm));
    }

    public static ParsedMessage parseServerMessage(byte[] data) throws Exception {
        if (data.length < 8) return ParsedMessage.error("ASR response too short");
        int messageType = (data[1] >> 4) & 0x0f;
        int flags = data[1] & 0x0f;
        int serialization = (data[2] >> 4) & 0x0f;
        int compression = data[2] & 0x0f;
        int offset = 4;
        if ((flags & 0x01) != 0 || (flags & 0x02) != 0) offset += 4;

        if (messageType == ERROR_RESPONSE) {
            if (data.length < offset + 8) return ParsedMessage.error("ASR error response too short");
            long code = u32(data, offset);
            int size = (int) u32(data, offset + 4);
            byte[] body = slice(data, offset + 8, size);
            if (compression == GZIP_COMPRESSION) body = gunzip(body);
            return new ParsedMessage("", true, true, code, new String(body, "UTF-8"));
        }

        if (messageType != FULL_SERVER || data.length < offset + 4) {
            return new ParsedMessage("", false, false, null, null);
        }
        int size = (int) u32(data, offset);
        byte[] body = slice(data, offset + 4, size);
        if (compression == GZIP_COMPRESSION) body = gunzip(body);
        if (serialization != JSON_SERIALIZATION) {
            return new ParsedMessage("", (flags & 0x02) != 0, false, null, null);
        }
        JSONObject obj = new JSONObject(new String(body, "UTF-8"));
        JSONObject result = obj.optJSONObject("result");
        String text = "";
        if (result != null) {
            text = result.optString("text", "");
            if (text.isEmpty()) {
                JSONArray utterances = result.optJSONArray("utterances");
                if (utterances != null) {
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < utterances.length(); i++) {
                        builder.append(utterances.getJSONObject(i).optString("text"));
                    }
                    text = builder.toString();
                }
            }
        }
        return new ParsedMessage(text, (flags & 0x02) != 0, false, null, null);
    }

    private static byte[] frame(int messageType, int flags, int serialization, int compression, int sequence, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(12 + payload.length).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0x11);
        buffer.put((byte) ((messageType << 4) | flags));
        buffer.put((byte) ((serialization << 4) | compression));
        buffer.put((byte) 0);
        buffer.putInt(sequence);
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    private static long u32(byte[] data, int offset) {
        return ((long) data[offset] & 0xff) << 24
                | ((long) data[offset + 1] & 0xff) << 16
                | ((long) data[offset + 2] & 0xff) << 8
                | ((long) data[offset + 3] & 0xff);
    }

    private static byte[] slice(byte[] data, int offset, int size) {
        int end = Math.min(data.length, offset + Math.max(0, size));
        byte[] out = new byte[Math.max(0, end - offset)];
        System.arraycopy(data, offset, out, 0, out.length);
        return out;
    }

    private static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = gzip.read(buffer)) >= 0) out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static String jsonEscape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c);
                        out.append("\\u");
                        for (int j = hex.length(); j < 4; j++) out.append('0');
                        out.append(hex);
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    public static final class ParsedMessage {
        public final String text;
        public final boolean isFinal;
        public final boolean isError;
        public final Long errorCode;
        public final String errorMessage;

        ParsedMessage(String text, boolean isFinal, boolean isError, Long errorCode, String errorMessage) {
            this.text = text;
            this.isFinal = isFinal;
            this.isError = isError;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        static ParsedMessage error(String message) {
            return new ParsedMessage("", true, true, null, message);
        }
    }
}
