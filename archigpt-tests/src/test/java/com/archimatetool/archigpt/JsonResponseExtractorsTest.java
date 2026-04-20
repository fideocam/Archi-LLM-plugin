package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JsonResponseExtractorsTest {

    @Test
    public void openAi_extractsAssistantContent() {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hello \\\"world\\\"\"}}]}";
        assertEquals("Hello \"world\"", JsonResponseExtractors.openAiAssistantContent(json));
    }

    @Test
    public void anthropic_extractsFirstText() {
        String json = "{\"content\":[{\"type\":\"text\",\"text\":\"Line1\\nLine2\"}]}";
        assertEquals("Line1\nLine2", JsonResponseExtractors.anthropicFirstText(json));
    }

    @Test
    public void gemini_extractsCandidateText() {
        String json = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}";
        assertEquals("OK", JsonResponseExtractors.geminiCandidatesText(json));
    }
}
