/**
 * Imports validated ArchiMateLLMResult into an open Archi model (elements and relationships).
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.archimatetool.model.IConnectable;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
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
     * Turn a hyphenated UUID or 32-hex string into Archi's {@code id-} form.
     * Returns null if {@code id} is not an identifier, so callers can fall back to name lookup
     * without generating a random id.
     */
    static String normalizeLookupId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String trimmed = id.trim();
        if (ARCHIMATE_ID.matcher(trimmed).matches()) {
            return trimmed;
        }
        String noHyphens = trimmed.replace("-", "");
        if (noHyphens.length() == 32 && noHyphens.matches("[0-9a-fA-F]+")) {
            return "id-" + noHyphens;
        }
        return null;
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
     * are added to that folder when its type matches. When targetDiagram is non-null, new elements
     * are added as figures and new relationships as arrows when both ends already have (or just
     * received) figures on that view.
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
        Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures = new HashMap<>();

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
            IArchimateElement existing = existingElementForSpec(model, eClass, name, e.getId());
            if (existing != null) {
                rememberConceptId(idToConcept, e.getId(), existing);
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
                pendingFigures.put(element, dmo);
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
            rel.setSource(source);
            rel.setTarget(target);
            compound.add(new AddRelationshipCommand(rel, source, target, relFolder));
            if (targetDiagram != null && result.getDiagram() == null) {
                appendRelationshipArrowCommand(compound, targetDiagram, source, target, rel, pendingFigures);
            }
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
                IArchimateConcept concept = findConceptById(model, id);
                if (concept instanceof IArchimateElement) {
                    for (IDiagramModelArchimateObject fig : figuresForElement(diagram, (IArchimateElement) concept)) {
                        compound.add(DiagramCommandFactory.createDeleteDiagramObjectCommand(fig));
                    }
                }
            }
        }
        if (relationshipIds != null) {
            for (String id : relationshipIds) {
                IArchimateConcept concept = findConceptById(model, id);
                if (concept instanceof IArchimateRelationship) {
                    for (IDiagramModelConnection conn : connectionsForRelationship(diagram, (IArchimateRelationship) concept)) {
                        compound.add(DiagramCommandFactory.createDeleteDiagramConnectionCommand(conn));
                    }
                }
            }
        }
    }

    private static void appendRemoveFromModelCommands(NonNotifyingCompoundCommand compound, IArchimateModel model,
            List<String> relationshipIds, List<String> elementIds) {
        if (model == null) return;
        Set<IArchimateElement> elements = new LinkedHashSet<>();
        Set<IArchimateRelationship> relationships = new LinkedHashSet<>();
        collectRemovals(model, relationshipIds, elementIds, elements, relationships);
        for (IArchimateRelationship rel : relationships) {
            appendDeleteRelationshipCommands(compound, model, rel);
        }
        for (IArchimateElement element : elements) {
            appendDeleteElementCommands(compound, model, element);
        }
    }

    private static void collectRemovals(IArchimateModel model, List<String> relationshipIds, List<String> elementIds,
            Set<IArchimateElement> elements, Set<IArchimateRelationship> relationships) {
        if (relationshipIds != null) {
            for (String id : relationshipIds) {
                IArchimateConcept concept = findConceptById(model, id);
                if (concept instanceof IArchimateRelationship) {
                    collectRelationshipAndDependents((IArchimateRelationship) concept, relationships);
                }
            }
        }
        if (elementIds != null) {
            for (String id : elementIds) {
                IArchimateConcept concept = findConceptById(model, id);
                if (concept instanceof IArchimateElement) {
                    IArchimateElement element = (IArchimateElement) concept;
                    elements.add(element);
                    collectAttachedRelationships(element, relationships);
                }
            }
        }
    }

    /** Same order as Archi's DeleteCommandHandler: connected relationships, then nested relationship-to-relationship. */
    private static void collectAttachedRelationships(IArchimateConcept concept, Set<IArchimateRelationship> out) {
        if (concept == null) {
            return;
        }
        List<IArchimateRelationship> attached = new ArrayList<>();
        attached.addAll(concept.getSourceRelationships());
        attached.addAll(concept.getTargetRelationships());
        for (IArchimateRelationship rel : attached) {
            collectRelationshipAndDependents(rel, out);
        }
    }

    private static void collectRelationshipAndDependents(IArchimateRelationship rel, Set<IArchimateRelationship> out) {
        if (rel == null || !out.add(rel)) {
            return;
        }
        collectAttachedRelationships(rel, out);
    }

    private static void appendDeleteRelationshipCommands(NonNotifyingCompoundCommand compound, IArchimateModel model,
            IArchimateRelationship rel) {
        if (model.getDiagramModels() != null) {
            for (IDiagramModel dm : model.getDiagramModels()) {
                if (dm instanceof IArchimateDiagramModel) {
                    for (IDiagramModelConnection conn : connectionsForRelationship((IArchimateDiagramModel) dm, rel)) {
                        compound.add(DiagramCommandFactory.createDeleteDiagramConnectionCommand(conn));
                    }
                }
            }
        }
        compound.add(new DeleteArchimateRelationshipCommand(rel));
    }

    private static void appendDeleteElementCommands(NonNotifyingCompoundCommand compound, IArchimateModel model,
            IArchimateElement element) {
        if (model.getDiagramModels() != null) {
            for (IDiagramModel dm : model.getDiagramModels()) {
                if (dm instanceof IArchimateDiagramModel) {
                    for (IDiagramModelArchimateObject fig : figuresForElement((IArchimateDiagramModel) dm, element)) {
                        compound.add(DiagramCommandFactory.createDeleteDiagramObjectCommand(fig));
                    }
                }
            }
        }
        compound.add(new DeleteArchimateElementCommand(element));
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
        Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures = new HashMap<>();

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
            IArchimateElement existing = existingElementForSpec(model, eClass, name, e.getId());
            if (existing != null) {
                rememberConceptId(idToConcept, e.getId(), existing);
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
                pendingFigures.put(element, dmo);
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
            if (targetDiagram != null && result.getDiagram() == null) {
                addArchimateConnectionNow(targetDiagram, source, target, rel, pendingFigures);
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
            IArchimateRelationship rel = null;
            if (connSpec.getRelationshipId() != null && !connSpec.getRelationshipId().isEmpty()) {
                rel = idToRelationship.get(connSpec.getRelationshipId());
                if (rel == null) {
                    IArchimateConcept found = findConceptById(model, connSpec.getRelationshipId());
                    if (found instanceof IArchimateRelationship) {
                        rel = (IArchimateRelationship) found;
                    }
                }
            }
            if (rel == null) continue;
            compound.add(new AddArchimateDiagramConnectionCommand(sourceDmo, targetDmo, rel));
        }
        appendMissingRelationshipArrows(compound, diagram, idToRelationship, elementIdToDiagramObject);
    }

    /** Draw imported relationships that the LLM omitted from diagram.connections. */
    private static void appendMissingRelationshipArrows(NonNotifyingCompoundCommand compound, IArchimateDiagramModel diagram,
            Map<String, IArchimateRelationship> idToRelationship,
            Map<String, IDiagramModelArchimateObject> elementIdToDiagramObject) {
        if (diagram == null || idToRelationship == null || idToRelationship.isEmpty()) {
            return;
        }
        Map<IArchimateElement, IDiagramModelArchimateObject> byElement = new HashMap<>();
        if (elementIdToDiagramObject != null) {
            for (IDiagramModelArchimateObject dmo : elementIdToDiagramObject.values()) {
                if (dmo != null && dmo.getArchimateElement() != null) {
                    byElement.put(dmo.getArchimateElement(), dmo);
                }
            }
        }
        for (IArchimateRelationship rel : new java.util.LinkedHashSet<>(idToRelationship.values())) {
            if (!(rel.getSource() instanceof IArchimateElement) || !(rel.getTarget() instanceof IArchimateElement)) {
                continue;
            }
            appendRelationshipArrowCommand(compound, diagram, (IArchimateElement) rel.getSource(),
                    (IArchimateElement) rel.getTarget(), rel, byElement);
        }
    }

    /** Add an ArchiMate relationship arrow between two figures; undo disconnects it. */
    private static final class AddArchimateDiagramConnectionCommand extends Command {
        private final IConnectable source;
        private final IConnectable target;
        private final IArchimateRelationship relationship;
        private IDiagramModelArchimateConnection connection;

        AddArchimateDiagramConnectionCommand(IConnectable source, IConnectable target, IArchimateRelationship relationship) {
            super("ArchiGPT: add diagram connection");
            this.source = source;
            this.target = target;
            this.relationship = relationship;
        }

        @Override
        public void execute() {
            if (alreadyShowsRelationship(source, relationship)) {
                return;
            }
            connection = IArchimateFactory.eINSTANCE.createDiagramModelArchimateConnection();
            connection.setArchimateRelationship(relationship);
            connection.connect(source, target);
        }

        @Override
        public void undo() {
            if (connection != null) {
                connection.disconnect();
            }
        }

        @Override
        public void redo() {
            if (connection != null) {
                connection.reconnect();
            }
        }
    }

    private static void appendRelationshipArrowCommand(NonNotifyingCompoundCommand compound, IArchimateDiagramModel diagram,
            IArchimateElement source, IArchimateElement target, IArchimateRelationship rel,
            Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures) {
        IDiagramModelArchimateObject sourceDmo = findFigureOnDiagram(diagram, source, pendingFigures);
        IDiagramModelArchimateObject targetDmo = findFigureOnDiagram(diagram, target, pendingFigures);
        if (sourceDmo == null || targetDmo == null || alreadyShowsRelationship(sourceDmo, rel)) {
            return;
        }
        compound.add(new AddArchimateDiagramConnectionCommand(sourceDmo, targetDmo, rel));
    }

    private static void addArchimateConnectionNow(IArchimateDiagramModel diagram, IArchimateElement source,
            IArchimateElement target, IArchimateRelationship rel,
            Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures) {
        IDiagramModelArchimateObject sourceDmo = findFigureOnDiagram(diagram, source, pendingFigures);
        IDiagramModelArchimateObject targetDmo = findFigureOnDiagram(diagram, target, pendingFigures);
        if (sourceDmo == null || targetDmo == null || alreadyShowsRelationship(sourceDmo, rel)) {
            return;
        }
        IDiagramModelArchimateConnection conn = IArchimateFactory.eINSTANCE.createDiagramModelArchimateConnection();
        conn.setArchimateRelationship(rel);
        conn.connect(sourceDmo, targetDmo);
    }

    private static IDiagramModelArchimateObject findFigureOnDiagram(IDiagramModelContainer container,
            IArchimateElement element, Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures) {
        if (element == null) {
            return null;
        }
        if (pendingFigures != null) {
            IDiagramModelArchimateObject pending = pendingFigures.get(element);
            if (pending != null) {
                return pending;
            }
        }
        return findFigureInChildren(container, element);
    }

    private static IDiagramModelArchimateObject findFigureInChildren(IDiagramModelContainer container,
            IArchimateElement element) {
        if (container == null || element == null) {
            return null;
        }
        for (Object child : container.getChildren()) {
            if (child instanceof IDiagramModelArchimateObject) {
                IDiagramModelArchimateObject dmo = (IDiagramModelArchimateObject) child;
                if (element.equals(dmo.getArchimateElement())) {
                    return dmo;
                }
            }
            if (child instanceof IDiagramModelContainer) {
                IDiagramModelArchimateObject nested = findFigureInChildren((IDiagramModelContainer) child, element);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static boolean alreadyShowsRelationship(IConnectable sourceFigure, IArchimateRelationship rel) {
        if (sourceFigure == null || rel == null) {
            return false;
        }
        for (Object c : sourceFigure.getSourceConnections()) {
            if (c instanceof IDiagramModelConnection
                    && rel.equals(getConnectionRelationship((IDiagramModelConnection) c))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove figures/connections from a single diagram only; elements and relationships stay in the model.
     */
    private static void removeFiguresFromDiagramOnly(IArchimateDiagramModel diagram, IArchimateModel model,
            List<String> elementIds, List<String> relationshipIds) {
        if (diagram == null || model == null) return;
        if (elementIds != null) {
            for (String id : elementIds) {
                IArchimateConcept concept = findConceptById(model, id);
                if (concept instanceof IArchimateElement) {
                    for (IDiagramModelArchimateObject fig : figuresForElement(diagram, (IArchimateElement) concept)) {
                        removeDiagramObjectAndConnections(fig);
                    }
                }
            }
        }
        if (relationshipIds != null) {
            for (String id : relationshipIds) {
                IArchimateConcept concept = findConceptById(model, id);
                if (concept instanceof IArchimateRelationship) {
                    for (IDiagramModelConnection conn : connectionsForRelationship(diagram, (IArchimateRelationship) concept)) {
                        conn.disconnect();
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
     * Relationships listed by the LLM are removed first; deleting an element also removes every
     * relationship attached to it (Archi's tree-delete behaviour).
     */
    private static void removeFromModel(IArchimateModel model, List<String> relationshipIds, List<String> elementIds) {
        if (model == null || (relationshipIds == null && elementIds == null)) return;
        Set<IArchimateElement> elements = new LinkedHashSet<>();
        Set<IArchimateRelationship> relationships = new LinkedHashSet<>();
        collectRemovals(model, relationshipIds, elementIds, elements, relationships);
        for (IArchimateRelationship rel : relationships) {
            removeRelationshipFromDiagrams(model, rel);
            removeConceptFromFolder(rel);
            rel.disconnect();
        }
        for (IArchimateElement element : elements) {
            removeElementFromDiagrams(model, element);
            removeConceptFromFolder(element);
        }
    }

    private static IArchimateConcept findConceptById(IArchimateModel model, String id) {
        if (model == null || id == null || id.trim().isEmpty()) {
            return null;
        }
        String trimmed = id.trim();
        IArchimateConcept found = findConceptByExactId(model, trimmed);
        if (found != null) {
            return found;
        }
        String normalized = normalizeLookupId(trimmed);
        if (normalized != null && !normalized.equals(trimmed)) {
            found = findConceptByExactId(model, normalized);
            if (found != null) {
                return found;
            }
        }
        return findUniqueConceptByName(model, trimmed);
    }

    private static IArchimateConcept findConceptByExactId(IArchimateModel model, String id) {
        for (IFolder folder : model.getFolders()) {
            IArchimateConcept found = findConceptInFolder(folder, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static IArchimateConcept findConceptInFolder(IFolder folder, String id) {
        if (folder == null) return null;
        for (EObject obj : folder.getElements()) {
            if (obj instanceof IArchimateConcept) {
                String existing = ((IArchimateConcept) obj).getId();
                if (existing != null && existing.equalsIgnoreCase(id)) {
                    return (IArchimateConcept) obj;
                }
            }
        }
        for (IFolder child : folder.getFolders()) {
            IArchimateConcept found = findConceptInFolder(child, id);
            if (found != null) return found;
        }
        return null;
    }

    private static IArchimateConcept findUniqueConceptByName(IArchimateModel model, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        List<IArchimateConcept> matches = new ArrayList<>();
        for (IFolder folder : model.getFolders()) {
            collectConceptsByName(folder, name, matches);
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static void collectConceptsByName(IFolder folder, String name, List<IArchimateConcept> matches) {
        if (folder == null) {
            return;
        }
        for (EObject obj : folder.getElements()) {
            if (obj instanceof IArchimateConcept) {
                String existing = ((IArchimateConcept) obj).getName();
                if (existing != null && existing.trim().equalsIgnoreCase(name)) {
                    matches.add((IArchimateConcept) obj);
                }
            }
        }
        for (IFolder child : folder.getFolders()) {
            collectConceptsByName(child, name, matches);
        }
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
            for (IDiagramModelConnection conn : connectionsForRelationship((IArchimateDiagramModel) dm, relationship)) {
                conn.disconnect();
            }
        }
    }

    private static void removeElementFromDiagrams(IArchimateModel model, IArchimateElement element) {
        if (model.getDiagramModels() == null) return;
        for (IDiagramModel dm : model.getDiagramModels()) {
            if (!(dm instanceof IArchimateDiagramModel)) continue;
            for (IDiagramModelArchimateObject fig : figuresForElement((IArchimateDiagramModel) dm, element)) {
                removeDiagramObjectAndConnections(fig);
            }
        }
    }

    private static List<IDiagramModelArchimateObject> figuresForElement(IDiagramModelContainer container,
            IArchimateElement element) {
        List<IDiagramModelArchimateObject> out = new ArrayList<>();
        collectFiguresForElement(container, element, out);
        return out;
    }

    private static void collectFiguresForElement(IDiagramModelContainer container, IArchimateElement element,
            List<IDiagramModelArchimateObject> out) {
        if (container == null || element == null) {
            return;
        }
        for (Object child : new ArrayList<Object>(container.getChildren())) {
            if (child instanceof IDiagramModelArchimateObject) {
                IDiagramModelArchimateObject dmo = (IDiagramModelArchimateObject) child;
                if (element.equals(dmo.getArchimateElement())) {
                    out.add(dmo);
                }
            }
            if (child instanceof IDiagramModelContainer) {
                collectFiguresForElement((IDiagramModelContainer) child, element, out);
            }
        }
    }

    private static List<IDiagramModelConnection> connectionsForRelationship(IDiagramModelContainer container,
            IArchimateRelationship relationship) {
        List<IDiagramModelConnection> out = new ArrayList<>();
        collectConnectionsForRelationship(container, relationship, out);
        return out;
    }

    private static void collectConnectionsForRelationship(IDiagramModelContainer container,
            IArchimateRelationship relationship, List<IDiagramModelConnection> out) {
        if (container == null || relationship == null) {
            return;
        }
        for (Object child : new ArrayList<Object>(container.getChildren())) {
            if (child instanceof IConnectable) {
                collectConnectionsOnConnectable((IConnectable) child, relationship, out);
            }
            if (child instanceof IDiagramModelContainer) {
                collectConnectionsForRelationship((IDiagramModelContainer) child, relationship, out);
            }
        }
    }

    private static void collectConnectionsOnConnectable(IConnectable connectable, IArchimateRelationship relationship,
            List<IDiagramModelConnection> out) {
        if (connectable == null) {
            return;
        }
        for (Object c : new ArrayList<Object>(connectable.getSourceConnections())) {
            if (!(c instanceof IDiagramModelConnection)) {
                continue;
            }
            IDiagramModelConnection conn = (IDiagramModelConnection) c;
            if (relationship.equals(getConnectionRelationship(conn)) && !out.contains(conn)) {
                out.add(conn);
            }
            collectConnectionsOnConnectable(conn, relationship, out);
        }
    }

    private static void removeDiagramObjectAndConnections(IDiagramModelArchimateObject dmo) {
        if (dmo == null) {
            return;
        }
        List<IDiagramModelConnection> conns = new ArrayList<>();
        conns.addAll(dmo.getSourceConnections());
        conns.addAll(dmo.getTargetConnections());
        for (IDiagramModelConnection c : conns) {
            c.disconnect();
        }
        if (dmo instanceof IDiagramModelContainer) {
            for (Object child : new ArrayList<Object>(((IDiagramModelContainer) dmo).getChildren())) {
                if (child instanceof IDiagramModelArchimateObject) {
                    removeDiagramObjectAndConnections((IDiagramModelArchimateObject) child);
                }
            }
        }
        Object parent = dmo.eContainer();
        if (parent instanceof IDiagramModelContainer) {
            ((IDiagramModelContainer) parent).getChildren().remove(dmo);
        }
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
            IArchimateRelationship rel = null;
            if (connSpec.getRelationshipId() != null && !connSpec.getRelationshipId().isEmpty()) {
                rel = idToRelationship.get(connSpec.getRelationshipId());
                if (rel == null) {
                    IArchimateConcept found = findConceptById(model, connSpec.getRelationshipId());
                    if (found instanceof IArchimateRelationship) {
                        rel = (IArchimateRelationship) found;
                    }
                }
            }
            if (rel == null || alreadyShowsRelationship(sourceDmo, rel)) continue;
            IDiagramModelArchimateConnection conn = IArchimateFactory.eINSTANCE.createDiagramModelArchimateConnection();
            conn.setArchimateRelationship(rel);
            conn.connect(sourceDmo, targetDmo);
        }
        Map<IArchimateElement, IDiagramModelArchimateObject> byElement = new HashMap<>();
        for (IDiagramModelArchimateObject dmo : elementIdToDiagramObject.values()) {
            if (dmo != null && dmo.getArchimateElement() != null) {
                byElement.put(dmo.getArchimateElement(), dmo);
            }
        }
        for (IArchimateRelationship rel : new java.util.LinkedHashSet<>(idToRelationship.values())) {
            if (!(rel.getSource() instanceof IArchimateElement) || !(rel.getTarget() instanceof IArchimateElement)) {
                continue;
            }
            addArchimateConnectionNow(diagram, (IArchimateElement) rel.getSource(), (IArchimateElement) rel.getTarget(),
                    rel, byElement);
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

    private static void rememberConceptId(Map<String, IArchimateConcept> idToConcept, String llmId,
            IArchimateConcept concept) {
        if (idToConcept == null || concept == null) {
            return;
        }
        if (concept.getId() != null && !concept.getId().isEmpty()) {
            idToConcept.put(concept.getId(), concept);
        }
        if (llmId != null && !llmId.trim().isEmpty()) {
            String trimmed = llmId.trim();
            idToConcept.put(trimmed, concept);
            String normalized = normalizeLookupId(trimmed);
            if (normalized != null) {
                idToConcept.put(normalized, concept);
            }
        }
    }

    /**
     * Element already in the model for this spec: same id, or same type+name (LLM re-listing context).
     * Callers must still map the LLM's id onto that element so relationships in this payload resolve.
     */
    private static IArchimateElement existingElementForSpec(IArchimateModel model, EClass eClass, String name,
            String llmId) {
        IArchimateConcept byId = findConceptById(model, llmId);
        if (byId instanceof IArchimateElement && eClass != null && eClass.isInstance(byId)) {
            return (IArchimateElement) byId;
        }
        return findElementByTypeAndName(model, eClass, name);
    }

    private static IArchimateElement findElementByTypeAndName(IArchimateModel model, EClass eClass, String name) {
        if (model == null || eClass == null) {
            return null;
        }
        String n = name == null ? "" : name.trim();
        for (IFolder f : model.getFolders()) {
            IArchimateElement found = findElementByTypeAndNameInFolder(f, eClass, n);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static IArchimateElement findElementByTypeAndNameInFolder(IFolder folder, EClass eClass, String name) {
        if (folder == null) {
            return null;
        }
        for (EObject obj : folder.getElements()) {
            if (obj instanceof IArchimateElement && eClass.isInstance(obj)) {
                String existing = ((IArchimateElement) obj).getName();
                if (existing != null && existing.trim().equalsIgnoreCase(name)) {
                    return (IArchimateElement) obj;
                }
            }
        }
        for (IFolder child : folder.getFolders()) {
            IArchimateElement found = findElementByTypeAndNameInFolder(child, eClass, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
