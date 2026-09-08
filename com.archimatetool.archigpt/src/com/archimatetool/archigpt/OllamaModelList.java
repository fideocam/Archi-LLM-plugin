/**
 * Builds the ArchiGPT model dropdown from Ollama {@code /api/tags} without inventing defaults.
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

@SuppressWarnings("nls")
public final class OllamaModelList {

    private OllamaModelList() {}

    /**
     * Unique, case-insensitive sorted names from the server. Does not add {@link OllamaClient#DEFAULT_MODEL}.
     */
    public static List<String> fromServerNames(List<String> names) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (names != null) {
            for (String n : names) {
                if (n != null && !n.trim().isEmpty()) {
                    merged.add(n.trim());
                }
            }
        }
        List<String> items = new ArrayList<>(merged);
        Collections.sort(items, String.CASE_INSENSITIVE_ORDER);
        return items;
    }

    /**
     * Index of {@code name} in a tags list. Prefers an exact match, then {@code name:latest}.
     */
    public static int indexOfModel(List<String> items, String name) {
        if (items == null || name == null || name.isEmpty()) {
            return -1;
        }
        int exact = items.indexOf(name);
        if (exact >= 0) {
            return exact;
        }
        String latest = name + ":latest";
        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            if (item != null && (latest.equalsIgnoreCase(item) || name.equalsIgnoreCase(item))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Model to select after a successful tags refresh. Empty if the server listed none.
     * Never falls back to {@link OllamaClient#DEFAULT_MODEL} unless that tag was on the server.
     */
    public static String selectionAfterRefresh(List<String> serverItems, String previous) {
        List<String> items = serverItems == null ? Collections.<String>emptyList() : serverItems;
        int idx = indexOfModel(items, previous);
        if (idx >= 0) {
            return items.get(idx);
        }
        if (!items.isEmpty()) {
            return items.get(0);
        }
        return "";
    }
}
