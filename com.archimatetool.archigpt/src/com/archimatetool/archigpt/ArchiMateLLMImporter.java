/**
 * Imports validated ArchiMateLLMResult into an open Archi model (elements and relationships).
 */
package com.archimatetool.archigpt;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IFolder;

/**
 * Adds elements and relationships from a validated ArchiMateLLMResult to an existing IArchimateModel.
 */
@SuppressWarnings("nls")
public final class ArchiMateLLMImporter {

    private ArchiMateLLMImporter() {}

    /**
     * Import the validated result into the given model (uses default folders per type).
     * Call ArchiMateSchemaValidator.validate() before calling this.
     */
    public static void importIntoModel(ArchiMateLLMResult result, IArchimateModel model) {
        importIntoModel(result, model, null, null);
    }

    /**
     * Import with optional target folder (no diagram).
     */
    public static void importIntoModel(ArchiMateLLMResult result, IArchimateModel model, IFolder targetFolder) {
        importIntoModel(result, model, targetFolder, null);
    }

    /**
     * Import the validated result into the given model. When targetFolder is non-null, new elements
     * and relationships are added to that folder. When targetDiagram is non-null, new elements are
     * also added as figures to that diagram (e.g. the open or selected view).
     */
    public static void importIntoModel(ArchiMateLLMResult result, IArchimateModel model, IFolder targetFolder, IArchimateDiagramModel targetDiagram) {
        Map<String, IArchimateConcept> idToConcept = new HashMap<>();
        Map<String, IArchimateRelationship> idToRelationship = new HashMap<>();

        int diagramY = 50;
        final int elementWidth = 120;
        final int elementHeight = 55;
        final int gap = 25;

        for (ArchiMateLLMResult.ElementSpec e : result.getElements()) {
            EClass eClass = (EClass) IArchimatePackage.eINSTANCE.getEClassifier(e.getType());
            if (eClass == null || !IArchimatePackage.eINSTANCE.getArchimateElement().isSuperTypeOf(eClass)) {
                continue;
            }
            String name = e.getName() != null ? e.getName() : "";
            if (elementExistsInModel(model, eClass, name)) {
                continue;
            }
            IArchimateElement element = (IArchimateElement) IArchimateFactory.eINSTANCE.create(eClass);
            element.setName(name);
            element.setId(e.getId());
            IFolder folder = targetFolder != null ? targetFolder : model.getDefaultFolderForObject(element);
            folder.getElements().add(element);
            idToConcept.put(e.getId(), element);

            if (targetDiagram != null && result.getDiagram() == null) {
                IDiagramModelArchimateObject dmo = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
                dmo.setArchimateElement(element);
                dmo.setBounds(50, diagramY, elementWidth, elementHeight);
                targetDiagram.getChildren().add(dmo);
                diagramY += elementHeight + gap;
            }
        }

        for (ArchiMateLLMResult.RelationshipSpec r : result.getRelationships()) {
            EClass rClass = (EClass) IArchimatePackage.eINSTANCE.getEClassifier(r.getType());
            if (rClass == null || !IArchimatePackage.eINSTANCE.getArchimateRelationship().isSuperTypeOf(rClass)) {
                continue;
            }
            IArchimateElement source = (IArchimateElement) idToConcept.get(r.getSource());
            IArchimateElement target = (IArchimateElement) idToConcept.get(r.getTarget());
            if (source == null || target == null) {
                continue;
            }
            IArchimateRelationship rel = (IArchimateRelationship) IArchimateFactory.eINSTANCE.create(rClass);
            rel.setName(r.getName() != null ? r.getName() : "");
            if (r.getId() != null && !r.getId().isEmpty()) {
                rel.setId(r.getId());
                idToRelationship.put(r.getId(), rel);
            }
            rel.setSource(source);
            rel.setTarget(target);
            IFolder folder = targetFolder != null ? targetFolder : model.getDefaultFolderForObject(rel);
            folder.getElements().add(rel);
        }

        if (result.getDiagram() != null && result.getDiagram().getName() != null && !result.getDiagram().getName().isEmpty()) {
            createNewDiagram(result.getDiagram(), model, idToConcept, idToRelationship);
        }
    }

    private static void createNewDiagram(ArchiMateLLMResult.DiagramSpec spec, IArchimateModel model,
            Map<String, IArchimateConcept> idToConcept, Map<String, IArchimateRelationship> idToRelationship) {
        IArchimateDiagramModel diagram = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        diagram.setName(spec.getName());
        if (spec.getViewpoint() != null && !spec.getViewpoint().isEmpty()) {
            diagram.setViewpoint(spec.getViewpoint());
        }
        model.getDiagramModels().add(diagram);

        Map<String, IDiagramModelArchimateObject> elementIdToDiagramObject = new HashMap<>();
        for (ArchiMateLLMResult.DiagramNodeSpec node : spec.getNodes()) {
            IArchimateConcept concept = idToConcept.get(node.getElementId());
            if (!(concept instanceof IArchimateElement)) continue;
            IDiagramModelArchimateObject dmo = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
            dmo.setArchimateElement((IArchimateElement) concept);
            dmo.setBounds(node.getX(), node.getY(), node.getWidth(), node.getHeight());
            diagram.getChildren().add(dmo);
            elementIdToDiagramObject.put(node.getElementId(), dmo);
        }

        for (ArchiMateLLMResult.DiagramConnectionSpec connSpec : spec.getConnections()) {
            IDiagramModelArchimateObject sourceDmo = elementIdToDiagramObject.get(connSpec.getSourceElementId());
            IDiagramModelArchimateObject targetDmo = elementIdToDiagramObject.get(connSpec.getTargetElementId());
            if (sourceDmo == null || targetDmo == null) continue;
            IDiagramModelConnection conn = IArchimateFactory.eINSTANCE.createDiagramModelConnection();
            conn.setSource(sourceDmo);
            conn.setTarget(targetDmo);
            if (connSpec.getRelationshipId() != null && !connSpec.getRelationshipId().isEmpty()) {
                IArchimateRelationship rel = idToRelationship.get(connSpec.getRelationshipId());
                if (rel != null) setConnectionRelationship(conn, rel);
            }
            addConnectionToDiagram(diagram, conn);
        }
    }

    @SuppressWarnings("unchecked")
    private static void addConnectionToDiagram(IArchimateDiagramModel diagram, IDiagramModelConnection conn) {
        try {
            java.lang.reflect.Method getConn = diagram.getClass().getMethod("getConnections");
            java.util.List<IDiagramModelConnection> list = (java.util.List<IDiagramModelConnection>) getConn.invoke(diagram);
            if (list != null) list.add(conn);
        } catch (Exception e) {
            try {
                Object src = conn.getSource();
                if (src != null) {
                    java.lang.reflect.Method getOut = src.getClass().getMethod("getSourceConnections");
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> list = (java.util.List<Object>) getOut.invoke(src);
                    if (list != null) list.add(conn);
                }
            } catch (Exception e2) {
                // skip connection
            }
        }
    }

    private static void setConnectionRelationship(IDiagramModelConnection conn, IArchimateRelationship rel) {
        try {
            conn.getClass().getMethod("setRelationship", IArchimateRelationship.class).invoke(conn, rel);
        } catch (Exception e1) {
            try {
                conn.getClass().getMethod("setArchimateRelationship", IArchimateRelationship.class).invoke(conn, rel);
            } catch (Exception e2) {
                // API may use different method name
            }
        }
    }

    private static boolean elementExistsInModel(IArchimateModel model, EClass eClass, String name) {
        if (model == null || eClass == null) return false;
        String n = name == null ? "" : name.trim();
        for (IFolder f : model.getFolders()) {
            if (folderContainsElementWithTypeAndName(f, eClass, n)) return true;
        }
        return false;
    }

    private static boolean folderContainsElementWithTypeAndName(IFolder folder, EClass eClass, String name) {
        if (folder == null) return false;
        for (EObject obj : folder.getElements()) {
            if (obj instanceof IArchimateElement && eClass.isInstance(obj)) {
                String existing = ((IArchimateElement) obj).getName();
                if (existing != null && existing.trim().equalsIgnoreCase(name)) return true;
            }
        }
        for (IFolder child : folder.getFolders()) {
            if (folderContainsElementWithTypeAndName(child, eClass, name)) return true;
        }
        return false;
    }
}
