/**
 * Builds the user message string sent to the LLM (model XML, selection context, and prompt).
 * Extracted for testability so tests can verify selection is included in the prompt.
 */
package com.archimatetool.archigpt;

/**
 * Builds the full user message that is sent to the LLM.
 */
@SuppressWarnings("nls")
public final class UserMessageBuilder {

    private UserMessageBuilder() {}

    /**
     * Build the user message that will be sent to the LLM. Includes supplied model XML,
     * selection context (when present), and the user's prompt.
     *
     * @param selectionContext description of selected folder/element/view, or empty if none
     * @param modelXml         serialized ArchiMate model XML, or empty
     * @param prompt           user's request text
     * @return the full user message string
     */
    /**
     * Build user message with the ArchiMate model XML FIRST so the LLM receives it (many backends
     * preserve the start of the context). Then a clear delimiter and the user request + selection.
     */
    public static String buildUserMessage(String selectionContext, String modelXml, String prompt) {
        return buildUserMessage(selectionContext, modelXml, prompt, null);
    }

    /**
     * @param skillBody optional tool instructions from {@link PromptLibrary}; truncated and wrapped
     */
    public static String buildUserMessage(String selectionContext, String modelXml, String prompt, String skillBody) {
        StringBuilder sb = new StringBuilder();
        if (modelXml != null && !modelXml.isEmpty()) {
            sb.append("ArchiMate model (Open Exchange XML):\n\n").append(modelXml).append("\n\n");
        }
        sb.append("--- END OF MODEL ---\n\n");
        if (selectionContext != null && !selectionContext.isEmpty()) {
            sb.append(selectionContext);
            if (!selectionContext.endsWith("\n")) {
                sb.append('\n');
            }
            sb.append('\n');
        }
        sb.append("User request: ").append(prompt != null ? prompt : "").append("\n\n");
        appendSkill(sb, skillBody);
        return sb.toString();
    }

    static void appendSkill(StringBuilder sb, String skillBody) {
        String skill = PromptLibrary.truncateSkill(skillBody);
        if (skill.isEmpty()) {
            return;
        }
        sb.append(PromptLibrary.TOOL_INSTRUCTIONS_START).append('\n').append(skill).append('\n')
                .append(PromptLibrary.TOOL_INSTRUCTIONS_END).append("\n\n");
    }

    /** Approximate user-message size excluding model XML (for context budgeting). */
    public static int estimateNonXmlOverheadChars(String selectionContext, String prompt) {
        return estimateNonXmlOverheadChars(selectionContext, prompt, null);
    }

    public static int estimateNonXmlOverheadChars(String selectionContext, String prompt, String skillBody) {
        int sc = selectionContext != null ? selectionContext.length() : 0;
        int pr = prompt != null ? prompt.length() : 0;
        String skill = PromptLibrary.truncateSkill(skillBody);
        int sk = skill.isEmpty() ? 0 : skill.length() + 80;
        return sc + pr + sk + 120;
    }
}
