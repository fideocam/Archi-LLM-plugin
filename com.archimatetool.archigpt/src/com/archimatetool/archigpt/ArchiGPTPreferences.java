/**
 * Eclipse preference keys for ArchiGPT (Ollama server URL).
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
    public static final String P_KNOWLEDGE_FOLDER = "knowledge.folder";
    public static final String P_KNOWLEDGE_MAX_CHARS = "knowledge.maxChars";

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

    public static String getStoredKnowledgeFolder() {
        return node().get(P_KNOWLEDGE_FOLDER, "");
    }

    public static String getKnowledgeFolder() {
        return LlmContextConfig.resolveKnowledgeFolder(getStoredKnowledgeFolder());
    }

    public static void setKnowledgeFolder(String folder) throws BackingStoreException {
        node().put(P_KNOWLEDGE_FOLDER, folder != null ? folder.trim() : "");
        flush();
    }

    public static int getKnowledgeMaxChars() {
        int stored = node().getInt(P_KNOWLEDGE_MAX_CHARS, KnowledgeRetriever.DEFAULT_MAX_CHARS);
        return LlmContextConfig.resolveKnowledgeMaxChars(stored);
    }

    public static void setKnowledgeMaxChars(int maxChars) throws BackingStoreException {
        node().putInt(P_KNOWLEDGE_MAX_CHARS, LlmContextConfig.resolveKnowledgeMaxChars(maxChars));
        flush();
    }
}
