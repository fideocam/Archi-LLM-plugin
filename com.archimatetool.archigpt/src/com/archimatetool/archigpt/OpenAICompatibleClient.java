package com.archimatetool.archigpt;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenAI Chat Completions, Azure OpenAI (same JSON with {@code api-key} header), or custom OpenAI-compatible HTTPS endpoints.
 */
@SuppressWarnings("nls")
public class OpenAICompatibleClient implements LLMClient {

    private final String chatCompletionsUrl;
    private final String model;
    private final String apiKey;
    private final boolean azureApiKeyHeader;

    /**
     * @param baseUrl            for OpenAI/custom: {@code https://api.openai.com/v1} (no trailing path). For Azure: full
     *                           {@code …/deployments/DEP/chat/completions?api-version=…} URL.
     * @param model              model or deployment name for JSON body
     * @param apiKey             API key (Bearer for OpenAI/custom; Azure uses {@code api-key} header)
     * @param azureApiKeyHeader  if true, send {@code api-key} instead of {@code Authorization: Bearer}
     */
    public OpenAICompatibleClient(String baseUrl, String model, String apiKey, boolean azureApiKeyHeader) {
        this.model = model != null ? model : "";
        this.apiKey = apiKey != null ? apiKey : "";
        this.azureApiKeyHeader = azureApiKeyHeader;
        String b = baseUrl != null ? baseUrl.replaceAll("/$", "") : "";
        if (azureApiKeyHeader && (b.contains("/chat/completions"))) {
            this.chatCompletionsUrl = b;
        } else {
            this.chatCompletionsUrl = b + "/chat/completions";
        }
    }

    @Override
    public boolean checkConnection() {
        if (apiKey.isEmpty()) {
            return false;
        }
        try {
            if (azureApiKeyHeader) {
                return chatCompletionsUrl.startsWith("https://");
            }
            String modelsUrl = chatCompletionsUrl.substring(0, chatCompletionsUrl.lastIndexOf("/chat/completions")) + "/models";
            Map<String, String> h = authHeaders();
            ExternalLlmHttp.get(modelsUrl, h);
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
        int mt = maxOutputTokens > 0 ? maxOutputTokens : 4096;
        String body = "{\"model\":\"" + JsonEscape.escapeString(model) + "\",\"stream\":false,\"max_tokens\":" + mt
                + ",\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + JsonEscape.escapeString(systemPrompt) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + JsonEscape.escapeString(userPrompt) + "\"}"
                + "]}";
        String raw = ExternalLlmHttp.postJson(chatCompletionsUrl, authHeaders(), body, connectionHolder);
        String content = JsonResponseExtractors.openAiAssistantContent(raw);
        if (content == null || content.isEmpty()) {
            return raw;
        }
        return content;
    }

    private Map<String, String> authHeaders() {
        Map<String, String> m = new LinkedHashMap<>();
        if (azureApiKeyHeader) {
            m.put("api-key", apiKey);
        } else {
            m.put("Authorization", "Bearer " + apiKey);
        }
        return m;
    }

    @Override
    public String endpointSummary() {
        try {
            return new java.net.URL(chatCompletionsUrl).getHost();
        } catch (Exception e) {
            return chatCompletionsUrl;
        }
    }
}
