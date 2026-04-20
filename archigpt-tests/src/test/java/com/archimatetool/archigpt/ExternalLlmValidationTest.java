package com.archimatetool.archigpt;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link LlmClientFactory#validateInputs} and {@link LlmClientFactory#createClientForTesting} (package-visible for tests).
 */
public class ExternalLlmValidationTest {

    @Test
    public void validate_ollama_noApiKey_ok() {
        LlmClientFactory.validateInputs(ArchiGPTPreferences.PROVIDER_OLLAMA, "http://localhost:11434", "llama3.2", "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validate_openai_httpRejected() {
        LlmClientFactory.validateInputs(ArchiGPTPreferences.PROVIDER_OPENAI, "http://api.example.com/v1", "gpt-4o", "k");
    }

    @Test
    public void validate_openai_localhostHttp_ok() {
        LlmClientFactory.validateInputs(ArchiGPTPreferences.PROVIDER_OPENAI, "http://localhost:9999/v1", "m", "key");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validate_openai_missingKey() {
        LlmClientFactory.validateInputs(ArchiGPTPreferences.PROVIDER_OPENAI, "https://api.openai.com/v1", "gpt-4o", "  ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validate_openai_emptyBase() {
        LlmClientFactory.validateInputs(ArchiGPTPreferences.PROVIDER_OPENAI, "   ", "gpt-4o", "sk-x");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validate_azure_missingChatCompletionsPath() {
        LlmClientFactory.validateInputs(ArchiGPTPreferences.PROVIDER_AZURE_OPENAI, "https://x.openai.azure.com/openai/deployments/d", "d",
                "k");
    }

    @Test
    public void validate_azure_fullUrl_ok() {
        LlmClientFactory.validateInputs(ArchiGPTPreferences.PROVIDER_AZURE_OPENAI,
                "https://res.openai.azure.com/openai/deployments/mydep/chat/completions?api-version=2024-02-15-preview", "mydep", "k");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validate_emptyModel() {
        LlmClientFactory.validateInputs(ArchiGPTPreferences.PROVIDER_OPENAI, "https://api.openai.com/v1", "  ", "k");
    }

    @Test
    public void createClient_types() {
        assertTrue(LlmClientFactory.createClientForTesting(ArchiGPTPreferences.PROVIDER_OLLAMA, "http://localhost:11434", "m", "")
                instanceof OllamaClient);
        assertTrue(LlmClientFactory.createClientForTesting(ArchiGPTPreferences.PROVIDER_OPENAI, "https://api.openai.com/v1", "gpt-4o", "k")
                instanceof OpenAICompatibleClient);
        assertTrue(LlmClientFactory.createClientForTesting(ArchiGPTPreferences.PROVIDER_ANTHROPIC, "https://api.anthropic.com/v1", "c", "k")
                instanceof AnthropicMessagesClient);
        assertTrue(LlmClientFactory.createClientForTesting(ArchiGPTPreferences.PROVIDER_GOOGLE,
                "https://generativelanguage.googleapis.com/v1beta", "gemini-pro", "k") instanceof GeminiClient);
    }
}
