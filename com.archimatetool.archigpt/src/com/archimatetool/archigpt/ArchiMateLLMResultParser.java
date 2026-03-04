/**
 * Parses LLM response text into ArchiMateLLMResult (elements and relationships).
 * Expects JSON matching the schema defined in ArchiMateSystemPrompt.
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("nls")
public final class ArchiMateLLMResultParser {

    private static final Pattern ELEMENT_BLOCK = Pattern.compile(
            "\"type\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"id\"\\s*:\\s*\"([^\"]*)\"",
            Pattern.DOTALL);
    private static final Pattern RELATIONSHIP_BLOCK = Pattern.compile(
            "\"type\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"source\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"target\"\\s*:\\s*\"([^\"]*)\"",
            Pattern.DOTALL);
    private static final Pattern REL_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern REL_ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern ERROR_FIELD = Pattern.compile("\"error\"\\s*:\\s*\"([^\"]*)\"");

    /**
     * Extract JSON from markdown code block if present, then parse into ArchiMateLLMResult.
     */
    public static ArchiMateLLMResult parse(String rawResponse) {
        String json = extractJson(rawResponse);
        ArchiMateLLMResult result = new ArchiMateLLMResult();

        Matcher errMatcher = ERROR_FIELD.matcher(json);
        if (errMatcher.find()) {
            result.setError(errMatcher.group(1).trim());
        }

        int elementsStart = json.indexOf("\"elements\"");
        if (elementsStart >= 0) {
            int arrayStart = json.indexOf("[", elementsStart);
            int arrayEnd = findMatchingBracket(json, arrayStart);
            if (arrayEnd > arrayStart) {
                String elementsStr = json.substring(arrayStart + 1, arrayEnd);
                parseElements(elementsStr, result);
            }
        }

        int relsStart = json.indexOf("\"relationships\"");
        if (relsStart >= 0) {
            int arrayStart = json.indexOf("[", relsStart);
            int arrayEnd = findMatchingBracket(json, arrayStart);
            if (arrayEnd > arrayStart) {
                String relsStr = json.substring(arrayStart + 1, arrayEnd);
                parseRelationships(relsStr, result);
            }
        }

        return result;
    }

    private static String extractJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        int start = s.indexOf('{');
        if (start >= 0) {
            int end = findMatchingBracket(s, start);
            if (end > start) return s.substring(start, end + 1);
        }
        return s;
    }

    private static int findMatchingBracket(String s, int openIndex) {
        int depth = 1;
        for (int i = openIndex + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static void parseElements(String elementsStr, ArchiMateLLMResult result) {
        List<int[]> ranges = findObjectRanges(elementsStr);
        for (int[] r : ranges) {
            String block = elementsStr.substring(r[0], r[1]);
            Matcher m = ELEMENT_BLOCK.matcher(block);
            if (m.find()) {
                ArchiMateLLMResult.ElementSpec e = new ArchiMateLLMResult.ElementSpec();
                e.setType(m.group(1).trim());
                e.setName(unescapeJson(m.group(2)));
                e.setId(m.group(3).trim());
                result.getElements().add(e);
            }
        }
    }

    private static void parseRelationships(String relsStr, ArchiMateLLMResult result) {
        List<int[]> ranges = findObjectRanges(relsStr);
        for (int[] r : ranges) {
            String block = relsStr.substring(r[0], r[1]);
            Matcher m = RELATIONSHIP_BLOCK.matcher(block);
            if (m.find()) {
                ArchiMateLLMResult.RelationshipSpec rel = new ArchiMateLLMResult.RelationshipSpec();
                rel.setType(m.group(1).trim());
                rel.setSource(m.group(2).trim());
                rel.setTarget(m.group(3).trim());
                Matcher nameM = REL_NAME.matcher(block);
                rel.setName(nameM.find() ? unescapeJson(nameM.group(1)) : "");
                Matcher idM = REL_ID.matcher(block);
                rel.setId(idM.find() ? idM.group(1).trim() : null);
                result.getRelationships().add(rel);
            }
        }
    }

    private static List<int[]> findObjectRanges(String str) {
        List<int[]> list = new ArrayList<>();
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '{') {
                int end = findMatchingBracket(str, i);
                if (end > i) {
                    list.add(new int[] { i, end + 1 });
                    i = end;
                }
            }
        }
        return list;
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\"", "\"");
    }
}
