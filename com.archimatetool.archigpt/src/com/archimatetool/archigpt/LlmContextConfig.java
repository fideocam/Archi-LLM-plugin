/**
 * Tunable limits for how much model XML is sent to the LLM and how large an Ollama context is requested.
 * Large Archi models often exceed the default XML cap; behaviour then matches "small models work, large ones don't".
 */
package com.archimatetool.archigpt;

/**
 * JVM system properties (e.g. in Archi.ini <code>-vmargs</code>):
 * <ul>
 *   <li>{@value #PROP_MAX_XML_CHARS} — max characters of Open Exchange XML (optional). If unset, a budget is computed from Ollama {@code num_ctx} minus system prompt, wrappers, and a reply reserve.</li>
 *   <li>{@value #PROP_OLLAMA_NUM_CTX} — Ollama {@code num_ctx} (optional). If unset, ArchiGPT uses {@code POST /api/show} but clamps the reported value to {@value #DEFAULT_OLLAMA_REPORTED_CTX_CAP} (see {@value #PROP_OLLAMA_REPORTED_CTX_CAP}): the model's <em>maximum</em> context is often 128k+ and requesting that for every call can make Ollama appear hung (slow allocation / CPU) even for small prompts.</li>
 *   <li>{@value #PROP_CHUNKED_ANALYSIS} — set {@code false} to disable multi-request plain-text analysis for huge models.</li>
 *   <li>{@value #PROP_SEMANTIC_CHUNKED_ANALYSIS} — set {@code false} to use plain string-split chunks instead of folder/view-based chunks.</li>
 *   <li>{@value #PROP_OLLAMA_READ_TIMEOUT_MS} — max milliseconds to wait for Ollama to finish one chat/generate response (default {@value #DEFAULT_OLLAMA_READ_TIMEOUT_MS}). Use {@code 0} for no read timeout. If you see {@code Read timed out}, try lowering {@value #PROP_OLLAMA_REPORTED_CTX_CAP} or setting an explicit {@value #PROP_OLLAMA_NUM_CTX} before raising the timeout.</li>
 * </ul>
 */
@SuppressWarnings("nls")
public final class LlmContextConfig {

    /** Property: maximum model XML characters sent to the LLM (positive integer). */
    public static final String PROP_MAX_XML_CHARS = "archigpt.maxXmlChars";

    /** Property: Ollama options.num_ctx (positive integer). */
    public static final String PROP_OLLAMA_NUM_CTX = "archigpt.ollamaNumCtx";

    /**
     * Property: upper bound on {@code num_ctx} when it is inferred from {@code /api/show} (not when {@link #PROP_OLLAMA_NUM_CTX} is set).
     * Models often report 128k+; passing that to every request can stall Ollama for a long time on CPU or limited GPU memory.
     */
    public static final String PROP_OLLAMA_REPORTED_CTX_CAP = "archigpt.ollamaReportedCtxCap";

    /** Property: set to {@code false} to disable multi-pass analysis for huge models (default: enabled when prompt looks like analysis). */
    public static final String PROP_CHUNKED_ANALYSIS = "archigpt.chunkedAnalysis";

    /** Property: set to {@code false} to fall back to splitting the full XML string instead of folder/view-based chunks. */
    public static final String PROP_SEMANTIC_CHUNKED_ANALYSIS = "archigpt.semanticChunkedAnalysis";

    /**
     * Property: socket read timeout in milliseconds for Ollama {@code /api/chat} and {@code /api/generate}.
     * {@code 0} means unlimited (Java {@link java.net.HttpURLConnection} behaviour).
     */
    public static final String PROP_OLLAMA_READ_TIMEOUT_MS = "archigpt.ollamaReadTimeoutMs";

    /** Default read timeout for one completion (2 minutes). Prefer fixing oversized {@code num_ctx} before raising this. */
    public static final int DEFAULT_OLLAMA_READ_TIMEOUT_MS = 120_000;

    /** Upper bound for read timeout when set via property (2 hours). */
    private static final int OLLAMA_READ_TIMEOUT_MS_CEILING = 7_200_000;

    /** Default XML cap: enough for several diagrams + folders on typical models; was 12_000 and was tight for large models. */
    public static final int DEFAULT_MAX_XML_CHARS = 36_000;

    /** Upper bound so a mistaken property cannot allocate huge strings (aligned with ModelContextToXml internal limit). */
    private static final int MAX_XML_CHARS_CEILING = 500_000;

    /** Default Ollama context when {@code /api/show} is unavailable (also clamped by {@link #ollamaReportedCtxCap()} when not using explicit {@link #PROP_OLLAMA_NUM_CTX}). */
    public static final int DEFAULT_NUM_CTX = 65_536;

    /**
     * Default cap when {@code num_ctx} is taken from the model's reported maximum. That maximum is not a recommended request size;
     * values like 131072 make many setups spend minutes allocating or swapping per request.
     */
    public static final int DEFAULT_OLLAMA_REPORTED_CTX_CAP = 32_768;

    /**
     * Practical upper bound for Ollama {@code num_ctx} (256k tokens). Values above this are clamped.
     */
    public static final int OLLAMA_NUM_CTX_MAX = 256 * 1024;

    private static final int MAX_NUM_CTX = OLLAMA_NUM_CTX_MAX;

    /** Full serialized XML above this size is treated as a "large" model for user warnings. */
    private static final int LARGE_FULL_MODEL_XML_CHARS = 48_000;

    private LlmContextConfig() {}

    public static int getMaxXmlCharsCeiling() {
        return MAX_XML_CHARS_CEILING;
    }

    public static boolean chunkedAnalysisEnabled() {
        String p = System.getProperty(PROP_CHUNKED_ANALYSIS);
        if (p == null || p.trim().isEmpty()) {
            return true;
        }
        return !"false".equalsIgnoreCase(p.trim());
    }

    public static boolean semanticChunkedAnalysisEnabled() {
        String p = System.getProperty(PROP_SEMANTIC_CHUNKED_ANALYSIS);
        if (p == null || p.trim().isEmpty()) {
            return true;
        }
        return !"false".equalsIgnoreCase(p.trim());
    }

    public static boolean hasExplicitOllamaNumCtx() {
        String raw = System.getProperty(PROP_OLLAMA_NUM_CTX);
        return raw != null && !raw.trim().isEmpty();
    }

    public static boolean hasExplicitOllamaReportedCtxCap() {
        String raw = System.getProperty(PROP_OLLAMA_REPORTED_CTX_CAP);
        return raw != null && !raw.trim().isEmpty();
    }

    /**
     * Max {@code num_ctx} when using {@code /api/show} (or fallback default) without {@link #PROP_OLLAMA_NUM_CTX}.
     */
    public static int ollamaReportedCtxCap() {
        return parsePositiveInt(System.getProperty(PROP_OLLAMA_REPORTED_CTX_CAP), DEFAULT_OLLAMA_REPORTED_CTX_CAP, MAX_NUM_CTX);
    }

    public static boolean hasExplicitMaxXmlChars() {
        String raw = System.getProperty(PROP_MAX_XML_CHARS);
        return raw != null && !raw.trim().isEmpty();
    }

    public static boolean hasExplicitOllamaReadTimeout() {
        String raw = System.getProperty(PROP_OLLAMA_READ_TIMEOUT_MS);
        return raw != null && !raw.trim().isEmpty();
    }

    /**
     * Milliseconds for {@link java.net.HttpURLConnection#setReadTimeout(int)} on Ollama completion requests.
     * {@code 0} = no read timeout. Invalid or negative values fall back to {@link #DEFAULT_OLLAMA_READ_TIMEOUT_MS}.
     */
    public static int resolveOllamaReadTimeoutMs() {
        String raw = System.getProperty(PROP_OLLAMA_READ_TIMEOUT_MS);
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_OLLAMA_READ_TIMEOUT_MS;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            if (v < 0) {
                return DEFAULT_OLLAMA_READ_TIMEOUT_MS;
            }
            if (v == 0) {
                return 0;
            }
            return Math.min(v, OLLAMA_READ_TIMEOUT_MS_CEILING);
        } catch (NumberFormatException e) {
            return DEFAULT_OLLAMA_READ_TIMEOUT_MS;
        }
    }

    /**
     * {@code num_ctx} for the next request: explicit {@link #PROP_OLLAMA_NUM_CTX} wins. Otherwise, when {@code /api/show}
     * returns a context length, use {@code min(reported, ollamaReportedCtxCap())} — not the raw model maximum, which is often
     * far larger than needed and can stall Ollama. If show fails, use {@code min}({@link #DEFAULT_NUM_CTX}, cap).
     */
    public static int resolveOllamaNumCtx(int reportedByOllama) {
        if (hasExplicitOllamaNumCtx()) {
            return ollamaNumCtx();
        }
        int cap = ollamaReportedCtxCap();
        if (reportedByOllama >= 2048) {
            return Math.min(Math.min(reportedByOllama, MAX_NUM_CTX), cap);
        }
        return Math.min(DEFAULT_NUM_CTX, cap);
    }

    /**
     * Max XML characters: JVM property wins; otherwise derive from {@code num_ctx} and reserved reply space.
     */
    public static int resolveMaxXmlChars(int numCtx, int systemPromptChars, int userOverheadChars) {
        if (hasExplicitMaxXmlChars()) {
            return maxXmlChars();
        }
        return LlmContextBudget.recommendedMaxXmlChars(numCtx, systemPromptChars, userOverheadChars,
                LlmContextBudget.DEFAULT_REPLY_RESERVE_TOKENS);
    }

    /**
     * Max characters of serialized model XML to include in the user message.
     */
    public static int maxXmlChars() {
        return parsePositiveInt(System.getProperty(PROP_MAX_XML_CHARS), DEFAULT_MAX_XML_CHARS, MAX_XML_CHARS_CEILING);
    }

    /**
     * Value passed to Ollama as {@code options.num_ctx} for chat completions.
     */
    public static int ollamaNumCtx() {
        return parsePositiveInt(System.getProperty(PROP_OLLAMA_NUM_CTX), DEFAULT_NUM_CTX, MAX_NUM_CTX);
    }

    /**
     * Warning text for the ArchiGPT Debug tab when the model or request is large enough to affect
     * quality, truncation, or Ollama performance. Empty if no warning applies.
     *
     * @param fullModelXmlChars  length of full model XML (uncapped)
     * @param sentModelXmlChars  length of model XML actually embedded in the user message
     * @param userMessageChars   full user message length (model + selection + prompt)
     * @param systemPromptChars  system prompt length
     * @param numCtxRequested    Ollama {@code num_ctx} for this request
     */
    public static String contextPerformanceWarning(int fullModelXmlChars, int sentModelXmlChars,
            int userMessageChars, int systemPromptChars, int numCtxRequested) {
        if (systemPromptChars < 0) {
            systemPromptChars = 0;
        }
        boolean truncated = fullModelXmlChars > sentModelXmlChars;
        boolean largeModel = fullModelXmlChars >= LARGE_FULL_MODEL_XML_CHARS;
        // Rough lower bound on input tokens (English/XML mix; conservative for “are we near the limit?”).
        int approxInputTokens = (systemPromptChars + userMessageChars + 3) / 4;
        boolean contextTight = numCtxRequested > 0 && approxInputTokens > (long) numCtxRequested * 70 / 100;

        if (!truncated && !largeModel && !contextTight) {
            return "";
        }

        StringBuilder w = new StringBuilder();
        w.append("\n\nWARNING — Context window / performance:\n");
        if (truncated) {
            w.append("Only ").append(sentModelXmlChars).append(" of ").append(fullModelXmlChars)
                    .append(" characters of model XML are sent; raise -D").append(PROP_MAX_XML_CHARS)
                    .append(" to include more. The LLM cannot see omitted content.\n");
        } else if (largeModel) {
            w.append("Serialized model XML is about ").append(fullModelXmlChars)
                    .append(" characters (large); quality may depend on your num_ctx and model.\n");
        }
        w.append("Long system + user text competes for Ollama's context (typically up to about ")
                .append(OLLAMA_NUM_CTX_MAX / 1024).append("k tokens). ")
                .append("Very large inputs can slow responses and leave little room for the reply.\n");
        w.append("This request uses num_ctx=").append(numCtxRequested)
                .append(" (~").append(approxInputTokens).append(" tokens estimated for system + user message).\n");
        w.append("Tune -D").append(PROP_MAX_XML_CHARS).append(", -D").append(PROP_OLLAMA_NUM_CTX).append(", and -D")
                .append(PROP_OLLAMA_REPORTED_CTX_CAP)
                .append(" in Archi.ini (vmargs) if needed; num_ctx above ").append(OLLAMA_NUM_CTX_MAX / 1024)
                .append("k is not supported by Ollama.");
        return w.toString();
    }

    private static int parsePositiveInt(String raw, int defaultValue, int ceiling) {
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            if (v <= 0) {
                return defaultValue;
            }
            return Math.min(v, ceiling);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
