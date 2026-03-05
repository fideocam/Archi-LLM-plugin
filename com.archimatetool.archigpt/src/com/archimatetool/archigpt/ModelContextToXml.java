/**
 * Serializes the open ArchiMate model (or a subset) to a compact XML representation
 * for inclusion in the LLM prompt and for display in the analysis response.
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IFolder;

/**
 * Builds a simple XML dump of the model structure (elements, relationships, and diagrams with their content)
 * so the LLM receives exact model content and we can show it in the response.
 */
@SuppressWarnings("nls")
public final class ModelContextToXml {

    /** Max characters for serialized model XML (avoids token limits). Increase if you need the full model; truncation message is appended when hit. */
    private static final int MAX_XML_CHARS = 500_000;

    private ModelContextToXml() {}

    /**
     * Serialize the given model to a compact XML string suitable for the LLM and for display.
     * Includes folders (elements/relationships), and all diagram/view content (nodes and connections on each diagram).
     * Truncates if over {@value #MAX_XML_CHARS} characters to avoid token limits.
     */
    /** Open Group ArchiMate 3.0 model namespace (valid Open Exchange document). */
    public static final String NS_ARCHIMATE = "http://www.opengroup.org/xsd/archimate/3.0/";
    /** Open Group ArchiMate 3.0 diagram namespace. */
    public static final String NS_DIAGRAM = "http://www.opengroup.org/xsd/archimate/3.0/diagram";
    /** Schema location for model and diagram (LLM should use both). */
    public static final String SCHEMA_LOCATION = "http://www.opengroup.org/xsd/archimate/3.0/ archimate3_Model.xsd "
            + "http://www.opengroup.org/xsd/archimate/3.0/diagram archimate3_Diagram.xsd";

    public static String toXml(IArchimateModel model) {
        if (model == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<model\n  xmlns=\"").append(NS_ARCHIMATE).append("\"\n  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n  xmlns:diagram=\"")
          .append(NS_DIAGRAM).append("\"\n  xsi:schemaLocation=\"").append(SCHEMA_LOCATION).append("\">\n");
        sb.append("  <archimateModel name=\"").append(escape(model.getName())).append("\">\n");
        appendFolders(sb, model.getFolders(), 2);
        List<IArchimateDiagramModel> diagrams = collectAllDiagramModels(model);
        appendViewsAndDiagrams(sb, diagrams, 2);
        sb.append("  </archimateModel>\n");
        sb.append("</model>");
        String out = sb.toString();
        if (out.length() > MAX_XML_CHARS) {
            out = out.substring(0, MAX_XML_CHARS) + "\n\n... (model truncated at " + MAX_XML_CHARS + " characters; full ArchiMate model not sent)";
        }
        return out;
    }

    /** Collect all ArchiMate diagram models from the model (getDiagramModels + any found via eAllContents). */
    private static List<IArchimateDiagramModel> collectAllDiagramModels(IArchimateModel model) {
        List<IArchimateDiagramModel> list = new ArrayList<>();
        if (model.getDiagramModels() != null) {
            for (IDiagramModel dm : model.getDiagramModels()) {
                if (dm instanceof IArchimateDiagramModel) {
                    list.add((IArchimateDiagramModel) dm);
                }
            }
        }
        java.util.Iterator<EObject> it = model.eAllContents();
        while (it.hasNext()) {
            EObject e = it.next();
            if (e instanceof IArchimateDiagramModel && !list.contains(e)) {
                list.add((IArchimateDiagramModel) e);
            }
        }
        return list;
    }

    private static void appendFolders(StringBuilder sb, java.util.List<IFolder> folders, int indent) {
        if (folders == null) return;
        String pad = repeat("  ", indent);
        for (IFolder folder : folders) {
            String name = folder.getName() != null ? folder.getName() : "";
            String typeName = folder.getType() != null ? folder.getType().getName() : "";
            sb.append(pad).append("<folder name=\"").append(escape(name)).append("\" type=\"").append(escape(typeName)).append("\">\n");
            for (EObject e : folder.getElements()) {
                if (e instanceof IArchimateElement) {
                    appendElement(sb, (IArchimateElement) e, indent + 1);
                }
            }
            appendFolders(sb, folder.getFolders(), indent + 1);
            sb.append(pad).append("</folder>\n");
        }
    }

    private static void appendViewsAndDiagrams(StringBuilder sb, List<IArchimateDiagramModel> diagramModels, int indent) {
        if (diagramModels == null || diagramModels.isEmpty()) return;
        String pad = repeat("  ", indent);
        sb.append(pad).append("<viewsAndDiagrams>\n");
        for (IArchimateDiagramModel dm : diagramModels) {
            String name = dm.getName() != null ? dm.getName() : "";
            String viewpoint = dm.getViewpoint() != null ? dm.getViewpoint() : "";
            sb.append(pad).append("  ").append("<view name=\"").append(escape(name)).append("\" viewpoint=\"").append(escape(viewpoint)).append("\">\n");
            appendDiagramContent(sb, dm, indent + 2);
            sb.append(pad).append("  ").append("</view>\n");
        }
        sb.append(pad).append("</viewsAndDiagrams>\n");
    }

    /** Serialize diagram content: nodes (element refs, bounds) and connections (source, target, relationship). */
    private static void appendDiagramContent(StringBuilder sb, IArchimateDiagramModel diagram, int indent) {
        if (diagram == null || diagram.getChildren() == null) return;
        String pad = repeat("  ", indent);
        for (Object child : diagram.getChildren()) {
            if (child instanceof IDiagramModelArchimateObject) {
                IDiagramModelArchimateObject dmo = (IDiagramModelArchimateObject) child;
                IArchimateElement el = dmo.getArchimateElement();
                String elId = el != null && el.getId() != null ? el.getId() : "";
                String elName = el != null && el.getName() != null ? el.getName() : "";
                int x = 0, y = 0, w = 120, h = 55;
                try {
                    Object bounds = dmo.getBounds();
                    if (bounds != null) {
                        x = safeInt(bounds, "getX", 0);
                        y = safeInt(bounds, "getY", 0);
                        w = safeInt(bounds, "getWidth", 120);
                        h = safeInt(bounds, "getHeight", 55);
                    }
                } catch (Exception ignored) {
                }
                sb.append(pad).append("<node elementRef=\"").append(escape(elId)).append("\" name=\"").append(escape(elName))
                  .append("\" x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"").append(w).append("\" height=\"").append(h).append("\"/>\n");
            } else if (child instanceof com.archimatetool.model.IDiagramModelConnection) {
                com.archimatetool.model.IDiagramModelConnection conn = (com.archimatetool.model.IDiagramModelConnection) child;
                String srcId = conn.getSource() != null ? getDiagramObjectId(conn.getSource()) : "";
                String tgtId = conn.getTarget() != null ? getDiagramObjectId(conn.getTarget()) : "";
                String relId = getConnectionRelationshipId(conn);
                sb.append(pad).append("<connection source=\"").append(escape(srcId)).append("\" target=\"").append(escape(tgtId))
                  .append("\" relationshipRef=\"").append(escape(relId)).append("\"/>\n");
            }
        }
    }

    private static String getDiagramObjectId(Object diagramObject) {
        if (diagramObject instanceof IDiagramModelArchimateObject) {
            IArchimateElement el = ((IDiagramModelArchimateObject) diagramObject).getArchimateElement();
            return el != null && el.getId() != null ? el.getId() : "";
        }
        return "";
    }

    /** Get relationship id from a diagram connection (API may be getArchimateRelationship or getRelationship). */
    private static String getConnectionRelationshipId(com.archimatetool.model.IDiagramModelConnection conn) {
        try {
            java.lang.reflect.Method m = conn.getClass().getMethod("getArchimateRelationship");
            Object rel = m.invoke(conn);
            if (rel != null) {
                java.lang.reflect.Method getId = rel.getClass().getMethod("getId");
                Object id = getId.invoke(rel);
                return id != null ? id.toString() : "";
            }
        } catch (Exception e1) {
            try {
                java.lang.reflect.Method m = conn.getClass().getMethod("getRelationship");
                Object rel = m.invoke(conn);
                if (rel != null) {
                    java.lang.reflect.Method getId = rel.getClass().getMethod("getId");
                    Object id = getId.invoke(rel);
                    return id != null ? id.toString() : "";
                }
            } catch (Exception e2) {
                // ignore
            }
        }
        return "";
    }

    private static void appendElement(StringBuilder sb, IArchimateElement element, int indent) {
        String pad = repeat("  ", indent);
        if (element instanceof IArchimateRelationship) {
            IArchimateRelationship rel = (IArchimateRelationship) element;
            String type = rel.eClass().getName();
            String name = rel.getName() != null ? rel.getName() : "";
            String id = rel.getId() != null ? rel.getId() : "";
            String srcId = rel.getSource() != null && rel.getSource().getId() != null ? rel.getSource().getId() : "";
            String tgtId = rel.getTarget() != null && rel.getTarget().getId() != null ? rel.getTarget().getId() : "";
            sb.append(pad).append("<relationship type=\"").append(escape(type)).append("\" name=\"").append(escape(name))
              .append("\" id=\"").append(escape(id)).append("\" source=\"").append(escape(srcId))
              .append("\" target=\"").append(escape(tgtId)).append("\"/>\n");
        } else {
            String type = element.eClass().getName();
            String name = element.getName() != null ? element.getName() : "";
            String id = element.getId() != null ? element.getId() : "";
            sb.append(pad).append("<element type=\"").append(escape(type)).append("\" name=\"").append(escape(name))
              .append("\" id=\"").append(escape(id)).append("\"/>\n");
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }

    private static int safeInt(Object obj, String getterName, int defaultValue) {
        if (obj == null) return defaultValue;
        try {
            Object v = obj.getClass().getMethod(getterName).invoke(obj);
            if (v instanceof Number) return ((Number) v).intValue();
        } catch (Exception ignored) {
        }
        return defaultValue;
    }
}
