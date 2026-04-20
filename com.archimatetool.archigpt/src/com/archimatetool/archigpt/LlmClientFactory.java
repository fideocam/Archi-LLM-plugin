package com.archimatetool.archigpt;

/**
 * Builds the configured {@link LLMClient} from {@link ArchiGPTPreferences}.
 */
@SuppressWarnings("nls")
public final class LlmClientFactory {

    private LlmClientFactory() {}

    public static LLMClient createClient() {
        return createClientForTesting(
                ArchiGPTPreferences.getProvider(),
                ArchiGPTPreferences.getBaseUrl(),
                ArchiGPTPreferences.getModel(),
                ArchiGPTPreferences.getApiKey());
    }

    /**
     * Same client wiring as {@link #createClient()} but with explicit parameters (for unit tests; same package).
     */
    static LLMClient createClientForTesting(String provider, String baseUrl, String model, String apiKey) {
        String p = provider != null ? provider : ArchiGPTPreferences.PROVIDER_OLLAMA;
        String base = baseUrl != null ? baseUrl : "";
        String m = model != null ? model : "";
        String key = apiKey != null ? apiKey : "";
        switch (p) {
            case ArchiGPTPreferences.PROVIDER_OPENAI:
            case ArchiGPTPreferences.PROVIDER_CUSTOM:
                return new OpenAICompatibleClient(base, m, key, false);
            case ArchiGPTPreferences.PROVIDER_AZURE_OPENAI:
                return new OpenAICompatibleClient(base, m, key, true);
            case ArchiGPTPreferences.PROVIDER_ANTHROPIC:
                return new AnthropicMessagesClient(base, m, key);
            case ArchiGPTPreferences.PROVIDER_GOOGLE:
                return new GeminiClient(base, m, key);
            case ArchiGPTPreferences.PROVIDER_OLLAMA:
            default:
                return new OllamaClient(base, m);
        }
    }

    /**
     * @throws IllegalArgumentException if required fields are missing for the selected provider
     */
    public static void validateConfiguration() {
        validateInputs(
                ArchiGPTPreferences.getProvider(),
                ArchiGPTPreferences.getBaseUrl(),
                ArchiGPTPreferences.getModel(),
                ArchiGPTPreferences.getApiKey());
    }

    /**
     * Same rules as {@link #validateConfiguration()} using explicit values (for unit tests).
     */
    static void validateInputs(String provider, String baseUrl, String model, String apiKey) {
        String p = provider != null ? provider : ArchiGPTPreferences.PROVIDER_OLLAMA;
        String base = baseUrl != null ? baseUrl : "";
        String m = model != null ? model : "";
        String key = apiKey != null ? apiKey : "";
        if (!ArchiGPTPreferences.PROVIDER_OLLAMA.equals(p)) {
            if (base.trim().isEmpty()) {
                throw new IllegalArgumentException("Base URL is required for " + ArchiGPTPreferences.providerDisplayName(p) + ".");
            }
            String bt = base.trim().toLowerCase();
            if (!bt.startsWith("https://") && !isLocalhostHttp(bt)) {
                throw new IllegalArgumentException("External LLM base URL must use https:// (or http://localhost for development).");
            }
            if (ArchiGPTPreferences.requiresApiKey(p) && key.trim().isEmpty()) {
                throw new IllegalArgumentException("API key is required for " + ArchiGPTPreferences.providerDisplayName(p)
                        + ". Set it in Window → Preferences → ArchiGPT.");
            }
        }
        if (m.trim().isEmpty()) {
            throw new IllegalArgumentException("Model name is required. Set it in Window → Preferences → ArchiGPT.");
        }
        if (ArchiGPTPreferences.PROVIDER_AZURE_OPENAI.equals(p)) {
            if (!base.contains("chat/completions")) {
                throw new IllegalArgumentException("Azure OpenAI: set Base URL to the full chat completions URL "
                        + "(…/deployments/DEPLOYMENT_NAME/chat/completions?api-version=…).");
            }
        }
    }

    private static boolean isLocalhostHttp(String baseTrimmedLower) {
        return baseTrimmedLower.startsWith("http://localhost") || baseTrimmedLower.startsWith("http://127.0.0.1");
    }
}
