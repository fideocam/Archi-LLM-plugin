package com.archimatetool.archigpt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Integration tests that call a real Ollama instance. Skipped when Ollama is not reachable
 * (e.g. not running or OLLAMA_INTEGRATION not set). Run with Ollama running and optionally
 * set OLLAMA_INTEGRATION=1 to force run (or rely on checkConnection() only).
 *
 * Tests: CHANGES (add element) → valid JSON and passes validator; ANALYSIS (describe) → plain text;
 * EXPORT (return model) → well-formed XML.
 */
@SuppressWarnings("nls")
public class OllamaIntegrationTest {

    /** Minimal Open Exchange model XML: two folders and one view. Used as fixed payload for all prompts. */
    private static final String MODEL_XML = "<?xml version=\"1.0\"?>\n"
            + "<model xmlns=\"http://www.opengroup.org/xsd/archimate/3.0/\">\n"
            + "<archimateModel name=\"IntegrationTest\">\n"
            + "  <folder name=\"Business\"/>\n"
            + "  <folder name=\"Application\"/>\n"
            + "  <viewsAndDiagrams><view name=\"View1\" id=\"id-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"/></viewsAndDiagrams>\n"
            + "</archimateModel>\n"
            + "</model>";

    private static final String PROMPT_CHANGES_ADD = "Add a BusinessActor named IntegrationTestUser.";
    private static final String PROMPT_ANALYSIS = "Describe this model in one sentence.";
    private static final String PROMPT_EXPORT = "Return the model as Open Exchange XML.";

    private static boolean ollamaAvailable;
    private static boolean archiAvailable;

    @Before
    public void assumeOllamaReachable() {
        if (!ollamaAvailable) {
            OllamaClient client = new OllamaClient();
            ollamaAvailable = client.checkConnection();
        }
        org.junit.Assume.assumeTrue(
                "Ollama not reachable at " + OllamaClient.DEFAULT_BASE_URL + ". Start Ollama (e.g. ollama serve) to run integration tests.",
                ollamaAvailable);
    }

    private static boolean hasArchiModel() {
        if (archiAvailable) return true;
        try {
            Class.forName("com.archimatetool.model.IArchimatePackage");
            archiAvailable = true;
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private String buildUserMessage(String prompt) {
        return UserMessageBuilder.buildUserMessage("", MODEL_XML, prompt);
    }

    /**
     * CHANGES: "Add a BusinessActor" should yield JSON that parses and (when Archi on classpath) passes validator.
     */
    @Test
    public void changesAdd_returnsValidJsonAndPassesValidator() throws Exception {
        String userMessage = buildUserMessage(PROMPT_CHANGES_ADD);
        OllamaClient client = new OllamaClient();
        String response = client.generateWithSystemPrompt(ArchiMateSystemPrompt.SYSTEM_PROMPT, userMessage);

        assertNotNull("LLM response should not be null", response);
        assertFalse("LLM response should not be empty", response.trim().isEmpty());

        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(response);
        assertTrue("CHANGES prompt should yield at least one element or relationship: " + response,
                !parsed.getElements().isEmpty() || !parsed.getRelationships().isEmpty());

        if (hasArchiModel()) {
            List<String> errors = ArchiMateSchemaValidator.validate(parsed);
            assertTrue("Parsed result should pass schema validation: " + errors, errors.isEmpty());
        }
    }

    /**
     * ANALYSIS: "Describe this model" should yield plain text, not CHANGES JSON (no import data).
     */
    @Test
    public void analysisDescribe_returnsPlainTextNotJson() throws Exception {
        String userMessage = buildUserMessage(PROMPT_ANALYSIS);
        OllamaClient client = new OllamaClient();
        String response = client.generateWithSystemPrompt(ArchiMateSystemPrompt.SYSTEM_PROMPT, userMessage);

        assertNotNull("LLM response should not be null", response);
        assertFalse("LLM response should not be empty", response.trim().isEmpty());

        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(response);
        assertTrue("ANALYSIS prompt should not return importable elements: " + response, parsed.getElements().isEmpty());
        assertTrue("ANALYSIS prompt should not return importable relationships: " + response, parsed.getRelationships().isEmpty());
        assertTrue("ANALYSIS prompt should not return a diagram: " + response,
                parsed.getDiagram() == null || parsed.getDiagram().getName() == null || parsed.getDiagram().getName().isEmpty());
    }

    /**
     * EXPORT: "Return the model as XML" should yield well-formed XML (parseable, root element present).
     * The LLM may wrap XML in markdown or add leading text; we extract the XML before parsing.
     */
    @Test
    public void exportModel_returnsWellFormedXml() throws Exception {
        String userMessage = buildUserMessage(PROMPT_EXPORT);
        OllamaClient client = new OllamaClient();
        String response = client.generateWithSystemPrompt(ArchiMateSystemPrompt.SYSTEM_PROMPT, userMessage);

        assertNotNull("LLM response should not be null", response);
        assertFalse("LLM response should not be empty", response.trim().isEmpty());

        String xml = extractXmlFromResponse(response);
        assertFalse("Response should contain XML (<?xml or <model): " + response.substring(0, Math.min(200, response.length())),
                xml.isEmpty());
        xml = xml.replace("\uFEFF", "").trim();

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        assertNotNull("Parsed document should not be null", doc);
        assertNotNull("Document should have a root element", doc.getDocumentElement());
        String rootName = doc.getDocumentElement().getLocalName() != null ? doc.getDocumentElement().getLocalName() : doc.getDocumentElement().getTagName();
        assertTrue("Root element name should be 'model': " + rootName, "model".equalsIgnoreCase(rootName));
    }

    /** Extract XML from response: strip markdown code fences, BOM, and any leading prose before <?xml or <model. */
    private static String extractXmlFromResponse(String response) {
        String s = response.trim();
        if (s.startsWith("\uFEFF")) s = s.substring(1);
        s = unescapeXmlInResponse(s);
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            if (start != -1) s = s.substring(start + 1);
            int end = s.indexOf("```");
            if (end != -1) s = s.substring(0, end);
        }
        s = s.trim();
        int xmlStart = s.indexOf("<?xml");
        if (xmlStart == -1) xmlStart = s.indexOf("<model");
        if (xmlStart >= 0) s = s.substring(xmlStart).trim();
        else s = "";
        return s;
    }

    /** Unescape common escapes so we can find <?xml or <model (LLM may return \\u003c or &lt;). */
    private static String unescapeXmlInResponse(String s) {
        if (s == null) return s;
        return s.replace("\\u003c", "<").replace("\\u003e", ">")
                .replace("&lt;", "<").replace("&gt;", ">");
    }
}
