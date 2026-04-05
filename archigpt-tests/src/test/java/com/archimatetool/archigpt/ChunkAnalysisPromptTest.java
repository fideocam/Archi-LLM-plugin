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
    }

    @Test
    public void buildChunkUserMessage_legacyOverload_omitsDigestLine() {
        String msg = ChunkAnalysisPrompt.buildChunkUserMessage("<x/>", 1, 1, "", "Q");
        assertTrue(msg.contains("Excerpt 1 of 1"));
        assertTrue(msg.contains("<x/>"));
        assertFalse(msg.contains("MODEL DIGEST"));
    }
}
