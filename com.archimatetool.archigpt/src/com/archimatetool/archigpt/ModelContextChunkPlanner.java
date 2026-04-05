/**
 * Builds analysis-sized XML chunks by folder subtree and diagram batches instead of arbitrary string splits.
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;

@SuppressWarnings("nls")
public final class ModelContextChunkPlanner {

    /** One request payload: human-readable scope title plus valid Open Exchange XML fragment. */
    public static final class PlannedChunk {
        public final String title;
        public final String xml;

        public PlannedChunk(String title, String xml) {
            this.title = title != null ? title : "";
            this.xml = xml != null ? xml : "";
        }
    }

    private ModelContextChunkPlanner() {}

    /**
     * Plan chunks: each top-level model folder (except the Views/diagrams container) as its own subtree or finer splits;
     * then diagram batches. Every {@code xml} is at most {@code maxCharsPerChunk} (after {@link ModelXmlChunker} splits
     * when a single folder or view is still too large).
     */
    public static List<PlannedChunk> plan(IArchimateModel model, int maxCharsPerChunk,
            List<IFolder> priorityFolders, List<IArchimateDiagramModel> priorityDiagrams) {
        if (model == null || maxCharsPerChunk < 512) {
            return Collections.emptyList();
        }
        List<PlannedChunk> out = new ArrayList<>();
        List<IFolder> tops = ModelContextToXml.orderedTopFolders(model, priorityFolders);
        for (IFolder f : tops) {
            if (ModelContextToXml.isDiagramsContainerFolder(f)) {
                continue;
            }
            out.addAll(chunkFolderSubtree(model, f, maxCharsPerChunk));
        }
        List<IArchimateDiagramModel> dms = ModelContextToXml.orderedDiagramsForModel(model, priorityDiagrams);
        out.addAll(packDiagrams(model, dms, maxCharsPerChunk));
        return out;
    }

    private static List<PlannedChunk> chunkFolderSubtree(IArchimateModel model, IFolder folder, int maxChars) {
        List<PlannedChunk> result = new ArrayList<>();
        String subtree = ModelContextToXml.toXmlFolderSubtree(model, folder);
        String title = "Folder " + ModelContextDigest.folderLabel(folder);
        if (subtree.length() <= maxChars) {
            result.add(new PlannedChunk(title, subtree));
            return result;
        }
        String direct = ModelContextToXml.toXmlFolderDirectMembersOnly(model, folder);
        if (!direct.isEmpty()) {
            if (direct.length() <= maxChars) {
                result.add(new PlannedChunk(title + " — elements stored directly in this folder (subfolders follow separately)",
                        direct));
            } else {
                int p = 1;
                for (String part : ModelXmlChunker.split(direct, maxChars)) {
                    result.add(new PlannedChunk(title + " — direct members (part " + p + ")", part));
                    p++;
                }
            }
        }
        if (folder.getFolders() != null) {
            for (IFolder child : folder.getFolders()) {
                result.addAll(chunkFolderSubtree(model, child, maxChars));
            }
        }
        if (result.isEmpty()) {
            int p = 1;
            for (String part : ModelXmlChunker.split(subtree, maxChars)) {
                result.add(new PlannedChunk(title + (part.length() < subtree.length() ? " (part " + p + ")" : ""), part));
                p++;
            }
        }
        return result;
    }

    private static List<PlannedChunk> packDiagrams(IArchimateModel model, List<IArchimateDiagramModel> dms, int maxChars) {
        if (dms == null || dms.isEmpty()) {
            return Collections.emptyList();
        }
        List<PlannedChunk> out = new ArrayList<>();
        int i = 0;
        while (i < dms.size()) {
            List<IArchimateDiagramModel> batch = new ArrayList<>();
            int j = i;
            while (j < dms.size()) {
                batch.add(dms.get(j));
                String xml = ModelContextToXml.toXmlDiagramList(model, batch);
                if (xml.length() > maxChars) {
                    batch.remove(batch.size() - 1);
                    break;
                }
                j++;
            }
            if (batch.isEmpty()) {
                IArchimateDiagramModel dm = dms.get(i);
                String one = ModelContextToXml.toXmlDiagramList(model, Collections.singletonList(dm));
                List<String> splits = ModelXmlChunker.split(one, maxChars);
                for (int s = 0; s < splits.size(); s++) {
                    String t = diagramTitle(dm);
                    String partLabel = splits.size() > 1 ? t + " (part " + (s + 1) + "/" + splits.size() + ")" : t;
                    out.add(new PlannedChunk(partLabel, splits.get(s)));
                }
                i++;
            } else {
                String xml = ModelContextToXml.toXmlDiagramList(model, batch);
                out.add(new PlannedChunk(viewsBatchTitle(batch), xml));
                i += batch.size();
            }
        }
        return out;
    }

    private static String diagramTitle(IArchimateDiagramModel dm) {
        String n = dm != null && dm.getName() != null ? dm.getName() : "";
        return "View: " + (n.isEmpty() ? "(unnamed)" : n);
    }

    private static String viewsBatchTitle(List<IArchimateDiagramModel> batch) {
        if (batch.size() == 1) {
            return diagramTitle(batch.get(0));
        }
        StringBuilder sb = new StringBuilder("Views: ");
        int max = 5;
        for (int k = 0; k < Math.min(batch.size(), max); k++) {
            if (k > 0) {
                sb.append(", ");
            }
            String n = batch.get(k).getName();
            sb.append(n != null && !n.isEmpty() ? n : "(unnamed)");
        }
        if (batch.size() > max) {
            sb.append(", … (+").append(batch.size() - max).append(" more)");
        }
        return sb.toString();
    }
}
