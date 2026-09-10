/**
 * Limits and confirmation rules for applying LLM-produced model mutations.
 * Ported from EaGPT (fideocam/sparxgpt).
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("nls")
public final class MutationPolicy {

    public static final int MAX_REPLY_CHARS = 200_000;
    public static final int MAX_ELEMENTS = 80;
    public static final int MAX_RELATIONSHIPS = 120;
    public static final int MAX_REMOVALS = 50;
    public static final int MAX_NAME_CHARS = 256;
    public static final int MAX_ID_CHARS = 80;
    public static final int MAX_COORD = 4000;

    private MutationPolicy() {}

    /**
     * Deletes from the model or of whole diagrams. These require an explicit user confirmation.
     * Remove-from-diagram-only is not treated as destructive.
     */
    public static boolean isDestructive(ArchiMateLLMResult result) {
        if (result == null) {
            return false;
        }
        return !result.getRemoveElementIds().isEmpty()
                || !result.getRemoveRelationshipIds().isEmpty()
                || !result.getRemoveDiagramNames().isEmpty();
    }

    public static List<String> checkLimits(ArchiMateLLMResult result) {
        List<String> errors = new ArrayList<>();
        if (result == null) {
            return errors;
        }
        if (result.getElements().size() > MAX_ELEMENTS) {
            errors.add("Too many elements in one reply (" + result.getElements().size() + " > " + MAX_ELEMENTS + ").");
        }
        if (result.getRelationships().size() > MAX_RELATIONSHIPS) {
            errors.add("Too many relationships in one reply (" + result.getRelationships().size()
                    + " > " + MAX_RELATIONSHIPS + ").");
        }
        int removals = result.getRemoveElementIds().size() + result.getRemoveRelationshipIds().size()
                + result.getRemoveDiagramNames().size() + result.getRemoveElementFromDiagramIds().size()
                + result.getRemoveRelationshipFromDiagramIds().size();
        if (removals > MAX_REMOVALS) {
            errors.add("Too many removals in one reply (" + removals + " > " + MAX_REMOVALS + ").");
        }
        for (ArchiMateLLMResult.ElementSpec e : result.getElements()) {
            checkName(errors, e.getName(), "Element");
            checkId(errors, e.getId(), "Element");
        }
        for (ArchiMateLLMResult.RelationshipSpec r : result.getRelationships()) {
            checkName(errors, r.getName(), "Relationship");
            checkId(errors, r.getId(), "Relationship");
            checkId(errors, r.getSource(), "Relationship source");
            checkId(errors, r.getTarget(), "Relationship target");
        }
        checkStringList(errors, result.getRemoveElementIds(), "Removal element id");
        checkStringList(errors, result.getRemoveRelationshipIds(), "Removal relationship id");
        checkStringList(errors, result.getRemoveElementFromDiagramIds(), "Diagram-removal element id");
        checkStringList(errors, result.getRemoveRelationshipFromDiagramIds(), "Diagram-removal relationship id");
        for (String name : result.getRemoveDiagramNames()) {
            checkName(errors, name, "Removed diagram");
        }
        if (result.getDiagram() != null) {
            checkName(errors, result.getDiagram().getName(), "Diagram");
            if (result.getDiagram().getNodes().size() > MAX_ELEMENTS) {
                errors.add("Too many diagram nodes in one reply (" + result.getDiagram().getNodes().size()
                        + " > " + MAX_ELEMENTS + ").");
            }
            for (ArchiMateLLMResult.DiagramNodeSpec n : result.getDiagram().getNodes()) {
                if (n.getX() < 0 || n.getY() < 0 || n.getX() > MAX_COORD || n.getY() > MAX_COORD) {
                    errors.add("Diagram node coordinate is out of range (0–" + MAX_COORD + ").");
                    break;
                }
            }
        }
        return errors;
    }

    public static String destructiveSummary(ArchiMateLLMResult result) {
        int el = result != null ? result.getRemoveElementIds().size() : 0;
        int rel = result != null ? result.getRemoveRelationshipIds().size() : 0;
        int diag = result != null ? result.getRemoveDiagramNames().size() : 0;
        return "ArchiGPT wants to delete " + el + " element(s), " + rel + " relationship(s), and "
                + diag + " diagram(s) from the model. Continue?";
    }

    private static void checkName(List<String> errors, String name, String what) {
        if (name != null && name.length() > MAX_NAME_CHARS) {
            errors.add(what + " name is too long (" + name.length() + " > " + MAX_NAME_CHARS + ").");
        }
    }

    private static void checkId(List<String> errors, String id, String what) {
        if (id != null && id.length() > MAX_ID_CHARS) {
            errors.add(what + " id is too long (" + id.length() + " > " + MAX_ID_CHARS + ").");
        }
    }

    private static void checkStringList(List<String> errors, List<String> ids, String what) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            checkId(errors, id, what);
        }
    }
}
