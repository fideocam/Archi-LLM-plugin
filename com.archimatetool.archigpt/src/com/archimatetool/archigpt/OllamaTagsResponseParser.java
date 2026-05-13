/**
 * Parses {@code GET /api/tags} JSON from Ollama for installed model names.
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("nls")
public final class OllamaTagsResponseParser {

    private static final Pattern NAME_FIELD = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private OllamaTagsResponseParser() {}

    /**
     * Extracts model names from Ollama tags response (order preserved, duplicates removed).
     */
    public static List<String> parseModelNames(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = NAME_FIELD.matcher(json);
        while (m.find()) {
            String name = m.group(1);
            if (name != null && !name.isEmpty()) {
                seen.add(name);
            }
        }
        return new ArrayList<>(seen);
    }
}
