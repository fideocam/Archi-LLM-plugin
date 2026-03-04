package com.archimatetool.archigpt;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * End-to-end tests: parse LLM response -> validate -> import into model.
 * Runs import only when Archi model is on the classpath (uses reflection so this compiles without Archi).
 */
public class ArchiMateImportFlowTest {

    private static final boolean ARCHI_AVAILABLE = hasArchiModel();

    private static boolean hasArchiModel() {
        try {
            Class.forName("com.archimatetool.model.IArchimateFactory");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static final String GOOD_LLM_RESPONSE = "{\"elements\":["
            + "{\"type\":\"BusinessActor\",\"name\":\"Customer\",\"id\":\"e1\"},"
            + "{\"type\":\"BusinessRole\",\"name\":\"Buyer\",\"id\":\"e2\"}"
            + "],\"relationships\":["
            + "{\"type\":\"AssignmentRelationship\",\"source\":\"e1\",\"target\":\"e2\",\"name\":\"\",\"id\":\"r1\"}"
            + "]}";

    @Before
    public void checkArchiAvailable() {
        if (!ARCHI_AVAILABLE) {
            org.junit.Assume.assumeTrue("Archi model not on classpath; skip import tests", false);
        }
    }

    @Test
    public void parseValidateImport_goodData_importsIntoModel() throws Exception {
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(GOOD_LLM_RESPONSE);
        List<String> errors = ArchiMateSchemaValidator.validate(parsed);
        assertTrue("Validation should pass: " + errors, errors.isEmpty());

        Class<?> factoryClass = Class.forName("com.archimatetool.model.IArchimateFactory");
        Object factory = factoryClass.getMethod("eINSTANCE").invoke(null);
        Object model = factoryClass.getMethod("createArchimateModel").invoke(factory);
        model.getClass().getMethod("setDefaults").invoke(model);

        Method importMethod = ArchiMateLLMImporter.class.getMethod("importIntoModel", ArchiMateLLMResult.class, Class.forName("com.archimatetool.model.IArchimateModel"));
        importMethod.invoke(null, parsed, model);

        Object folders = model.getClass().getMethod("getFolders").invoke(model);
        assertNotNull(folders);
        int totalElements = 0;
        for (Object folder : (Iterable<?>) folders) {
            Object elements = folder.getClass().getMethod("getElements").invoke(folder);
            totalElements += ((List<?>) elements).size();
        }
        assertTrue("Model should contain elements and relationships", totalElements >= 3);
    }
}
