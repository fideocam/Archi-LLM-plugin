package com.archimatetool.archigpt;

/**
 * Builds the configured {@link LLMClient} from {@link ArchiGPTPreferences}.
 */
@SuppressWarnings("nls")
public final class LlmClientFactory {

    private LlmClientFactory() {}

    public static LLMClient createClient() {
        String p = ArchiGPTPreferences.getProvider();
        String base = ArchiGPTPreferences.getBaseUrl();
        String model = ArchiGPTPreferences.getModel();
        String key = ArchiGPTPreferences.getApiKey();
        switch (p) {
            case ArchiGPTPreferences.PROVIDER_OPENAI:
            case ArchiGPTPreferences.PROVIDER_CUSTOM:
                return new OpenAICompatibleClient(base, model, key, false);
            case ArchiGPTPreferences.PROVIDER_AZURE_OPENAI:
                return new OpenAICompatibleClient(base, model, key, true);
            case ArchiGPTPreferences.PROVIDER_ANTHROPIC:
                return new AnthropicMessagesClient(base, model, key);
            case ArchiGPTPreferences.PROVIDER_GOOGLE:
                return new GeminiClient(base, model, key);
            case ArchiGPTPreferences.PROVIDER_OLLAMA:
            default:
                return new OllamaClient(base, model);
        }
    }

    /**
     * @throws IllegalArgumentException if required fields are missing for the selected provider
     */
    public static void validateConfiguration() {
        String p = ArchiGPTPreferences.getProvider();
        String base = ArchiGPTPreferences.getBaseUrl();
        String model = ArchiGPTPreferences.getModel();
        if (!ArchiGPTPreferences.PROVIDER_OLLAMA.equals(p)) {
            if (base == null || base.trim().isEmpty()) {
                throw new IllegalArgumentException("Base URL is required for " + ArchiGPTPreferences.providerDisplayName() + ".");
            }
            String bt = base.trim().toLowerCase();
            if (!bt.startsWith("https://") && !isLocalhostHttp(bt)) {
                throw new IllegalArgumentException("External LLM base URL must use https:// (or http://localhost for development).");
            }
            if (ArchiGPTPreferences.requiresApiKey() && (ArchiGPTPreferences.getApiKey() == null || ArchiGPTPreferences.getApiKey().trim().isEmpty())) {
                throw new IllegalArgumentException("API key is required for " + ArchiGPTPreferences.providerDisplayName()
                        + ". Set it in Window → Preferences → ArchiGPT.");
            }
        }
        if (model == null || model.trim().isEmpty()) {
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
