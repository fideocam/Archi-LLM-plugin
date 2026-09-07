/**
 * Eclipse preference keys for ArchiGPT (Ollama server URL and context size).
 * Values are stored in the workspace preference node — never committed to the repo.
 */
package com.archimatetool.archigpt;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;

@SuppressWarnings("nls")
public final class ArchiGPTPreferences {

    public static final String QUALIFIER = "com.archimatetool.archigpt";

    public static final String P_BASE_URL = "ollama.baseUrl";

    public static final String P_USE_MODEL_MAX_CTX = "ollama.useModelMaxCtx";

    public static final String P_NUM_CTX = "ollama.numCtx";

    private ArchiGPTPreferences() {}

    public static IEclipsePreferences node() {
        return InstanceScope.INSTANCE.getNode(QUALIFIER);
    }

    public static void flush() throws BackingStoreException {
        node().flush();
    }

    /**
     * Stored base URL from the preference store (may be blank). Does not apply the JVM override.
     */
    public static String getStoredBaseUrl() {
        return node().get(P_BASE_URL, "");
    }

    /**
     * Ollama API base URL in effect: {@code -Darchigpt.ollamaBaseUrl} wins, then the preference store,
     * then {@link OllamaClient#DEFAULT_BASE_URL}. Always normalized.
     */
    public static String getBaseUrl() {
        return LlmContextConfig.resolveOllamaBaseUrl(getStoredBaseUrl());
    }

    public static void setBaseUrl(String url) throws BackingStoreException {
        node().put(P_BASE_URL, LlmContextConfig.normalizeOllamaBaseUrl(url));
        flush();
    }

    /**
     * When true, requests use the model's reported maximum context from {@code /api/show}
     * instead of the 32k default cap.
     */
    public static boolean isUseModelMaxCtx() {
        return node().getBoolean(P_USE_MODEL_MAX_CTX, false);
    }

    public static void setUseModelMaxCtx(boolean useMax) throws BackingStoreException {
        node().putBoolean(P_USE_MODEL_MAX_CTX, useMax);
        flush();
    }

    /**
     * Custom {@code num_ctx} when {@link #isUseModelMaxCtx()} is false.
     */
    public static int getNumCtx() {
        int v = node().getInt(P_NUM_CTX, LlmContextConfig.DEFAULT_OLLAMA_REPORTED_CTX_CAP);
        if (v < LlmContextConfig.OLLAMA_NUM_CTX_MIN) {
            return LlmContextConfig.DEFAULT_OLLAMA_REPORTED_CTX_CAP;
        }
        return Math.min(v, LlmContextConfig.OLLAMA_NUM_CTX_MAX);
    }

    public static void setNumCtx(int tokens) throws BackingStoreException {
        int v = Math.max(LlmContextConfig.OLLAMA_NUM_CTX_MIN, Math.min(tokens, LlmContextConfig.OLLAMA_NUM_CTX_MAX));
        node().putInt(P_NUM_CTX, v);
        flush();
    }
}
