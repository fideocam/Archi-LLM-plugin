package com.archimatetool.archigpt;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Google AI Gemini {@code generateContent} (v1beta).
 */
@SuppressWarnings("nls")
public class GeminiClient implements LLMClient {

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final String generateContentUrl;

    public GeminiClient(String baseUrl, String model, String apiKey) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/$", "") : "https://generativelanguage.googleapis.com/v1beta";
        this.model = model != null && !model.trim().isEmpty() ? model.trim() : "gemini-2.0-flash";
        this.apiKey = apiKey != null ? apiKey : "";
        String encKey;
        try {
            encKey = URLEncoder.encode(this.apiKey, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            encKey = "";
        }
        this.generateContentUrl = this.baseUrl + "/models/" + this.model + ":generateContent?key=" + encKey;
    }

    @Override
    public boolean checkConnection() {
        if (apiKey.isEmpty()) {
            return false;
        }
        try {
            String encKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name());
            String listUrl = baseUrl + "/models?pageSize=1&key=" + encKey;
            ExternalLlmHttp.get(listUrl, Collections.emptyMap());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public int fetchReportedContextTokens() {
        return 0;
    }

    @Override
    public String generateWithSystemPrompt(String systemPrompt, String userPrompt, AtomicReference<HttpURLConnection> connectionHolder,
            int maxOutputTokens) throws IOException {
        int mt = maxOutputTokens > 0 ? maxOutputTokens : 8192;
        String body = "{"
                + "\"systemInstruction\":{\"parts\":[{\"text\":\"" + JsonEscape.escapeString(systemPrompt) + "\"}]},"
                + "\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"" + JsonEscape.escapeString(userPrompt) + "\"}]}],"
                + "\"generationConfig\":{\"maxOutputTokens\":" + mt + "}"
                + "}";
        String raw = ExternalLlmHttp.postJson(generateContentUrl, Collections.emptyMap(), body, connectionHolder);
        String text = JsonResponseExtractors.geminiCandidatesText(raw);
        if (text == null || text.isEmpty()) {
            return raw;
        }
        return text;
    }

    @Override
    public String endpointSummary() {
        return "generativelanguage.googleapis.com";
    }
}
