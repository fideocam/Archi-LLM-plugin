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
        Object actor = factoryClass.getMethod("create", org.eclipse.emf.ecore.EClass.class).invoke(factory, businessActorClass);
        actor.getClass().getMethod("setName", String.class).invoke(actor, elementName);
        actor.getClass().getMethod("setId", String.class).invoke(actor, elementId);
        @SuppressWarnings("unchecked")
        java.util.List<Object> elements = (java.util.List<Object>) folder.getClass().getMethod("getElements").invoke(folder);
        elements.add(actor);
        return model;
    }

}
