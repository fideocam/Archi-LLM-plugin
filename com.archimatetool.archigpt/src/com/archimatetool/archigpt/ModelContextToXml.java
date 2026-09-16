/**
 * Serializes the open ArchiMate model (or a subset) to a compact XML representation
 * for inclusion in the LLM prompt and for display in the analysis response.
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IConnectable;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.FolderType;

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
        return toXml(model, 0, null, null);
    }

    /** Top-level folders with priority first (same rules as full serialization). */
    public static List<IFolder> orderedTopFolders(IArchimateModel model, List<IFolder> priorityFolders) {
        if (model == null) return new ArrayList<>();
        return orderFolders(model.getFolders(), priorityFolders);
    }

    /** All diagrams with priority first (same rules as full serialization). */
    public static List<IArchimateDiagramModel> orderedDiagramsForModel(IArchimateModel model,
            List<IArchimateDiagramModel> priorityDiagrams) {
        if (model == null) return new ArrayList<>();
        return orderDiagrams(collectAllDiagramModels(model), priorityDiagrams);
    }

    /**
     * Valid Open Exchange fragment: declaration + one complete top-level folder subtree (all nested folders and elements).
     */
    public static String toXmlFolderSubtree(IArchimateModel model, IFolder folder) {
        if (model == null || folder == null) return "";
        StringBuilder sb = new StringBuilder();
        appendDocumentPreamble(sb);
        sb.append("  <archimateModel name=\"").append(escape(model.getName())).append("\">\n");
        appendOneFolder(sb, folder, 2);
        sb.append("  </archimateModel>\n</model>\n");
        return sb.toString();
    }

    /**
     * Elements and relationships stored directly on {@code folder} (not in child folders), wrapped as a minimal folder.
     */
    public static String toXmlFolderDirectMembersOnly(IArchimateModel model, IFolder folder) {
        if (model == null || folder == null) return "";
        StringBuilder sb = new StringBuilder();
        appendDocumentPreamble(sb);
        sb.append("  <archimateModel name=\"").append(escape(model.getName())).append("\">\n");
        String name = folder.getName() != null ? folder.getName() : "";
        String typeName = folder.getType() != null ? folder.getType().getName() : "";
        sb.append("    <folder name=\"").append(escape(name)).append("\" type=\"").append(escape(typeName)).append("\">\n");
        if (folder.getElements() != null) {
            for (EObject e : folder.getElements()) {
                appendFolderMember(sb, e, 3);
            }
        }
        sb.append("    </folder>\n");
        sb.append("  </archimateModel>\n</model>\n");
        return sb.toString();
    }

    /** Views/diagrams only (no element folders). */
    public static String toXmlDiagramList(IArchimateModel model, List<IArchimateDiagramModel> diagrams) {
        if (model == null || diagrams == null || diagrams.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        appendDocumentPreamble(sb);
        sb.append("  <archimateModel name=\"").append(escape(model.getName())).append("\">\n");
        appendViewsAndDiagrams(sb, diagrams, 2);
        sb.append("  </archimateModel>\n</model>\n");
        return sb.toString();
    }

    /** True if this folder is the Archi "Views" / diagrams container (diagram XML is emitted via {@link #toXmlDiagramList}). */
    public static boolean isDiagramsContainerFolder(IFolder folder) {
        return folder != null && folder.getType() == FolderType.DIAGRAMS;
    }

    private static void appendDocumentPreamble(StringBuilder sb) {
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<model\n  xmlns=\"").append(NS_ARCHIMATE).append("\"\n  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n  xmlns:diagram=\"")
                .append(NS_DIAGRAM).append("\"\n  xsi:schemaLocation=\"").append(SCHEMA_LOCATION).append("\">\n");
    }

    /**
     * Serialize the model to XML, putting priority folders and diagrams first so they fit within the context limit.
     *
     * @param model            the ArchiMate model
     * @param maxChars         max characters to emit (e.g. for LLM context); 0 = no limit, use {@value #MAX_XML_CHARS} internally
     * @param priorityFolders  folders to serialize first (e.g. selected); null = use default order
     * @param priorityDiagrams diagrams to serialize first (e.g. selected view); null = use default order
     * @return XML string with relevant parts first when priority is given, truncated to maxChars when maxChars &gt; 0
     */
    public static String toXml(IArchimateModel model, int maxChars,
            List<IFolder> priorityFolders, List<IArchimateDiagramModel> priorityDiagrams) {
        if (model == null) return "";
        int effectiveMax = maxChars > 0 ? maxChars : MAX_XML_CHARS;
        StringBuilder sb = new StringBuilder();
        appendDocumentPreamble(sb);
        sb.append("  <archimateModel name=\"").append(escape(model.getName())).append("\">\n");

        List<IFolder> orderedFolders = orderFolders(model.getFolders(), priorityFolders);
        List<IArchimateDiagramModel> allDiagrams = collectAllDiagramModels(model);
        List<IArchimateDiagramModel> orderedDiagrams = orderDiagrams(allDiagrams, priorityDiagrams);
        int cap = maxChars > 0 ? effectiveMax : 0;
        if (maxChars > 0) {
            // Capped LLM context: views before folders so diagram content is not omitted when folder XML fills the limit.
            appendViewsAndDiagramsWithLimit(sb, orderedDiagrams, 2, cap);
            appendFoldersWithLimit(sb, orderedFolders, 2, cap);
        } else {
            boolean truncated = appendFoldersWithLimit(sb, orderedFolders, 2, 0);
            if (!truncated) {
                appendViewsAndDiagramsWithLimit(sb, orderedDiagrams, 2, 0);
            }
        }
        sb.append("  </archimateModel>\n");
        sb.append("</model>");
        String out = sb.toString();
        if (maxChars <= 0 && out.length() > MAX_XML_CHARS) {
            out = out.substring(0, MAX_XML_CHARS) + "\n\n... (model truncated at " + MAX_XML_CHARS + " characters; full ArchiMate model not sent)";
        }
        return out;
    }

    /** Put priority items first, then the rest in original order; no duplicates. */
    private static List<IFolder> orderFolders(List<IFolder> all, List<IFolder> priority) {
        if (priority == null || priority.isEmpty()) return all != null ? all : new ArrayList<>();
        Set<IFolder> seen = new LinkedHashSet<>();
        List<IFolder> result = new ArrayList<>();
        for (IFolder f : priority) {
            if (f != null && !seen.contains(f)) {
                seen.add(f);
                result.add(f);
            }
        }
        if (all != null) {
            for (IFolder f : all) {
                if (f != null && !seen.contains(f)) {
                    seen.add(f);
                    result.add(f);
                }
            }
        }
        return result;
    }

    /** Put priority diagrams first, then the rest; no duplicates. */
    private static List<IArchimateDiagramModel> orderDiagrams(List<IArchimateDiagramModel> all, List<IArchimateDiagramModel> priority) {
        if (priority == null || priority.isEmpty()) return all != null ? all : new ArrayList<>();
        Set<IArchimateDiagramModel> seen = new LinkedHashSet<>();
        List<IArchimateDiagramModel> result = new ArrayList<>();
        for (IArchimateDiagramModel d : priority) {
            if (d != null && !seen.contains(d)) {
                seen.add(d);
                result.add(d);
            }
        }
        if (all != null) {
            for (IArchimateDiagramModel d : all) {
                if (d != null && !seen.contains(d)) {
                    seen.add(d);
                    result.add(d);
                }
            }
        }
        return result;
    }

    /** Append folders; when maxChars > 0, stop after a complete top-level folder if length >= maxChars. Returns true if truncated. */
    private static boolean appendFoldersWithLimit(StringBuilder sb, List<IFolder> folders, int indent, int maxChars) {
        if (folders == null) return false;
        String pad = repeat("  ", indent);
        for (IFolder folder : folders) {
            appendOneFolder(sb, folder, indent);
            if (maxChars > 0 && sb.length() >= maxChars) {
                sb.append("\n  ... (truncated for context limit)\n");
                return true;
            }
        }
        return false;
    }

    private static void appendOneFolder(StringBuilder sb, IFolder folder, int indent) {
        if (folder == null) return;
        String pad = repeat("  ", indent);
        String name = folder.getName() != null ? folder.getName() : "";
        String typeName = folder.getType() != null ? folder.getType().getName() : "";
        sb.append(pad).append("<folder name=\"").append(escape(name)).append("\" type=\"").append(escape(typeName)).append("\">\n");
        for (EObject e : folder.getElements()) {
            appendFolderMember(sb, e, indent + 1);
        }
        appendFolders(sb, folder.getFolders(), indent + 1);
        sb.append(pad).append("</folder>\n");
    }

    /** Append views/diagrams; when maxChars > 0, stop after a complete view if length >= maxChars. */
    private static void appendViewsAndDiagramsWithLimit(StringBuilder sb, List<IArchimateDiagramModel> diagramModels, int indent, int maxChars) {
        if (diagramModels == null || diagramModels.isEmpty()) return;
        String pad = repeat("  ", indent);
        sb.append(pad).append("<viewsAndDiagrams>\n");
        for (IArchimateDiagramModel dm : diagramModels) {
            String name = dm.getName() != null ? dm.getName() : "";
            String viewpoint = dm.getViewpoint() != null ? dm.getViewpoint() : "";
            sb.append(pad).append("  ").append("<view name=\"").append(escape(name)).append("\" viewpoint=\"").append(escape(viewpoint)).append("\">\n");
            appendDiagramContent(sb, dm, indent + 2);
            sb.append(pad).append("  ").append("</view>\n");
            if (maxChars > 0 && sb.length() >= maxChars) {
                sb.append(pad).append("  ... (truncated)\n");
                break;
            }
        }
        sb.append(pad).append("</viewsAndDiagrams>\n");
    }

    /**
     * Element and relationship ids that appear on at least one view (including nested groups).
     * Connections are taken from {@code getSourceConnections()} on figures, not from {@code getChildren()}.
     */
    public static final class DiagramUsage {
        public final Set<String> elementIds = new LinkedHashSet<String>();
        public final Set<String> relationshipIds = new LinkedHashSet<String>();
    }

    public static DiagramUsage collectDiagramUsage(IArchimateModel model) {
        DiagramUsage usage = new DiagramUsage();
        if (model == null) {
            return usage;
        }
        List<IArchimateDiagramModel> all = collectAllDiagramModels(model);
        for (int i = 0; i < all.size(); i++) {
            collectDiagramUsage(all.get(i), usage);
        }
        return usage;
    }

    static void collectDiagramUsage(IArchimateDiagramModel diagram, DiagramUsage usage) {
        if (diagram == null || usage == null) {
            return;
        }
        Set<IDiagramModelConnection> seen = new LinkedHashSet<IDiagramModelConnection>();
        walkContainer(diagram, usage, seen);
    }

    /**
     * Find all diagrams in the model that contain the given concept (element or relationship).
     * Used to put the selected element's view(s) first in the XML sent to the LLM.
     */
    public static List<IArchimateDiagramModel> getDiagramsContaining(IArchimateModel model, IArchimateConcept concept) {
        if (model == null || concept == null) return new ArrayList<>();
        List<IArchimateDiagramModel> out = new ArrayList<>();
        List<IArchimateDiagramModel> all = collectAllDiagramModels(model);
        for (IArchimateDiagramModel dm : all) {
            DiagramUsage usage = new DiagramUsage();
            collectDiagramUsage(dm, usage);
            String id = concept.getId();
            if (id != null && (usage.elementIds.contains(id) || usage.relationshipIds.contains(id))) {
                out.add(dm);
            }
        }
        return out;
    }

    /** All diagram/view models in the given ArchiMate model (for prioritization or name matching). */
    public static List<IArchimateDiagramModel> getAllDiagramModels(IArchimateModel model) {
        return collectAllDiagramModels(model);
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
                appendFolderMember(sb, e, indent + 1);
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

    /** Serialize diagram content: nested nodes and connections from figure {@code getSourceConnections()}. */
    private static void appendDiagramContent(StringBuilder sb, IArchimateDiagramModel diagram, int indent) {
        if (diagram == null) {
            return;
        }
        String pad = repeat("  ", indent);
        Set<IDiagramModelConnection> seen = new LinkedHashSet<IDiagramModelConnection>();
        appendContainerContent(sb, diagram, pad, seen);
    }

    private static void appendContainerContent(StringBuilder sb, IDiagramModelContainer container, String pad,
            Set<IDiagramModelConnection> seen) {
        if (container == null || container.getChildren() == null) {
            return;
        }
        for (Object child : container.getChildren()) {
            if (child instanceof IConnectable) {
                appendSourceConnections(sb, (IConnectable) child, pad, seen);
            }
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
                  .append("\" x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"").append(w)
                  .append("\" height=\"").append(h).append("\"/>\n");
                appendContainerContent(sb, dmo, pad, seen);
            } else if (child instanceof IDiagramModelContainer) {
                appendContainerContent(sb, (IDiagramModelContainer) child, pad, seen);
            }
        }
    }

    private static void appendSourceConnections(StringBuilder sb, IConnectable connectable, String pad,
            Set<IDiagramModelConnection> seen) {
        if (connectable == null || connectable.getSourceConnections() == null) {
            return;
        }
        for (IDiagramModelConnection conn : connectable.getSourceConnections()) {
            if (conn == null || !seen.add(conn)) {
                continue;
            }
            String srcId = conn.getSource() != null ? getDiagramObjectId(conn.getSource()) : "";
            String tgtId = conn.getTarget() != null ? getDiagramObjectId(conn.getTarget()) : "";
            String relId = getConnectionRelationshipId(conn);
            sb.append(pad).append("<connection source=\"").append(escape(srcId)).append("\" target=\"").append(escape(tgtId))
              .append("\" relationshipRef=\"").append(escape(relId)).append("\"/>\n");
            appendSourceConnections(sb, conn, pad, seen);
        }
    }

    private static void walkContainer(IDiagramModelContainer container, DiagramUsage usage,
            Set<IDiagramModelConnection> seen) {
        if (container == null || container.getChildren() == null || usage == null) {
            return;
        }
        for (Object child : container.getChildren()) {
            if (child instanceof IConnectable) {
                collectSourceConnections((IConnectable) child, usage, seen);
            }
            if (child instanceof IDiagramModelArchimateObject) {
                IDiagramModelArchimateObject dmo = (IDiagramModelArchimateObject) child;
                IArchimateElement el = dmo.getArchimateElement();
                if (el != null && el.getId() != null && !el.getId().isEmpty()) {
                    usage.elementIds.add(el.getId());
                }
                walkContainer(dmo, usage, seen);
            } else if (child instanceof IDiagramModelContainer) {
                walkContainer((IDiagramModelContainer) child, usage, seen);
            }
        }
    }

    private static void collectSourceConnections(IConnectable connectable, DiagramUsage usage,
            Set<IDiagramModelConnection> seen) {
        if (connectable == null || connectable.getSourceConnections() == null) {
            return;
        }
        for (IDiagramModelConnection conn : connectable.getSourceConnections()) {
            if (conn == null || !seen.add(conn)) {
                continue;
            }
            String relId = getConnectionRelationshipId(conn);
            if (relId != null && !relId.isEmpty()) {
                usage.relationshipIds.add(relId);
            }
            collectSourceConnections(conn, usage, seen);
        }
    }

    private static String getDiagramObjectId(Object diagramObject) {
        if (diagramObject instanceof IDiagramModelArchimateObject) {
            IArchimateElement el = ((IDiagramModelArchimateObject) diagramObject).getArchimateElement();
            return el != null && el.getId() != null ? el.getId() : "";
        }
        return "";
    }

    private static String getConnectionRelationshipId(IDiagramModelConnection conn) {
        if (conn instanceof IDiagramModelArchimateConnection) {
            IArchimateRelationship rel = ((IDiagramModelArchimateConnection) conn).getArchimateRelationship();
            if (rel != null && rel.getId() != null) {
                return rel.getId();
            }
        }
        try {
            java.lang.reflect.Method m = conn.getClass().getMethod("getArchimateRelationship");
            Object rel = m.invoke(conn);
            if (rel != null) {
                java.lang.reflect.Method getId = rel.getClass().getMethod("getId");
                Object id = getId.invoke(rel);
                return id != null ? id.toString() : "";
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static void appendFolderMember(StringBuilder sb, EObject obj, int indent) {
        if (obj instanceof IArchimateRelationship) {
            appendRelationship(sb, (IArchimateRelationship) obj, indent);
        } else if (obj instanceof IArchimateElement) {
            appendElement(sb, (IArchimateElement) obj, indent);
        }
    }

    private static void appendRelationship(StringBuilder sb, IArchimateRelationship rel, int indent) {
        String pad = repeat("  ", indent);
        String type = rel.eClass().getName();
        String name = rel.getName() != null ? rel.getName() : "";
        String id = rel.getId() != null ? rel.getId() : "";
        String srcId = rel.getSource() != null && rel.getSource().getId() != null ? rel.getSource().getId() : "";
        String tgtId = rel.getTarget() != null && rel.getTarget().getId() != null ? rel.getTarget().getId() : "";
        String attrs = "type=\"" + escape(type) + "\" name=\"" + escape(name) + "\" id=\"" + escape(id)
                + "\" source=\"" + escape(srcId) + "\" target=\"" + escape(tgtId) + "\"";
        appendTaggedConcept(sb, pad, "relationship", attrs, rel.getDocumentation());
    }

    private static void appendElement(StringBuilder sb, IArchimateElement element, int indent) {
        String pad = repeat("  ", indent);
        String type = element.eClass().getName();
        String name = element.getName() != null ? element.getName() : "";
        String id = element.getId() != null ? element.getId() : "";
        String attrs = "type=\"" + escape(type) + "\" name=\"" + escape(name) + "\" id=\"" + escape(id) + "\"";
        appendTaggedConcept(sb, pad, "element", attrs, element.getDocumentation());
    }

    private static void appendTaggedConcept(StringBuilder sb, String pad, String tag, String attributes,
            String documentation) {
        if (documentation != null && !documentation.isEmpty()) {
            sb.append(pad).append("<").append(tag).append(" ").append(attributes).append(">\n");
            sb.append(pad).append("  <documentation>").append(escape(documentation)).append("</documentation>\n");
            sb.append(pad).append("</").append(tag).append(">\n");
        } else {
            sb.append(pad).append("<").append(tag).append(" ").append(attributes).append("/>\n");
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
