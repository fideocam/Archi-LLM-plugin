/**
 * Heuristic: user prompt is likely asking for plain-text analysis (vs JSON model changes).
 */
package com.archimatetool.archigpt;

import java.util.regex.Pattern;

@SuppressWarnings("nls")
public final class AnalysisPromptIntent {

    private static final Pattern CHANGE_VERB = Pattern.compile(
            "(?i)\\b(add|create|generate|insert|make|remove|delete|rename|renaming)\\b"
                    + "|(?i)\\b(change|update|modify)\\b.{0,80}\\b(name|names|title|label)s?\\b"
                    + "|(?i)\\b(name|names)\\b.{0,40}\\b(change|update|modify|remove)\\b"
                    + "|(?i)\\b(change|update|modify|write|fill|set)\\b.{0,80}\\b(documentation|descriptions?|notes)\\b"
                    + "|(?i)\\b(documentation|descriptions?|notes)\\b.{0,40}\\b(change|update|modify|add|write|fill|set)\\b");

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
