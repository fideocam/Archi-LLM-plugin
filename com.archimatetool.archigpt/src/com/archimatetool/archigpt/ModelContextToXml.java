/**
 * Serializes the open ArchiMate model (or a subset) to a compact XML representation
 * for inclusion in the LLM prompt and for display in the analysis response.
 */
package com.archimatetool.archigpt;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IFolder;

/**
 * Builds a simple XML dump of the model structure (elements and relationships)
 * so the LLM receives exact model content and we can show it in the response.
 */
@SuppressWarnings("nls")
public final class ModelContextToXml {

    private static final int MAX_XML_CHARS = 120_000;

    private ModelContextToXml() {}

    /**
     * Serialize the given model to a compact XML string suitable for the LLM and for display.
     * Truncates if over {@value #MAX_XML_CHARS} characters to avoid token limits.
     */
    public static String toXml(IArchimateModel model) {
        if (model == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<archimateModel name=\"").append(escape(model.getName())).append("\">\n");
        appendFolders(sb, model.getFolders(), 1);
        appendViewsAndDiagrams(sb, model.getDiagramModels(), 1);
        sb.append("</archimateModel>");
        String out = sb.toString();
        if (out.length() > MAX_XML_CHARS) {
            out = out.substring(0, MAX_XML_CHARS) + "\n\n... (model truncated)";
        }
        return out;
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

    private static void appendViewsAndDiagrams(StringBuilder sb, java.util.List<IDiagramModel> diagramModels, int indent) {
        if (diagramModels == null || diagramModels.isEmpty()) return;
        String pad = repeat("  ", indent);
        sb.append(pad).append("<viewsAndDiagrams>\n");
        for (IDiagramModel dm : diagramModels) {
            String name = dm.getName() != null ? dm.getName() : "";
            if (dm instanceof IArchimateDiagramModel) {
                String viewpoint = ((IArchimateDiagramModel) dm).getViewpoint() != null ? ((IArchimateDiagramModel) dm).getViewpoint() : "";
                sb.append(pad).append("  ").append("<view name=\"").append(escape(name)).append("\" viewpoint=\"").append(escape(viewpoint)).append("\"/>\n");
            } else {
                sb.append(pad).append("  ").append("<diagram name=\"").append(escape(name)).append("\"/>\n");
            }
        }
        sb.append(pad).append("</viewsAndDiagrams>\n");
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
}
