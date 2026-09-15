/**
 * Builds a short text description of the current workbench selection for use as
 * context in the ArchiGPT prompt (e.g. selected view, element, or folder).
 */
package com.archimatetool.archigpt;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.ISelectionService;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModelObject;
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

    private static final String SELECTION_HEADER = "Current selection in the model"
            + " (if the user asks about these / the selected items, use ONLY this list):\n";

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
        IStructuredSelection structured = unwrapStructuredSelection((IStructuredSelection) selection);
        if (structured == null || structured.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(SELECTION_HEADER);
        @SuppressWarnings("unchecked")
        Iterator<Object> it = structured.iterator();
        int lines = 0;
        while (it.hasNext()) {
            Object obj = it.next();
            if (obj == null) continue;
            String line = describeSelectedObject(obj);
            if (line != null && !line.isEmpty()) {
                sb.append("- ").append(line).append("\n");
                lines++;
            }
        }
        if (lines == 0) return "";
        return sb.toString();
    }

    /**
     * Return true if the selection looks like model content (folder, element, relationship, or view).
     * Diagram canvas selections are GEF EditParts; those are unwrapped to ArchiMate objects first.
     */
    public static boolean isModelSelection(Object selection) {
        if (selection == null || !(selection instanceof IStructuredSelection)) return false;
        IStructuredSelection structured = unwrapStructuredSelection((IStructuredSelection) selection);
        if (structured == null || structured.isEmpty()) return false;
        return isDirectModelObject(structured.getFirstElement());
    }

    /**
     * Map GEF EditParts / IAdaptable wrappers to ArchiMate model objects so callers can use instanceof.
     */
    public static IStructuredSelection unwrapStructuredSelection(IStructuredSelection selection) {
        if (selection == null || selection.isEmpty()) {
            return selection;
        }
        List<Object> unwrapped = new ArrayList<Object>();
        @SuppressWarnings("unchecked")
        Iterator<Object> it = selection.iterator();
        while (it.hasNext()) {
            Object u = toModelObject(it.next());
            if (u != null && isDirectModelObject(u) && !unwrapped.contains(u)) {
                unwrapped.add(u);
            }
        }
        if (unwrapped.isEmpty()) {
            return selection;
        }
        return new StructuredSelection(unwrapped);
    }

    /**
     * Unwrap a diagram EditPart (or other IAdaptable) to the ArchiMate object it represents.
     */
    public static Object toModelObject(Object obj) {
        if (obj == null) {
            return null;
        }
        if (isDirectModelObject(obj)) {
            return obj;
        }
        Object fromAdapter = adaptToModelObject(obj);
        if (fromAdapter != null) {
            return fromAdapter;
        }
        Object fromGetModel = invokeNoArg(obj, "getModel");
        if (fromGetModel != null && fromGetModel != obj) {
            if (isDirectModelObject(fromGetModel)) {
                return fromGetModel;
            }
            Object nested = adaptToModelObject(fromGetModel);
            if (nested != null) {
                return nested;
            }
        }
        return obj;
    }

    static boolean isDirectModelObject(Object obj) {
        return obj instanceof IArchimateConcept
                || obj instanceof IFolder
                || obj instanceof IArchimateDiagramModel
                || obj instanceof IDiagramModelArchimateObject
                || obj instanceof IDiagramModelConnection
                || obj instanceof IDiagramModel
                || obj instanceof IArchimateModelObject;
    }

    private static Object adaptToModelObject(Object obj) {
        if (!(obj instanceof IAdaptable)) {
            return null;
        }
        IAdaptable adaptable = (IAdaptable) obj;
        Object[] tried = new Object[] {
                adaptable.getAdapter(IDiagramModelArchimateObject.class),
                adaptable.getAdapter(IDiagramModelConnection.class),
                adaptable.getAdapter(IArchimateConcept.class),
                adaptable.getAdapter(IArchimateDiagramModel.class),
                adaptable.getAdapter(IFolder.class),
                adaptable.getAdapter(IDiagramModel.class),
                adaptable.getAdapter(IArchimateModelObject.class)
        };
        for (int i = 0; i < tried.length; i++) {
            if (isDirectModelObject(tried[i])) {
                return tried[i];
            }
        }
        return null;
    }

    private static String describeSelectedObject(Object obj) {
        obj = toModelObject(obj);
        if (obj == null) {
            return "";
        }
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
            if (concept instanceof IArchimateRelationship) {
                IArchimateRelationship rel = (IArchimateRelationship) concept;
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
        Object v = invokeNoArg(obj, "getName");
        return v == null ? "" : v.toString();
    }

    private static Object invokeNoArg(Object obj, String methodName) {
        if (obj == null || methodName == null) {
            return null;
        }
        try {
            Method m = obj.getClass().getMethod(methodName);
            return m.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static IArchimateRelationship getConnectionRelationship(IDiagramModelConnection conn) {
        if (conn == null) return null;
        Object rel = invokeNoArg(conn, "getArchimateRelationship");
        if (rel instanceof IArchimateRelationship) {
            return (IArchimateRelationship) rel;
        }
        rel = invokeNoArg(conn, "getRelationship");
        if (rel instanceof IArchimateRelationship) {
            return (IArchimateRelationship) rel;
        }
        return null;
    }
}
