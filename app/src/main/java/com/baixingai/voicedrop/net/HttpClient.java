package com.baixingai.voicedrop.net;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class HttpClient {
    public Response get(String url, String bearer) throws IOException {
        return request("GET", url, bearer, null, null, null);
    }

    public Response delete(String url, String bearer) throws IOException {
        return request("DELETE", url, bearer, null, null, null);
    }

    public Response postJson(String url, String bearer, byte[] body) throws IOException {
        return request("POST", url, bearer, "application/json", body, null);
    }

    public Response patchJson(String url, String bearer, byte[] body) throws IOException {
        return request("PATCH", url, bearer, "application/json", body, null);
    }

    public Response putBytes(String url, String bearer, String contentType, byte[] body) throws IOException {
        return request("PUT", url, bearer, contentType, body, null);
    }

    public Response putFile(String url, String bearer, String contentType, File file) throws IOException {
        return request("PUT", url, bearer, contentType, null, file);
    }

    private Response request(String method, String url, String bearer, String contentType, byte[] body, File file) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(120_000);
        if (bearer != null && !bearer.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + bearer);
        conn.setRequestProperty("X-VD-Platform", "android");
        if (contentType != null) conn.setRequestProperty("Content-Type", contentType);
        if (body != null || file != null) {
            conn.setDoOutput(true);
            try (OutputStream out = conn.getOutputStream()) {
                if (file != null) {
                    try (InputStream in = new FileInputStream(file)) {
                        copy(in, out);
                    }
                } else {
                    out.write(body);
                }
            }
        }
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        byte[] bytes = stream == null ? new byte[0] : readAll(stream);
        conn.disconnect();
        return new Response(code, bytes);
    }

    public static byte[] readAll(InputStream in) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copy(input, out);
            return out.toByteArray();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
    }

    public static final class Response {
        public final int code;
        public final byte[] body;

        public Response(int code, byte[] body) {
            this.code = code;
            this.body = body;
        }

        public boolean ok() {
            return code >= 200 && code < 300;
        }

        public String text() {
            return new String(body);
        }
    }
}
