/**
 * Plain-text summary of an ArchiMate model for LLM context (counts, structure).
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IFolder;

@SuppressWarnings("nls")
public final class ModelContextDigest {

    private static final int MAX_DIAGRAM_NAMES = 35;
    private static final int MAX_TYPE_LINES = 18;

    private ModelContextDigest() {}

    /**
     * Human-readable digest: diagram count and names, element/relationship totals, per top-level folder (except Views),
     * and the most common ArchiMate types.
     */
    public static String toPlainText(IArchimateModel model) {
        if (model == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String mn = model.getName() != null ? model.getName() : "";
        sb.append("MODEL DIGEST (whole model — each XML excerpt below is only one part)\n");
        sb.append("Model name: \"").append(mn).append("\"\n");

        List<IArchimateDiagramModel> diagrams = ModelContextToXml.getAllDiagramModels(model);
        sb.append("Diagrams/views: ").append(diagrams.size());
        appendDiagramNameList(sb, diagrams);
        sb.append("\n");

        int[] er = countElementsAndRelationships(model);
        sb.append("ArchiMate elements (excluding relationships): ").append(er[0]).append("\n");
        sb.append("Relationships: ").append(er[1]).append("\n");

        Map<String, Integer> elTypes = new TreeMap<>();
        Map<String, Integer> relTypes = new TreeMap<>();
        collectTypeCounts(model, elTypes, relTypes);
        appendTopTypes(sb, "Element types (sample)", elTypes);
        appendTopTypes(sb, "Relationship types (sample)", relTypes);

        sb.append("By top-level folder (elements / relationships in that subtree):\n");
        List<IFolder> tops = model.getFolders();
        if (tops == null || tops.isEmpty()) {
            sb.append("  (no folders)\n");
        } else {
            for (IFolder f : tops) {
                if (ModelContextToXml.isDiagramsContainerFolder(f)) {
                    continue;
                }
                int[] c = countInFolderTree(f);
                sb.append("  - ").append(folderLabel(f)).append(": ").append(c[0]).append(" elements, ").append(c[1])
                        .append(" relationships\n");
            }
        }
        return sb.toString();
    }

    private static void appendDiagramNameList(StringBuilder sb, List<IArchimateDiagramModel> diagrams) {
        if (diagrams == null || diagrams.isEmpty()) {
            return;
        }
        sb.append("\n  Names: ");
        int n = Math.min(diagrams.size(), MAX_DIAGRAM_NAMES);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append("; ");
            }
            String nm = diagrams.get(i).getName();
            sb.append(nm != null && !nm.isEmpty() ? nm : "(unnamed)");
        }
        if (diagrams.size() > n) {
            sb.append(" … (+").append(diagrams.size() - n).append(" more)");
        }
    }

    private static int[] countElementsAndRelationships(IArchimateModel model) {
        int el = 0;
        int rel = 0;
        Iterator<EObject> it = model.eAllContents();
        while (it.hasNext()) {
            EObject o = it.next();
            if (o instanceof IArchimateRelationship) {
                rel++;
            } else if (o instanceof IArchimateElement) {
                el++;
            }
        }
        return new int[] { el, rel };
    }

    private static void collectTypeCounts(IArchimateModel model, Map<String, Integer> elTypes,
            Map<String, Integer> relTypes) {
        Iterator<EObject> it = model.eAllContents();
        while (it.hasNext()) {
            EObject o = it.next();
            if (o instanceof IArchimateRelationship) {
                String t = o.eClass().getName();
                relTypes.merge(t, 1, Integer::sum);
            } else if (o instanceof IArchimateElement) {
                String t = o.eClass().getName();
                elTypes.merge(t, 1, Integer::sum);
            }
        }
    }

    private static void appendTopTypes(StringBuilder sb, String heading, Map<String, Integer> types) {
        sb.append(heading).append(":\n");
        if (types.isEmpty()) {
            sb.append("  (none)\n");
            return;
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(types.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed());
        int lim = Math.min(entries.size(), MAX_TYPE_LINES);
        for (int i = 0; i < lim; i++) {
            Map.Entry<String, Integer> e = entries.get(i);
            sb.append("  - ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }
        if (entries.size() > lim) {
            sb.append("  … (").append(entries.size() - lim).append(" more types)\n");
        }
    }

    private static int[] countInFolderTree(IFolder folder) {
        int el = 0;
        int rel = 0;
        if (folder.getElements() != null) {
            for (EObject e : folder.getElements()) {
                if (e instanceof IArchimateRelationship) {
                    rel++;
                } else if (e instanceof IArchimateElement) {
                    el++;
                }
            }
        }
        if (folder.getFolders() != null) {
            for (IFolder sub : folder.getFolders()) {
                int[] c = countInFolderTree(sub);
                el += c[0];
                rel += c[1];
            }
        }
        return new int[] { el, rel };
    }

    static String folderLabel(IFolder folder) {
        if (folder == null) {
            return "(folder)";
        }
        String n = folder.getName() != null ? folder.getName() : "";
        String t = folder.getType() != null ? folder.getType().getName() : "";
        return "\"" + n + "\" (" + t + ")";
    }

    /** For tests: top type names sorted by count descending. */
    static List<String> topElementTypeNamesForTest(IArchimateModel model, int limit) {
        Map<String, Integer> elTypes = new TreeMap<>();
        collectTypeCounts(model, elTypes, new TreeMap<>());
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(elTypes.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed());
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            out.add(entries.get(i).getKey());
        }
        return Collections.unmodifiableList(out);
    }
}
