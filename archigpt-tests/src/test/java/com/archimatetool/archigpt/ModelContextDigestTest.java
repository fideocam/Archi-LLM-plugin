package com.archimatetool.archigpt;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;

public class ModelContextDigestTest {

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
            org.junit.Assume.assumeTrue("Archi model not on classpath; skip ModelContextDigest tests", false);
        }
    }

    @Test
    public void toPlainText_includesModelNameDiagramsAndFolders() throws Exception {
        Object model = createModelWithFolderAndElement("DigestModel", "Business", "Customer", "elem-1");
        Class<?> modelClass = Class.forName("com.archimatetool.model.IArchimateModel");
        String digest = (String) ModelContextDigest.class.getMethod("toPlainText", modelClass).invoke(null, model);
        assertTrue(digest.contains("DigestModel"));
        assertTrue(digest.contains("MODEL DIGEST"));
        assertTrue(digest.contains("Diagrams/views:"));
        assertTrue(digest.contains("ArchiMate elements"));
        assertTrue(digest.contains("Relationships:"));
        assertTrue(digest.contains("By top-level folder"));
    }

    private static Object createEmptyModel(String name) throws Exception {
        Class<?> factoryClass = Class.forName("com.archimatetool.model.IArchimateFactory");
        Object factory = factoryClass.getField("eINSTANCE").get(null);
        Object m = factoryClass.getMethod("createArchimateModel").invoke(factory);
        m.getClass().getMethod("setDefaults").invoke(m);
        m.getClass().getMethod("setName", String.class).invoke(m, name);
        return m;
    }

    private static Object createModelWithFolderAndElement(String modelName, String folderName, String elementName, String elementId)
            throws Exception {
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
}
