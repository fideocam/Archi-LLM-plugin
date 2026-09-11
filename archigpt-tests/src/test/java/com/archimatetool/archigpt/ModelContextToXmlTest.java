package com.archimatetool.archigpt;

import static org.junit.Assert.*;

import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for serializing the ArchiMate model to XML for the LLM prompt.
 * Uses reflection so tests compile without Archi; tests that need the model are skipped when Archi is not on classpath.
 */
public class ModelContextToXmlTest {

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
        if (!ARCHI_AVAILABLE) {
            org.junit.Assume.assumeTrue("Archi model not on classpath; skip ModelContextToXml tests", false);
        }
    }

    @Test
    public void toXml_nullModel_returnsEmptyString() throws Exception {
        try {
            Class<?> modelClass = Class.forName("com.archimatetool.model.IArchimateModel");
            String result = (String) ModelContextToXml.class.getMethod("toXml", modelClass).invoke(null, (Object) null);
            assertEquals("", result);
        } catch (ClassNotFoundException e) {
            org.junit.Assume.assumeNoException("Archi not on classpath", e);
        }
    }

    @Test
    public void toXml_emptyModel_returnsValidXmlWithRoot() throws Exception {
        if (!ARCHI_AVAILABLE) return;
        Object model = createEmptyModel("TestModel");
        String xml = (String) ModelContextToXml.class.getMethod("toXml", Class.forName("com.archimatetool.model.IArchimateModel")).invoke(null, model);
        assertNotNull(xml);
        assertTrue("Should start with XML declaration", xml.startsWith("<?xml version=\"1.0\""));
        assertTrue("Should contain root element", xml.contains("<archimateModel"));
        assertTrue("Should contain model name", xml.contains("name=\"TestModel\""));
        assertTrue("Should close root", xml.contains("</archimateModel>"));
    }

    @Test
    public void toXml_modelWithFolderAndElement_includesThemInXml() throws Exception {
        if (!ARCHI_AVAILABLE) return;
        Object model = createModelWithFolderAndElement("WithContent", "Business", "Customer", "elem-1");
        String xml = (String) ModelContextToXml.class.getMethod("toXml", Class.forName("com.archimatetool.model.IArchimateModel")).invoke(null, model);
        assertTrue("Should contain folder", xml.contains("<folder"));
        assertTrue("Should contain folder name Business", xml.contains("name=\"Business\""));
        assertTrue("Should contain element", xml.contains("<element"));
        assertTrue("Should contain BusinessActor type", xml.contains("BusinessActor"));
        assertTrue("Should contain element name Customer", xml.contains("Customer"));
        assertTrue("Should contain element id", xml.contains("elem-1"));
    }

    @Test
    public void toXml_includesDocumentationWhenPresent() throws Exception {
        if (!ARCHI_AVAILABLE) return;
        Object model = createModelWithFolderAndElement("WithDocs", "Business", "Customer", "elem-1");
        Object actor = findNamed("Customer", model);
        assertNotNull(actor);
        actor.getClass().getMethod("setDocumentation", String.class).invoke(actor, "The buyer of products.");
        String xml = (String) ModelContextToXml.class.getMethod("toXml", Class.forName("com.archimatetool.model.IArchimateModel")).invoke(null, model);
        assertTrue("Should include documentation element", xml.contains("<documentation>"));
        assertTrue("Should include documentation text", xml.contains("The buyer of products."));
        assertTrue("Element with documentation should not be self-closing", xml.contains("</element>"));
    }

    @Test
    public void toXml_omitsEmptyDocumentation() throws Exception {
        if (!ARCHI_AVAILABLE) return;
        Object model = createModelWithFolderAndElement("NoDocs", "Business", "Customer", "elem-1");
        String xml = (String) ModelContextToXml.class.getMethod("toXml", Class.forName("com.archimatetool.model.IArchimateModel")).invoke(null, model);
        assertFalse("Empty documentation must not appear", xml.contains("<documentation>"));
        assertTrue("Element without documentation stays self-closing", xml.contains("<element "));
        assertTrue(xml.contains("/>"));
    }

    @Test
    public void toXml_includesRelationshipsAndTheirDocumentation() throws Exception {
        if (!ARCHI_AVAILABLE) return;
        Object model = createModelWithAssignment("RelModel");
        String xml = (String) ModelContextToXml.class.getMethod("toXml", Class.forName("com.archimatetool.model.IArchimateModel")).invoke(null, model);
        assertTrue("Relationships must appear in folder XML, not only on diagrams", xml.contains("<relationship"));
        assertTrue(xml.contains("AssignmentRelationship"));
        assertTrue(xml.contains("id-rel-1") || xml.contains("rel-1"));
        assertTrue("Relationship documentation must be serialized", xml.contains("Assigns the role."));
        assertTrue(xml.contains("</relationship>"));
        assertTrue("Element documentation must still be serialized", xml.contains("The customer."));
    }

    @Test
    public void toXml_escapesSpecialCharactersInName() throws Exception {
        if (!ARCHI_AVAILABLE) return;
        Object model = createEmptyModel("Model \"with\" <tags> & amps");
        String xml = (String) ModelContextToXml.class.getMethod("toXml", Class.forName("com.archimatetool.model.IArchimateModel")).invoke(null, model);
        assertTrue("Should escape quotes", xml.contains("&quot;"));
        assertTrue("Should escape <", xml.contains("&lt;"));
        assertTrue("Should escape >", xml.contains("&gt;"));
        assertTrue("Should escape &", xml.contains("&amp;"));
    }

    private static Object createEmptyModel(String name) throws Exception {
        Class<?> factoryClass = Class.forName("com.archimatetool.model.IArchimateFactory");
        Object factory = factoryClass.getField("eINSTANCE").get(null);
        Object model = factoryClass.getMethod("createArchimateModel").invoke(factory);
        model.getClass().getMethod("setDefaults").invoke(model);
        model.getClass().getMethod("setName", String.class).invoke(model, name);
        return model;
    }

    private static Object createModelWithFolderAndElement(String modelName, String folderName, String elementName, String elementId) throws Exception {
        Object model = createEmptyModel(modelName);
        Class<?> factoryClass = Class.forName("com.archimatetool.model.IArchimateFactory");
        Object factory = factoryClass.getField("eINSTANCE").get(null);
        Object folder = factoryClass.getMethod("createFolder").invoke(factory);
        folder.getClass().getMethod("setName", String.class).invoke(folder, folderName);
        Method getFolders = model.getClass().getMethod("getFolders");
        @SuppressWarnings("unchecked")
        java.util.List<Object> folders = (java.util.List<Object>) getFolders.invoke(model);
        folders.add(folder);
        Object packageInst = Class.forName("com.archimatetool.model.IArchimatePackage").getField("eINSTANCE").get(null);
        Object businessActorClass = packageInst.getClass().getMethod("getBusinessActor").invoke(packageInst);
        Object actor = factoryClass.getMethod("create", Class.forName("org.eclipse.emf.ecore.EClass")).invoke(factory, businessActorClass);
        actor.getClass().getMethod("setName", String.class).invoke(actor, elementName);
        actor.getClass().getMethod("setId", String.class).invoke(actor, elementId);
        @SuppressWarnings("unchecked")
        java.util.List<Object> elements = (java.util.List<Object>) folder.getClass().getMethod("getElements").invoke(folder);
        elements.add(actor);
        return model;
    }

    private static Object createModelWithAssignment(String modelName) throws Exception {
        Object model = createEmptyModel(modelName);
        Class<?> factoryClass = Class.forName("com.archimatetool.model.IArchimateFactory");
        Object factory = factoryClass.getField("eINSTANCE").get(null);
        Class<?> eObjectClass = Class.forName("org.eclipse.emf.ecore.EObject");
        Class<?> conceptClass = Class.forName("com.archimatetool.model.IArchimateConcept");

        Object actor = factoryClass.getMethod("createBusinessActor").invoke(factory);
        actor.getClass().getMethod("setName", String.class).invoke(actor, "Customer");
        actor.getClass().getMethod("setId", String.class).invoke(actor, "id-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        actor.getClass().getMethod("setDocumentation", String.class).invoke(actor, "The customer.");
        addToDefaultFolder(model, actor, eObjectClass);

        Object role = factoryClass.getMethod("createBusinessRole").invoke(factory);
        role.getClass().getMethod("setName", String.class).invoke(role, "Buyer");
        role.getClass().getMethod("setId", String.class).invoke(role, "id-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        addToDefaultFolder(model, role, eObjectClass);

        Object rel = factoryClass.getMethod("createAssignmentRelationship").invoke(factory);
        rel.getClass().getMethod("setName", String.class).invoke(rel, "assigned");
        rel.getClass().getMethod("setId", String.class).invoke(rel, "rel-1");
        rel.getClass().getMethod("setDocumentation", String.class).invoke(rel, "Assigns the role.");
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

    private static Object findNamed(String name, Object model) throws Exception {
        Object folders = model.getClass().getMethod("getFolders").invoke(model);
        return findNamedInFolders(name, folders);
    }

    private static Object findNamedInFolders(String name, Object folders) throws Exception {
        for (Object folder : (Iterable<?>) folders) {
            Object elements = folder.getClass().getMethod("getElements").invoke(folder);
            for (Object el : (Iterable<?>) elements) {
                Object existing = el.getClass().getMethod("getName").invoke(el);
                if (name.equals(existing)) {
                    return el;
                }
            }
            Object childFolders = folder.getClass().getMethod("getFolders").invoke(folder);
            if (childFolders instanceof Iterable) {
                Object nested = findNamedInFolders(name, childFolders);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

}
