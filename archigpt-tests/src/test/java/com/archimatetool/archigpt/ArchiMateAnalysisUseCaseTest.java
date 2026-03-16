package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Verifies that the plugin supports the main enterprise-architecture analysis use cases
 * (strategy, dependencies, impact, governance, gaps, rationalization, data flows, roadmap).
 * These use cases require ANALYSIS mode: the LLM returns plain text, not JSON.
 */
@SuppressWarnings("nls")
public class ArchiMateAnalysisUseCaseTest {

    /** Minimal model XML used to build user messages for analysis questions. */
    private static final String MINIMAL_MODEL_XML = "<?xml version=\"1.0\"?>\n<model xmlns=\"http://www.opengroup.org/xsd/archimate/3.0/\">\n"
            + "<archimateModel name=\"Test\"><folder name=\"Business\"/><folder name=\"Application\"/></archimateModel>\n</model>";

    /**
     * Use case 1: Strategy and business understanding.
     * Architect checks how solutions support business. Typical elements: Business capability, Business process, Business role, Business service.
     */
    public static final UseCase USE_CASE_1_STRATEGY = new UseCase(
            "1. Strategian ja liiketoiminnan ymmärtäminen",
            "Strategy and business understanding",
            "Mitä liiketoimintakyvykkyyksiä tämä ratkaisu tukee?",
            "What business capabilities does this solution support?");

    /**
     * Use case 2: Dependency analysis.
     * Questions about systems depending on application, interfaces, data. Relationships: Serving, Used-by, Access, Flow.
     */
    public static final UseCase USE_CASE_2_DEPENDENCIES = new UseCase(
            "2. Riippuvuuksien analysointi",
            "Dependency analysis",
            "Mitkä järjestelmät ovat riippuvaisia tästä sovelluksesta?",
            "Which systems depend on this application?");

    /**
     * Use case 3: Change impact analysis.
     * Core ArchiMate use case: "If this changes, what else changes?"
     */
    public static final UseCase USE_CASE_3_IMPACT = new UseCase(
            "3. Muutoksen vaikutusanalyysi (impact analysis)",
            "Change impact analysis",
            "Mitä liiketoimintaprosesseja muutos tähän sovellukseen vaikuttaa?",
            "What business processes are affected by a change to this application?");

    /**
     * Use case 4: Responsibilities and ownership (governance).
     */
    public static final UseCase USE_CASE_4_GOVERNANCE = new UseCase(
            "4. Vastuiden ja omistajuuden tarkastelu",
            "Responsibilities and ownership",
            "Kuka omistaa tämän sovelluksen?",
            "Who owns this application?");

    /**
     * Use case 5: Identifying architecture gaps (quality assessment).
     */
    public static final UseCase USE_CASE_5_GAPS = new UseCase(
            "5. Arkkitehtuurin puutteiden tunnistaminen",
            "Identifying architecture gaps",
            "Onko jokaisella prosessilla sitä tukeva sovellus?",
            "Does every process have a supporting application?");

    /**
     * Use case 6: Simplification and rationalization (finding overlaps).
     */
    public static final UseCase USE_CASE_6_RATIONALIZATION = new UseCase(
            "6. Yksinkertaistaminen ja rationalisointi",
            "Simplification and rationalization",
            "Onko useita sovelluksia jotka tukevat samaa prosessia?",
            "Are there multiple applications supporting the same process?");

    /**
     * Use case 7: Understanding data flows (data and integration architecture).
     */
    public static final UseCase USE_CASE_7_DATA_FLOWS = new UseCase(
            "7. Tietovirtojen ymmärtäminen",
            "Understanding data flows",
            "Mistä tämä data syntyy? Missä dataa käytetään?",
            "Where does this data originate? Where is it used?");

    /**
     * Use case 8: Roadmap and transition architecture.
     */
    public static final UseCase USE_CASE_8_ROADMAP = new UseCase(
            "8. Roadmap- ja muutosarkkitehtuuri",
            "Roadmap and transition architecture",
            "Mikä on nykytila vs tavoitetila? Mitkä komponentit poistuvat?",
            "What is the current vs target state? Which components are being removed?");

    public static List<UseCase> allUseCases() {
        List<UseCase> list = new ArrayList<>();
        list.add(USE_CASE_1_STRATEGY);
        list.add(USE_CASE_2_DEPENDENCIES);
        list.add(USE_CASE_3_IMPACT);
        list.add(USE_CASE_4_GOVERNANCE);
        list.add(USE_CASE_5_GAPS);
        list.add(USE_CASE_6_RATIONALIZATION);
        list.add(USE_CASE_7_DATA_FLOWS);
        list.add(USE_CASE_8_ROADMAP);
        return list;
    }

    public static final class UseCase {
        public final String titleFi;
        public final String titleEn;
        public final String questionFi;
        public final String questionEn;

        public UseCase(String titleFi, String titleEn, String questionFi, String questionEn) {
            this.titleFi = titleFi;
            this.titleEn = titleEn;
            this.questionFi = questionFi;
            this.questionEn = questionEn;
        }
    }

    // --- Tests ---

    @Test
    public void systemPrompt_requiresAnalysisForDescriptionAndReview() {
        String p = ArchiMateSystemPrompt.SYSTEM_PROMPT;
        assertTrue("Prompt must instruct ANALYSIS for description/analysis/explain/review",
                p.contains("analysis") || p.contains("description") || p.contains("explain") || p.contains("review"));
        assertTrue("Prompt must say analysis gets plain text, not JSON",
                p.contains("plain text") || p.contains("ANALYSIS"));
        assertTrue("Prompt must distinguish from CHANGES (JSON) so analysis questions do not get JSON",
                p.contains("CHANGES") && (p.contains("JSON") || p.contains("json")));
    }

    @Test
    public void systemPrompt_supportsArchitectAnalysisThemes() {
        String p = ArchiMateSystemPrompt.SYSTEM_PROMPT;
        assertTrue("Prompt should support business/strategy analysis", p.contains("business") || p.contains("model"));
        assertTrue("Prompt should support view/diagram description", p.contains("view") || p.contains("diagram"));
        assertTrue("Prompt should reference elements and relationships for analysis", p.contains("elements") && p.contains("relationships"));
        assertTrue("Prompt should minimize hallucinations so analysis is based on model", p.contains("supplied XML") || p.contains("model"));
    }

    @Test
    public void userMessageFormat_forEachUseCase_containsDelimiterAndQuestion() {
        for (UseCase uc : allUseCases()) {
            String question = uc.questionEn;
            String userMessage = UserMessageBuilder.buildUserMessage("", MINIMAL_MODEL_XML, question);
            assertTrue("Use case " + uc.titleEn + ": message must contain model delimiter",
                    userMessage.contains("--- END OF MODEL ---"));
            assertTrue("Use case " + uc.titleEn + ": message must contain User request",
                    userMessage.contains("User request:"));
            assertTrue("Use case " + uc.titleEn + ": message must contain the question",
                    userMessage.contains(question));
            assertTrue("Use case " + uc.titleEn + ": message must contain model XML for context",
                    userMessage.contains("ArchiMate model") && userMessage.contains(MINIMAL_MODEL_XML.substring(0, 50)));
        }
    }

    @Test
    public void userMessageFormat_finnishQuestion_includedInMessage() {
        UseCase uc = USE_CASE_1_STRATEGY;
        String userMessage = UserMessageBuilder.buildUserMessage("", MINIMAL_MODEL_XML, uc.questionFi);
        assertTrue("Finnish question must be included", userMessage.contains(uc.questionFi));
        assertTrue("Message must have valid structure", userMessage.contains("--- END OF MODEL ---"));
    }

    /**
     * When the LLM returns plain analysis text (no JSON), the parser yields no import data,
     * so the plugin correctly shows "Analysis result" instead of trying to import.
     */
    @Test
    public void plainTextAnalysisResponse_treatedAsAnalysisNotImport() {
        String analysisResponse = "This solution supports the following business capabilities: Order Management and Customer Service. "
                + "The main processes that use this application are Order Fulfilment and Invoicing. "
                + "The component produces value by enabling self-service ordering.";
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(analysisResponse);
        assertNotNull(parsed);
        assertTrue("Analysis response must not be parsed as elements to add", parsed.getElements().isEmpty());
        assertTrue("Analysis response must not be parsed as relationships to add", parsed.getRelationships().isEmpty());
        assertFalse("Analysis response must not have diagram to create", parsed.getDiagram() != null && parsed.getDiagram().getName() != null && !parsed.getDiagram().getName().isEmpty());
        assertTrue("Analysis response must not have remove from model", parsed.getRemoveElementIds().isEmpty() && parsed.getRemoveRelationshipIds().isEmpty());
        assertTrue("Analysis response must not have remove diagram", parsed.getRemoveDiagramNames().isEmpty());
        assertTrue("Analysis response must not have remove from diagram only", parsed.getRemoveElementFromDiagramIds().isEmpty() && parsed.getRemoveRelationshipFromDiagramIds().isEmpty());
    }

    /**
     * Same as above for a Finnish analysis-style response.
     */
    @Test
    public void plainTextAnalysisResponseFinnish_treatedAsAnalysisNotImport() {
        String analysisResponse = "Tämä ratkaisu tukee liiketoimintakyvykkyyksiä: Tilaushallinta ja Asiakaspalvelu. "
                + "Prosessit jotka käyttävät sovellusta: Tilausprosessi ja Laskutus.";
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(analysisResponse);
        assertNotNull(parsed);
        assertTrue(parsed.getElements().isEmpty());
        assertTrue(parsed.getRelationships().isEmpty());
    }

    @Test
    public void allEightUseCasesDefined() {
        assertEquals("All 8 enterprise-architecture analysis use cases must be defined", 8, allUseCases().size());
    }
}
