package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OllamaShowResponseParserTest {

    @Test
    public void parsesContextLengthFromModelInfo() {
        String json = "{\"model_info\":{\"llama.context_length\":131072}}";
        assertEquals(131072, OllamaShowResponseParser.parseContextTokens(json));
    }

    @Test
    public void parsesNumCtxKey() {
        String json = "{\"model_info\":{\"general.num_ctx\":8192}}";
        assertEquals(8192, OllamaShowResponseParser.parseContextTokens(json));
    }

    @Test
    public void picksLargestValue() {
        String json = "{\"x\":\"num_ctx 2048\",\"model_info\":{\"a.context_length\":4096,\"b.context_length\":16384}}";
        assertEquals(16384, OllamaShowResponseParser.parseContextTokens(json));
    }

    @Test
    public void emptyReturnsZero() {
        assertEquals(0, OllamaShowResponseParser.parseContextTokens(""));
    }
}
