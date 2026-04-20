/**
 * Eclipse preference keys for ArchiGPT LLM provider (Ollama and external APIs).
 * API keys are stored in the workspace preference node — never committed to the repo.
 */
package com.archimatetool.archigpt;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;

@SuppressWarnings("nls")
public final class ArchiGPTPreferences {

    public static final String QUALIFIER = "com.archimatetool.archigpt";

    public static final String P_PROVIDER = "llm.provider";
    public static final String P_API_KEY = "llm.apiKey";
    public static final String P_BASE_URL = "llm.baseUrl";
    public static final String P_MODEL = "llm.model";
    public static final String P_MAX_OUTPUT_TOKENS = "llm.maxOutputTokens";
    /** Used only for XML budget when not using Ollama /api/show (rough input token budget). */
    public static final String P_ESTIMATED_CONTEXT_TOKENS = "llm.estimatedContextTokens";

    public static final String PROVIDER_OLLAMA = "ollama";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_AZURE_OPENAI = "azure_openai";
    public static final String PROVIDER_ANTHROPIC = "anthropic";
    public static final String PROVIDER_GOOGLE = "google";
    public static final String PROVIDER_CUSTOM = "custom";

    private static final String DEF_OLLAMA_URL = OllamaClient.DEFAULT_BASE_URL;
    private static final String DEF_OLLAMA_MODEL = OllamaClient.DEFAULT_MODEL;
    private static final String DEF_OPENAI_URL = "https://api.openai.com/v1";
    private static final String DEF_OPENAI_MODEL = "gpt-4o";
    private static final String DEF_ANTHROPIC_URL = "https://api.anthropic.com/v1";
    private static final String DEF_ANTHROPIC_MODEL = "claude-sonnet-4-20250514";
    private static final String DEF_GOOGLE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String DEF_GOOGLE_MODEL = "gemini-2.0-flash";

    private ArchiGPTPreferences() {}

    public static IEclipsePreferences node() {
        return InstanceScope.INSTANCE.getNode(QUALIFIER);
    }

    public static void flush() throws BackingStoreException {
        node().flush();
    }

    public static String getProvider() {
        return node().get(P_PROVIDER, PROVIDER_OLLAMA);
    }

    public static String getApiKey() {
        return node().get(P_API_KEY, "");
    }

    public static String getBaseUrl() {
        String p = getProvider();
        String def;
        switch (p) {
            case PROVIDER_OPENAI:
            case PROVIDER_CUSTOM:
                def = DEF_OPENAI_URL;
                break;
            case PROVIDER_AZURE_OPENAI:
                def = "";
                break;
            case PROVIDER_ANTHROPIC:
                def = DEF_ANTHROPIC_URL;
                break;
            case PROVIDER_GOOGLE:
                def = DEF_GOOGLE_URL;
                break;
            case PROVIDER_OLLAMA:
            default:
                def = DEF_OLLAMA_URL;
                break;
        }
        return node().get(P_BASE_URL, def);
    }

    public static String getModel() {
        String p = getProvider();
        String def;
        switch (p) {
            case PROVIDER_OPENAI:
            case PROVIDER_CUSTOM:
                def = DEF_OPENAI_MODEL;
                break;
            case PROVIDER_AZURE_OPENAI:
                def = "";
                break;
            case PROVIDER_ANTHROPIC:
                def = DEF_ANTHROPIC_MODEL;
                break;
            case PROVIDER_GOOGLE:
                def = DEF_GOOGLE_MODEL;
                break;
            case PROVIDER_OLLAMA:
            default:
                def = DEF_OLLAMA_MODEL;
                break;
        }
        return node().get(P_MODEL, def);
    }

    public static int getMaxOutputTokens() {
        return parseInt(node().get(P_MAX_OUTPUT_TOKENS, "16384"), 16384, 1, 200_000);
    }

    public static int getEstimatedContextTokens() {
        return parseInt(node().get(P_ESTIMATED_CONTEXT_TOKENS, "200000"), 200_000, 4096, LlmContextConfig.OLLAMA_NUM_CTX_MAX);
    }

    private static int parseInt(String raw, int def, int min, int max) {
        try {
            int v = Integer.parseInt(raw.trim());
            if (v < min) {
                return min;
            }
            if (v > max) {
                return max;
            }
            return v;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static boolean isOllama() {
        return PROVIDER_OLLAMA.equals(getProvider());
    }

    public static boolean requiresApiKey() {
        String p = getProvider();
        return PROVIDER_OPENAI.equals(p) || PROVIDER_AZURE_OPENAI.equals(p) || PROVIDER_ANTHROPIC.equals(p)
                || PROVIDER_GOOGLE.equals(p) || PROVIDER_CUSTOM.equals(p);
    }

    public static String providerDisplayName() {
        switch (getProvider()) {
            case PROVIDER_OPENAI:
                return "OpenAI";
            case PROVIDER_AZURE_OPENAI:
                return "Azure OpenAI";
            case PROVIDER_ANTHROPIC:
                return "Anthropic";
            case PROVIDER_GOOGLE:
                return "Google Gemini";
            case PROVIDER_CUSTOM:
                return "Custom (OpenAI-compatible)";
            case PROVIDER_OLLAMA:
            default:
                return "Ollama (local)";
        }
    }
}
