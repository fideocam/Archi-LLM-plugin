package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Non-network smoke tests for external {@link LLMClient} implementations.
 */
public class ExternalLlmClientsSmokeTest {

    @Test
    public void openAi_endpointSummary() {
        OpenAICompatibleClient c = new OpenAICompatibleClient("https://api.openai.com/v1", "gpt-4o", "secret", false);
        assertEquals("api.openai.com", c.endpointSummary());
    }

    @Test
    public void openAi_checkConnection_falseWithoutKey() {
        OpenAICompatibleClient c = new OpenAICompatibleClient("https://api.openai.com/v1", "gpt-4o", "", false);
        assertFalse(c.checkConnection());
    }

    @Test
    public void azure_checkConnection_httpsFullUrl_skipsNetwork() {
        String url = "https://example.openai.azure.com/openai/deployments/d/chat/completions?api-version=2024-02-15-preview";
        OpenAICompatibleClient c = new OpenAICompatibleClient(url, "d", "any-key", true);
        assertTrue(c.checkConnection());
        assertEquals("example.openai.azure.com", c.endpointSummary());
    }

    @Test
    public void anthropic_endpointSummary() {
        AnthropicMessagesClient c = new AnthropicMessagesClient("https://api.anthropic.com/v1", "claude-3-5-sonnet-20241022", "k");
        assertEquals("api.anthropic.com", c.endpointSummary());
    }

    @Test
    public void gemini_endpointSummary_constantHost() {
        GeminiClient c = new GeminiClient("https://generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash", "k");
        assertEquals("generativelanguage.googleapis.com", c.endpointSummary());
    }

    @Test
    public void gemini_checkConnection_falseWithoutKey() {
        GeminiClient c = new GeminiClient("https://generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash", "");
        assertFalse(c.checkConnection());
    }

    @Test
    public void archiGptPreferences_requiresApiKeyByProvider() {
        assertFalse(ArchiGPTPreferences.requiresApiKey(ArchiGPTPreferences.PROVIDER_OLLAMA));
        assertTrue(ArchiGPTPreferences.requiresApiKey(ArchiGPTPreferences.PROVIDER_OPENAI));
        assertTrue(ArchiGPTPreferences.requiresApiKey(ArchiGPTPreferences.PROVIDER_GOOGLE));
    }

    @Test
    public void archiGptPreferences_providerDisplayNameByProvider() {
        assertEquals("OpenAI", ArchiGPTPreferences.providerDisplayName(ArchiGPTPreferences.PROVIDER_OPENAI));
        assertEquals("Ollama (local)", ArchiGPTPreferences.providerDisplayName(ArchiGPTPreferences.PROVIDER_OLLAMA));
    }
}
