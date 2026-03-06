package com.archimatetool.archigpt;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for validating good and faulty ArchiMate data against the schema.
 * These tests run only when the Archi model (IArchimatePackage) is on the classpath
 * (e.g. when building with -P with-archi or in an Eclipse/OSGi environment).
 */
public class ArchiMateSchemaValidatorTest {

    private static final boolean ARCHI_AVAILABLE = hasArchiModel();

    private static boolean hasArchiModel() {
        try {
            Class.forName("com.archimatetool.model.IArchimatePackage");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Before
    public void checkArchiAvailable() {
        if (!ARCHI_AVAILABLE) {
            org.junit.Assume.assumeTrue("Archi model not on classpath (IArchimatePackage); skip validator tests", false);
        }
    }

    @Test
    public void validateGoodData_returnsNoErrors() {
        ArchiMateLLMResult result = new ArchiMateLLMResult();
        ArchiMateLLMResult.ElementSpec e1 = new ArchiMateLLMResult.ElementSpec();
        e1.setType("BusinessActor");
        e1.setName("Customer");
        e1.setId("e1");
        result.getElements().add(e1);
        ArchiMateLLMResult.ElementSpec e2 = new ArchiMateLLMResult.ElementSpec();
        e2.setType("BusinessRole");
        e2.setName("Buyer");
        e2.setId("e2");
        result.getElements().add(e2);
        ArchiMateLLMResult.RelationshipSpec r = new ArchiMateLLMResult.RelationshipSpec();
        r.setType("AssignmentRelationship");
        r.setSource("e1");
        r.setTarget("e2");
        result.getRelationships().add(r);

        List<String> errors = ArchiMateSchemaValidator.validate(result);
        assertTrue("Expected no errors for valid data: " + errors, errors.isEmpty());
    }

    @Test
    public void validateFaulty_elementMissingId_returnsError() {
        ArchiMateLLMResult result = new ArchiMateLLMResult();
        ArchiMateLLMResult.ElementSpec e = new ArchiMateLLMResult.ElementSpec();
        e.setType("BusinessActor");
        e.setName("A");
        e.setId("");
        result.getElements().add(e);

        List<String> errors = ArchiMateSchemaValidator.validate(result);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(msg -> msg.contains("missing id") || msg.contains("id")));
    }

    @Test
    public void validateFaulty_elementInvalidType_returnsError() {
        ArchiMateLLMResult result = new ArchiMateLLMResult();
        ArchiMateLLMResult.ElementSpec e = new ArchiMateLLMResult.ElementSpec();
        e.setType("NotAnArchiMateType");
        e.setName("A");
        e.setId("e1");
        result.getElements().add(e);

        List<String> errors = ArchiMateSchemaValidator.validate(result);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(msg -> msg.contains("Invalid ArchiMate element type") && msg.contains("NotAnArchiMateType")));
    }

    @Test
    public void validate_relationshipSourceOrTargetNotInResponse_passes() {
        // Source/target may refer to existing model elements (resolved at import); validator only requires non-empty.
        ArchiMateLLMResult result = new ArchiMateLLMResult();
        ArchiMateLLMResult.ElementSpec e = new ArchiMateLLMResult.ElementSpec();
        e.setType("BusinessActor");
        e.setName("A");
        e.setId("e1");
        result.getElements().add(e);
        ArchiMateLLMResult.RelationshipSpec r = new ArchiMateLLMResult.RelationshipSpec();
        r.setType("AssignmentRelationship");
        r.setSource("e99");
        r.setTarget("e1");
        result.getRelationships().add(r);

        List<String> errors = ArchiMateSchemaValidator.validate(result);
        assertTrue("Relationship with source not in response is allowed (may exist in model)", errors.isEmpty());
    }

    @Test
    public void validate_relationshipTargetNotInResponse_passes() {
        ArchiMateLLMResult result = new ArchiMateLLMResult();
        ArchiMateLLMResult.ElementSpec e = new ArchiMateLLMResult.ElementSpec();
        e.setType("BusinessActor");
        e.setName("A");
        e.setId("e1");
        result.getElements().add(e);
        ArchiMateLLMResult.RelationshipSpec r = new ArchiMateLLMResult.RelationshipSpec();
        r.setType("AssignmentRelationship");
        r.setSource("e1");
        r.setTarget("e99");
        result.getRelationships().add(r);

        List<String> errors = ArchiMateSchemaValidator.validate(result);
        assertTrue("Relationship with target not in response is allowed (may exist in model)", errors.isEmpty());
    }

    @Test
    public void validateFaulty_relationshipInvalidType_returnsError() {
        ArchiMateLLMResult result = new ArchiMateLLMResult();
        ArchiMateLLMResult.ElementSpec e1 = new ArchiMateLLMResult.ElementSpec();
        e1.setType("BusinessActor");
        e1.setName("A");
        e1.setId("e1");
        result.getElements().add(e1);
        ArchiMateLLMResult.ElementSpec e2 = new ArchiMateLLMResult.ElementSpec();
        e2.setType("BusinessRole");
        e2.setName("B");
        e2.setId("e2");
        result.getElements().add(e2);
        ArchiMateLLMResult.RelationshipSpec r = new ArchiMateLLMResult.RelationshipSpec();
        r.setType("NotARealRelationship");
        r.setSource("e1");
        r.setTarget("e2");
        result.getRelationships().add(r);

        List<String> errors = ArchiMateSchemaValidator.validate(result);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(msg -> msg.contains("Invalid ArchiMate relationship type") && msg.contains("NotARealRelationship")));
    }

    @Test
    public void validate_emptyResult_returnsNoErrors() {
        ArchiMateLLMResult result = new ArchiMateLLMResult();
        List<String> errors = ArchiMateSchemaValidator.validate(result);
        assertTrue(errors.isEmpty());
    }
}
