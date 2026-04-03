/**
 * Heuristic: user prompt is likely asking for plain-text analysis (vs JSON model changes).
 */
package com.archimatetool.archigpt;

import java.util.regex.Pattern;

@SuppressWarnings("nls")
public final class AnalysisPromptIntent {

    private static final Pattern CHANGE_VERB = Pattern.compile(
            "(?i)\\b(add|create|generate|insert|make|remove|delete)\\b");

    private AnalysisPromptIntent() {}

    /**
     * True if the prompt probably expects ANALYSIS / plain text, not CHANGES JSON.
     */
    public static boolean likelyAnalysisOnly(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            return true;
        }
        if (DiagramCreationIntent.userAskedForBrandNewView(prompt)) {
            return false;
        }
        return !CHANGE_VERB.matcher(prompt).find();
    }
}
