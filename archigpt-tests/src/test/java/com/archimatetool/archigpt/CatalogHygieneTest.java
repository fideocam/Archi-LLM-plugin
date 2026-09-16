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

    private static Object createModelWithAssignment() throws Exception {
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
        return model;
    }

    private static void addToDefaultFolder(Object model, Object concept, Class<?> eObjectClass) throws Exception {
        Object folder = model.getClass().getMethod("getDefaultFolderForObject", eObjectClass).invoke(model, concept);
        @SuppressWarnings("unchecked")
        java.util.List<Object> elements = (java.util.List<Object>) folder.getClass().getMethod("getElements").invoke(folder);
        elements.add(concept);
    }
}
