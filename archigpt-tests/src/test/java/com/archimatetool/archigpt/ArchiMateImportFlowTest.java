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

    /** ArchiMate id format: id- + 32 hex. */
    private static final String E1 = "id-a1b2c3d4e5f67890abcdef1234567890";
    private static final String E2 = "id-b2c3d4e5f67890abcdef1234567890ab";
    private static final String R1 = "id-c3d4e5f67890abcdef1234567890abcd";

    private static final String GOOD_LLM_RESPONSE = "{\"elements\":["
            + "{\"type\":\"BusinessActor\",\"name\":\"Customer\",\"id\":\"" + E1 + "\"},"
            + "{\"type\":\"BusinessRole\",\"name\":\"Buyer\",\"id\":\"" + E2 + "\"}"
            + "],\"relationships\":["
            + "{\"type\":\"AssignmentRelationship\",\"source\":\"" + E1 + "\",\"target\":\"" + E2 + "\",\"name\":\"\",\"id\":\"" + R1 + "\"}"
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
        assertTrue("Second import should not add duplicate elements (elements skipped); relationship may be added again",
                countAfterSecond >= countAfterFirst && countAfterSecond <= countAfterFirst + 1);
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
        String a1 = "id-d4e5f67890abcdef1234567890abcdef12";
        String jsonWithDiagram = "{\"elements\":[{\"type\":\"BusinessActor\",\"name\":\"A\",\"id\":\"" + a1 + "\"}],\"relationships\":[],"
                + "\"diagram\":{\"name\":\"Test View\",\"viewpoint\":\"\",\"nodes\":[{\"elementId\":\"" + a1 + "\",\"x\":50,\"y\":50,\"width\":120,\"height\":55}],\"connections\":[]}}";
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

    @Test
    public void importWithHyphenatedUuid_normalizesToArchiMateId() throws Exception {
        String hyphenated = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String json = "{\"elements\":[{\"type\":\"BusinessActor\",\"name\":\"X\",\"id\":\"" + hyphenated + "\"}],\"relationships\":[]}";
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(json);
        List<String> errors = ArchiMateSchemaValidator.validate(parsed);
        assertTrue("Validation should pass: " + errors, errors.isEmpty());

        Object model = Class.forName("com.archimatetool.model.IArchimateFactory").getField("eINSTANCE").get(null)
                .getClass().getMethod("createArchimateModel").invoke(Class.forName("com.archimatetool.model.IArchimateFactory").getField("eINSTANCE").get(null));
        model.getClass().getMethod("setDefaults").invoke(model);
        Method importMethod = ArchiMateLLMImporter.class.getMethod("importIntoModel", ArchiMateLLMResult.class, Class.forName("com.archimatetool.model.IArchimateModel"));
        importMethod.invoke(null, parsed, model);

        Object folders = model.getClass().getMethod("getFolders").invoke(model);
        Object businessFolder = null;
        for (Object f : (Iterable<?>) folders) {
            if (String.valueOf(f.getClass().getMethod("getName").invoke(f)).toLowerCase().contains("business")) {
                businessFolder = f;
                break;
            }
        }
        assertNotNull(businessFolder);
        Object elements = businessFolder.getClass().getMethod("getElements").invoke(businessFolder);
        assertTrue("Should have at least one element", ((List<?>) elements).size() >= 1);
        Object element = ((List<?>) elements).get(0);
        String id = (String) element.getClass().getMethod("getId").invoke(element);
        assertTrue("Id should be ArchiMate format (id- + 32 hex): " + id, id != null && id.startsWith("id-") && id.length() == 35 && id.substring(3).matches("[0-9a-fA-F]+"));
    }

    @Test
    public void importWithDiagram_sameNameAsExisting_addsToExistingDiagram() throws Exception {
        String a1 = "id-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String json = "{\"elements\":[{\"type\":\"BusinessActor\",\"name\":\"NewActor\",\"id\":\"" + a1 + "\"}],\"relationships\":[],"
                + "\"diagram\":{\"name\":\"Existing View\",\"viewpoint\":\"\",\"nodes\":[{\"elementId\":\"" + a1 + "\",\"x\":50,\"y\":50,\"width\":120,\"height\":55}],\"connections\":[]}}";
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(json);
        List<String> errors = ArchiMateSchemaValidator.validate(parsed);
        assertTrue("Validation should pass: " + errors, errors.isEmpty());

        Object factory = Class.forName("com.archimatetool.model.IArchimateFactory").getField("eINSTANCE").get(null);
        Object model = factory.getClass().getMethod("createArchimateModel").invoke(factory);
        model.getClass().getMethod("setDefaults").invoke(model);

        Object diagramsFolder = model.getClass().getMethod("getFolder", Class.forName("com.archimatetool.model.FolderType")).invoke(model, Class.forName("com.archimatetool.model.FolderType").getField("DIAGRAMS").get(null));
        Object existingDiagram = Class.forName("com.archimatetool.model.IArchimateFactory").getField("eINSTANCE").get(null).getClass().getMethod("createArchimateDiagramModel").invoke(Class.forName("com.archimatetool.model.IArchimateFactory").getField("eINSTANCE").get(null));
        existingDiagram.getClass().getMethod("setName", String.class).invoke(existingDiagram, "Existing View");
        @SuppressWarnings("unchecked")
        java.util.List<Object> folderElements = (java.util.List<Object>) diagramsFolder.getClass().getMethod("getElements").invoke(diagramsFolder);
        folderElements.add(existingDiagram);

        Method importMethod = ArchiMateLLMImporter.class.getMethod("importIntoModel", ArchiMateLLMResult.class, Class.forName("com.archimatetool.model.IArchimateModel"), Class.forName("com.archimatetool.model.IFolder"), Class.forName("com.archimatetool.model.IArchimateDiagramModel"));
        importMethod.invoke(null, parsed, model, null, null);

        Object diagramModels = model.getClass().getMethod("getDiagramModels").invoke(model);
        int count = ((java.util.List<?>) diagramModels).size();
        assertEquals("Should still have one diagram (add to existing, not create new)", 1, count);
        Object diagram = ((java.util.List<?>) diagramModels).get(0);
        Object children = diagram.getClass().getMethod("getChildren").invoke(diagram);
        assertTrue("Existing diagram should have the new element as figure", ((List<?>) children).size() >= 1);
    }

    @Test
    public void importWithTargetDiagram_addsRelationshipArrowsOnView() throws Exception {
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(GOOD_LLM_RESPONSE);
        List<String> errors = ArchiMateSchemaValidator.validate(parsed);
        assertTrue("Validation should pass: " + errors, errors.isEmpty());

        Object factory = Class.forName("com.archimatetool.model.IArchimateFactory").getField("eINSTANCE").get(null);
        Object model = factory.getClass().getMethod("createArchimateModel").invoke(factory);
        model.getClass().getMethod("setDefaults").invoke(model);

        Object diagramsFolder = model.getClass().getMethod("getFolder", Class.forName("com.archimatetool.model.FolderType"))
                .invoke(model, Class.forName("com.archimatetool.model.FolderType").getField("DIAGRAMS").get(null));
        Object diagram = factory.getClass().getMethod("createArchimateDiagramModel").invoke(factory);
        diagram.getClass().getMethod("setName", String.class).invoke(diagram, "Open View");
        @SuppressWarnings("unchecked")
        java.util.List<Object> folderElements = (java.util.List<Object>) diagramsFolder.getClass().getMethod("getElements")
                .invoke(diagramsFolder);
        folderElements.add(diagram);

        Method importMethod = ArchiMateLLMImporter.class.getMethod("importIntoModel", ArchiMateLLMResult.class,
                Class.forName("com.archimatetool.model.IArchimateModel"), Class.forName("com.archimatetool.model.IFolder"),
                Class.forName("com.archimatetool.model.IArchimateDiagramModel"));
        importMethod.invoke(null, parsed, model, null, diagram);

        Object children = diagram.getClass().getMethod("getChildren").invoke(diagram);
        assertTrue("Open view should have figures for the new elements", ((List<?>) children).size() >= 2);
        int connectionCount = 0;
        for (Object child : (List<?>) children) {
            Object sourceConns = child.getClass().getMethod("getSourceConnections").invoke(child);
            if (sourceConns instanceof List) {
                connectionCount += ((List<?>) sourceConns).size();
            }
        }
        assertTrue("Open view should show a relationship arrow between the new figures, not only store it in the model",
                connectionCount >= 1);
    }

    @Test
    public void importRelationshipOntoExistingFigures_drawsArrowWithoutAddingNodes() throws Exception {
        String json = "{\"elements\":[],\"relationships\":["
                + "{\"type\":\"AssignmentRelationship\",\"source\":\"" + E1 + "\",\"target\":\"" + E2 + "\",\"name\":\"\",\"id\":\"" + R1 + "\"}"
                + "]}";
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(json);
        assertTrue(ArchiMateSchemaValidator.validate(parsed).isEmpty());

        Object factory = factory();
        Object model = createModel(factory);
        Object actor1 = addNamedConcept(factory, model, "createBusinessActor", "Customer", E1);
        Object actor2 = addNamedConcept(factory, model, "createBusinessRole", "Buyer", E2);
        Object diagram = addEmptyDiagram(factory, model, "Open View");
        addFigure(factory, diagram, actor1);
        addFigure(factory, diagram, actor2);
        assertEquals(2, childCount(diagram));
        assertEquals(0, sourceConnectionCount(diagram));

        importWithDiagram(parsed, model, diagram);

        assertEquals("Relationship-only import should not add extra figures", 2, childCount(diagram));
        assertTrue("Both existing figures should be joined by an arrow", sourceConnectionCount(diagram) >= 1);
    }

    @Test
    public void importNewViewWithEmptyConnections_stillDrawsRelationshipArrows() throws Exception {
        String json = "{\"elements\":["
                + "{\"type\":\"BusinessActor\",\"name\":\"Customer\",\"id\":\"" + E1 + "\"},"
                + "{\"type\":\"BusinessRole\",\"name\":\"Buyer\",\"id\":\"" + E2 + "\"}"
                + "],\"relationships\":["
                + "{\"type\":\"AssignmentRelationship\",\"source\":\"" + E1 + "\",\"target\":\"" + E2 + "\",\"name\":\"\",\"id\":\"" + R1 + "\"}"
                + "],\"diagram\":{\"name\":\"Arrows View\",\"viewpoint\":\"\",\"nodes\":["
                + "{\"elementId\":\"" + E1 + "\",\"x\":50,\"y\":50,\"width\":120,\"height\":55},"
                + "{\"elementId\":\"" + E2 + "\",\"x\":220,\"y\":50,\"width\":120,\"height\":55}"
                + "],\"connections\":[]}}";
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(json);
        assertTrue(ArchiMateSchemaValidator.validate(parsed).isEmpty());

        Object factory = factory();
        Object model = createModel(factory);
        importWithDiagram(parsed, model, null);

        Object diagram = findDiagramByName(model, "Arrows View");
        assertNotNull(diagram);
        assertTrue("New view should contain both nodes", childCount(diagram) >= 2);
        assertTrue("Omitted diagram.connections should still get relationship arrows", sourceConnectionCount(diagram) >= 1);
    }

    @Test
    public void importRelationshipWhenOnlyOneEndOnDiagram_doesNotDrawArrow() throws Exception {
        String json = "{\"elements\":[],\"relationships\":["
                + "{\"type\":\"AssignmentRelationship\",\"source\":\"" + E1 + "\",\"target\":\"" + E2 + "\",\"name\":\"\",\"id\":\"" + R1 + "\"}"
                + "]}";
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(json);
        assertTrue(ArchiMateSchemaValidator.validate(parsed).isEmpty());

        Object factory = factory();
        Object model = createModel(factory);
        Object actor1 = addNamedConcept(factory, model, "createBusinessActor", "Customer", E1);
        addNamedConcept(factory, model, "createBusinessRole", "Buyer", E2);
        Object diagram = addEmptyDiagram(factory, model, "Open View");
        addFigure(factory, diagram, actor1);

        importWithDiagram(parsed, model, diagram);

        assertEquals("Missing-end import should not add the other figure", 1, childCount(diagram));
        assertEquals("No arrow when only one end is on the view", 0, sourceConnectionCount(diagram));
    }

    @Test
    public void importRemoveElement_alsoRemovesConnectedRelationship() throws Exception {
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(GOOD_LLM_RESPONSE);
        assertTrue(ArchiMateSchemaValidator.validate(parsed).isEmpty());

        Object factory = factory();
        Object model = createModel(factory);
        importWithDiagram(parsed, model, null);
        assertTrue("Setup should add two elements and a relationship", countElementsInModel(model) >= 3);
        assertTrue(modelContainsId(model, E1));
        assertTrue(modelContainsId(model, R1));

        ArchiMateLLMResult remove = ArchiMateLLMResultParser.parse(
                "{\"elements\":[],\"relationships\":[],\"removeElementIds\":[\"" + E1 + "\"]}");
        importWithDiagram(remove, model, null);

        assertFalse("Removed element should leave the model", modelContainsId(model, E1));
        assertFalse("Relationships attached to the removed element should also be deleted", modelContainsId(model, R1));
        assertTrue("Unrelated element should remain", modelContainsId(model, E2));
    }

    @Test
    public void importRemoveElement_hyphenatedUuidStillMatches() throws Exception {
        Object factory = factory();
        Object model = createModel(factory);
        addNamedConcept(factory, model, "createBusinessActor", "Customer", E1);

        String hyphenated = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ArchiMateLLMResult remove = ArchiMateLLMResultParser.parse(
                "{\"elements\":[],\"relationships\":[],\"removeElementIds\":[\"" + hyphenated + "\"]}");
        importWithDiagram(remove, model, null);

        assertFalse(modelContainsId(model, E1));
    }

    @Test
    public void importRemoveElement_uniqueNameFallback() throws Exception {
        Object factory = factory();
        Object model = createModel(factory);
        addNamedConcept(factory, model, "createBusinessActor", "Customer", E1);
        addNamedConcept(factory, model, "createBusinessRole", "Buyer", E2);

        ArchiMateLLMResult remove = ArchiMateLLMResultParser.parse(
                "{\"elements\":[],\"relationships\":[],\"removeElementIds\":[\"Customer\"]}");
        importWithDiagram(remove, model, null);

        assertFalse(modelContainsId(model, E1));
        assertTrue(modelContainsId(model, E2));
    }

    @Test
    public void importRemoveElement_ambiguousNameDoesNotDelete() throws Exception {
        Object factory = factory();
        Object model = createModel(factory);
        addNamedConcept(factory, model, "createBusinessActor", "Customer", E1);
        String e3 = "id-d4e5f67890abcdef1234567890abcdef";
        addNamedConcept(factory, model, "createBusinessActor", "Customer", e3);

        ArchiMateLLMResult remove = ArchiMateLLMResultParser.parse(
                "{\"elements\":[],\"relationships\":[],\"removeElementIds\":[\"Customer\"]}");
        importWithDiagram(remove, model, null);

        assertTrue(modelContainsId(model, E1));
        assertTrue(modelContainsId(model, e3));
    }

    @Test
    public void importRemoveFromDiagramOnly_leavesElementInModel() throws Exception {
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(GOOD_LLM_RESPONSE);
        assertTrue(ArchiMateSchemaValidator.validate(parsed).isEmpty());

        Object factory = factory();
        Object model = createModel(factory);
        Object diagram = addEmptyDiagram(factory, model, "Open View");
        importWithDiagram(parsed, model, diagram);
        assertTrue(childCount(diagram) >= 2);
        assertTrue(modelContainsId(model, E1));

        ArchiMateLLMResult remove = ArchiMateLLMResultParser.parse(
                "{\"elements\":[],\"relationships\":[],\"removeElementFromDiagramIds\":[\"" + E1 + "\"]}");
        importWithDiagram(remove, model, diagram);

        assertTrue("Diagram-only remove must keep the concept in the model", modelContainsId(model, E1));
        assertEquals("Figure should be gone from the open view", 1, childCount(diagram));
    }

    @Test
    public void importDuplicateElementNames_stillAttachesRelationshipsUsingLlmIds() throws Exception {
        Object factory = factory();
        Object model = createModel(factory);
        addNamedConcept(factory, model, "createBusinessActor", "Customer", E1);
        addNamedConcept(factory, model, "createBusinessRole", "Buyer", E2);
        int before = countElementsInModel(model);

        String newActorId = "id-eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
        String newRoleId = "id-ffffffffffffffffffffffffffffffff";
        String newRelId = "id-dddddddddddddddddddddddddddddddd";
        String json = "{\"elements\":["
                + "{\"type\":\"BusinessActor\",\"name\":\"Customer\",\"id\":\"" + newActorId + "\"},"
                + "{\"type\":\"BusinessRole\",\"name\":\"Buyer\",\"id\":\"" + newRoleId + "\"}"
                + "],\"relationships\":["
                + "{\"type\":\"AssignmentRelationship\",\"source\":\"" + newActorId + "\",\"target\":\"" + newRoleId
                + "\",\"name\":\"\",\"id\":\"" + newRelId + "\"}]}";
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(json);
        assertTrue(ArchiMateSchemaValidator.validate(parsed).isEmpty());
        importWithDiagram(parsed, model, null);

        assertEquals("Existing elements must not be duplicated", before, countElementsInModel(model) - 1);
        assertTrue(modelContainsId(model, E1));
        assertTrue(modelContainsId(model, E2));
        assertTrue("New relationship should land in the model", modelContainsId(model, newRelId));
    }

    @Test
    public void importRenameById_updatesNameWithoutDuplicating() throws Exception {
        Object factory = factory();
        Object model = createModel(factory);
        addNamedConcept(factory, model, "createApplicationComponent", "System xxx", E1);
        int before = countElementsInModel(model);

        String json = "{\"elements\":[{\"type\":\"ApplicationComponent\",\"name\":\"System\",\"id\":\"" + E1
                + "\"}],\"relationships\":[]}";
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(json);
        assertTrue(ArchiMateSchemaValidator.validate(parsed).isEmpty());
        importWithDiagram(parsed, model, null);

        assertEquals("Rename must not create a second element", before, countElementsInModel(model));
        assertEquals("System", nameOfId(model, E1));
        assertTrue(modelContainsId(model, E1));
    }

    @Test
    public void importWithTargetDiagram_newElementIsInModelFolderAndOffsetFromExisting() throws Exception {
        Object factory = factory();
        Object model = createModel(factory);
        Object existing = addNamedConcept(factory, model, "createBusinessActor", "Customer", E1);
        Object diagram = addEmptyDiagram(factory, model, "Open View");
        addFigure(factory, diagram, existing);

        String json = "{\"elements\":[{\"type\":\"BusinessRole\",\"name\":\"Buyer\",\"id\":\"" + E2
                + "\"}],\"relationships\":[]}";
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(json);
        assertTrue(ArchiMateSchemaValidator.validate(parsed).isEmpty());
        importWithDiagram(parsed, model, diagram);

        assertTrue("New element must appear in the model tree (a folder), not only as a figure",
                modelContainsId(model, E2));
        Object created = findById(model, E2);
        assertNotNull(created);
        Object container = created.getClass().getMethod("eContainer").invoke(created);
        assertTrue("Imported element must be contained by a folder",
                Class.forName("com.archimatetool.model.IFolder").isInstance(container));
        assertEquals(2, childCount(diagram));
        int[] newBounds = figureBoundsForElementId(diagram, E2);
        assertNotNull(newBounds);
        assertFalse("New figure must not land on the existing (50,50) object",
                newBounds[0] == 50 && newBounds[1] == 50);
        assertTrue("New figure should sit left of or below the existing cluster",
                newBounds[0] <= 50 || newBounds[1] >= 50 + 55);
    }

    @Test
    public void importDiagramNodes_doesNotStackSecondFigureForElementAlreadyOnView() throws Exception {
        Object factory = factory();
        Object model = createModel(factory);
        Object existing = addNamedConcept(factory, model, "createBusinessActor", "Customer", E1);
        Object diagram = addEmptyDiagram(factory, model, "Existing View");
        addFigure(factory, diagram, existing);
        assertEquals(1, childCount(diagram));

        String json = "{\"elements\":[{\"type\":\"BusinessActor\",\"name\":\"Customer\",\"id\":\"" + E1
                + "\"}],\"relationships\":[],"
                + "\"diagram\":{\"name\":\"Existing View\",\"viewpoint\":\"\",\"nodes\":["
                + "{\"elementId\":\"" + E1 + "\",\"x\":50,\"y\":50,\"width\":120,\"height\":55}],\"connections\":[]}}";
        ArchiMateLLMResult parsed = ArchiMateLLMResultParser.parse(json);
        assertTrue(ArchiMateSchemaValidator.validate(parsed).isEmpty());
        importWithDiagram(parsed, model, null);

        assertEquals("Existing figure must not be duplicated on top of itself", 1, childCount(diagram));
    }

    private static boolean modelContainsId(Object model, String id) throws Exception {
        Object folders = model.getClass().getMethod("getFolders").invoke(model);
        return folderContainsId(folders, id);
    }

    private static boolean folderContainsId(Object folders, String id) throws Exception {
        for (Object folder : (Iterable<?>) folders) {
            Object elements = folder.getClass().getMethod("getElements").invoke(folder);
            for (Object el : (Iterable<?>) elements) {
                Object existing = el.getClass().getMethod("getId").invoke(el);
                if (id.equals(existing)) {
                    return true;
                }
            }
            Object childFolders = folder.getClass().getMethod("getFolders").invoke(folder);
            if (childFolders instanceof Iterable && folderContainsId(childFolders, id)) {
                return true;
            }
        }
        return false;
    }

    private static String nameOfId(Object model, String id) throws Exception {
        Object found = findById(model, id);
        if (found == null) {
            return null;
        }
        Object name = found.getClass().getMethod("getName").invoke(found);
        return name != null ? name.toString() : null;
    }

    private static Object findById(Object model, String id) throws Exception {
        Object folders = model.getClass().getMethod("getFolders").invoke(model);
        return findByIdInFolders(folders, id);
    }

    private static Object findByIdInFolders(Object folders, String id) throws Exception {
        for (Object folder : (Iterable<?>) folders) {
            Object elements = folder.getClass().getMethod("getElements").invoke(folder);
            for (Object el : (Iterable<?>) elements) {
                Object existing = el.getClass().getMethod("getId").invoke(el);
                if (id.equals(existing)) {
                    return el;
                }
            }
            Object childFolders = folder.getClass().getMethod("getFolders").invoke(folder);
            if (childFolders instanceof Iterable) {
                Object nested = findByIdInFolders(childFolders, id);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static int[] figureBoundsForElementId(Object diagram, String elementId) throws Exception {
        for (Object child : (List<?>) diagram.getClass().getMethod("getChildren").invoke(diagram)) {
            Object element;
            try {
                element = child.getClass().getMethod("getArchimateElement").invoke(child);
            } catch (NoSuchMethodException missing) {
                continue;
            }
            if (element == null) {
                continue;
            }
            Object id = element.getClass().getMethod("getId").invoke(element);
            if (!elementId.equals(id)) {
                continue;
            }
            Object bounds = child.getClass().getMethod("getBounds").invoke(child);
            int x = (Integer) bounds.getClass().getMethod("getX").invoke(bounds);
            int y = (Integer) bounds.getClass().getMethod("getY").invoke(bounds);
            return new int[] { x, y };
        }
        return null;
    }

    private static Object factory() throws Exception {
        return Class.forName("com.archimatetool.model.IArchimateFactory").getField("eINSTANCE").get(null);
    }

    private static Object createModel(Object factory) throws Exception {
        Object model = factory.getClass().getMethod("createArchimateModel").invoke(factory);
        model.getClass().getMethod("setDefaults").invoke(model);
        return model;
    }

    private static void importWithDiagram(ArchiMateLLMResult parsed, Object model, Object diagram) throws Exception {
        Method importMethod = ArchiMateLLMImporter.class.getMethod("importIntoModel", ArchiMateLLMResult.class,
                Class.forName("com.archimatetool.model.IArchimateModel"), Class.forName("com.archimatetool.model.IFolder"),
                Class.forName("com.archimatetool.model.IArchimateDiagramModel"));
        importMethod.invoke(null, parsed, model, null, diagram);
    }

    private static Object addEmptyDiagram(Object factory, Object model, String name) throws Exception {
        Object diagramsFolder = model.getClass().getMethod("getFolder", Class.forName("com.archimatetool.model.FolderType"))
                .invoke(model, Class.forName("com.archimatetool.model.FolderType").getField("DIAGRAMS").get(null));
        Object diagram = factory.getClass().getMethod("createArchimateDiagramModel").invoke(factory);
        diagram.getClass().getMethod("setName", String.class).invoke(diagram, name);
        @SuppressWarnings("unchecked")
        java.util.List<Object> folderElements = (java.util.List<Object>) diagramsFolder.getClass().getMethod("getElements")
                .invoke(diagramsFolder);
        folderElements.add(diagram);
        return diagram;
    }

    private static Object addNamedConcept(Object factory, Object model, String createMethod, String name, String id) throws Exception {
        Object concept = factory.getClass().getMethod(createMethod).invoke(factory);
        concept.getClass().getMethod("setName", String.class).invoke(concept, name);
        concept.getClass().getMethod("setId", String.class).invoke(concept, id);
        Object defaultFolder = model.getClass()
                .getMethod("getDefaultFolderForObject", Class.forName("org.eclipse.emf.ecore.EObject"))
                .invoke(model, concept);
        @SuppressWarnings("unchecked")
        java.util.List<Object> folderElements = (java.util.List<Object>) defaultFolder.getClass().getMethod("getElements")
                .invoke(defaultFolder);
        folderElements.add(concept);
        return concept;
    }

    private static void addFigure(Object factory, Object diagram, Object element) throws Exception {
        Object dmo = factory.getClass().getMethod("createDiagramModelArchimateObject").invoke(factory);
        dmo.getClass().getMethod("setArchimateElement", Class.forName("com.archimatetool.model.IArchimateElement"))
                .invoke(dmo, element);
        dmo.getClass().getMethod("setBounds", int.class, int.class, int.class, int.class)
                .invoke(dmo, 50, 50, 120, 55);
        @SuppressWarnings("unchecked")
        java.util.List<Object> children = (java.util.List<Object>) diagram.getClass().getMethod("getChildren").invoke(diagram);
        children.add(dmo);
    }

    private static Object findDiagramByName(Object model, String expected) throws Exception {
        Object diagramModels = model.getClass().getMethod("getDiagramModels").invoke(model);
        for (Object dm : (Iterable<?>) diagramModels) {
            Object name = dm.getClass().getMethod("getName").invoke(dm);
            if (expected.equals(name != null ? name.toString() : null)) {
                return dm;
            }
        }
        return null;
    }

    private static int childCount(Object diagram) throws Exception {
        return ((List<?>) diagram.getClass().getMethod("getChildren").invoke(diagram)).size();
    }

    private static int sourceConnectionCount(Object diagram) throws Exception {
        int connectionCount = 0;
        for (Object child : (List<?>) diagram.getClass().getMethod("getChildren").invoke(diagram)) {
            Object sourceConns = child.getClass().getMethod("getSourceConnections").invoke(child);
            if (sourceConns instanceof List) {
                connectionCount += ((List<?>) sourceConns).size();
            }
        }
        return connectionCount;
    }
}
