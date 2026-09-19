/**
 * Detects when the user explicitly asked for a brand-new Archi view/diagram (vs adding to the current one).
 */
package com.archimatetool.archigpt;

import java.util.Locale;

import com.archimatetool.model.IArchimateDiagramModel;

/**
 * Used at import time: when the user has an open diagram but did not ask for a new canvas, the LLM may still
 * emit a {@code diagram} object — that would create an unwanted second view. Nodes in that block are applied
 * to the open view instead of being discarded.
 */
@SuppressWarnings("nls")
public final class DiagramCreationIntent {

    private DiagramCreationIntent() {}

    /**
     * When a view is already open and the user did not ask for another canvas, point any LLM
     * {@code diagram} block at that view (so its nodes are drawn) instead of creating a second view.
     * An empty diagram object is dropped so catalog elements can still be auto-placed on the open view.
     *
     * @return true if a diagram block was retargeted or dropped
     */
    public static boolean retargetDiagramToOpenView(ArchiMateLLMResult result, IArchimateDiagramModel openView,
            String prompt) {
        if (result == null || openView == null || userAskedForBrandNewView(prompt)) {
            return false;
        }
        ArchiMateLLMResult.DiagramSpec diagram = result.getDiagram();
        if (diagram == null || diagram.getName() == null || diagram.getName().trim().isEmpty()) {
            return false;
        }
        if (diagram.getNodes() != null && !diagram.getNodes().isEmpty()) {
            diagram.setName(openView.getName());
        } else {
            result.setDiagram(null);
        }
        return true;
    }

    /**
     * True if the natural-language prompt clearly asks for an additional or new diagram/view canvas.
     */
    public static boolean userAskedForBrandNewView(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            return false;
        }
        String p = " " + prompt.toLowerCase(Locale.ROOT).replace('\n', ' ').replace('\r', ' ') + " ";
        return containsWordy(p, "new diagram")
                || containsWordy(p, "new view")
                || containsWordy(p, "another diagram")
                || containsWordy(p, "another view")
                || containsWordy(p, "second diagram")
                || containsWordy(p, "second view")
                || containsWordy(p, "create a new diagram")
                || containsWordy(p, "create a new view")
                || containsWordy(p, "add a new diagram")
                || containsWordy(p, "add a new view")
                || containsWordy(p, "add a diagram")
                || containsWordy(p, "add a view")
                || containsWordy(p, "create a diagram")
                || containsWordy(p, "create a view")
                || containsWordy(p, "create diagram")
                || containsWordy(p, "create view")
                || containsWordy(p, "design a diagram")
                || containsWordy(p, "design a view")
                || containsWordy(p, "diagram from scratch")
                || containsWordy(p, "make a new diagram")
                || containsWordy(p, "make a new view");
    }

    private static boolean containsWordy(String spacedLower, String phrase) {
        return spacedLower.contains(" " + phrase + " ")
                || spacedLower.contains(" " + phrase + ".")
                || spacedLower.contains(" " + phrase + ",")
                || spacedLower.contains(" " + phrase + "?")
                || spacedLower.contains(" " + phrase + "!");
    }
}
