package com.archimatetool.archigpt;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Anthropic Messages API (<a href="https://docs.anthropic.com/en/api/messages">messages</a>).
 */
@SuppressWarnings("nls")
public class AnthropicMessagesClient implements LLMClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final String messagesUrl;
    private final String model;
    private final String apiKey;

    public AnthropicMessagesClient(String baseUrl, String model, String apiKey) {
        String b = baseUrl != null ? baseUrl.replaceAll("/$", "") : "https://api.anthropic.com/v1";
        this.messagesUrl = b + "/messages";
        this.model = model != null ? model : "";
        this.apiKey = apiKey != null ? apiKey : "";
    }

    @Override
    public boolean checkConnection() {
        return !apiKey.isEmpty() && messagesUrl.startsWith("https://");
    }

    @Override
    public int fetchReportedContextTokens() {
        return 0;
    }

    @Override
    public String generateWithSystemPrompt(String systemPrompt, String userPrompt, AtomicReference<HttpURLConnection> connectionHolder,
            int maxOutputTokens) throws IOException {
        int mt = maxOutputTokens > 0 ? maxOutputTokens : 8192;
        String body = "{\"model\":\"" + JsonEscape.escapeString(model) + "\",\"max_tokens\":" + mt
                + ",\"system\":\"" + JsonEscape.escapeString(systemPrompt) + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + JsonEscape.escapeString(userPrompt) + "\"}]}";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        String raw = ExternalLlmHttp.postJson(messagesUrl, headers, body, connectionHolder);
        String text = JsonResponseExtractors.anthropicFirstText(raw);
        if (text == null || text.isEmpty()) {
            return raw;
        }
        return text;
    }

    @Override
    public String endpointSummary() {
        try {
            return new java.net.URL(messagesUrl).getHost();
        } catch (Exception e) {
            return messagesUrl;
        }
    }
}
