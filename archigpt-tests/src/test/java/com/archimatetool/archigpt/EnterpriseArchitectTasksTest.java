package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * Ten typical enterprise-architect tasks on ArchiMate models: analyzing, creating elements and
 * relationships, removing concepts, and defining a new view. Each test checks how the plugin
 * classifies the prompt and shapes the HTTP payload; change tasks also check representative LLM
 * JSON parses and (when {@code IArchimatePackage} is on the classpath) passes
 * {@link ArchiMateSchemaValidator}.
 *
 * <p>Complements {@link ArchiMateAnalysisUseCaseTest}, which focuses on eight analysis questions
 * (Finnish/English) and plain-text handling.</p>
 */
@SuppressWarnings("nls")
public class EnterpriseArchitectTasksTest {

    private static final boolean ARCHI_AVAILABLE = hasArchiModel();

    private static boolean hasArchiModel() {
        try {
            Class.forName("com.archimatetool.model.IArchimatePackage");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static final String MINIMAL_MODEL_XML = "<?xml version=\"1.0\"?>\n<model xmlns=\"http://www.opengroup.org/xsd/archimate/3.0/\">\n"
            + "<archimateModel name=\"EA Task Test\">\n"
            + "  <folder name=\"Business\"/>\n"
            + "  <folder name=\"Application\"/>\n"
            + "  <viewsAndDiagrams><view name=\"Landscape\" id=\"id-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"/></viewsAndDiagrams>\n"
            + "</archimateModel>\n</model>";

    private static final String E_APP = "id-a1b2c3d4e5f67890abcdef1234567890";
    private static final String E_SVC = "id-b2c3d4e5f67890abcdef1234567890ab";
    private static final String E_ACTOR = "id-c3d4e5f67890abcdef1234567890abcd";
    private static final String R_SERV = "id-d4e5f67890abcdef1234567890abcdef";

    private static void assertUserMessageWellFormed(String taskLabel, String prompt) {
        String userMessage = UserMessageBuilder.buildUserMessage("", MINIMAL_MODEL_XML, prompt);
        assertTrue(taskLabel + ": delimiter", userMessage.contains("--- END OF MODEL ---"));
        assertTrue(taskLabel + ": user request section", userMessage.contains("User request:"));
        assertTrue(taskLabel + ": prompt text", userMessage.contains(prompt.trim()));
        assertTrue(taskLabel + ": model context", userMessage.contains("EA Task Test"));
    }

    private static void assertSchemaValidIfArchiPresent(String taskLabel, ArchiMateLLMResult result) {
        if (!ARCHI_AVAILABLE) {
            return;
        }
        List<String> errors = ArchiMateSchemaValidator.validate(result);
        assertTrue(taskLabel + ": " + errors, errors.isEmpty());
    }

    // --- 1–3: Analysis (plain text expected from LLM; no CHANGES JSON) ---

    @Test
    public void task01_stakeholderSummary_classifiedAsAnalysis() {
        String prompt = "Summarize this architecture for executive stakeholders in five bullet points.";
        assertTrue(AnalysisPromptIntent.likelyAnalysisOnly(prompt));
        assertFalse(DiagramCreationIntent.userAskedForBrandNewView(prompt));
        assertUserMessageWellFormed("task01", prompt);
    }

    @Test
    public void task02_crossLayerDependencies_classifiedAsAnalysis() {
        String prompt = "Which application components implement business processes shown in this model?";
        assertTrue(AnalysisPromptIntent.likelyAnalysisOnly(prompt));
        assertFalse(DiagramCreationIntent.userAskedForBrandNewView(prompt));
        assertUserMessageWellFormed("task02", prompt);
    }

    @Test
    public void task03_complianceAndRisk_classifiedAsAnalysis() {
        String prompt = "List potential single points of failure or missing documentation in this landscape.";
        assertTrue(AnalysisPromptIntent.likelyAnalysisOnly(prompt));
        assertFalse(DiagramCreationIntent.userAskedForBrandNewView(prompt));
        assertUserMessageWellFormed("task03", prompt);
    }

    // --- 4–5: Create structure (CHANGES JSON) ---

    @Test
    public void task04_addBusinessActor_classifiedAsChanges_jsonParsesAndValidates() {
        String prompt = "Add a BusinessActor named Enterprise Architect.";
        assertFalse(AnalysisPromptIntent.likelyAnalysisOnly(prompt));
        assertFalse(DiagramCreationIntent.userAskedForBrandNewView(prompt));
        assertUserMessageWellFormed("task04", prompt);

        String json = "{\"elements\":[{\"type\":\"BusinessActor\",\"name\":\"Enterprise Architect\",\"id\":\"" + E_ACTOR
                + "\"}],\"relationships\":[]}";
        ArchiMateLLMResult r = ArchiMateLLMResultParser.parse(json);
        assertEquals(1, r.getElements().size());
        assertEquals("BusinessActor", r.getElements().get(0).getType());
        assertSchemaValidIfArchiPresent("task04", r);
    }

    @Test
    public void task05_addApplicationAndServingRelationship_classifiedAsChanges_jsonParsesAndValidates() {
        String prompt = "Create an ApplicationComponent CRM Portal that serves the BusinessService Customer Self-Service.";
        assertFalse(AnalysisPromptIntent.likelyAnalysisOnly(prompt));
        assertFalse(DiagramCreationIntent.userAskedForBrandNewView(prompt));
        assertUserMessageWellFormed("task05", prompt);

        String json = "{\"elements\":["
                + "{\"type\":\"ApplicationComponent\",\"name\":\"CRM Portal\",\"id\":\"" + E_APP + "\"},"
                + "{\"type\":\"BusinessService\",\"name\":\"Customer Self-Service\",\"id\":\"" + E_SVC + "\"}],"
                + "\"relationships\":[{\"type\":\"ServingRelationship\",\"source\":\"" + E_APP + "\",\"target\":\"" + E_SVC
                + "\",\"name\":\"serves\",\"id\":\"" + R_SERV + "\"}]}";
        ArchiMateLLMResult r = ArchiMateLLMResultParser.parse(json);
        assertEquals(2, r.getElements().size());
        assertEquals(1, r.getRelationships().size());
        assertSchemaValidIfArchiPresent("task05", r);
    }

    // --- 6–7: Remove / retire ---

    @Test
    public void task06_retireApplication_classifiedAsChanges_removeIdsParseAndValidate() {
        String prompt = "Remove the legacy ApplicationComponent PayrollBatch from the model.";
        assertFalse(AnalysisPromptIntent.likelyAnalysisOnly(prompt));
        assertUserMessageWellFormed("task06", prompt);

        String json = "{\"elements\":[],\"relationships\":[],\"removeElementIds\":[\"" + E_APP + "\"]}";
        ArchiMateLLMResult r = ArchiMateLLMResultParser.parse(json);
        assertTrue(r.getElements().isEmpty());
        assertEquals(1, r.getRemoveElementIds().size());
        assertEquals(E_APP, r.getRemoveElementIds().get(0));
        assertSchemaValidIfArchiPresent("task06", r);
    }

    @Test
    public void task07_deleteRelationship_classifiedAsChanges() {
        String prompt = "Delete the assignment relationship between the role and the process.";
        assertFalse(AnalysisPromptIntent.likelyAnalysisOnly(prompt));
        assertUserMessageWellFormed("task07", prompt);

        String json = "{\"elements\":[],\"relationships\":[],\"removeRelationshipIds\":[\"" + R_SERV + "\"]}";
        ArchiMateLLMResult r = ArchiMateLLMResultParser.parse(json);
        assertEquals(1, r.getRemoveRelationshipIds().size());
        assertSchemaValidIfArchiPresent("task07", r);
    }

    // --- 8: New view / diagram canvas ---

    @Test
    public void task08_newIntegrationView_intentPromptAndDiagramJson() {
        String prompt = "Create a new view that shows only application-to-application interfaces.";
        assertFalse(AnalysisPromptIntent.likelyAnalysisOnly(prompt));
        assertTrue(DiagramCreationIntent.userAskedForBrandNewView(prompt));
        assertUserMessageWellFormed("task08", prompt);

        String json = "{\"elements\":[{\"type\":\"ApplicationComponent\",\"name\":\"A\",\"id\":\"" + E_APP + "\"},"
                + "{\"type\":\"ApplicationComponent\",\"name\":\"B\",\"id\":\"" + E_SVC + "\"}],"
                + "\"relationships\":[{\"type\":\"FlowRelationship\",\"source\":\"" + E_APP + "\",\"target\":\"" + E_SVC
                + "\",\"id\":\"" + R_SERV + "\"}],"
                + "\"diagram\":{\"name\":\"Integration slice\",\"viewpoint\":\"Application\","
                + "\"nodes\":[{\"elementId\":\"" + E_APP + "\",\"x\":40,\"y\":40,\"width\":120,\"height\":55},"
                + "{\"elementId\":\"" + E_SVC + "\",\"x\":200,\"y\":40,\"width\":120,\"height\":55}],"
                + "\"connections\":[{\"sourceElementId\":\"" + E_APP + "\",\"targetElementId\":\"" + E_SVC
                + "\",\"relationshipId\":\"" + R_SERV + "\"}]}}";
        ArchiMateLLMResult r = ArchiMateLLMResultParser.parse(json);
        assertNotNull(r.getDiagram());
        assertEquals("Integration slice", r.getDiagram().getName());
        assertEquals(2, r.getDiagram().getNodes().size());
        assertSchemaValidIfArchiPresent("task08", r);
    }

    // --- 9–10: Further analysis ---

    @Test
    public void task09_rationalization_classifiedAsAnalysis() {
        String prompt = "Identify overlapping applications that could be consolidated and explain why.";
        assertTrue(AnalysisPromptIntent.likelyAnalysisOnly(prompt));
        assertUserMessageWellFormed("task09", prompt);
    }

    @Test
    public void task10_roadmapAlignment_classifiedAsAnalysis() {
        String prompt = "What in this model is not yet aligned to a business capability?";
        assertTrue(AnalysisPromptIntent.likelyAnalysisOnly(prompt));
        assertUserMessageWellFormed("task10", prompt);
    }

}
