package com.baixingai.voicedrop.net;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public class HttpClientTest {
    @Test
    public void sendsAndroidPlatformHeader() throws Exception {
        AtomicReference<String> platform = new AtomicReference<>("");
        ServerSocket server = new ServerSocket(0);
        Thread thread = new Thread(() -> {
            try (Socket socket = server.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 OutputStream out = socket.getOutputStream()) {
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("x-vd-platform:")) {
                        platform.set(line.substring(line.indexOf(':') + 1).trim());
                    }
                }
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                out.write(("HTTP/1.1 200 OK\r\nContent-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(body);
                out.flush();
            } catch (Exception ignored) {
            }
        });
        thread.start();
        try {
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/ping";
            new HttpClient().get(url, "");
            thread.join(1000);
            assertEquals("android", platform.get());
        } finally {
            server.close();
        }
    }
}
