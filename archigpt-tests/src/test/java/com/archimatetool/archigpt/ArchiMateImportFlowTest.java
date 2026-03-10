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
        Object factory = factoryClass.getField("eINSTANCE").get(null);
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

    @Test
    public void importDuplicateElement_skipsExistingElement() throws Exception {
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(GOOD_LLM_RESPONSE);
        List<String> errors = ArchiMateSchemaValidator.validate(parsed);
        assertTrue("Validation should pass: " + errors, errors.isEmpty());

        Class<?> factoryClass = Class.forName("com.archimatetool.model.IArchimateFactory");
        Object factory = factoryClass.getField("eINSTANCE").get(null);
        Object model = factoryClass.getMethod("createArchimateModel").invoke(factory);
        model.getClass().getMethod("setDefaults").invoke(model);

        Method importMethod = ArchiMateLLMImporter.class.getMethod("importIntoModel", ArchiMateLLMResult.class, Class.forName("com.archimatetool.model.IArchimateModel"));
        importMethod.invoke(null, parsed, model);

        int countAfterFirst = countElementsInModel(model);
        assertTrue("First import should add elements", countAfterFirst >= 2);

        importMethod.invoke(null, parsed, model);

        int countAfterSecond = countElementsInModel(model);
        assertEquals("Second import with same elements should not duplicate (duplicates skipped)", countAfterFirst, countAfterSecond);
    }

    private static int countElementsInModel(Object model) throws Exception {
        int total = 0;
        Object folders = model.getClass().getMethod("getFolders").invoke(model);
        for (Object folder : (Iterable<?>) folders) {
            Object elements = folder.getClass().getMethod("getElements").invoke(folder);
            total += ((List<?>) elements).size();
        }
        return total;
    }

    @Test
    public void importWithTargetFolder_relationshipsGoToRelationsFolderNotTargetFolder() throws Exception {
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(GOOD_LLM_RESPONSE);
        List<String> errors = ArchiMateSchemaValidator.validate(parsed);
        assertTrue("Validation should pass: " + errors, errors.isEmpty());

        Class<?> factoryClass = Class.forName("com.archimatetool.model.IArchimateFactory");
        Object factory = factoryClass.getField("eINSTANCE").get(null);
        Object model = factoryClass.getMethod("createArchimateModel").invoke(factory);
        model.getClass().getMethod("setDefaults").invoke(model);

        Object folders = model.getClass().getMethod("getFolders").invoke(model);
        Object targetFolder = null;
        for (Object f : (Iterable<?>) folders) {
            Object name = f.getClass().getMethod("getName").invoke(f);
            if (name != null && name.toString().toLowerCase().contains("business")) {
                targetFolder = f;
                break;
            }
        }
        if (targetFolder == null) {
            targetFolder = ((java.util.List<?>) folders).get(0);
        }

        Method importMethod = ArchiMateLLMImporter.class.getMethod("importIntoModel", ArchiMateLLMResult.class,
                Class.forName("com.archimatetool.model.IArchimateModel"), Class.forName("com.archimatetool.model.IFolder"), Class.forName("com.archimatetool.model.IArchimateDiagramModel"));
        importMethod.invoke(null, parsed, model, targetFolder, null);

        Object targetFolderElements = targetFolder.getClass().getMethod("getElements").invoke(targetFolder);
        int relationshipsInTargetFolder = 0;
        for (Object el : (Iterable<?>) targetFolderElements) {
            if (el.getClass().getSimpleName().toLowerCase().contains("relationship")) {
                relationshipsInTargetFolder++;
            }
        }
        assertTrue("Relationships should not be in element folder (Business); they go to Relations folder", relationshipsInTargetFolder == 0);
    }

    @Test
    public void importWithDiagram_createsNewViewInModel() throws Exception {
        String jsonWithDiagram = "{\"elements\":[{\"type\":\"BusinessActor\",\"name\":\"A\",\"id\":\"a1\"}],\"relationships\":[],"
                + "\"diagram\":{\"name\":\"Test View\",\"viewpoint\":\"\",\"nodes\":[{\"elementId\":\"a1\",\"x\":50,\"y\":50,\"width\":120,\"height\":55}],\"connections\":[]}}";
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(jsonWithDiagram);
        List<String> errors = ArchiMateSchemaValidator.validate(parsed);
        assertTrue("Validation should pass: " + errors, errors.isEmpty());

        Class<?> factoryClass = Class.forName("com.archimatetool.model.IArchimateFactory");
        Object factory = factoryClass.getField("eINSTANCE").get(null);
        Object model = factoryClass.getMethod("createArchimateModel").invoke(factory);
        model.getClass().getMethod("setDefaults").invoke(model);

        Method importMethod = ArchiMateLLMImporter.class.getMethod("importIntoModel", ArchiMateLLMResult.class, Class.forName("com.archimatetool.model.IArchimateModel"), Class.forName("com.archimatetool.model.IFolder"), Class.forName("com.archimatetool.model.IArchimateDiagramModel"));
        importMethod.invoke(null, parsed, model, null, null);

        Object diagramModels = model.getClass().getMethod("getDiagramModels").invoke(model);
        assertNotNull(diagramModels);
        int count = ((java.util.List<?>) diagramModels).size();
        assertTrue("Model should have at least one diagram (new view) after import", count >= 1);
        boolean found = false;
        for (Object dm : (Iterable<?>) diagramModels) {
            Object name = dm.getClass().getMethod("getName").invoke(dm);
            if ("Test View".equals(name != null ? name.toString() : null)) {
                found = true;
                break;
            }
        }
        assertTrue("Diagram named 'Test View' should exist in model", found);
    }
}
