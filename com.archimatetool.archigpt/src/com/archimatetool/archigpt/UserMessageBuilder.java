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
    public static String buildUserMessage(String selectionContext, String modelXml, String prompt) {
        StringBuilder sb = new StringBuilder();
        if (modelXml != null && !modelXml.isEmpty()) {
            sb.append("Supplied ArchiMate model (XML):\n\n").append(modelXml).append("\n\n");
        }
        if (selectionContext != null && !selectionContext.isEmpty()) {
            sb.append(selectionContext).append("\n\n");
        }
        sb.append("User request: ").append(prompt);
        return sb.toString();
    }
}
