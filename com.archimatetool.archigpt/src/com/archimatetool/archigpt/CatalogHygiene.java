/**
 * Deterministic catalog-versus-canvas inventories. The LLM cannot reliably set-difference
 * relationships vs view connections when XML is truncated or split across chunks, and Archi
 * stores connections on figures ({@code getSourceConnections()}), not as diagram children.
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;

@SuppressWarnings("nls")
public final class CatalogHygiene {

    static final String FINDINGS_START = "--- PLUGIN FINDINGS (computed from the live Archi model; trust this over truncated XML) ---";
    static final String FINDINGS_END = "--- END PLUGIN FINDINGS ---";

    private static final int MAX_LINES = 400;

    private CatalogHygiene() {}

    public static boolean wantsUnusedRelationships(PromptLibrary.Entry tool, String prompt) {
        if (tool != null && "tidy-relationships-not-on-views".equals(tool.id)) {
            return true;
        }
        return mentionsUnused(prompt, "relationship");
    }

    public static boolean wantsUnusedElements(PromptLibrary.Entry tool, String prompt) {
        if (tool != null && "tidy-elements-not-on-views".equals(tool.id)) {
            return true;
        }
        return mentionsUnused(prompt, "element");
    }

    public static String findingsFor(IArchimateModel model, PromptLibrary.Entry tool, String prompt) {
        if (model == null) {
            return "";
        }
        boolean rels = wantsUnusedRelationships(tool, prompt);
        boolean els = wantsUnusedElements(tool, prompt);
        if (!rels && !els) {
            return "";
        }
        ModelContextToXml.DiagramUsage usage = ModelContextToXml.collectDiagramUsage(model);
        StringBuilder sb = new StringBuilder();
        sb.append(FINDINGS_START).append('\n');
        if (rels) {
            sb.append(unusedRelationshipsReport(model, usage));
        }
        if (els) {
            if (rels) {
                sb.append('\n');
            }
            sb.append(unusedElementsReport(model, usage));
        }
        sb.append(FINDINGS_END).append('\n');
        return sb.toString();
    }

    static String unusedRelationshipsReport(IArchimateModel model, ModelContextToXml.DiagramUsage usage) {
        List<IArchimateRelationship> unused = new ArrayList<IArchimateRelationship>();
        Iterator<EObject> it = model.eAllContents();
        while (it.hasNext()) {
            EObject e = it.next();
            if (e instanceof IArchimateRelationship) {
                IArchimateRelationship rel = (IArchimateRelationship) e;
                String id = rel.getId();
                if (id == null || id.isEmpty() || !usage.relationshipIds.contains(id)) {
                    unused.add(rel);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Unused relationships (not drawn on any view): ").append(unused.size()).append('\n');
        if (unused.isEmpty()) {
            sb.append("None. Every catalog relationship appears as a connection on at least one view.\n");
            return sb.toString();
        }
        int n = Math.min(unused.size(), MAX_LINES);
        for (int i = 0; i < n; i++) {
            sb.append("- ").append(describeRelationship(unused.get(i))).append('\n');
        }
        if (unused.size() > MAX_LINES) {
            sb.append("… and ").append(unused.size() - MAX_LINES).append(" more (list truncated).\n");
        }
        return sb.toString();
    }

    static String unusedElementsReport(IArchimateModel model, ModelContextToXml.DiagramUsage usage) {
        List<IArchimateElement> unused = new ArrayList<IArchimateElement>();
        Iterator<EObject> it = model.eAllContents();
        while (it.hasNext()) {
            EObject e = it.next();
            if (e instanceof IArchimateElement) {
                IArchimateElement el = (IArchimateElement) e;
                String id = el.getId();
                if (id == null || id.isEmpty() || !usage.elementIds.contains(id)) {
                    unused.add(el);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Unused elements (not placed on any view): ").append(unused.size()).append('\n');
        if (unused.isEmpty()) {
            sb.append("None. Every catalog element appears as a node on at least one view.\n");
            return sb.toString();
        }
        int n = Math.min(unused.size(), MAX_LINES);
        for (int i = 0; i < n; i++) {
            sb.append("- ").append(describeElement(unused.get(i))).append('\n');
        }
        if (unused.size() > MAX_LINES) {
            sb.append("… and ").append(unused.size() - MAX_LINES).append(" more (list truncated).\n");
        }
        return sb.toString();
    }

    private static boolean mentionsUnused(String prompt, String noun) {
        if (prompt == null || prompt.trim().isEmpty()) {
            return false;
        }
        String p = prompt.toLowerCase(Locale.ROOT);
        if (!p.contains(noun)) {
            return false;
        }
        return p.contains("not on any") || p.contains("unused") || p.contains("do not appear")
                || p.contains("never appear") || p.contains("not appear as a connection")
                || p.contains("not appear as a node");
    }

    private static String describeRelationship(IArchimateRelationship rel) {
        String type = rel.eClass() != null ? rel.eClass().getName() : "Relationship";
        String name = rel.getName() != null ? rel.getName() : "";
        String id = rel.getId() != null ? rel.getId() : "";
        String src = describeEnd(rel.getSource());
        String tgt = describeEnd(rel.getTarget());
        String named = name.isEmpty() ? type : type + " \"" + name + "\"";
        return named + " (id=" + id + ") " + src + " -> " + tgt;
    }

    private static String describeElement(IArchimateElement el) {
        String type = el.eClass() != null ? el.eClass().getName() : "Element";
        String name = el.getName() != null ? el.getName() : "";
        String id = el.getId() != null ? el.getId() : "";
        String named = name.isEmpty() ? type : type + " \"" + name + "\"";
        return named + " (id=" + id + ")";
    }

    private static String describeEnd(com.archimatetool.model.IArchimateConcept c) {
        if (c == null) {
            return "(none)";
        }
        String type = c.eClass() != null ? c.eClass().getName() : "Concept";
        String name = c.getName() != null ? c.getName() : "";
        String id = c.getId() != null ? c.getId() : "";
        String named = name.isEmpty() ? type : type + " \"" + name + "\"";
        return named + " (id=" + id + ")";
    }
}
