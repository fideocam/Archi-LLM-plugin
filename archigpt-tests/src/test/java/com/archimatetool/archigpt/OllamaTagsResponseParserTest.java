package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class OllamaTagsResponseParserTest {

    @Test
    public void parseModelNames_extractsNamesInOrder() {
        String json = "{\"models\":[{\"name\":\"b:latest\",\"size\":1},{\"name\":\"a:latest\",\"size\":2}]}";
        List<String> names = OllamaTagsResponseParser.parseModelNames(json);
        assertEquals(Arrays.asList("b:latest", "a:latest"), names);
    }

    @Test
    public void parseModelNames_deduplicates() {
        String json = "{\"models\":[{\"name\":\"x:latest\"},{\"name\":\"x:latest\"}]}";
        List<String> names = OllamaTagsResponseParser.parseModelNames(json);
        assertEquals(1, names.size());
        assertEquals("x:latest", names.get(0));
    }

    @Test
    public void parseModelNames_emptyForInvalid() {
        assertTrue(OllamaTagsResponseParser.parseModelNames(null).isEmpty());
        assertTrue(OllamaTagsResponseParser.parseModelNames("{}").isEmpty());
    }
}
