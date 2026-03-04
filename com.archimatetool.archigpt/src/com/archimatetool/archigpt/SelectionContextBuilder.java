/**
 * Builds a short text description of the current workbench selection for use as
 * context in the ArchiGPT prompt (e.g. selected view, element, or folder).
 */
package com.archimatetool.archigpt;

import java.lang.reflect.Method;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionService;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IFolder;

/**
 * Builds context string from the current selection in Archi (model tree, etc.).
 */
@SuppressWarnings("nls")
public final class SelectionContextBuilder {

    private SelectionContextBuilder() {}

    /**
     * Build a short context string from the given selection, or empty string if none.
     * Suitable to prepend to the user prompt so the LLM knows what is selected.
     */
    public static String buildFromSelectionService(ISelectionService selectionService) {
        if (selectionService == null) return "";
        Object selection = selectionService.getSelection();
        if (selection == null) return "";
        if (!(selection instanceof IStructuredSelection)) return "";
        IStructuredSelection structured = (IStructuredSelection) selection;
        if (structured.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("Current selection in the model:\n");
        @SuppressWarnings("unchecked")
        java.util.Iterator<Object> it = structured.iterator();
        while (it.hasNext()) {
            Object obj = it.next();
            if (obj == null) continue;
            String line = describeSelectedObject(obj);
            if (line != null && !line.isEmpty()) {
                sb.append("- ").append(line).append("\n");
            }
        }
        if (sb.length() <= "Current selection in the model:\n".length()) return "";
        return sb.toString();
    }

    private static String describeSelectedObject(Object obj) {
        if (obj instanceof IArchimateConcept) {
            IArchimateConcept concept = (IArchimateConcept) obj;
            String type = concept.eClass().getName();
            String name = nullToEmpty(concept.getName());
            String id = concept.getId() != null ? concept.getId() : "";
            if (concept instanceof com.archimatetool.model.IArchimateRelationship) {
                com.archimatetool.model.IArchimateRelationship rel = (com.archimatetool.model.IArchimateRelationship) concept;
                String src = rel.getSource() != null ? nullToEmpty(rel.getSource().getName()) : "?";
                String tgt = rel.getTarget() != null ? nullToEmpty(rel.getTarget().getName()) : "?";
                return String.format("Relationship %s \"%s\" (id=%s) from \"%s\" to \"%s\"", type, name, id, src, tgt);
            }
            return String.format("Element %s \"%s\" (id=%s)", type, name, id);
        }
        if (obj instanceof IFolder) {
            String name = nullToEmpty(((IFolder) obj).getName());
            return "Folder \"" + name + "\"";
        }
        String name = tryGetName(obj);
        String kind = obj.getClass().getSimpleName();
        if (name != null && !name.isEmpty()) {
            return kind + " \"" + name + "\"";
        }
        return kind;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String tryGetName(Object obj) {
        try {
            Method m = obj.getClass().getMethod("getName");
            Object v = m.invoke(obj);
            return v == null ? "" : v.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
