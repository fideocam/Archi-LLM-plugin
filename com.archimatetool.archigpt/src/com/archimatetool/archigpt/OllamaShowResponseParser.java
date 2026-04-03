/**
 * Extracts reported context size from Ollama {@code /api/show} JSON (varies by model/runtime).
 */
package com.archimatetool.archigpt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("nls")
public final class OllamaShowResponseParser {

    private static final Pattern CONTEXT_LENGTH = Pattern.compile("\"[^\"]*context_length\"\\s*:\\s*(\\d+)");
    /** e.g. "num_ctx": 4096 or "llama.num_ctx": 8192 inside model_info */
    private static final Pattern NUM_CTX_JSON = Pattern.compile("\"[^\"]*num_ctx\"\\s*:\\s*(\\d+)");
    /** Modelfile parameters block sometimes lists: num_ctx 4096 */
    private static final Pattern NUM_CTX_TEXT = Pattern.compile("(?m)^num_ctx\\s+(\\d+)\\s*$");

    private OllamaShowResponseParser() {}

    /**
     * Best-effort context length in tokens. Returns 0 if nothing recognized.
     */
    public static int parseContextTokens(String showResponseJson) {
        if (showResponseJson == null || showResponseJson.isEmpty()) {
            return 0;
        }
        int best = 0;
        Matcher m = CONTEXT_LENGTH.matcher(showResponseJson);
        while (m.find()) {
            best = Math.max(best, parseIntSafe(m.group(1)));
        }
        m = NUM_CTX_JSON.matcher(showResponseJson);
        while (m.find()) {
            best = Math.max(best, parseIntSafe(m.group(1)));
        }
        m = NUM_CTX_TEXT.matcher(showResponseJson);
        while (m.find()) {
            best = Math.max(best, parseIntSafe(m.group(1)));
        }
        return best;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
