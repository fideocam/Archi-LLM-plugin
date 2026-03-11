/**
 * System prompt for Ollama that constrains the LLM to Open Group ArchiMate 3.2
 * and defines the JSON output format for validation and import into Archi.
 * The prompt is loaded from system-prompt.txt in the plugin; edit that file to change the prompt without recompiling.
 */
package com.archimatetool.archigpt;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * System prompt and output format for ArchiMate 3.2–compliant LLM responses.
 * Loads from system-prompt.txt when available; otherwise uses built-in default.
 */
@SuppressWarnings("nls")
public final class ArchiMateSystemPrompt {

    private static final String CONFIG_FILE = "system-prompt.txt";

    private ArchiMateSystemPrompt() {}

    /** Built-in default if system-prompt.txt is missing or unreadable (e.g. unit tests outside OSGi). */
    private static final String DEFAULT_SYSTEM_PROMPT = "You are an expert in the Open Group ArchiMate 3.2 specification. "
            + "Use the Open Group ArchiMate XSD schemas (archimate3_Model.xsd and archimate3_Diagram.xsd) as your reference. "
            + "Respond in one of two ways:\n\n"
            + "1) ANALYSIS: For analysis, description, or review, respond with plain text only. "
            + "CRITICAL — minimize hallucinations: You will be given the exact ArchiMate model content as XML. Only describe or refer to elements and relationships that actually appear in that supplied XML. "
            + "Never mention, assume, or infer elements, connections, or relationships that are not explicitly present in the supplied model.\n\n"
            + "2) CHANGES: For changes or additions, respond ONLY with a single JSON object. You will be given the current ArchiMate model as XML in the user message. Use it to avoid duplicates. "
            + "CRITICAL — do not add elements that already exist: Only output elements that do NOT already appear in the supplied model (compare by type and name, or by id). "
            + "For create/generate requests (process, service, component, etc.) respond with multiple related elements and a fragment; include a diagram object only for a new view. "
            + "NEW DIAGRAM: For a new view/diagram include a \"diagram\" object; omit it when adding to an existing view. "
            + "Use ArchiMate ids: id- plus 32 hex (e.g. id-a1b2c3d4e5f67890abcdef1234567890). Optional removeElementIds and removeRelationshipIds arrays for remove requests. "
            + "The JSON must have: {\"elements\":[{\"type\":\"<Type>\",\"name\":\"<name>\",\"id\":\"<id>\"},...],\"relationships\":[{\"type\":\"<Type>\",\"source\":\"<id>\",\"target\":\"<id>\",\"name\":\"<label>\",\"id\":\"<id>\"},...]}. "
            + "If you cannot produce valid JSON, respond with {\"elements\":[],\"relationships\":[],\"error\":\"<reason>\"}.";

    private static volatile String loadedPrompt;

    /**
     * System prompt for the LLM. Loaded once from system-prompt.txt in the plugin bundle; falls back to built-in default if the file is missing.
     */
    public static String getSystemPrompt() {
        if (loadedPrompt != null) {
            return loadedPrompt;
        }
        synchronized (ArchiMateSystemPrompt.class) {
            if (loadedPrompt != null) {
                return loadedPrompt;
            }
            try {
                Bundle bundle = null;
                try {
                    bundle = FrameworkUtil.getBundle(ArchiMateSystemPrompt.class);
                } catch (NoClassDefFoundError e) {
                    // Not in OSGi (e.g. unit tests); use default
                }
                if (bundle != null) {
                    URL in = bundle.getEntry(CONFIG_FILE);
                    if (in != null) {
                        InputStream is = in.openStream();
                        try {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                            try {
                                String content = reader.lines().collect(Collectors.joining("\n")).trim();
                                if (!content.isEmpty()) {
                                    loadedPrompt = content;
                                    return loadedPrompt;
                                }
                            } finally {
                                reader.close();
                            }
                        } finally {
                            is.close();
                        }
                    }
                }
            } catch (Exception | NoClassDefFoundError ignored) {
            }
            loadedPrompt = DEFAULT_SYSTEM_PROMPT;
            return loadedPrompt;
        }
    }

    /** System prompt (loaded from system-prompt.txt at first use; fallback to built-in default). */
    public static final String SYSTEM_PROMPT = getSystemPrompt();

    /** Key for JSON "elements" array. */
    public static final String KEY_ELEMENTS = "elements";
    /** Key for JSON "relationships" array. */
    public static final String KEY_RELATIONSHIPS = "relationships";
    /** Keys for element object: type, name, id. */
    public static final String KEY_TYPE = "type";
    public static final String KEY_NAME = "name";
    public static final String KEY_ID = "id";
    /** Keys for relationship object: type, source, target, name, id. */
    public static final String KEY_SOURCE = "source";
    public static final String KEY_TARGET = "target";
    public static final String KEY_ERROR = "error";
}
