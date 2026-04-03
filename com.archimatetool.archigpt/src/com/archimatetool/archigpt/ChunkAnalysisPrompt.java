/**
 * Short system prompt and user-message shape for multi-pass analysis of large models.
 */
package com.archimatetool.archigpt;

@SuppressWarnings("nls")
public final class ChunkAnalysisPrompt {

    /** Kept short to maximize room for XML excerpts and the assistant reply. */
    public static final String SYSTEM_PROMPT = "You receive one excerpt of an ArchiMate Open Exchange XML model. "
            + "It is excerpt k of n of the SAME model. Answer the user's request using ONLY this excerpt. "
            + "If the excerpt lacks information needed for the question, say what is missing. "
            + "Reply in plain text only (not JSON). Be concise.";

    private ChunkAnalysisPrompt() {}

    public static String buildChunkUserMessage(String xmlExcerpt, int excerptIndex1, int totalExcerpts,
            String selectionContext, String prompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("Excerpt ").append(excerptIndex1).append(" of ").append(totalExcerpts)
                .append(" of the same ArchiMate model (Open Exchange XML).\n\n");
        sb.append("ArchiMate model (Open Exchange XML):\n\n").append(xmlExcerpt).append("\n\n");
        sb.append("--- END OF MODEL EXCERPT ---\n\n");
        sb.append("User request: ").append(prompt != null ? prompt : "").append("\n\n");
        if (selectionContext != null && !selectionContext.isEmpty()) {
            sb.append(selectionContext);
        }
        return sb.toString();
    }
}
