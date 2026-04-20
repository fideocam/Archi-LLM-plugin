/**
 * Extract assistant text from provider JSON responses without a full JSON parser.
 */
package com.archimatetool.archigpt;

@SuppressWarnings("nls")
public final class JsonResponseExtractors {

    private JsonResponseExtractors() {}

    /** OpenAI chat.completions: choices[0].message.content */
    public static String openAiAssistantContent(String json) {
        if (json == null) {
            return "";
        }
        int choices = json.indexOf("\"choices\"");
        if (choices < 0) {
            return "";
        }
        int msg = json.indexOf("\"message\"", choices);
        if (msg < 0) {
            return "";
        }
        String[] prefixes = new String[] { "\"content\":\"", "\"content\": \"" };
        for (String pre : prefixes) {
            int ck = json.indexOf(pre, msg);
            if (ck >= 0) {
                return readJsonStringValue(json, ck + pre.length());
            }
        }
        return "";
    }

    /** Anthropic messages: first block text in content array */
    public static String anthropicFirstText(String json) {
        if (json == null) {
            return "";
        }
        int contentArr = json.indexOf("\"content\"");
        if (contentArr < 0) {
            return "";
        }
        String[] prefixes = new String[] { "\"text\":\"", "\"text\": \"" };
        for (String pre : prefixes) {
            int tk = json.indexOf(pre, contentArr);
            if (tk >= 0) {
                return readJsonStringValue(json, tk + pre.length());
            }
        }
        return "";
    }

    /** Gemini generateContent: candidates[0].content.parts[0].text */
    public static String geminiCandidatesText(String json) {
        if (json == null) {
            return "";
        }
        int cand = json.indexOf("\"candidates\"");
        if (cand < 0) {
            return "";
        }
        int parts = json.indexOf("\"parts\"", cand);
        if (parts < 0) {
            return "";
        }
        String[] prefixes = new String[] { "\"text\":\"", "\"text\": \"" };
        for (String pre : prefixes) {
            int tk = json.indexOf(pre, parts);
            if (tk >= 0) {
                return readJsonStringValue(json, tk + pre.length());
            }
        }
        return "";
    }

    /**
     * {@code json[pos]} should be whitespace then {@code "…"} starting the string value (after {@code :} from key).
     */
    private static String readJsonStringValue(String json, int pos) {
        int i = pos;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length()) {
            return "";
        }
        if (json.charAt(i) == '"') {
            i++;
        }
        StringBuilder sb = new StringBuilder();
        for (; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(i + 1);
                i++;
                switch (n) {
                    case '"':
                    case '\\':
                    case '/':
                        sb.append(n);
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (i + 4 < json.length()) {
                            try {
                                int cp = Integer.parseInt(json.substring(i + 1, i + 5), 16);
                                sb.append((char) cp);
                                i += 4;
                            } catch (Exception e) {
                                sb.append('u');
                            }
                        }
                        break;
                    default:
                        sb.append(n);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
