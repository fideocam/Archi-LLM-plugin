package com.archimatetool.archigpt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@SuppressWarnings("nls")
final class ExternalLlmHttp {

    private static final int CONNECT_MS = 15_000;

    private ExternalLlmHttp() {}

    static String postJson(String urlString, Map<String, String> headers, String jsonBody,
            AtomicReference<HttpURLConnection> connectionHolder) throws IOException {
        byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (connectionHolder != null) {
            connectionHolder.set(conn);
        }
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_MS);
            conn.setReadTimeout(LlmContextConfig.resolveOllamaReadTimeoutMs());
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int code = conn.getResponseCode();
            String resp = readFully(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code >= 400) {
                throw new IOException("HTTP " + code + summarizeErrorBody(resp));
            }
            return resp != null ? resp : "";
        } finally {
            if (connectionHolder != null) {
                connectionHolder.set(null);
            }
            conn.disconnect();
        }
    }

    static String get(String urlString, Map<String, String> headers) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            conn.setConnectTimeout(CONNECT_MS);
            conn.setReadTimeout(15_000);
            int code = conn.getResponseCode();
            String resp = readFully(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code >= 400) {
                throw new IOException("HTTP " + code + summarizeErrorBody(resp));
            }
            return resp != null ? resp : "";
        } finally {
            conn.disconnect();
        }
    }

    private static String summarizeErrorBody(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String t = body.replace("\n", " ").trim();
        if (t.length() > 400) {
            return ": " + t.substring(0, 400) + "…";
        }
        return ": " + t;
    }

    private static String readFully(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return r.lines().collect(Collectors.joining("\n"));
        }
    }
}
