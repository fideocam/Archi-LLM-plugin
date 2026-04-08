/**
 * Short system prompt and user-message shape for multi-pass analysis of large models.
 */
package com.archimatetool.archigpt;

@SuppressWarnings("nls")
public final class ChunkAnalysisPrompt {

    /** Kept short to maximize room for XML excerpts and the assistant reply. */
    public static final String SYSTEM_PROMPT = "You receive one excerpt of an ArchiMate Open Exchange XML model. "
            + "A MODEL DIGEST at the start of the user message summarizes the whole model (counts, folder breakdown); "
            + "the XML is only one part. It is excerpt k of n of the SAME model. "
            + "Answer using this excerpt and the digest; if the excerpt lacks detail, say what is missing. "
            + "If the user’s selection context refers to a specific diagram/view but this XML fragment has no <view> for that name, "
            + "say that this excerpt does not contain that view—do not invent diagram content. "
            + "Reply in plain text only (not JSON). Be concise.";

    private ChunkAnalysisPrompt() {}

    public static String buildChunkUserMessage(String xmlExcerpt, int excerptIndex1, int totalExcerpts,
            String selectionContext, String prompt) {
        return buildChunkUserMessage(null, null, xmlExcerpt, excerptIndex1, totalExcerpts, selectionContext, prompt);
    }

    /**
     * @param modelDigest   optional full-model summary (same on every excerpt); null or blank to omit
     * @param chunkTitle    optional scope label (e.g. folder or view name); null or blank to omit
     */
    public static String buildChunkUserMessage(String modelDigest, String chunkTitle, String xmlExcerpt,
            int excerptIndex1, int totalExcerpts, String selectionContext, String prompt) {
        StringBuilder sb = new StringBuilder();
        if (modelDigest != null && !modelDigest.isEmpty()) {
            sb.append(modelDigest.trim()).append("\n\n---\n\n");
        }
        sb.append("Excerpt ").append(excerptIndex1).append(" of ").append(totalExcerpts)
                .append(" of the same ArchiMate model (Open Exchange XML).\n");
        if (chunkTitle != null && !chunkTitle.isEmpty()) {
            sb.append("This excerpt scope: ").append(chunkTitle).append("\n");
        }
        sb.append("\nArchiMate model (Open Exchange XML):\n\n").append(xmlExcerpt != null ? xmlExcerpt : "")
                .append("\n\n--- END OF MODEL EXCERPT ---\n\n");
        sb.append("User request: ").append(prompt != null ? prompt : "").append("\n\n");
        if (selectionContext != null && !selectionContext.isEmpty()) {
            sb.append(selectionContext);
        }
        return sb.toString();
    }
}
