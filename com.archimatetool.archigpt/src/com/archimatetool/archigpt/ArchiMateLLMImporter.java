/**
 * Imports validated ArchiMateLLMResult into an open Archi model (elements and relationships).
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.FolderType;

/**
 * Adds elements and relationships from a validated ArchiMateLLMResult to an existing IArchimateModel.
 */
@SuppressWarnings("nls")
public final class ArchiMateLLMImporter {

    /** ArchiMate/Archi identifier format: id- plus 32 hex chars (xs:ID / NCName friendly). */
    private static final Pattern ARCHIMATE_ID = Pattern.compile("id-[0-9a-fA-F]{32}");

    private ArchiMateLLMImporter() {}

    /**
     * Return a valid ArchiMate identifier (id- + 32 hex). Normalizes hyphenated UUIDs or generates a new id if missing/invalid.
     */
    static String ensureArchiMateId(String id) {
        if (id != null && !id.isEmpty()) {
            if (ARCHIMATE_ID.matcher(id.trim()).matches()) {
                return id.trim();
            }
            String noHyphens = id.trim().replace("-", "");
            if (noHyphens.length() == 32 && noHyphens.matches("[0-9a-fA-F]+")) {
                return "id-" + noHyphens;
            }
        }
        return "id-" + UUID.randomUUID().toString().replace("-", "");
    }

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
            String normalizedType = ArchiMateSchemaValidator.normalizeElementType(e.getType());
            EClass eClass = (EClass) IArchimatePackage.eINSTANCE.getEClassifier(normalizedType);
            if (eClass == null || !IArchimatePackage.eINSTANCE.getArchimateElement().isSuperTypeOf(eClass)) {
                continue;
            }
            String name = e.getName() != null ? e.getName() : "";
            if (elementExistsInModel(model, eClass, name)) {
                continue;
            }
            IArchimateElement element = (IArchimateElement) IArchimateFactory.eINSTANCE.create(eClass);
            element.setName(name);
            String elementId = ensureArchiMateId(e.getId());
            element.setId(elementId);
            IFolder folder = targetFolder != null ? targetFolder : model.getDefaultFolderForObject(element);
            folder.getElements().add(element);
            idToConcept.put(elementId, element);
            if (!elementId.equals(e.getId()) && e.getId() != null && !e.getId().isEmpty()) {
                idToConcept.put(e.getId().trim(), element);
            }

            if (targetDiagram != null && result.getDiagram() == null) {
                IDiagramModelArchimateObject dmo = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
                dmo.setArchimateElement(element);
                dmo.setBounds(50, diagramY, elementWidth, elementHeight);
                targetDiagram.getChildren().add(dmo);
                diagramY += elementHeight + gap;
            }
        }

        for (ArchiMateLLMResult.RelationshipSpec r : result.getRelationships()) {
            String relType = ArchiMateSchemaValidator.normalizeRelationshipType(r.getType());
            EClass rClass = (EClass) IArchimatePackage.eINSTANCE.getEClassifier(relType);
            if (rClass == null || !IArchimatePackage.eINSTANCE.getArchimateRelationship().isSuperTypeOf(rClass)) {
                continue;
            }
            IArchimateConcept sourceConcept = idToConcept.get(r.getSource());
            if (sourceConcept == null) {
                sourceConcept = findConceptById(model, r.getSource());
            }
            IArchimateConcept targetConcept = idToConcept.get(r.getTarget());
            if (targetConcept == null) {
                targetConcept = findConceptById(model, r.getTarget());
            }
            if (!(sourceConcept instanceof IArchimateElement) || !(targetConcept instanceof IArchimateElement)) {
                continue;
            }
            IArchimateElement source = (IArchimateElement) sourceConcept;
            IArchimateElement target = (IArchimateElement) targetConcept;
            IArchimateRelationship rel = (IArchimateRelationship) IArchimateFactory.eINSTANCE.create(rClass);
            rel.setName(r.getName() != null ? r.getName() : "");
            String relId = ensureArchiMateId(r.getId());
            rel.setId(relId);
            idToRelationship.put(relId, rel);
            if (!relId.equals(r.getId()) && r.getId() != null && !r.getId().isEmpty()) {
                idToRelationship.put(r.getId().trim(), rel);
            }
            rel.setSource(source);
            rel.setTarget(target);
            // Always put relationships in the model's Relations folder, not in element folders (e.g. Technology, Strategy)
            IFolder relFolder = model.getDefaultFolderForObject(rel);
            if (relFolder != null) {
                relFolder.getElements().add(rel);
            }
        }

        if (result.getDiagram() != null && result.getDiagram().getName() != null && !result.getDiagram().getName().isEmpty()) {
            String diagramName = result.getDiagram().getName().trim();
            IArchimateDiagramModel existingDiagram = findDiagramByName(model, diagramName);
            if (existingDiagram != null) {
                addNodesAndConnectionsToDiagram(existingDiagram, result.getDiagram(), model, idToConcept, idToRelationship);
            } else {
                createNewDiagram(result.getDiagram(), model, idToConcept, idToRelationship);
            }
        }

        removeFromModel(model, result.getRemoveRelationshipIds(), result.getRemoveElementIds());
    }

    /**
     * Remove concepts by id from the model and from all diagrams (figures and connections).
     * Relationships are removed first, then elements.
     */
    private static void removeFromModel(IArchimateModel model, List<String> relationshipIds, List<String> elementIds) {
        if (model == null || (relationshipIds == null && elementIds == null)) return;
        if (relationshipIds != null) {
            for (String id : relationshipIds) {
                IArchimateConcept concept = findConceptById(model, id);
                if (concept instanceof IArchimateRelationship) {
                    removeRelationshipFromDiagrams(model, (IArchimateRelationship) concept);
                    removeConceptFromFolder(concept);
                }
            }
        }
        if (elementIds != null) {
            for (String id : elementIds) {
                IArchimateConcept concept = findConceptById(model, id);
                if (concept instanceof IArchimateElement) {
                    removeElementFromDiagrams(model, (IArchimateElement) concept);
                    removeConceptFromFolder(concept);
                }
            }
        }
    }

    private static IArchimateConcept findConceptById(IArchimateModel model, String id) {
        if (model == null || id == null || id.isEmpty()) return null;
        for (IFolder folder : model.getFolders()) {
            IArchimateConcept found = findConceptInFolder(folder, id);
            if (found != null) return found;
        }
        return null;
    }

    private static IArchimateConcept findConceptInFolder(IFolder folder, String id) {
        if (folder == null) return null;
        for (EObject obj : folder.getElements()) {
            if (obj instanceof IArchimateConcept) {
                String existing = ((IArchimateConcept) obj).getId();
                if (id.equals(existing)) return (IArchimateConcept) obj;
            }
        }
        for (IFolder child : folder.getFolders()) {
            IArchimateConcept found = findConceptInFolder(child, id);
            if (found != null) return found;
        }
        return null;
    }

    private static void removeConceptFromFolder(IArchimateConcept concept) {
        if (concept == null) return;
        Object container = concept.eContainer();
        if (container instanceof IFolder) {
            ((IFolder) container).getElements().remove(concept);
        }
    }

    private static void removeRelationshipFromDiagrams(IArchimateModel model, IArchimateRelationship relationship) {
        if (model.getDiagramModels() == null) return;
        for (IDiagramModel dm : model.getDiagramModels()) {
            if (!(dm instanceof IArchimateDiagramModel)) continue;
            IArchimateDiagramModel diagram = (IArchimateDiagramModel) dm;
            List<Object> toRemove = new ArrayList<>();
            for (Object child : diagram.getChildren()) {
                if (child instanceof IDiagramModelConnection) {
                    IDiagramModelConnection conn = (IDiagramModelConnection) child;
                    if (getConnectionRelationship(conn) == relationship) toRemove.add(child);
                }
            }
            for (Object obj : toRemove) {
                removeConnectionFromDiagram(diagram, (IDiagramModelConnection) obj);
            }
        }
    }

    private static void removeElementFromDiagrams(IArchimateModel model, IArchimateElement element) {
        if (model.getDiagramModels() == null) return;
        for (IDiagramModel dm : model.getDiagramModels()) {
            if (!(dm instanceof IArchimateDiagramModel)) continue;
            IArchimateDiagramModel diagram = (IArchimateDiagramModel) dm;
            List<Object> figuresToRemove = new ArrayList<>();
            for (Object child : diagram.getChildren()) {
                if (child instanceof IDiagramModelArchimateObject) {
                    if (element.equals(((IDiagramModelArchimateObject) child).getArchimateElement())) {
                        figuresToRemove.add(child);
                    }
                }
            }
            for (Object fig : figuresToRemove) {
                removeDiagramObjectAndConnections(diagram, (IDiagramModelArchimateObject) fig);
            }
        }
    }

    private static void removeDiagramObjectAndConnections(IArchimateDiagramModel diagram, IDiagramModelArchimateObject dmo) {
        List<Object> toRemove = new ArrayList<>();
        for (Object child : diagram.getChildren()) {
            if (child instanceof IDiagramModelConnection) {
                IDiagramModelConnection c = (IDiagramModelConnection) child;
                if (c.getSource() == dmo || c.getTarget() == dmo) toRemove.add(child);
            }
        }
        for (Object obj : toRemove) {
            removeConnectionFromDiagram(diagram, (IDiagramModelConnection) obj);
        }
        diagram.getChildren().remove(dmo);
    }

    private static void removeConnectionFromDiagram(IArchimateDiagramModel diagram, IDiagramModelConnection conn) {
        try {
            java.lang.reflect.Method getConn = diagram.getClass().getMethod("getConnections");
            @SuppressWarnings("unchecked")
            java.util.List<IDiagramModelConnection> list = (java.util.List<IDiagramModelConnection>) getConn.invoke(diagram);
            if (list != null) list.remove(conn);
            return;
        } catch (Exception e) {
            // fallback: connection may be in children
        }
        diagram.getChildren().remove(conn);
    }

    private static IArchimateRelationship getConnectionRelationship(IDiagramModelConnection conn) {
        try {
            java.lang.reflect.Method m = conn.getClass().getMethod("getArchimateRelationship");
            Object rel = m.invoke(conn);
            if (rel instanceof IArchimateRelationship) return (IArchimateRelationship) rel;
        } catch (Exception e1) {
            try {
                java.lang.reflect.Method m = conn.getClass().getMethod("getRelationship");
                Object rel = m.invoke(conn);
                if (rel instanceof IArchimateRelationship) return (IArchimateRelationship) rel;
            } catch (Exception e2) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Find an existing diagram in the model by name (case-insensitive). Returns null if none.
     */
    private static IArchimateDiagramModel findDiagramByName(IArchimateModel model, String name) {
        if (model == null || name == null || name.isEmpty()) return null;
        if (model.getDiagramModels() == null) return null;
        for (IDiagramModel dm : model.getDiagramModels()) {
            if (!(dm instanceof IArchimateDiagramModel)) continue;
            String existingName = dm.getName();
            if (existingName != null && existingName.trim().equalsIgnoreCase(name)) {
                return (IArchimateDiagramModel) dm;
            }
        }
        return null;
    }

    /**
     * Add nodes (figures) and connections from the spec to an existing diagram. Used when a diagram
     * with the same name already exists, so we add to it instead of creating a duplicate.
     */
    private static void addNodesAndConnectionsToDiagram(IArchimateDiagramModel diagram,
            ArchiMateLLMResult.DiagramSpec spec, IArchimateModel model,
            Map<String, IArchimateConcept> idToConcept, Map<String, IArchimateRelationship> idToRelationship) {
        if (diagram == null || spec == null) return;
        Map<String, IDiagramModelArchimateObject> elementIdToDiagramObject = new HashMap<>();
        for (ArchiMateLLMResult.DiagramNodeSpec node : spec.getNodes()) {
            String elementId = node.getElementId();
            if (elementId == null || elementId.isEmpty()) continue;
            IArchimateConcept concept = idToConcept.get(elementId);
            if (concept == null) {
                concept = findConceptById(model, elementId);
            }
            if (!(concept instanceof IArchimateElement)) continue;
            IDiagramModelArchimateObject dmo = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
            dmo.setArchimateElement((IArchimateElement) concept);
            dmo.setBounds(node.getX(), node.getY(), node.getWidth(), node.getHeight());
            diagram.getChildren().add(dmo);
            elementIdToDiagramObject.put(elementId, dmo);
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
                if (rel == null) rel = (IArchimateRelationship) findConceptById(model, connSpec.getRelationshipId());
                if (rel != null) setConnectionRelationship(conn, rel);
            }
            addConnectionToDiagram(diagram, conn);
        }
    }

    private static void createNewDiagram(ArchiMateLLMResult.DiagramSpec spec, IArchimateModel model,
            Map<String, IArchimateConcept> idToConcept, Map<String, IArchimateRelationship> idToRelationship) {
        IArchimateDiagramModel diagram = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        diagram.setName(spec.getName() != null ? spec.getName() : "New View");
        if (spec.getViewpoint() != null && !spec.getViewpoint().isEmpty()) {
            diagram.setViewpoint(spec.getViewpoint());
        }
        // Archi stores diagrams in the DIAGRAMS folder, not in getDiagramModels() (which returns a copy).
        IFolder diagramsFolder = model.getFolder(FolderType.DIAGRAMS);
        if (diagramsFolder != null) {
            diagramsFolder.getElements().add(diagram);
        } else {
            // Fallback if folder structure not initialized
            if (model.getDiagramModels() != null) {
                model.getDiagramModels().add(diagram);
            }
            try {
                diagram.getClass().getMethod("setArchimateModel", IArchimateModel.class).invoke(diagram, model);
            } catch (Exception ignored) {
            }
        }
        addNodesAndConnectionsToDiagram(diagram, spec, model, idToConcept, idToRelationship);
    }

    @SuppressWarnings("unchecked")
    private static void addConnectionToDiagram(IArchimateDiagramModel diagram, IDiagramModelConnection conn) {
        try {
            java.lang.reflect.Method getConn = diagram.getClass().getMethod("getConnections");
            java.util.List<IDiagramModelConnection> list = (java.util.List<IDiagramModelConnection>) getConn.invoke(diagram);
            if (list != null) list.add(conn);
            return;
        } catch (Exception e) {
            // fallback
        }
        try {
            Object src = conn.getSource();
            if (src != null) {
                java.lang.reflect.Method getOut = src.getClass().getMethod("getSourceConnections");
                @SuppressWarnings("unchecked")
                java.util.List<IDiagramModelConnection> list = (java.util.List<IDiagramModelConnection>) getOut.invoke(src);
                if (list != null) list.add(conn);
            }
        } catch (Exception e2) {
            try {
                @SuppressWarnings("rawtypes")
                java.util.List children = diagram.getChildren();
                if (children != null) children.add(conn);
            } catch (Exception e3) {
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
