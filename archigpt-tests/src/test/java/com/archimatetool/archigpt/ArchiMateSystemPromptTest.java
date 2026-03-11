package com.archimatetool.archigpt;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests that the system prompt contains required instructions for analysis and changes,
 * including anti-hallucination and duplicate-avoidance.
 */
public class ArchiMateSystemPromptTest {

    @Test
    public void systemPrompt_containsAnalysisAndChangesModes() {
        String p = ArchiMateSystemPrompt.SYSTEM_PROMPT;
        assertTrue("Should define ANALYSIS mode", p.contains("ANALYSIS"));
        assertTrue("Should define CHANGES mode", p.contains("CHANGES"));
    }

    @Test
    public void systemPrompt_analysis_minimizeHallucinations() {
        String p = ArchiMateSystemPrompt.SYSTEM_PROMPT;
        assertTrue("Analysis should mention minimizing hallucinations", p.contains("minimize hallucinations"));
        assertTrue("Analysis should reference supplied XML", p.contains("supplied XML"));
        assertTrue("Analysis should forbid inventing elements", p.contains("Never mention, assume, or infer elements"));
    }

    @Test
    public void systemPrompt_changes_modelProvidedAndNoDuplicates() {
        String p = ArchiMateSystemPrompt.SYSTEM_PROMPT;
        assertTrue("CHANGES should say model is provided", p.contains("given the current ArchiMate model as XML"));
        assertTrue("CHANGES should forbid adding existing elements", p.contains("do not add elements that already exist"));
        assertTrue("CHANGES should mention duplicate check", p.contains("do NOT already appear in the supplied model"));
    }

    @Test
    public void systemPrompt_containsJsonStructure() {
        String p = ArchiMateSystemPrompt.SYSTEM_PROMPT;
        assertTrue("Should describe elements array", p.contains("\"elements\""));
        assertTrue("Should describe relationships array", p.contains("\"relationships\""));
        assertTrue("Should mention error field", p.contains("\"error\""));
    }

    @Test
    public void systemPrompt_mentionsDiagramForNewView() {
        String p = ArchiMateSystemPrompt.SYSTEM_PROMPT;
        assertTrue("Should mention diagram object for new view", p.contains("diagram"));
        assertTrue("Should mention diagram for create/generate", p.contains("NEW DIAGRAM") || p.contains("new view"));
    }

    @Test
    public void systemPrompt_mentionsFragmentAndMultipleElements() {
        String p = ArchiMateSystemPrompt.SYSTEM_PROMPT;
        assertTrue("Should mention fragment or multiple elements for process/service", p.contains("multiple") || p.contains("fragment"));
        assertTrue("Should mention removeElementIds/removeRelationshipIds", p.contains("removeElementIds") && p.contains("removeRelationshipIds"));
    }
}
