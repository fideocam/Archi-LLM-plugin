/**
 * Client for local Ollama API. Sends prompts to http://localhost:11434/api/generate.
 */
package com.archimatetool.archigpt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Connects to a locally running Ollama instance and requests a completion for the given prompt.
 */
@SuppressWarnings("nls")
public class OllamaClient {

    public static final String DEFAULT_BASE_URL = "http://localhost:11434";
    public static final String DEFAULT_MODEL = "llama3.2";

    private final String baseUrl;
    private final String model;

    public OllamaClient() {
        this(DEFAULT_BASE_URL, DEFAULT_MODEL);
    }

    public OllamaClient(String baseUrl, String model) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.model = model != null ? model : DEFAULT_MODEL;
    }

    /**
     * Check that Ollama is reachable (e.g. for user feedback before a long request).
     *
     * @return true if the server responds
     */
    /**
     * Ask Ollama for the running model's reported context length (tokens), via {@code POST /api/show}.
     * Returns 0 if the call fails or no value is found (caller should fall back to defaults).
     */
    public int fetchReportedContextTokens() throws IOException {
        String body = "{\"model\":\"" + escapeJson(model) + "\"}";
        URL url = new URL(baseUrl + "/api/show");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            String resp = readFully(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code >= 400) {
                return 0;
            }
            return OllamaShowResponseParser.parseContextTokens(resp);
        } finally {
            conn.disconnect();
        }
    }

    public boolean checkConnection() {
        try {
            URL url = new URL(baseUrl + "/api/tags");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 400;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Send a user prompt with a system prompt using the chat API (no cancellation support).
     */
    public String generateWithSystemPrompt(String systemPrompt, String userPrompt) throws IOException {
        return generateWithSystemPrompt(systemPrompt, userPrompt, null);
    }

    /**
     * Send a user prompt with a system prompt (e.g. ArchiMate 3.2 instructions) using the chat API.
     *
     * @param systemPrompt    system message that defines role and output format
     * @param userPrompt      user message (e.g. the requested model change)
     * @param connectionHolder optional; if non-null, the active HttpURLConnection is stored here so the caller can disconnect it to cancel the request
     * @return the model's response text (e.g. JSON for import)
     */
    public String generateWithSystemPrompt(String systemPrompt, String userPrompt,
            AtomicReference<HttpURLConnection> connectionHolder) throws IOException {
        return generateWithSystemPrompt(systemPrompt, userPrompt, connectionHolder, 0);
    }

    /**
     * Send a user prompt with a system prompt using the chat API, optionally requesting a larger context size.
     *
     * @param systemPrompt    system message that defines role and output format
     * @param userPrompt      user message (e.g. prompt + model XML)
     * @param connectionHolder optional; if non-null, the active HttpURLConnection is stored here for cancellation
     * @param numCtx          requested context length in tokens (e.g. 8192); if &lt;= 0, Ollama default is used
     * @return the model's response text
     */
    public String generateWithSystemPrompt(String systemPrompt, String userPrompt,
            AtomicReference<HttpURLConnection> connectionHolder, int numCtx) throws IOException {
        String requestBody = buildChatRequestJson(systemPrompt, userPrompt, numCtx);
        URL url = new URL(baseUrl + "/api/chat");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (connectionHolder != null) {
            connectionHolder.set(conn);
        }
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(LlmContextConfig.resolveOllamaReadTimeoutMs());

            try (OutputStream os = conn.getOutputStream()) {
                byte[] bytes = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(bytes);
            }

            int code = conn.getResponseCode();
            String body = readFully(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code >= 400) {
                throw new IOException("Ollama returned " + code + (body != null && !body.isEmpty() ? ": " + body : ""));
            }
            return extractMessageContent(body != null ? body : "");
        } finally {
            if (connectionHolder != null) {
                connectionHolder.set(null);
            }
            conn.disconnect();
        }
    }

    private String buildChatRequestJson(String systemPrompt, String userPrompt) {
        return buildChatRequestJson(systemPrompt, userPrompt, 0);
    }

    private String buildChatRequestJson(String systemPrompt, String userPrompt, int numCtx) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(escapeJson(model)).append("\",\"stream\":false");
        if (numCtx > 0) {
            sb.append(",\"options\":{\"num_ctx\":").append(numCtx).append("}");
        }
        sb.append(",\"messages\":[");
        sb.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(systemPrompt)).append("\"},");
        sb.append("{\"role\":\"user\",\"content\":\"").append(escapeJson(userPrompt)).append("\"}");
        sb.append("]}");
        return sb.toString();
    }

    /** Extract "message.content" from Ollama chat API response JSON. */
    private static String extractMessageContent(String json) {
        int msgStart = json.indexOf("\"message\"");
        if (msgStart == -1) return json;
        int contentKey = json.indexOf("\"content\":\"", msgStart);
        if (contentKey == -1) return json;
        contentKey += "\"content\":\"".length();
        StringBuilder sb = new StringBuilder();
        for (int i = contentKey; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == '"' || next == '\\' || next == 'n' || next == 'r' || next == 't') {
                    i++;
                    if (next == 'n') sb.append('\n');
                    else if (next == 'r') sb.append('\r');
                    else if (next == 't') sb.append('\t');
                    else sb.append(next);
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    /**
     * Send the prompt to Ollama and return the generated response text (no system prompt).
     *
     * @param prompt the user prompt
     * @return the model's response, or an error message if the request failed
     */
    public String generate(String prompt) throws IOException {
        String requestBody = buildRequestJson(prompt);
        URL url = new URL(baseUrl + "/api/generate");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(LlmContextConfig.resolveOllamaReadTimeoutMs());

            try (OutputStream os = conn.getOutputStream()) {
                byte[] bytes = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(bytes);
            }

            int code = conn.getResponseCode();
            String body = readFully(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code >= 400) {
                throw new IOException("Ollama returned " + code + (body != null && !body.isEmpty() ? ": " + body : ""));
            }
            return extractResponse(body != null ? body : "");
        } finally {
            conn.disconnect();
        }
    }

    private String buildRequestJson(String prompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(escapeJson(model)).append("\",\"prompt\":\"");
        sb.append(escapeJson(prompt));
        sb.append("\",\"stream\":false}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:   sb.append(c); break;
            }
        }
        return sb.toString();
    }

    private static String readFully(InputStream in) throws IOException {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return r.lines().collect(Collectors.joining("\n"));
        }
    }

    /** Extract the "response" field value from Ollama JSON (handles escaped quotes). */
    private static String extractResponse(String json) {
        String key = "\"response\":\"";
        int start = json.indexOf(key);
        if (start == -1) return json;
        start += key.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == '"' || next == '\\' || next == 'n' || next == 'r' || next == 't') {
                    i++;
                    if (next == 'n') sb.append('\n');
                    else if (next == 'r') sb.append('\r');
                    else if (next == 't') sb.append('\t');
                    else sb.append(next);
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
