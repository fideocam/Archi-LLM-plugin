/**
 * Builds a short text description of the current workbench selection for use as
 * context in the ArchiGPT prompt (e.g. selected view, element, or folder).
 */
package com.archimatetool.archigpt;

import java.lang.reflect.Method;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionService;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelConnection;

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
        return buildFromStructuredSelection(selectionService.getSelection());
    }

    /**
     * Build context string from an ISelection (e.g. IStructuredSelection).
     * Use this when you have a cached or specific selection.
     */
    public static String buildFromStructuredSelection(Object selection) {
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

    /**
     * Return true if the selection looks like model content (folder, element, relationship, or view).
     */
    public static boolean isModelSelection(Object selection) {
        if (selection == null || !(selection instanceof IStructuredSelection)) return false;
        IStructuredSelection structured = (IStructuredSelection) selection;
        if (structured.isEmpty()) return false;
        Object first = structured.getFirstElement();
        return first instanceof IArchimateConcept || first instanceof IFolder || first instanceof IArchimateDiagramModel
                || first instanceof IDiagramModelArchimateObject || first instanceof IDiagramModelConnection;
    }

    private static String describeSelectedObject(Object obj) {
        // Selection on diagram canvas: figure (element on diagram) or connection (relationship on diagram)
        if (obj instanceof IDiagramModelArchimateObject) {
            IDiagramModelArchimateObject dmo = (IDiagramModelArchimateObject) obj;
            IArchimateElement element = dmo.getArchimateElement();
            if (element != null) {
                String type = element.eClass().getName();
                String name = nullToEmpty(element.getName());
                String id = element.getId() != null ? element.getId() : "";
                String onView = diagramContextSuffix(dmo.getDiagramModel());
                return String.format("Element %s \"%s\" (id=%s)%s", type, name, id, onView);
            }
        }
        if (obj instanceof IDiagramModelConnection) {
            IDiagramModelConnection dmc = (IDiagramModelConnection) obj;
            IArchimateRelationship rel = getConnectionRelationship(dmc);
            if (rel != null) {
                String type = rel.eClass().getName();
                String name = nullToEmpty(rel.getName());
                String id = rel.getId() != null ? rel.getId() : "";
                String src = rel.getSource() != null ? nullToEmpty(rel.getSource().getName()) : "?";
                String tgt = rel.getTarget() != null ? nullToEmpty(rel.getTarget().getName()) : "?";
                String onView = diagramContextSuffix(dmc.getDiagramModel());
                return String.format("Relationship %s \"%s\" (id=%s) from \"%s\" to \"%s\"%s", type, name, id, src, tgt, onView);
            }
        }
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
        if (obj instanceof IArchimateDiagramModel) {
            String name = nullToEmpty(((IArchimateDiagramModel) obj).getName());
            String vp = ((IArchimateDiagramModel) obj).getViewpoint() != null ? ((IArchimateDiagramModel) obj).getViewpoint() : "";
            return "View/Diagram \"" + name + "\"" + (vp.isEmpty() ? "" : " (viewpoint: " + vp + ")");
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

    /** Suffix naming the diagram so the LLM can tie canvas selection to the correct &lt;view&gt; in XML. */
    private static String diagramContextSuffix(IDiagramModel dm) {
        if (!(dm instanceof IArchimateDiagramModel)) {
            return "";
        }
        String vn = nullToEmpty(((IArchimateDiagramModel) dm).getName());
        if (vn.isEmpty()) {
            return "";
        }
        return " on diagram \"" + vn + "\"";
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

    private static IArchimateRelationship getConnectionRelationship(IDiagramModelConnection conn) {
        if (conn == null) return null;
        try {
            Method m = conn.getClass().getMethod("getArchimateRelationship");
            Object rel = m.invoke(conn);
            if (rel instanceof IArchimateRelationship) return (IArchimateRelationship) rel;
        } catch (Exception e1) {
            // continue
        }
        try {
            Method m = conn.getClass().getMethod("getRelationship");
            Object rel = m.invoke(conn);
            if (rel instanceof IArchimateRelationship) return (IArchimateRelationship) rel;
        } catch (Exception e2) {
            // continue
        }
        return null;
    }
}
