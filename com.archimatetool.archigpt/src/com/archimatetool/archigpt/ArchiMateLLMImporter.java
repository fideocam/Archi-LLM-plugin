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
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;

import com.archimatetool.editor.diagram.commands.AddDiagramObjectCommand;
import com.archimatetool.editor.diagram.commands.DiagramCommandFactory;
import com.archimatetool.editor.model.commands.AddListMemberCommand;
import com.archimatetool.editor.model.commands.DeleteArchimateElementCommand;
import com.archimatetool.editor.model.commands.DeleteArchimateRelationshipCommand;
import com.archimatetool.editor.model.commands.DeleteDiagramModelCommand;
import com.archimatetool.editor.model.commands.NonNotifyingCompoundCommand;
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
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.FolderType;

/**
 * Adds elements and relationships from a validated ArchiMateLLMResult to an existing IArchimateModel.
 * When the model exposes a GEF {@link CommandStack} (normal in Archi), all changes are executed as one
 * compound command so Undo/Redo stays consistent.
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
        CommandStack stack = getCommandStack(model);
        if (stack != null) {
            NonNotifyingCompoundCommand compound = new NonNotifyingCompoundCommand("ArchiGPT: apply LLM changes");
            buildImportCommands(compound, result, model, targetFolder, targetDiagram);
            if (!compound.getCommands().isEmpty()) {
                stack.execute(compound);
            }
        } else {
            runImportWithoutCommandStack(result, model, targetFolder, targetDiagram);
        }
    }

    private static CommandStack getCommandStack(IArchimateModel model) {
        if (model == null) {
            return null;
        }
        try {
            Object adapter = model.getAdapter(CommandStack.class);
            return adapter instanceof CommandStack ? (CommandStack) adapter : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void buildImportCommands(NonNotifyingCompoundCommand compound, ArchiMateLLMResult result,
            IArchimateModel model, IFolder targetFolder, IArchimateDiagramModel targetDiagram) {
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
            IFolder defaultFolder = model.getDefaultFolderForObject(element);
            IFolder folder = defaultFolder;
            if (targetFolder != null && defaultFolder != null && targetFolder.getType() == defaultFolder.getType()) {
                folder = targetFolder;
            }
            if (folder != null) {
                compound.add(new AddListMemberCommand("ArchiGPT: add element", folder.getElements(), element));
            }
            idToConcept.put(elementId, element);
            if (!elementId.equals(e.getId()) && e.getId() != null && !e.getId().isEmpty()) {
                idToConcept.put(e.getId().trim(), element);
            }

            if (targetDiagram != null && result.getDiagram() == null) {
                IDiagramModelArchimateObject dmo = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
                dmo.setArchimateElement(element);
                dmo.setBounds(50, diagramY, elementWidth, elementHeight);
                compound.add(new AddDiagramObjectCommand(targetDiagram, dmo));
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
            IFolder relFolder = model.getDefaultFolderForObject(rel);
            compound.add(new AddRelationshipCommand(rel, source, target, relFolder));
        }

        if (result.getDiagram() != null && result.getDiagram().getName() != null && !result.getDiagram().getName().isEmpty()) {
            String diagramName = result.getDiagram().getName().trim();
            IArchimateDiagramModel existingDiagram = findDiagramByName(model, diagramName);
            if (existingDiagram != null) {
                appendDiagramContentCommands(compound, existingDiagram, result.getDiagram(), model, idToConcept, idToRelationship);
            } else {
                appendNewDiagramCommands(compound, result.getDiagram(), model, idToConcept, idToRelationship);
            }
        }

        appendRemoveCommands(compound, model, targetDiagram, result);
    }

    /** When DIAGRAMS folder is missing, attach diagram via model list (rare); supports undo. */
    private static final class AddDiagramToModelFallbackCommand extends Command {
        private final IArchimateModel model;
        private final IArchimateDiagramModel diagram;

        AddDiagramToModelFallbackCommand(IArchimateModel model, IArchimateDiagramModel diagram) {
            super("ArchiGPT: add diagram");
            this.model = model;
            this.diagram = diagram;
        }

        @Override
        public void execute() {
            if (model.getDiagramModels() != null) {
                model.getDiagramModels().add(diagram);
            }
            try {
                diagram.getClass().getMethod("setArchimateModel", IArchimateModel.class).invoke(diagram, model);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void undo() {
            if (model.getDiagramModels() != null) {
                model.getDiagramModels().remove(diagram);
            }
        }
    }

    /** Relationship: set endpoints and add to folder; undo removes and disconnects. */
    private static final class AddRelationshipCommand extends Command {
        private final IArchimateRelationship rel;
        private final IArchimateElement source;
        private final IArchimateElement target;
        private final IFolder relFolder;

        AddRelationshipCommand(IArchimateRelationship rel, IArchimateElement source, IArchimateElement target, IFolder relFolder) {
            super("ArchiGPT: add relationship");
            this.rel = rel;
            this.source = source;
            this.target = target;
            this.relFolder = relFolder;
        }

        @Override
        public void execute() {
            rel.setSource(source);
            rel.setTarget(target);
            if (relFolder != null) {
                relFolder.getElements().add(rel);
            }
        }

        @Override
        public void undo() {
            if (relFolder != null) {
                relFolder.getElements().remove(rel);
            }
            rel.disconnect();
        }
    }

    private static void appendRemoveCommands(NonNotifyingCompoundCommand compound, IArchimateModel model,
            IArchimateDiagramModel targetDiagram, ArchiMateLLMResult result) {
        if (targetDiagram != null && (result.getRemoveElementFromDiagramIds() != null || result.getRemoveRelationshipFromDiagramIds() != null)) {
            appendRemoveFromDiagramCommands(compound, targetDiagram, model,
                    result.getRemoveElementFromDiagramIds() != null ? result.getRemoveElementFromDiagramIds() : java.util.Collections.emptyList(),
                    result.getRemoveRelationshipFromDiagramIds() != null ? result.getRemoveRelationshipFromDiagramIds() : java.util.Collections.emptyList());
        }
        // Match Archi delete order: views first, then concepts (see DeleteCommandHandler).
        appendRemoveDiagramCommands(compound, model, result.getRemoveDiagramNames());
        appendRemoveFromModelCommands(compound, model, result.getRemoveRelationshipIds(), result.getRemoveElementIds());
    }

    private static void appendRemoveFromDiagramCommands(NonNotifyingCompoundCommand compound, IArchimateDiagramModel diagram,
            IArchimateModel model, List<String> elementIds, List<String> relationshipIds) {
        if (diagram == null || model == null) return;
        if (elementIds != null) {
            for (String id : elementIds) {
                if (id == null || id.trim().isEmpty()) continue;
                IArchimateConcept concept = findConceptById(model, id.trim());
                if (concept instanceof IArchimateElement) {
                    for (Object child : new ArrayList<>(diagram.getChildren())) {
                        if (child instanceof IDiagramModelArchimateObject) {
                            if (concept.equals(((IDiagramModelArchimateObject) child).getArchimateElement())) {
                                compound.add(DiagramCommandFactory.createDeleteDiagramObjectCommand((IDiagramModelArchimateObject) child));
                            }
                        }
                    }
                }
            }
        }
        if (relationshipIds != null) {
            for (String id : relationshipIds) {
                if (id == null || id.trim().isEmpty()) continue;
                IArchimateConcept concept = findConceptById(model, id.trim());
                if (concept instanceof IArchimateRelationship) {
                    for (Object child : new ArrayList<>(diagram.getChildren())) {
                        if (child instanceof IDiagramModelConnection) {
                            if (concept.equals(getConnectionRelationship((IDiagramModelConnection) child))) {
                                compound.add(DiagramCommandFactory.createDeleteDiagramConnectionCommand((IDiagramModelConnection) child));
                            }
                        }
                    }
                }
            }
        }
    }

    private static void appendRemoveFromModelCommands(NonNotifyingCompoundCommand compound, IArchimateModel model,
            List<String> relationshipIds, List<String> elementIds) {
        if (model == null) return;
        if (relationshipIds != null) {
            for (String id : relationshipIds) {
                IArchimateConcept concept = findConceptById(model, id);
                if (concept instanceof IArchimateRelationship) {
                    IArchimateRelationship rel = (IArchimateRelationship) concept;
                    if (model.getDiagramModels() != null) {
                        for (IDiagramModel dm : model.getDiagramModels()) {
                            if (!(dm instanceof IArchimateDiagramModel)) continue;
                            IArchimateDiagramModel diag = (IArchimateDiagramModel) dm;
                            for (Object child : new ArrayList<>(diag.getChildren())) {
                                if (child instanceof IDiagramModelConnection) {
                                    if (rel.equals(getConnectionRelationship((IDiagramModelConnection) child))) {
                                        compound.add(DiagramCommandFactory.createDeleteDiagramConnectionCommand((IDiagramModelConnection) child));
                                    }
                                }
                            }
                        }
                    }
                    compound.add(new DeleteArchimateRelationshipCommand(rel));
                }
            }
        }
        if (elementIds != null) {
            for (String id : elementIds) {
                IArchimateConcept concept = findConceptById(model, id);
                if (concept instanceof IArchimateElement) {
                    IArchimateElement element = (IArchimateElement) concept;
                    if (model.getDiagramModels() != null) {
                        for (IDiagramModel dm : model.getDiagramModels()) {
                            if (!(dm instanceof IArchimateDiagramModel)) continue;
                            IArchimateDiagramModel diag = (IArchimateDiagramModel) dm;
                            for (Object child : new ArrayList<>(diag.getChildren())) {
                                if (child instanceof IDiagramModelArchimateObject) {
                                    if (element.equals(((IDiagramModelArchimateObject) child).getArchimateElement())) {
                                        compound.add(DiagramCommandFactory.createDeleteDiagramObjectCommand((IDiagramModelArchimateObject) child));
                                    }
                                }
                            }
                        }
                    }
                    compound.add(new DeleteArchimateElementCommand(element));
                }
            }
        }
    }

    private static void appendRemoveDiagramCommands(NonNotifyingCompoundCommand compound, IArchimateModel model, List<String> diagramNames) {
        if (model == null || diagramNames == null || diagramNames.isEmpty()) return;
        for (String name : diagramNames) {
            if (name == null || name.trim().isEmpty()) continue;
            IArchimateDiagramModel diagram = findDiagramByName(model, name.trim());
            if (diagram != null) {
                compound.add(new DeleteDiagramModelCommand(diagram));
            }
        }
    }

    /**
     * Same mutations as {@link #buildImportCommands} but without CommandStack (e.g. headless tests).
     */
    private static void runImportWithoutCommandStack(ArchiMateLLMResult result, IArchimateModel model,
            IFolder targetFolder, IArchimateDiagramModel targetDiagram) {
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
            IFolder defaultFolder = model.getDefaultFolderForObject(element);
            IFolder folder = defaultFolder;
            if (targetFolder != null && defaultFolder != null && targetFolder.getType() == defaultFolder.getType()) {
                folder = targetFolder;
            }
            if (folder != null) {
                folder.getElements().add(element);
            }
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

        if (targetDiagram != null && (result.getRemoveElementFromDiagramIds() != null || result.getRemoveRelationshipFromDiagramIds() != null)) {
            removeFiguresFromDiagramOnly(targetDiagram, model,
                    result.getRemoveElementFromDiagramIds() != null ? result.getRemoveElementFromDiagramIds() : java.util.Collections.emptyList(),
                    result.getRemoveRelationshipFromDiagramIds() != null ? result.getRemoveRelationshipFromDiagramIds() : java.util.Collections.emptyList());
        }
        removeDiagramsFromModel(model, result.getRemoveDiagramNames());
        removeFromModel(model, result.getRemoveRelationshipIds(), result.getRemoveElementIds());
    }

    private static void appendNewDiagramCommands(NonNotifyingCompoundCommand compound, ArchiMateLLMResult.DiagramSpec spec,
            IArchimateModel model, Map<String, IArchimateConcept> idToConcept, Map<String, IArchimateRelationship> idToRelationship) {
        IArchimateDiagramModel diagram = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        diagram.setName(spec.getName() != null ? spec.getName() : "New View");
        if (spec.getViewpoint() != null && !spec.getViewpoint().isEmpty()) {
            diagram.setViewpoint(spec.getViewpoint());
        }
        IFolder diagramsFolder = model.getFolder(FolderType.DIAGRAMS);
        if (diagramsFolder != null) {
            compound.add(new AddListMemberCommand("ArchiGPT: add diagram", diagramsFolder.getElements(), diagram));
        } else {
            compound.add(new AddDiagramToModelFallbackCommand(model, diagram));
        }
        appendDiagramContentCommands(compound, diagram, spec, model, idToConcept, idToRelationship);
    }

    private static void appendDiagramContentCommands(NonNotifyingCompoundCommand compound, IArchimateDiagramModel diagram,
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
            compound.add(new AddDiagramObjectCommand(diagram, dmo));
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
            compound.add(new AddDiagramConnectionCommand(diagram, conn));
        }
    }

    /** Add diagram connection using the same storage Archi expects; undo removes it. */
    private static final class AddDiagramConnectionCommand extends Command {
        private final IArchimateDiagramModel diagram;
        private final IDiagramModelConnection conn;

        AddDiagramConnectionCommand(IArchimateDiagramModel diagram, IDiagramModelConnection conn) {
            super("ArchiGPT: add diagram connection");
            this.diagram = diagram;
            this.conn = conn;
        }

        @Override
        public void execute() {
            addConnectionToDiagram(diagram, conn);
        }

        @Override
        public void undo() {
            removeConnectionFromDiagram(diagram, conn);
        }
    }

    /**
     * Remove figures/connections from a single diagram only; elements and relationships stay in the model.
     */
    private static void removeFiguresFromDiagramOnly(IArchimateDiagramModel diagram, IArchimateModel model,
            List<String> elementIds, List<String> relationshipIds) {
        if (diagram == null || model == null) return;
        if (elementIds != null) {
            for (String id : elementIds) {
                if (id == null || id.trim().isEmpty()) continue;
                IArchimateConcept concept = findConceptById(model, id.trim());
                if (concept instanceof IArchimateElement) {
                    List<Object> toRemove = new ArrayList<>();
                    for (Object child : diagram.getChildren()) {
                        if (child instanceof IDiagramModelArchimateObject) {
                            if (concept.equals(((IDiagramModelArchimateObject) child).getArchimateElement())) {
                                toRemove.add(child);
                            }
                        }
                    }
                    for (Object fig : toRemove) {
                        removeDiagramObjectAndConnections(diagram, (IDiagramModelArchimateObject) fig);
                    }
                }
            }
        }
        if (relationshipIds != null) {
            for (String id : relationshipIds) {
                if (id == null || id.trim().isEmpty()) continue;
                IArchimateConcept concept = findConceptById(model, id.trim());
                if (concept instanceof IArchimateRelationship) {
                    IArchimateRelationship rel = (IArchimateRelationship) concept;
                    List<Object> toRemove = new ArrayList<>();
                    for (Object child : diagram.getChildren()) {
                        if (child instanceof IDiagramModelConnection) {
                            if (rel.equals(getConnectionRelationship((IDiagramModelConnection) child))) {
                                toRemove.add(child);
                            }
                        }
                    }
                    for (Object obj : toRemove) {
                        removeConnectionFromDiagram(diagram, (IDiagramModelConnection) obj);
                    }
                }
            }
        }
    }

    /**
     * Remove diagrams (views) by name from the model. The diagram and its figures/connections are
     * deleted; elements and relationships in the model are not removed.
     */
    private static void removeDiagramsFromModel(IArchimateModel model, List<String> diagramNames) {
        if (model == null || diagramNames == null || diagramNames.isEmpty()) return;
        for (String name : diagramNames) {
            if (name == null || name.trim().isEmpty()) continue;
            IArchimateDiagramModel diagram = findDiagramByName(model, name.trim());
            if (diagram != null) {
                Object container = diagram.eContainer();
                if (container instanceof IFolder) {
                    ((IFolder) container).getElements().remove(diagram);
                }
            }
        }
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
                    ((IArchimateRelationship) concept).disconnect();
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
        IFolder diagramsFolder = model.getFolder(FolderType.DIAGRAMS);
        if (diagramsFolder != null) {
            diagramsFolder.getElements().add(diagram);
        } else {
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
