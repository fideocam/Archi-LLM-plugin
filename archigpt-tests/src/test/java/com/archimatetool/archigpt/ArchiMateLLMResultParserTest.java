package com.archimatetool.archigpt;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

/**
 * Tests for parsing good and faulty ArchiMate JSON from the LLM.
 */
public class ArchiMateLLMResultParserTest {

    private static final String GOOD_JSON = "{\"elements\":["
            + "{\"type\":\"BusinessActor\",\"name\":\"Customer\",\"id\":\"e1\"},"
            + "{\"type\":\"BusinessRole\",\"name\":\"Buyer\",\"id\":\"e2\"}"
            + "],\"relationships\":["
            + "{\"type\":\"AssignmentRelationship\",\"source\":\"e1\",\"target\":\"e2\",\"name\":\"assigned\",\"id\":\"r1\"}"
            + "]}";

    @Test
    public void parseGoodJson_returnsElementsAndRelationships() {
        ArchiMateLLMResult result = ArchiMateLLMResultParser.parse(GOOD_JSON);
        assertNotNull(result);
        assertEquals(2, result.getElements().size());
        assertEquals(1, result.getRelationships().size());

        ArchiMateLLMResult.ElementSpec e1 = result.getElements().get(0);
        assertEquals("BusinessActor", e1.getType());
        assertEquals("Customer", e1.getName());
        assertEquals("e1", e1.getId());

        ArchiMateLLMResult.ElementSpec e2 = result.getElements().get(1);
        assertEquals("BusinessRole", e2.getType());
        assertEquals("Buyer", e2.getName());
        assertEquals("e2", e2.getId());

        ArchiMateLLMResult.RelationshipSpec r1 = result.getRelationships().get(0);
        assertEquals("AssignmentRelationship", r1.getType());
        assertEquals("e1", r1.getSource());
        assertEquals("e2", r1.getTarget());
        assertEquals("assigned", r1.getName());
        assertEquals("r1", r1.getId());
    }

    @Test
    public void parseGoodJson_withMarkdownCodeBlock_extractsJson() {
        String withMarkdown = "Some text\n```json\n" + GOOD_JSON + "\n```\nmore text";
        ArchiMateLLMResult result = ArchiMateLLMResultParser.parse(withMarkdown);
        assertEquals(2, result.getElements().size());
        assertEquals(1, result.getRelationships().size());
    }

    @Test
    public void parseGoodJson_relationshipWithoutNameOrId_stillParses() {
        String json = "{\"elements\":[{\"type\":\"BusinessActor\",\"name\":\"A\",\"id\":\"e1\"}],"
                + "\"relationships\":[{\"type\":\"ServingRelationship\",\"source\":\"e1\",\"target\":\"e1\"}]}";
        ArchiMateLLMResult result = ArchiMateLLMResultParser.parse(json);
        assertEquals(1, result.getRelationships().size());
        assertEquals("ServingRelationship", result.getRelationships().get(0).getType());
        assertNull(result.getRelationships().get(0).getId());
        assertEquals("", result.getRelationships().get(0).getName());
    }

    @Test
    public void parseFaulty_emptyString_returnsEmptyResult() {
        ArchiMateLLMResult result = ArchiMateLLMResultParser.parse("");
        assertNotNull(result);
        assertTrue(result.getElements().isEmpty());
        assertTrue(result.getRelationships().isEmpty());
    }

    @Test
    public void parseFaulty_null_returnsEmptyResult() {
        ArchiMateLLMResult result = ArchiMateLLMResultParser.parse(null);
        assertNotNull(result);
        assertTrue(result.getElements().isEmpty());
        assertTrue(result.getRelationships().isEmpty());
    }

    @Test
    public void parseFaulty_notJson_returnsEmptyResult() {
        ArchiMateLLMResult result = ArchiMateLLMResultParser.parse("just plain text");
        assertNotNull(result);
        assertTrue(result.getElements().isEmpty());
        assertTrue(result.getRelationships().isEmpty());
    }

    @Test
    public void parseFaulty_malformedJson_missingElementsArray_returnsEmptyElements() {
        String json = "{\"elements\": null,\"relationships\":[]}";
        ArchiMateLLMResult result = ArchiMateLLMResultParser.parse(json);
        assertTrue(result.getElements().isEmpty());
        assertTrue(result.getRelationships().isEmpty());
    }

    @Test
    public void parseFaulty_jsonWithErrorField_setsErrorOnResult() {
        String json = "{\"elements\":[],\"relationships\":[],\"error\":\"Cannot produce valid ArchiMate\"}";
        ArchiMateLLMResult result = ArchiMateLLMResultParser.parse(json);
        assertEquals("Cannot produce valid ArchiMate", result.getError());
    }

    @Test
    public void parseFaulty_elementMissingType_skipsThatElement() {
        String json = "{\"elements\":[{\"name\":\"NoType\",\"id\":\"e1\"}],\"relationships\":[]}";
        ArchiMateLLMResult result = ArchiMateLLMResultParser.parse(json);
        assertTrue(result.getElements().isEmpty());
    }

    @Test
    public void parseGoodJson_escapedNewlineInName() {
        String json = "{\"elements\":[{\"type\":\"BusinessActor\",\"name\":\"Line1\\nLine2\",\"id\":\"e1\"}],\"relationships\":[]}";
        ArchiMateLLMResult result = ArchiMateLLMResultParser.parse(json);
        assertEquals(1, result.getElements().size());
        assertTrue(result.getElements().get(0).getName().contains("\n"));
    }
}
