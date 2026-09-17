package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Unused-relationship inventory is computed in Java so it does not depend on LLM context size.
 */
@SuppressWarnings("nls")
public class CatalogHygieneTest {

    private static final boolean ARCHI_AVAILABLE = hasArchiModel();

    private static boolean hasArchiModel() {
        try {
            Class.forName("com.archimatetool.model.IArchimateModel");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Before
    public void checkArchiAvailable() {
        org.junit.Assume.assumeTrue("Archi model not on classpath", ARCHI_AVAILABLE);
    }

    @Test
    public void wantsUnusedRelationships_toolIdAndPrompt() {
        PromptLibrary.Entry tool = PromptLibrary.findById("tidy-relationships-not-on-views");
        assertTrue(CatalogHygiene.wantsUnusedRelationships(tool, "ignore"));
        assertTrue(CatalogHygiene.wantsUnusedRelationships(null,
                "List model relationships that do not appear as a connection on any view."));
        assertFalse(CatalogHygiene.wantsUnusedRelationships(null, "Describe this view"));
        assertFalse(CatalogHygiene.wantsUnusedRelationships(PromptLibrary.findById("tidy-duplicate-names"),
                "Find duplicate names"));
    }

    @Test
    public void wantsUnusedElements_toolIdAndPrompt() {
        PromptLibrary.Entry tool = PromptLibrary.findById("tidy-elements-not-on-views");
        assertTrue(CatalogHygiene.wantsUnusedElements(tool, "ignore"));
        assertTrue(CatalogHygiene.wantsUnusedElements(null,
                "Which catalog elements never appear as a node on any view?"));
        assertFalse(CatalogHygiene.wantsUnusedElements(null, "Describe this view"));
        assertFalse(CatalogHygiene.wantsUnusedElements(PromptLibrary.findById("tidy-duplicate-names"),
                "Find duplicate names"));
    }

    @Test
    public void findingsFor_emptyWhenNeitherInventoryIsWanted() throws Exception {
        assertEquals("", CatalogHygiene.findingsFor(null, null, "unused relationships"));
        Object model = createModelWithAssignment();
        assertEquals("", CatalogHygiene.findingsFor(
                (com.archimatetool.model.IArchimateModel) model,
                PromptLibrary.findById("tidy-duplicate-names"),
                "Find duplicate names"));
    }

    @Test
    public void assignmentWithNoDiagram_isUnused() throws Exception {
        Object model = createModelWithAssignment();
        String report = CatalogHygiene.findingsFor(
                (com.archimatetool.model.IArchimateModel) model,
                PromptLibrary.findById("tidy-relationships-not-on-views"),
                "");
        assertTrue(report.contains(CatalogHygiene.FINDINGS_START));
        assertTrue(report.contains("Unused relationships (not drawn on any view): 1"));
        assertTrue(report.contains("rel-1"));
        assertTrue(report.contains("Customer"));
        assertTrue(report.contains("Buyer"));
        assertFalse(report.contains("Unused elements"));
    }

    @Test
    public void assignmentWithNoDiagram_unusedElementsAreTheEnds() throws Exception {
        Object model = createModelWithAssignment();
        String report = CatalogHygiene.findingsFor(
                (com.archimatetool.model.IArchimateModel) model,
                PromptLibrary.findById("tidy-elements-not-on-views"),
                "");
        assertTrue(report.contains("Unused elements (not placed on any view): 2"));
        assertTrue(report.contains("Customer"));
        assertTrue(report.contains("Buyer"));
    }

    @Test
    public void assignmentDrawnOnView_isNotUnused() throws Exception {
        Object model = createModelWithDrawnAssignment();
        com.archimatetool.model.IArchimateModel am = (com.archimatetool.model.IArchimateModel) model;
        String rels = CatalogHygiene.findingsFor(am, PromptLibrary.findById("tidy-relationships-not-on-views"), "");
        assertTrue(rels.contains("Unused relationships (not drawn on any view): 0"));
        assertTrue(rels.contains("None. Every catalog relationship appears as a connection on at least one view."));
        assertFalse(rels.contains("rel-1"));

        String els = CatalogHygiene.findingsFor(am, PromptLibrary.findById("tidy-elements-not-on-views"), "");
        assertTrue(els.contains("Unused elements (not placed on any view): 0"));
        assertTrue(els.contains("None. Every catalog element appears as a node on at least one view."));
    }

    @Test
    public void figuresWithoutConnection_leaveRelationshipUnused() throws Exception {
        Object model = createModelWithFiguresButNoConnection();
        String rels = CatalogHygiene.findingsFor(
                (com.archimatetool.model.IArchimateModel) model,
                PromptLibrary.findById("tidy-relationships-not-on-views"),
                "");
        assertTrue(rels.contains("Unused relationships (not drawn on any view): 1"));
        assertTrue(rels.contains("rel-1"));
        String els = CatalogHygiene.findingsFor(
                (com.archimatetool.model.IArchimateModel) model,
                PromptLibrary.findById("tidy-elements-not-on-views"),
                "");
        assertTrue(els.contains("Unused elements (not placed on any view): 0"));
    }

    @Test
    public void findingsFor_promptCanRequestBothInventories() throws Exception {
        Object model = createModelWithAssignment();
        String report = CatalogHygiene.findingsFor(
                (com.archimatetool.model.IArchimateModel) model,
                null,
                "List unused relationships and unused elements that never appear on any view.");
        assertTrue(report.contains("Unused relationships (not drawn on any view): 1"));
        assertTrue(report.contains("Unused elements (not placed on any view): 2"));
        assertTrue(report.contains(CatalogHygiene.FINDINGS_END));
    }

    private static Object createModelWithAssignment() throws Exception {
        return createAssignmentParts()[0];
    }

    private static Object createModelWithDrawnAssignment() throws Exception {
        Object[] parts = createAssignmentParts();
        addView(parts[0], parts[1], parts[2], parts[3], true);
        return parts[0];
    }

    private static Object createModelWithFiguresButNoConnection() throws Exception {
        Object[] parts = createAssignmentParts();
        addView(parts[0], parts[1], parts[2], parts[3], false);
        return parts[0];
    }

    private static Object[] createAssignmentParts() throws Exception {
        Class<?> factoryClass = Class.forName("com.archimatetool.model.IArchimateFactory");
        Object factory = factoryClass.getField("eINSTANCE").get(null);
        Object model = factoryClass.getMethod("createArchimateModel").invoke(factory);
        model.getClass().getMethod("setDefaults").invoke(model);
        model.getClass().getMethod("setName", String.class).invoke(model, "RelModel");
        Class<?> eObjectClass = Class.forName("org.eclipse.emf.ecore.EObject");
        Class<?> conceptClass = Class.forName("com.archimatetool.model.IArchimateConcept");

        Object actor = factoryClass.getMethod("createBusinessActor").invoke(factory);
        actor.getClass().getMethod("setName", String.class).invoke(actor, "Customer");
        actor.getClass().getMethod("setId", String.class).invoke(actor, "id-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        addToDefaultFolder(model, actor, eObjectClass);

        Object role = factoryClass.getMethod("createBusinessRole").invoke(factory);
        role.getClass().getMethod("setName", String.class).invoke(role, "Buyer");
        role.getClass().getMethod("setId", String.class).invoke(role, "id-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        addToDefaultFolder(model, role, eObjectClass);

        Object rel = factoryClass.getMethod("createAssignmentRelationship").invoke(factory);
        rel.getClass().getMethod("setName", String.class).invoke(rel, "assigned");
        rel.getClass().getMethod("setId", String.class).invoke(rel, "rel-1");
        rel.getClass().getMethod("setSource", conceptClass).invoke(rel, actor);
        rel.getClass().getMethod("setTarget", conceptClass).invoke(rel, role);
        addToDefaultFolder(model, rel, eObjectClass);
        return new Object[] { model, actor, role, rel };
    }

    private static void addView(Object model, Object actor, Object role, Object rel, boolean withConnection)
            throws Exception {
        Class<?> factoryClass = Class.forName("com.archimatetool.model.IArchimateFactory");
        Object factory = factoryClass.getField("eINSTANCE").get(null);
        Object diagramsFolder = model.getClass()
                .getMethod("getFolder", Class.forName("com.archimatetool.model.FolderType"))
                .invoke(model, Class.forName("com.archimatetool.model.FolderType").getField("DIAGRAMS").get(null));
        Object diagram = factoryClass.getMethod("createArchimateDiagramModel").invoke(factory);
        diagram.getClass().getMethod("setName", String.class).invoke(diagram, "Drawn");
        @SuppressWarnings("unchecked")
        java.util.List<Object> folderElements = (java.util.List<Object>) diagramsFolder.getClass()
                .getMethod("getElements").invoke(diagramsFolder);
        folderElements.add(diagram);

        Object srcFig = factoryClass.getMethod("createDiagramModelArchimateObject").invoke(factory);
        srcFig.getClass().getMethod("setArchimateElement", Class.forName("com.archimatetool.model.IArchimateElement"))
                .invoke(srcFig, actor);
        Object tgtFig = factoryClass.getMethod("createDiagramModelArchimateObject").invoke(factory);
        tgtFig.getClass().getMethod("setArchimateElement", Class.forName("com.archimatetool.model.IArchimateElement"))
                .invoke(tgtFig, role);
        @SuppressWarnings("unchecked")
        java.util.List<Object> children = (java.util.List<Object>) diagram.getClass().getMethod("getChildren")
                .invoke(diagram);
        children.add(srcFig);
        children.add(tgtFig);
        if (!withConnection) {
            return;
        }
        Object conn = factoryClass.getMethod("createDiagramModelArchimateConnection").invoke(factory);
        conn.getClass()
                .getMethod("setArchimateRelationship", Class.forName("com.archimatetool.model.IArchimateRelationship"))
                .invoke(conn, rel);
        Class<?> connectable = Class.forName("com.archimatetool.model.IConnectable");
        conn.getClass().getMethod("connect", connectable, connectable).invoke(conn, srcFig, tgtFig);
    }

    private static void addToDefaultFolder(Object model, Object concept, Class<?> eObjectClass) throws Exception {
        Object folder = model.getClass().getMethod("getDefaultFolderForObject", eObjectClass).invoke(model, concept);
        @SuppressWarnings("unchecked")
        java.util.List<Object> elements = (java.util.List<Object>) folder.getClass().getMethod("getElements").invoke(folder);
        elements.add(concept);
    }
}
