package com.archimatetool.archigpt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChunkAnalysisPromptTest {

    @Test
    public void buildChunkUserMessage_includesDigestAndScope() {
        String digest = "MODEL DIGEST\nDiagrams/views: 2\n";
        String title = "Folder \"Business\" (business)";
        String xml = "<model>...</model>";
        String msg = ChunkAnalysisPrompt.buildChunkUserMessage(digest, title, xml, 2, 5, "", "Summarize");
        assertTrue(msg.startsWith(digest.trim()));
        assertTrue(msg.contains("Excerpt 2 of 5"));
        assertTrue(msg.contains("This excerpt scope: " + title));
        assertTrue(msg.contains(xml));
        assertTrue(msg.contains("User request: Summarize"));
        assertFalse(msg.contains(PromptLibrary.TOOL_INSTRUCTIONS_START));
    }

    @Test
    public void buildChunkUserMessage_placesSelectionBeforeRequest() {
        String msg = ChunkAnalysisPrompt.buildChunkUserMessage("digest", "scope", "<x/>", 1, 2,
                "Current selection in the model:\n- Element BusinessActor \"Customer\" (id=abc)\n", "Review", null);
        int sel = msg.indexOf("Element BusinessActor \"Customer\"");
        int req = msg.indexOf("User request: Review");
        assertTrue(sel >= 0 && req > sel);
    }

    @Test
    public void buildChunkUserMessage_includesSkillInstructions() {
        String msg = ChunkAnalysisPrompt.buildChunkUserMessage("digest", "scope", "<x/>", 1, 2, "", "Review",
                "Cite element ids.");
        assertTrue(msg.contains(PromptLibrary.TOOL_INSTRUCTIONS_START));
        assertTrue(msg.contains("Cite element ids."));
        assertTrue(msg.contains(PromptLibrary.TOOL_INSTRUCTIONS_END));
    }

    @Test
    public void buildChunkUserMessage_legacyOverload_omitsDigestLine() {
        String msg = ChunkAnalysisPrompt.buildChunkUserMessage("<x/>", 1, 1, "", "Q");
        assertTrue(msg.contains("Excerpt 1 of 1"));
        assertTrue(msg.contains("<x/>"));
        assertFalse(msg.contains("MODEL DIGEST"));
    }
}
