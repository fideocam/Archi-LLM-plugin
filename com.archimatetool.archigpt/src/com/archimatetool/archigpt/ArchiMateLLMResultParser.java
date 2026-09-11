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

    private static final Pattern ERROR_FIELD = Pattern.compile("\"error\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    /**
     * Extract JSON from markdown code block if present, then parse into ArchiMateLLMResult.
     */
    public static ArchiMateLLMResult parse(String rawResponse) {
        String json = extractJson(rawResponse);
        ArchiMateLLMResult result = new ArchiMateLLMResult();

        Matcher errMatcher = ERROR_FIELD.matcher(json);
        if (errMatcher.find()) {
            result.setError(unescapeJson(errMatcher.group(1).trim()));
        }

        String elementsStr = jsonArrayBody(json, "elements");
        if (elementsStr != null) {
            parseElements(elementsStr, result);
        }

        String relsStr = jsonArrayBody(json, "relationships");
        if (relsStr != null) {
            parseRelationships(relsStr, result);
        }

        // Find the "diagram" key (not "diagram" inside a value like "name":"Some diagram")
        Pattern diagramKeyPattern = Pattern.compile("\"diagram\"\\s*:\\s*\\{");
        Matcher diagramKeyMatcher = diagramKeyPattern.matcher(json);
        if (diagramKeyMatcher.find()) {
            int objStart = diagramKeyMatcher.end() - 1; // position of the "{"
            int objEnd = findMatchingBracket(json, objStart);
            if (objEnd > objStart) {
                String diagramStr = json.substring(objStart, objEnd + 1);
                parseDiagram(diagramStr, result);
            }
        }

        parseStringArray(json, "\"removeElementIds\"", result.getRemoveElementIds());
        parseStringArray(json, "\"removeRelationshipIds\"", result.getRemoveRelationshipIds());
        parseStringArray(json, "\"removeDiagramNames\"", result.getRemoveDiagramNames());
        parseStringArray(json, "\"removeElementFromDiagramIds\"", result.getRemoveElementFromDiagramIds());
        parseStringArray(json, "\"removeRelationshipFromDiagramIds\"", result.getRemoveRelationshipFromDiagramIds());

        return result;
    }

    private static String jsonArrayBody(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[").matcher(json);
        if (!m.find()) {
            return null;
        }
        int arrayStart = m.end() - 1;
        int arrayEnd = findMatchingBracket(json, arrayStart);
        if (arrayEnd <= arrayStart) {
            return null;
        }
        return json.substring(arrayStart + 1, arrayEnd);
    }

    private static void parseStringArray(String json, String key, List<String> out) {
        int start = json.indexOf(key);
        if (start < 0) return;
        int arrayStart = json.indexOf("[", start);
        int arrayEnd = findMatchingBracket(json, arrayStart);
        if (arrayEnd <= arrayStart) return;
        String arr = json.substring(arrayStart + 1, arrayEnd);
        Pattern idPattern = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = idPattern.matcher(arr);
        while (m.find()) {
            String id = unescapeJson(m.group(1).trim());
            if (!id.isEmpty()) out.add(id);
        }
    }

    private static void parseDiagram(String diagramStr, ArchiMateLLMResult result) {
        ArchiMateLLMResult.DiagramSpec diagram = new ArchiMateLLMResult.DiagramSpec();
        Pattern nameP = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");
        Pattern viewpointP = Pattern.compile("\"viewpoint\"\\s*:\\s*\"([^\"]*)\"");
        Matcher nameM = nameP.matcher(diagramStr);
        if (nameM.find()) diagram.setName(unescapeJson(nameM.group(1).trim()));
        Matcher vpM = viewpointP.matcher(diagramStr);
        if (vpM.find()) diagram.setViewpoint(unescapeJson(vpM.group(1).trim()));

        int nodesStart = diagramStr.indexOf("\"nodes\"");
        if (nodesStart >= 0) {
            int arrayStart = diagramStr.indexOf("[", nodesStart);
            int arrayEnd = findMatchingBracket(diagramStr, arrayStart);
            if (arrayEnd > arrayStart) {
                String nodesStr = diagramStr.substring(arrayStart + 1, arrayEnd);
                for (int[] r : findObjectRanges(nodesStr)) {
                    String block = nodesStr.substring(r[0], r[1]);
                    ArchiMateLLMResult.DiagramNodeSpec node = parseDiagramNode(block);
                    if (node != null && node.getElementId() != null) diagram.getNodes().add(node);
                }
            }
        }

        int connStart = diagramStr.indexOf("\"connections\"");
        if (connStart >= 0) {
            int arrayStart = diagramStr.indexOf("[", connStart);
            int arrayEnd = findMatchingBracket(diagramStr, arrayStart);
            if (arrayEnd > arrayStart) {
                String connStr = diagramStr.substring(arrayStart + 1, arrayEnd);
                for (int[] r : findObjectRanges(connStr)) {
                    String block = connStr.substring(r[0], r[1]);
                    ArchiMateLLMResult.DiagramConnectionSpec conn = parseDiagramConnection(block);
                    if (conn != null) diagram.getConnections().add(conn);
                }
            }
        }

        result.setDiagram(diagram);
    }

    private static ArchiMateLLMResult.DiagramNodeSpec parseDiagramNode(String block) {
        Pattern p = Pattern.compile("\"elementId\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(block);
        if (!m.find()) return null;
        ArchiMateLLMResult.DiagramNodeSpec node = new ArchiMateLLMResult.DiagramNodeSpec();
        node.setElementId(m.group(1).trim());
        node.setX(parseInt(block, "x", 0));
        node.setY(parseInt(block, "y", 0));
        node.setWidth(parseInt(block, "width", 120));
        node.setHeight(parseInt(block, "height", 55));
        return node;
    }

    private static ArchiMateLLMResult.DiagramConnectionSpec parseDiagramConnection(String block) {
        Pattern src = Pattern.compile("\"sourceElementId\"\\s*:\\s*\"([^\"]*)\"");
        Pattern tgt = Pattern.compile("\"targetElementId\"\\s*:\\s*\"([^\"]*)\"");
        Pattern rel = Pattern.compile("\"relationshipId\"\\s*:\\s*\"([^\"]*)\"");
        Matcher srcM = src.matcher(block);
        Matcher tgtM = tgt.matcher(block);
        if (!srcM.find() || !tgtM.find()) return null;
        ArchiMateLLMResult.DiagramConnectionSpec c = new ArchiMateLLMResult.DiagramConnectionSpec();
        c.setSourceElementId(srcM.group(1).trim());
        c.setTargetElementId(tgtM.group(1).trim());
        Matcher relM = rel.matcher(block);
        if (relM.find()) c.setRelationshipId(relM.group(1).trim());
        return c;
    }

    private static int parseInt(String block, String key, int defaultValue) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(block);
        return m.find() ? Integer.parseInt(m.group(1).trim()) : defaultValue;
    }

    private static String extractJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                    continue;
                }
                if (c == '\\') {
                    escape = true;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c != '{') {
                continue;
            }
            int end = findMatchingBracket(s, i);
            if (end <= i) continue;
            String candidate = s.substring(i, end + 1);
            // Must be the CHANGES payload (add, new view, or remove)
            if (candidate.contains("\"elements\"") || candidate.contains("\"diagram\"")
                    || candidate.contains("\"removeElementIds\"") || candidate.contains("\"removeRelationshipIds\"")
                    || candidate.contains("\"removeDiagramNames\"")
                    || candidate.contains("\"removeElementFromDiagramIds\"")
                    || candidate.contains("\"removeRelationshipFromDiagramIds\"")) {
                return candidate;
            }
        }
        return s;
    }

    private static void parseElements(String elementsStr, ArchiMateLLMResult result) {
        List<int[]> ranges = findObjectRanges(elementsStr);
        for (int[] r : ranges) {
            String block = elementsStr.substring(r[0], r[1]);
            String type = jsonStringField(block, "type");
            String id = jsonStringField(block, "id");
            if (type == null || type.isEmpty() || id == null || id.isEmpty()) {
                continue;
            }
            ArchiMateLLMResult.ElementSpec e = new ArchiMateLLMResult.ElementSpec();
            e.setType(type.trim());
            String name = jsonStringField(block, "name");
            e.setName(name);
            e.setId(id.trim());
            e.setDocumentation(jsonStringField(block, "documentation"));
            result.getElements().add(e);
        }
    }

    private static void parseRelationships(String relsStr, ArchiMateLLMResult result) {
        List<int[]> ranges = findObjectRanges(relsStr);
        for (int[] r : ranges) {
            String block = relsStr.substring(r[0], r[1]);
            String type = jsonStringField(block, "type");
            String source = jsonStringField(block, "source");
            String target = jsonStringField(block, "target");
            if (type == null || type.isEmpty() || source == null || source.isEmpty()
                    || target == null || target.isEmpty()) {
                continue;
            }
            ArchiMateLLMResult.RelationshipSpec rel = new ArchiMateLLMResult.RelationshipSpec();
            rel.setType(type.trim());
            rel.setSource(source.trim());
            rel.setTarget(target.trim());
            String name = jsonStringField(block, "name");
            rel.setName(name);
            String id = jsonStringField(block, "id");
            rel.setId(id != null && !id.trim().isEmpty() ? id.trim() : null);
            rel.setDocumentation(jsonStringField(block, "documentation"));
            result.getRelationships().add(rel);
        }
    }

    /** Reads a JSON string field regardless of key order or extra properties. */
    static String jsonStringField(String block, String key) {
        if (block == null || key == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .matcher(block);
        return m.find() ? unescapeJson(m.group(1)) : null;
    }

    private static List<int[]> findObjectRanges(String str) {
        List<int[]> list = new ArrayList<>();
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                int end = findMatchingBracket(str, i);
                if (end > i) {
                    list.add(new int[] { i, end + 1 });
                    i = end;
                }
            }
        }
        return list;
    }

    /**
     * Index of the matching closer for {@code s[openIndex]} ({@code {/[}), ignoring brackets inside JSON strings.
     */
    static int findMatchingBracket(String s, int openIndex) {
        if (s == null || openIndex < 0 || openIndex >= s.length()) {
            return -1;
        }
        int depth = 1;
        boolean inString = false;
        boolean escape = false;
        for (int i = openIndex + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                    continue;
                }
                if (c == '\\') {
                    escape = true;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String unescapeJson(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                out.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case '"':
                    out.append('"');
                    break;
                case '\\':
                    out.append('\\');
                    break;
                case '/':
                    out.append('/');
                    break;
                case 'u':
                    if (i + 4 < s.length()) {
                        try {
                            out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                            break;
                        } catch (NumberFormatException ignored) {
                            // fall through and keep the 'u'
                        }
                    }
                    out.append('u');
                    break;
                default:
                    out.append(n);
                    break;
            }
        }
        return out.toString();
    }
}
