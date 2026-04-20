package com.archimatetool.archigpt;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstraction over Ollama and external chat APIs (OpenAI-compatible, Anthropic, Google Gemini).
 */
public interface LLMClient {

    /**
     * Lightweight reachability check (provider-specific).
     *
     * @return true if the service appears reachable with current configuration
     */
    boolean checkConnection();

    /**
     * Ollama: calls {@code /api/show} when implemented. Other clients return 0 (caller uses preference-based budget).
     */
    int fetchReportedContextTokens() throws IOException;

    /**
     * Single-turn chat: system message plus one user message.
     *
     * @param numCtxOrMaxOutputTokens for {@link OllamaClient}, {@code num_ctx}; for cloud APIs, max output tokens for the completion
     */
    String generateWithSystemPrompt(String systemPrompt, String userPrompt, AtomicReference<HttpURLConnection> connectionHolder,
            int numCtxOrMaxOutputTokens) throws IOException;

    /** Short label for user-visible errors (e.g. host URL, never the API key). */
    String endpointSummary();
}
