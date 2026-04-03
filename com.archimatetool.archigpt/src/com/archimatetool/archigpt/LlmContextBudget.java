/**
 * Derives how much model XML can fit in an Ollama request while reserving space for the assistant reply.
 */
package com.archimatetool.archigpt;

/**
 * Converts an Ollama {@code num_ctx} into a safe character budget for embedded model XML.
 */
@SuppressWarnings("nls")
public final class LlmContextBudget {

    /** Tokens reserved for the model's answer (and template overhead). */
    public static final int DEFAULT_REPLY_RESERVE_TOKENS = 8_192;

    /** Do not send less than this many XML characters when auto-sizing (unless num_ctx is tiny). */
    private static final int MIN_AUTO_XML_CHARS = 6_000;

    private LlmContextBudget() {}

    /**
     * Recommended max characters of model XML to embed, given total context and fixed overhead.
     *
     * @param numCtxTokens        Ollama context size (tokens)
     * @param systemPromptChars   full system message length
     * @param userOverheadChars   user message without model XML (selection + prompt + wrappers)
     * @param replyReserveTokens  tokens to leave for assistant output
     */
    public static int recommendedMaxXmlChars(int numCtxTokens, int systemPromptChars, int userOverheadChars,
            int replyReserveTokens) {
        if (numCtxTokens <= 0) {
            numCtxTokens = LlmContextConfig.DEFAULT_NUM_CTX;
        }
        int systemTok = Math.max(1, (systemPromptChars + 3) / 4);
        int overheadTok = Math.max(1, (userOverheadChars + 3) / 4);
        int availTok = numCtxTokens - replyReserveTokens - systemTok - overheadTok;
        if (availTok < 256) {
            availTok = 256;
        }
        int chars = availTok * 3;
        return Math.min(LlmContextConfig.getMaxXmlCharsCeiling(), Math.max(MIN_AUTO_XML_CHARS, chars));
    }
}
