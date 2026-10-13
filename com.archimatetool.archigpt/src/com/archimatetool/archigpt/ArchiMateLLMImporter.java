/**
 * Imports validated ArchiMateLLMResult into an open Archi model (elements and relationships).
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IConnectable;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;
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

    private static final int DEFAULT_ELEMENT_WIDTH = 120;
    private static final int DEFAULT_ELEMENT_HEIGHT = 55;
    private static final int DEFAULT_GAP = 25;
    private static final int EMPTY_DIAGRAM_X = 50;
    private static final int EMPTY_DIAGRAM_Y = 50;
    private static final int CANVAS_MARGIN = 10;

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
    public static ImportStats importIntoModel(ArchiMateLLMResult result, IArchimateModel model) {
        return importIntoModel(result, model, null, null);
    }

    /**
     * Import with optional target folder (no diagram).
     */
    public static ImportStats importIntoModel(ArchiMateLLMResult result, IArchimateModel model, IFolder targetFolder) {
        return importIntoModel(result, model, targetFolder, null);
    }

    /**
     * Import the validated result into the given model. When targetFolder is non-null, new elements
     * are added to that folder when its type matches. When targetDiagram is non-null, new elements
     * are added as figures and new relationships as arrows when both ends already have (or just
     * received) figures on that view.
     */
    public static ImportStats importIntoModel(ArchiMateLLMResult result, IArchimateModel model, IFolder targetFolder, IArchimateDiagramModel targetDiagram) {
        ImportStats stats = new ImportStats();
        CommandStack stack = getCommandStack(model);
        if (stack != null) {
            NonNotifyingCompoundCommand compound = new NonNotifyingCompoundCommand("ArchiGPT: apply LLM changes");
            buildImportCommands(compound, result, model, targetFolder, targetDiagram, stats);
            if (!compound.getCommands().isEmpty()) {
                stack.execute(compound);
            }
        } else {
            runImportWithoutCommandStack(result, model, targetFolder, targetDiagram, stats);
        }
        return stats;
    }

    /** Counts of what the importer actually applied (creates vs in-place edits). */
    public static final class ImportStats {
        private int createdElements;
        private int renamedElements;
        private int createdRelationships;
        private int renamedRelationships;
        private int updatedDocumentation;
        /** Figures or connection arrows actually added to a view (including existing catalog concepts). */
        private int addedToView;

        public int getCreatedElements() {
            return createdElements;
        }

        public int getRenamedElements() {
            return renamedElements;
        }

        public int getCreatedRelationships() {
            return createdRelationships;
        }

        public int getRenamedRelationships() {
            return renamedRelationships;
        }

        public int getUpdatedDocumentation() {
            return updatedDocumentation;
        }

        public int getAddedToView() {
            return addedToView;
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
            IArchimateModel model, IFolder targetFolder, IArchimateDiagramModel targetDiagram, ImportStats stats) {
        Map<String, IArchimateConcept> idToConcept = new HashMap<>();
        Map<String, IArchimateElement> preparedByTypeAndName = new HashMap<>();
        Map<String, IArchimateRelationship> idToRelationship = new HashMap<>();
        Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures = new HashMap<>();
        List<PreparedElement> preparedAll = new ArrayList<>();

        for (ArchiMateLLMResult.ElementSpec e : result.getElements()) {
            PreparedElement prepared = prepareElement(e, model, targetFolder, idToConcept, preparedByTypeAndName,
                    compound, stats);
            if (prepared != null) {
                preparedAll.add(prepared);
            }
        }

        for (ArchiMateLLMResult.RelationshipSpec r : result.getRelationships()) {
            String relType = ArchiMateSchemaValidator.normalizeRelationshipType(r.getType());
            EClass rClass = (EClass) IArchimatePackage.eINSTANCE.getEClassifier(relType);
            if (rClass == null || !IArchimatePackage.eINSTANCE.getArchimateRelationship().isSuperTypeOf(rClass)) {
                continue;
            }
            IArchimateRelationship existingRel = existingRelationshipForSpec(model, r);
            if (existingRel != null) {
                rememberRelationshipId(idToRelationship, r.getId(), existingRel);
                applyExistingRelationshipEdits(compound, existingRel, r, stats);
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
            IArchimateElement targetEl = (IArchimateElement) targetConcept;
            IArchimateRelationship rel = (IArchimateRelationship) IArchimateFactory.eINSTANCE.create(rClass);
            rel.setName(r.getName() != null ? r.getName() : "");
            if (r.getDocumentation() != null) {
                rel.setDocumentation(r.getDocumentation());
            }
            String relId = ensureArchiMateId(r.getId());
            rel.setId(relId);
            idToRelationship.put(relId, rel);
            if (!relId.equals(r.getId()) && r.getId() != null && !r.getId().isEmpty()) {
                idToRelationship.put(r.getId().trim(), rel);
            }
            IFolder relFolder = resolveFolderFor(model, rel, null);
            rel.setSource(source);
            rel.setTarget(targetEl);
            compound.add(new AddRelationshipCommand(rel, source, targetEl, relFolder));
            if (stats != null) {
                stats.createdRelationships++;
            }
        }

        if (result.getDiagram() != null && result.getDiagram().getName() != null && !result.getDiagram().getName().isEmpty()) {
            String diagramName = result.getDiagram().getName().trim();
            IArchimateDiagramModel existingDiagram = findDiagramByName(model, diagramName);
            if (existingDiagram != null) {
                appendDiagramContentCommands(compound, existingDiagram, result.getDiagram(), model, idToConcept,
                        idToRelationship, pendingFigures, stats);
            } else {
                appendNewDiagramCommands(compound, result.getDiagram(), model, idToConcept, idToRelationship,
                        pendingFigures, stats);
            }
        }

        placePayloadOnOpenView(compound, targetDiagram, preparedAll, result, model, idToConcept, pendingFigures,
                idToRelationship, stats);

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

    /** Undoable documentation update for an existing concept. */
    private static final class SetDocumentationCommand extends Command {
        private final IArchimateConcept concept;
        private final String newDocumentation;
        private String oldDocumentation;

        SetDocumentationCommand(IArchimateConcept concept, String newDocumentation) {
            super("ArchiGPT: set documentation");
            this.concept = concept;
            this.newDocumentation = newDocumentation != null ? newDocumentation : "";
        }

        @Override
        public boolean canExecute() {
            return concept != null && namesDiffer(concept.getDocumentation(), newDocumentation);
        }

        @Override
        public void execute() {
            oldDocumentation = concept.getDocumentation();
            concept.setDocumentation(newDocumentation);
        }

        @Override
        public void undo() {
            concept.setDocumentation(oldDocumentation);
        }
    }

    /** Undoable name update for an existing concept (rename). */
    private static final class SetNameCommand extends Command {
        private final IArchimateConcept concept;
        private final String newName;
        private String oldName;

        SetNameCommand(IArchimateConcept concept, String newName) {
            super("ArchiGPT: rename");
            this.concept = concept;
            this.newName = newName != null ? newName : "";
        }

        @Override
        public boolean canExecute() {
            return concept != null && namesDiffer(concept.getName(), newName);
        }

        @Override
        public void execute() {
            oldName = concept.getName();
            concept.setName(newName);
        }

        @Override
        public void undo() {
            concept.setName(oldName);
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
            IFolder targetFolder, IArchimateDiagramModel targetDiagram, ImportStats stats) {
        Map<String, IArchimateConcept> idToConcept = new HashMap<>();
        Map<String, IArchimateElement> preparedByTypeAndName = new HashMap<>();
        Map<String, IArchimateRelationship> idToRelationship = new HashMap<>();
        Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures = new HashMap<>();
        List<PreparedElement> preparedAll = new ArrayList<>();

        for (ArchiMateLLMResult.ElementSpec e : result.getElements()) {
            PreparedElement prepared = prepareElement(e, model, targetFolder, idToConcept, preparedByTypeAndName, null,
                    stats);
            if (prepared != null) {
                preparedAll.add(prepared);
            }
        }

        for (ArchiMateLLMResult.RelationshipSpec r : result.getRelationships()) {
            String relType = ArchiMateSchemaValidator.normalizeRelationshipType(r.getType());
            EClass rClass = (EClass) IArchimatePackage.eINSTANCE.getEClassifier(relType);
            if (rClass == null || !IArchimatePackage.eINSTANCE.getArchimateRelationship().isSuperTypeOf(rClass)) {
                continue;
            }
            IArchimateRelationship existingRel = existingRelationshipForSpec(model, r);
            if (existingRel != null) {
                rememberRelationshipId(idToRelationship, r.getId(), existingRel);
                applyExistingRelationshipEdits(null, existingRel, r, stats);
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
            IArchimateElement targetEl = (IArchimateElement) targetConcept;
            IArchimateRelationship rel = (IArchimateRelationship) IArchimateFactory.eINSTANCE.create(rClass);
            rel.setName(r.getName() != null ? r.getName() : "");
            if (r.getDocumentation() != null) {
                rel.setDocumentation(r.getDocumentation());
            }
            String relId = ensureArchiMateId(r.getId());
            rel.setId(relId);
            idToRelationship.put(relId, rel);
            if (!relId.equals(r.getId()) && r.getId() != null && !r.getId().isEmpty()) {
                idToRelationship.put(r.getId().trim(), rel);
            }
            rel.setSource(source);
            rel.setTarget(targetEl);
            IFolder relFolder = resolveFolderFor(model, rel, null);
            if (relFolder != null) {
                relFolder.getElements().add(rel);
            }
            if (stats != null) {
                stats.createdRelationships++;
            }
        }

        if (result.getDiagram() != null && result.getDiagram().getName() != null && !result.getDiagram().getName().isEmpty()) {
            String diagramName = result.getDiagram().getName().trim();
            IArchimateDiagramModel existingDiagram = findDiagramByName(model, diagramName);
            if (existingDiagram != null) {
                addNodesAndConnectionsToDiagram(existingDiagram, result.getDiagram(), model, idToConcept,
                        idToRelationship, pendingFigures, stats);
            } else {
                createNewDiagram(result.getDiagram(), model, idToConcept, idToRelationship, pendingFigures, stats);
            }
        }

        placePayloadOnOpenView(null, targetDiagram, preparedAll, result, model, idToConcept, pendingFigures,
                idToRelationship, stats);

        if (targetDiagram != null && (result.getRemoveElementFromDiagramIds() != null || result.getRemoveRelationshipFromDiagramIds() != null)) {
            removeFiguresFromDiagramOnly(targetDiagram, model,
                    result.getRemoveElementFromDiagramIds() != null ? result.getRemoveElementFromDiagramIds() : java.util.Collections.emptyList(),
                    result.getRemoveRelationshipFromDiagramIds() != null ? result.getRemoveRelationshipFromDiagramIds() : java.util.Collections.emptyList());
        }
        removeDiagramsFromModel(model, result.getRemoveDiagramNames());
        removeFromModel(model, result.getRemoveRelationshipIds(), result.getRemoveElementIds());
    }

    private static void appendNewDiagramCommands(NonNotifyingCompoundCommand compound, ArchiMateLLMResult.DiagramSpec spec,
            IArchimateModel model, Map<String, IArchimateConcept> idToConcept, Map<String, IArchimateRelationship> idToRelationship,
            Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures, ImportStats stats) {
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
        appendDiagramContentCommands(compound, diagram, spec, model, idToConcept, idToRelationship, pendingFigures, stats);
    }

    /**
     * Place payload elements that are not yet on the open view, then draw relationship arrows.
     * Used when adding to the current canvas (including after retargeting an LLM diagram block).
     */
    private static void placePayloadOnOpenView(NonNotifyingCompoundCommand compound, IArchimateDiagramModel targetDiagram,
            List<PreparedElement> preparedAll, ArchiMateLLMResult result, IArchimateModel model,
            Map<String, IArchimateConcept> idToConcept, Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures,
            Map<String, IArchimateRelationship> idToRelationship, ImportStats stats) {
        if (!payloadShouldLandOnOpenView(targetDiagram, result) || preparedAll == null) {
            return;
        }
        List<PreparedElement> missing = new ArrayList<PreparedElement>();
        Set<IArchimateElement> seenOnView = new LinkedHashSet<IArchimateElement>();
        for (PreparedElement prepared : preparedAll) {
            if (prepared == null || prepared.element == null || !seenOnView.add(prepared.element)) {
                continue;
            }
            if (pendingFigures != null && pendingFigures.containsKey(prepared.element)) {
                continue;
            }
            if (findFigureInChildren(targetDiagram, prepared.element) != null) {
                continue;
            }
            missing.add(prepared);
        }
        placeCreatedFiguresOnView(compound, targetDiagram, missing, result, model, idToConcept, pendingFigures, stats);
        if (idToRelationship == null || idToRelationship.isEmpty()) {
            return;
        }
        for (IArchimateRelationship rel : new LinkedHashSet<IArchimateRelationship>(idToRelationship.values())) {
            if (!(rel.getSource() instanceof IArchimateElement) || !(rel.getTarget() instanceof IArchimateElement)) {
                continue;
            }
            if (compound != null) {
                appendRelationshipArrowCommand(compound, targetDiagram, (IArchimateElement) rel.getSource(),
                        (IArchimateElement) rel.getTarget(), rel, pendingFigures, stats);
            } else {
                addArchimateConnectionNow(targetDiagram, (IArchimateElement) rel.getSource(),
                        (IArchimateElement) rel.getTarget(), rel, pendingFigures, stats);
            }
        }
    }

    private static boolean payloadShouldLandOnOpenView(IArchimateDiagramModel targetDiagram, ArchiMateLLMResult result) {
        if (targetDiagram == null) {
            return false;
        }
        if (result == null || result.getDiagram() == null || result.getDiagram().getName() == null
                || result.getDiagram().getName().trim().isEmpty()) {
            return true;
        }
        String named = result.getDiagram().getName().trim();
        String open = targetDiagram.getName() != null ? targetDiagram.getName().trim() : "";
        return named.equalsIgnoreCase(open);
    }

    private static void appendDiagramContentCommands(NonNotifyingCompoundCommand compound, IArchimateDiagramModel diagram,
            ArchiMateLLMResult.DiagramSpec spec, IArchimateModel model,
            Map<String, IArchimateConcept> idToConcept, Map<String, IArchimateRelationship> idToRelationship,
            Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures, ImportStats stats) {
        if (diagram == null || spec == null) return;
        Map<String, IDiagramModelArchimateObject> elementIdToDiagramObject = new HashMap<>();
        int[] nodeDelta = nodeTranslationToClearExisting(diagram, spec, model, idToConcept);
        for (ArchiMateLLMResult.DiagramNodeSpec node : spec.getNodes()) {
            String elementId = node.getElementId();
            if (elementId == null || elementId.isEmpty()) continue;
            IArchimateConcept concept = idToConcept.get(elementId);
            if (concept == null) {
                concept = findConceptById(model, elementId);
            }
            if (!(concept instanceof IArchimateElement)) continue;
            IArchimateElement element = (IArchimateElement) concept;
            IDiagramModelArchimateObject existingFig = figureAlreadyQueued(diagram, element, pendingFigures);
            if (existingFig != null) {
                elementIdToDiagramObject.put(elementId, existingFig);
                continue;
            }
            int x = node.getX() + nodeDelta[0];
            int y = node.getY() + nodeDelta[1];
            IDiagramModelArchimateObject dmo = createFigure(element, x, y, node.getWidth(), node.getHeight());
            compound.add(new AddDiagramObjectCommand(diagram, dmo));
            elementIdToDiagramObject.put(elementId, dmo);
            if (pendingFigures != null) {
                pendingFigures.put(element, dmo);
            }
            noteAddedToView(stats);
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
            noteAddedToView(stats);
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
                    (IArchimateElement) rel.getTarget(), rel, byElement, null);
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
            Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures, ImportStats stats) {
        IDiagramModelArchimateObject sourceDmo = findFigureOnDiagram(diagram, source, pendingFigures);
        IDiagramModelArchimateObject targetDmo = findFigureOnDiagram(diagram, target, pendingFigures);
        if (sourceDmo == null || targetDmo == null || alreadyShowsRelationship(sourceDmo, rel)) {
            return;
        }
        compound.add(new AddArchimateDiagramConnectionCommand(sourceDmo, targetDmo, rel));
        noteAddedToView(stats);
    }

    private static void addArchimateConnectionNow(IArchimateDiagramModel diagram, IArchimateElement source,
            IArchimateElement target, IArchimateRelationship rel,
            Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures, ImportStats stats) {
        IDiagramModelArchimateObject sourceDmo = findFigureOnDiagram(diagram, source, pendingFigures);
        IDiagramModelArchimateObject targetDmo = findFigureOnDiagram(diagram, target, pendingFigures);
        if (sourceDmo == null || targetDmo == null || alreadyShowsRelationship(sourceDmo, rel)) {
            return;
        }
        IDiagramModelArchimateConnection conn = IArchimateFactory.eINSTANCE.createDiagramModelArchimateConnection();
        conn.setArchimateRelationship(rel);
        conn.connect(sourceDmo, targetDmo);
        noteAddedToView(stats);
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

    private static IDiagramModelArchimateObject figureAlreadyQueued(IArchimateDiagramModel diagram,
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
        return findFigureInChildren(diagram, element);
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
            Map<String, IArchimateConcept> idToConcept, Map<String, IArchimateRelationship> idToRelationship,
            Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures, ImportStats stats) {
        if (diagram == null || spec == null) return;
        Map<String, IDiagramModelArchimateObject> elementIdToDiagramObject = new HashMap<>();
        int[] nodeDelta = nodeTranslationToClearExisting(diagram, spec, model, idToConcept);
        for (ArchiMateLLMResult.DiagramNodeSpec node : spec.getNodes()) {
            String elementId = node.getElementId();
            if (elementId == null || elementId.isEmpty()) continue;
            IArchimateConcept concept = idToConcept.get(elementId);
            if (concept == null) {
                concept = findConceptById(model, elementId);
            }
            if (!(concept instanceof IArchimateElement)) continue;
            IArchimateElement element = (IArchimateElement) concept;
            IDiagramModelArchimateObject existingFig = figureAlreadyQueued(diagram, element, pendingFigures);
            if (existingFig != null) {
                elementIdToDiagramObject.put(elementId, existingFig);
                continue;
            }
            int x = node.getX() + nodeDelta[0];
            int y = node.getY() + nodeDelta[1];
            IDiagramModelArchimateObject dmo = createFigure(element, x, y, node.getWidth(), node.getHeight());
            diagram.getChildren().add(dmo);
            elementIdToDiagramObject.put(elementId, dmo);
            if (pendingFigures != null) {
                pendingFigures.put(element, dmo);
            }
            noteAddedToView(stats);
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
            noteAddedToView(stats);
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
                    rel, byElement, stats);
        }
    }

    private static void createNewDiagram(ArchiMateLLMResult.DiagramSpec spec, IArchimateModel model,
            Map<String, IArchimateConcept> idToConcept, Map<String, IArchimateRelationship> idToRelationship,
            Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures, ImportStats stats) {
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
        addNodesAndConnectionsToDiagram(diagram, spec, model, idToConcept, idToRelationship, pendingFigures, stats);
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

    private static final class PreparedElement {
        final IArchimateElement element;
        final boolean created;

        PreparedElement(IArchimateElement element, boolean created) {
            this.element = element;
            this.created = created;
        }
    }

    /**
     * Resolve an element spec to one model object. Same type and name always reuses the existing
     * element (including one created earlier in this import). A matching id renames in place only
     * when no element of that type and name exists yet.
     */
    private static PreparedElement prepareElement(ArchiMateLLMResult.ElementSpec e, IArchimateModel model,
            IFolder targetFolder, Map<String, IArchimateConcept> idToConcept,
            Map<String, IArchimateElement> preparedByTypeAndName, NonNotifyingCompoundCommand compound,
            ImportStats stats) {
        String normalizedType = ArchiMateSchemaValidator.normalizeElementType(e.getType());
        EClass eClass = (EClass) IArchimatePackage.eINSTANCE.getEClassifier(normalizedType);
        if (eClass == null || !IArchimatePackage.eINSTANCE.getArchimateElement().isSuperTypeOf(eClass)) {
            return null;
        }
        String name = e.getName();
        String llmId = e.getId() != null ? e.getId().trim() : "";
        if (!llmId.isEmpty()) {
            IArchimateConcept mapped = idToConcept.get(llmId);
            if (mapped instanceof IArchimateElement) {
                IArchimateElement mappedEl = (IArchimateElement) mapped;
                if (!(eClass.isInstance(mappedEl) && (name == null || sameElementName(mappedEl.getName(), name)))) {
                    // Same example/placeholder id reused for a different element in this payload.
                    llmId = "";
                }
            }
        }
        String nameKey = typeAndNameKey(eClass, name);
        if (nameKey != null && preparedByTypeAndName != null) {
            IArchimateElement batched = preparedByTypeAndName.get(nameKey);
            if (batched != null && eClass.isInstance(batched)) {
                return finishExistingElement(e, batched, eClass, name, llmId, idToConcept, preparedByTypeAndName,
                        compound, stats);
            }
        }
        IArchimateElement existing = existingElementForSpec(model, eClass, name != null ? name : "", llmId);
        if (existing != null) {
            return finishExistingElement(e, existing, eClass, name, llmId, idToConcept, preparedByTypeAndName,
                    compound, stats);
        }
        IArchimateElement element = (IArchimateElement) IArchimateFactory.eINSTANCE.create(eClass);
        element.setName(name != null ? name : "");
        if (e.getDocumentation() != null) {
            element.setDocumentation(e.getDocumentation());
        }
        String elementId = ensureArchiMateId(llmId.isEmpty() ? null : llmId);
        element.setId(elementId);
        IFolder folder = resolveFolderFor(model, element, targetFolder);
        if (folder == null) {
            return null;
        }
        if (compound != null) {
            compound.add(new AddListMemberCommand("ArchiGPT: add element", folder.getElements(), element));
        } else {
            folder.getElements().add(element);
        }
        idToConcept.put(elementId, element);
        if (!llmId.isEmpty() && !elementId.equals(llmId)) {
            idToConcept.put(llmId, element);
        }
        rememberPrepared(preparedByTypeAndName, eClass, name, element);
        if (stats != null) {
            stats.createdElements++;
        }
        return new PreparedElement(element, true);
    }

    private static PreparedElement finishExistingElement(ArchiMateLLMResult.ElementSpec e, IArchimateElement existing,
            EClass eClass, String name, String llmId, Map<String, IArchimateConcept> idToConcept,
            Map<String, IArchimateElement> preparedByTypeAndName, NonNotifyingCompoundCommand compound,
            ImportStats stats) {
        rememberConceptId(idToConcept, llmId.isEmpty() ? existing.getId() : llmId, existing);
        rememberPrepared(preparedByTypeAndName, eClass, name != null ? name : existing.getName(), existing);
        if (name != null && eClass.isInstance(existing) && !sameElementName(existing.getName(), name)
                && namesDiffer(existing.getName(), name)) {
            applyRename(compound, existing, name);
            if (stats != null) {
                stats.renamedElements++;
            }
        }
        if (applyDocumentation(compound, existing, e.getDocumentation()) && stats != null) {
            stats.updatedDocumentation++;
        }
        return new PreparedElement(existing, false);
    }

    private static void rememberPrepared(Map<String, IArchimateElement> preparedByTypeAndName, EClass eClass,
            String name, IArchimateElement element) {
        if (preparedByTypeAndName == null || element == null) {
            return;
        }
        String key = typeAndNameKey(eClass, name);
        if (key != null) {
            preparedByTypeAndName.putIfAbsent(key, element);
        }
    }

    /** Null when the name is blank, so unnamed elements are not collapsed together. */
    private static String typeAndNameKey(EClass eClass, String name) {
        if (eClass == null) {
            return null;
        }
        String normalized = normalizeElementName(name);
        if (normalized.isEmpty()) {
            return null;
        }
        return eClass.getName() + "\n" + normalized;
    }

    private static String normalizeElementName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean sameElementName(String a, String b) {
        String left = normalizeElementName(a);
        return !left.isEmpty() && left.equals(normalizeElementName(b));
    }

    private static void applyRename(NonNotifyingCompoundCommand compound, IArchimateConcept concept, String name) {
        if (compound != null) {
            compound.add(new SetNameCommand(concept, name));
        } else {
            concept.setName(name);
        }
    }

    /**
     * Apply name/documentation on an existing relationship. Null name or documentation means leave unchanged.
     */
    private static void applyExistingRelationshipEdits(NonNotifyingCompoundCommand compound,
            IArchimateRelationship existingRel, ArchiMateLLMResult.RelationshipSpec r, ImportStats stats) {
        if (existingRel == null || r == null) {
            return;
        }
        if (r.getName() != null && namesDiffer(existingRel.getName(), r.getName())) {
            applyRename(compound, existingRel, r.getName());
            if (stats != null) {
                stats.renamedRelationships++;
            }
        }
        if (applyDocumentation(compound, existingRel, r.getDocumentation()) && stats != null) {
            stats.updatedDocumentation++;
        }
    }

    /**
     * Apply documentation when the LLM included the field. Null means leave existing text unchanged.
     * @return true if the stored documentation actually changed
     */
    private static boolean applyDocumentation(NonNotifyingCompoundCommand compound, IArchimateConcept concept,
            String documentation) {
        if (concept == null || documentation == null || !namesDiffer(concept.getDocumentation(), documentation)) {
            return false;
        }
        if (compound != null) {
            compound.add(new SetDocumentationCommand(concept, documentation));
        } else {
            concept.setDocumentation(documentation);
        }
        return true;
    }

    private static IArchimateRelationship existingRelationshipForSpec(IArchimateModel model,
            ArchiMateLLMResult.RelationshipSpec r) {
        if (r == null) {
            return null;
        }
        IArchimateConcept byId = findConceptById(model, r.getId());
        return byId instanceof IArchimateRelationship ? (IArchimateRelationship) byId : null;
    }

    private static void rememberRelationshipId(Map<String, IArchimateRelationship> idToRelationship, String llmId,
            IArchimateRelationship rel) {
        if (idToRelationship == null || rel == null) {
            return;
        }
        if (rel.getId() != null && !rel.getId().isEmpty()) {
            idToRelationship.put(rel.getId(), rel);
        }
        if (llmId != null && !llmId.trim().isEmpty()) {
            String trimmed = llmId.trim();
            idToRelationship.put(trimmed, rel);
            String normalized = normalizeLookupId(trimmed);
            if (normalized != null) {
                idToRelationship.put(normalized, rel);
            }
        }
    }

    private static boolean namesDiffer(String a, String b) {
        String left = a != null ? a : "";
        String right = b != null ? b : "";
        return !left.equals(right);
    }

    /**
     * Folder that should contain a new concept. Never returns a diagrams folder; falls back so
     * new elements are not left as view-only figures.
     */
    private static IFolder resolveFolderFor(IArchimateModel model, IArchimateConcept object, IFolder targetFolder) {
        if (model == null || object == null) {
            return null;
        }
        IFolder defaultFolder = model.getDefaultFolderForObject(object);
        if (targetFolder != null && defaultFolder != null && targetFolder.getType() == defaultFolder.getType()) {
            return targetFolder;
        }
        if (defaultFolder != null) {
            return defaultFolder;
        }
        IFolder user = model.getFolder(FolderType.USER);
        if (user != null) {
            return user;
        }
        IFolder other = model.getFolder(FolderType.OTHER);
        if (other != null) {
            return other;
        }
        if (model.getFolders() != null) {
            for (IFolder f : model.getFolders()) {
                if (f.getType() != FolderType.DIAGRAMS) {
                    return f;
                }
            }
        }
        return null;
    }

    private static IDiagramModelArchimateObject createFigure(IArchimateElement element, int x, int y, int width,
            int height) {
        IDiagramModelArchimateObject dmo = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
        dmo.setArchimateElement(element);
        dmo.setBounds(x, y, width, height);
        return dmo;
    }

    /**
     * Place newly created elements on an existing view. Prefer slots around the on-diagram
     * concept they connect to most so relationship arrows stay short and do not cross the cluster.
     * Unconnected elements still fall back to a column just outside the occupied area.
     */
    private static void placeCreatedFiguresOnView(NonNotifyingCompoundCommand compound, IArchimateDiagramModel diagram,
            List<PreparedElement> createdForView, ArchiMateLLMResult result, IArchimateModel model,
            Map<String, IArchimateConcept> idToConcept, Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures,
            ImportStats stats) {
        if (diagram == null || createdForView == null || createdForView.isEmpty()) {
            return;
        }
        List<int[]> occupied = collectOccupiedRects(diagram);
        List<PreparedElement> remaining = new ArrayList<PreparedElement>(createdForView);
        while (!remaining.isEmpty()) {
            PreparedElement chosen = null;
            IArchimateElement anchor = null;
            for (PreparedElement candidate : remaining) {
                IArchimateElement found = bestAnchorOnView(candidate.element, result, model, idToConcept, diagram,
                        pendingFigures);
                if (found != null) {
                    chosen = candidate;
                    anchor = found;
                    break;
                }
            }
            if (chosen == null) {
                chosen = remaining.get(0);
            }
            int[] xy = anchor != null
                    ? firstFreeSlotAround(anchor, diagram, pendingFigures, occupied)
                    : firstFreeFallbackSlot(diagram, occupied);
            IDiagramModelArchimateObject dmo = createFigure(chosen.element, xy[0], xy[1],
                    DEFAULT_ELEMENT_WIDTH, DEFAULT_ELEMENT_HEIGHT);
            if (compound != null) {
                compound.add(new AddDiagramObjectCommand(diagram, dmo));
            } else {
                diagram.getChildren().add(dmo);
            }
            pendingFigures.put(chosen.element, dmo);
            occupied.add(new int[] { xy[0], xy[1], DEFAULT_ELEMENT_WIDTH, DEFAULT_ELEMENT_HEIGHT });
            remaining.remove(chosen);
            noteAddedToView(stats);
        }
    }

    /**
     * On-diagram (or already-placed) element with the most payload relationships to {@code newbie}.
     * Prefers an existing figure over a newly placed satellite when counts tie.
     */
    private static IArchimateElement bestAnchorOnView(IArchimateElement newbie, ArchiMateLLMResult result,
            IArchimateModel model, Map<String, IArchimateConcept> idToConcept, IArchimateDiagramModel diagram,
            Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures) {
        if (newbie == null || result == null) {
            return null;
        }
        Set<IArchimateElement> candidates = new LinkedHashSet<IArchimateElement>();
        collectElementsWithFigures(diagram, candidates);
        if (pendingFigures != null) {
            candidates.addAll(pendingFigures.keySet());
        }
        candidates.remove(newbie);
        IArchimateElement best = null;
        int bestCount = 0;
        boolean bestPending = true;
        for (IArchimateElement candidate : candidates) {
            int count = payloadConnectionCount(newbie, candidate, result, model, idToConcept);
            if (count <= 0) {
                continue;
            }
            boolean pending = pendingFigures != null && pendingFigures.containsKey(candidate);
            if (count > bestCount || (count == bestCount && bestPending && !pending)) {
                best = candidate;
                bestCount = count;
                bestPending = pending;
            }
        }
        return best;
    }

    private static int payloadConnectionCount(IArchimateElement a, IArchimateElement b, ArchiMateLLMResult result,
            IArchimateModel model, Map<String, IArchimateConcept> idToConcept) {
        if (a == null || b == null || result == null || result.getRelationships() == null) {
            return 0;
        }
        int n = 0;
        for (ArchiMateLLMResult.RelationshipSpec r : result.getRelationships()) {
            IArchimateElement source = resolveElement(r.getSource(), idToConcept, model);
            IArchimateElement target = resolveElement(r.getTarget(), idToConcept, model);
            if (source == null || target == null) {
                continue;
            }
            if ((source == a && target == b) || (source == b && target == a)) {
                n++;
            }
        }
        return n;
    }

    private static IArchimateElement resolveElement(String id, Map<String, IArchimateConcept> idToConcept,
            IArchimateModel model) {
        IArchimateConcept concept = null;
        if (idToConcept != null && id != null) {
            concept = idToConcept.get(id);
            if (concept == null) {
                String normalized = normalizeLookupId(id);
                if (normalized != null) {
                    concept = idToConcept.get(normalized);
                }
            }
        }
        if (concept == null) {
            concept = findConceptById(model, id);
        }
        return concept instanceof IArchimateElement ? (IArchimateElement) concept : null;
    }

    private static int[] firstFreeSlotAround(IArchimateElement anchor, IArchimateDiagramModel diagram,
            Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures, List<int[]> occupied) {
        int[] hub = boundsOfFigure(anchor, diagram, pendingFigures);
        if (hub == null) {
            return firstFreeFallbackSlot(diagram, occupied);
        }
        int[] centroid = occupiedCentroid(occupied, hub);
        for (int[] slot : candidateSlotsAround(hub, centroid, DEFAULT_ELEMENT_WIDTH, DEFAULT_ELEMENT_HEIGHT)) {
            if (slot[0] < CANVAS_MARGIN || slot[1] < CANVAS_MARGIN) {
                continue;
            }
            if (!overlapsOccupied(slot[0], slot[1], DEFAULT_ELEMENT_WIDTH, DEFAULT_ELEMENT_HEIGHT, occupied)) {
                return slot;
            }
        }
        return firstFreeFallbackSlot(diagram, occupied);
    }

    /**
     * Cardinal then diagonal slots, outward from the cluster centroid first, then further rings.
     * East/South/West/North order when the hub is the only figure.
     */
    private static List<int[]> candidateSlotsAround(int[] hub, int[] centroid, int w, int h) {
        int hubCx = hub[0] + hub[2] / 2;
        int hubCy = hub[1] + hub[3] / 2;
        int outX = hubCx - centroid[0];
        int outY = hubCy - centroid[1];
        if (outX == 0 && outY == 0) {
            outX = 1;
        }
        final int preferX = outX;
        final int preferY = outY;
        Integer[] order = new Integer[] { 0, 1, 2, 3, 4, 5, 6, 7 };
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                int[] da = AROUND_DIRS[a.intValue()];
                int[] db = AROUND_DIRS[b.intValue()];
                int sa = da[0] * preferX + da[1] * preferY;
                int sb = db[0] * preferX + db[1] * preferY;
                if (sa != sb) {
                    return sb - sa;
                }
                return a.intValue() - b.intValue();
            }
        });
        List<int[]> slots = new ArrayList<int[]>();
        for (int ring = 1; ring <= 6; ring++) {
            int extraX = (ring - 1) * (w + DEFAULT_GAP);
            int extraY = (ring - 1) * (h + DEFAULT_GAP);
            for (int oi = 0; oi < order.length; oi++) {
                int[] dir = AROUND_DIRS[order[oi].intValue()];
                int x;
                int y;
                if (dir[0] > 0) {
                    x = hub[0] + hub[2] + DEFAULT_GAP + extraX;
                } else if (dir[0] < 0) {
                    x = hub[0] - w - DEFAULT_GAP - extraX;
                } else {
                    x = hubCx - w / 2;
                }
                if (dir[1] > 0) {
                    y = hub[1] + hub[3] + DEFAULT_GAP + extraY;
                } else if (dir[1] < 0) {
                    y = hub[1] - h - DEFAULT_GAP - extraY;
                } else {
                    y = hubCy - h / 2;
                }
                slots.add(new int[] { x, y });
            }
        }
        return slots;
    }

    private static final int[][] AROUND_DIRS = {
            { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 },
            { 1, 1 }, { -1, 1 }, { 1, -1 }, { -1, -1 }
    };

    private static int[] firstFreeFallbackSlot(IArchimateDiagramModel diagram, List<int[]> occupied) {
        int[] origin = newFigureOrigin(diagram, DEFAULT_ELEMENT_WIDTH, DEFAULT_ELEMENT_HEIGHT);
        int x = origin[0];
        int y = origin[1];
        if (x < CANVAS_MARGIN) {
            x = CANVAS_MARGIN;
        }
        if (y < CANVAS_MARGIN) {
            y = CANVAS_MARGIN;
        }
        for (int i = 0; i < 40; i++) {
            if (!overlapsOccupied(x, y, DEFAULT_ELEMENT_WIDTH, DEFAULT_ELEMENT_HEIGHT, occupied)) {
                return new int[] { x, y };
            }
            y += DEFAULT_ELEMENT_HEIGHT + DEFAULT_GAP;
        }
        return new int[] { x, y };
    }

    private static int[] boundsOfFigure(IArchimateElement element, IArchimateDiagramModel diagram,
            Map<IArchimateElement, IDiagramModelArchimateObject> pendingFigures) {
        IDiagramModelArchimateObject dmo = findFigureOnDiagram(diagram, element, pendingFigures);
        if (dmo == null) {
            return null;
        }
        IBounds b = dmo.getBounds();
        if (b == null) {
            return null;
        }
        return new int[] { b.getX(), b.getY(), b.getWidth(), b.getHeight() };
    }

    private static void collectElementsWithFigures(IDiagramModelContainer container, Set<IArchimateElement> out) {
        if (container == null || container.getChildren() == null) {
            return;
        }
        for (Object child : container.getChildren()) {
            if (child instanceof IDiagramModelArchimateObject) {
                IArchimateElement el = ((IDiagramModelArchimateObject) child).getArchimateElement();
                if (el != null) {
                    out.add(el);
                }
            }
            if (child instanceof IDiagramModelContainer) {
                collectElementsWithFigures((IDiagramModelContainer) child, out);
            }
        }
    }

    private static List<int[]> collectOccupiedRects(IDiagramModelContainer container) {
        List<int[]> out = new ArrayList<int[]>();
        accumulateOccupiedRects(container, out);
        return out;
    }

    private static void accumulateOccupiedRects(IDiagramModelContainer container, List<int[]> out) {
        if (container == null || container.getChildren() == null) {
            return;
        }
        for (Object child : container.getChildren()) {
            if (child instanceof IDiagramModelObject) {
                IBounds b = ((IDiagramModelObject) child).getBounds();
                if (b != null) {
                    out.add(new int[] { b.getX(), b.getY(), b.getWidth(), b.getHeight() });
                }
            }
            if (child instanceof IDiagramModelContainer) {
                accumulateOccupiedRects((IDiagramModelContainer) child, out);
            }
        }
    }

    private static int[] occupiedCentroid(List<int[]> occupied, int[] fallbackHub) {
        if (occupied == null || occupied.isEmpty()) {
            return new int[] { fallbackHub[0] + fallbackHub[2] / 2, fallbackHub[1] + fallbackHub[3] / 2 };
        }
        long sx = 0;
        long sy = 0;
        int n = 0;
        for (int[] r : occupied) {
            sx += r[0] + r[2] / 2;
            sy += r[1] + r[3] / 2;
            n++;
        }
        return new int[] { (int) (sx / n), (int) (sy / n) };
    }

    private static boolean overlapsOccupied(int x, int y, int w, int h, List<int[]> occupied) {
        if (occupied == null) {
            return false;
        }
        int pad = Math.max(1, DEFAULT_GAP / 2);
        for (int[] r : occupied) {
            if (x < r[0] + r[2] + pad && x + w + pad > r[0] && y < r[1] + r[3] + pad && y + h + pad > r[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Origin for new figures: empty view stays at (50,50); otherwise about 1/3 of an element size
     * to the left and below the current occupied area so new objects are near existing ones without
     * landing on top of them.
     */
    private static int[] newFigureOrigin(IDiagramModelContainer diagram, int elementWidth, int elementHeight) {
        int[] occupied = occupiedBounds(diagram);
        if (occupied == null) {
            return new int[] { EMPTY_DIAGRAM_X, EMPTY_DIAGRAM_Y };
        }
        int dx = Math.max(1, elementWidth / 3);
        int dy = Math.max(1, elementHeight / 3);
        int x = occupied[0] - dx;
        int y = occupied[3] + dy;
        if (x < CANVAS_MARGIN) {
            x = CANVAS_MARGIN;
        }
        return new int[] { x, y };
    }

    /**
     * If the LLM's node coordinates overlap figures already on the view, shift the whole new
     * group to {@link #newFigureOrigin} while keeping relative layout.
     */
    private static int[] nodeTranslationToClearExisting(IArchimateDiagramModel diagram,
            ArchiMateLLMResult.DiagramSpec spec, IArchimateModel model,
            Map<String, IArchimateConcept> idToConcept) {
        if (diagram == null || spec == null || spec.getNodes().isEmpty()) {
            return new int[] { 0, 0 };
        }
        int[] occupied = occupiedBounds(diagram);
        if (occupied == null) {
            return new int[] { 0, 0 };
        }
        int specMinX = Integer.MAX_VALUE;
        int specMinY = Integer.MAX_VALUE;
        boolean overlaps = false;
        boolean anyNew = false;
        for (ArchiMateLLMResult.DiagramNodeSpec node : spec.getNodes()) {
            String elementId = node.getElementId();
            IArchimateConcept concept = idToConcept != null ? idToConcept.get(elementId) : null;
            if (concept == null) {
                concept = findConceptById(model, elementId);
            }
            if (concept instanceof IArchimateElement
                    && findFigureInChildren(diagram, (IArchimateElement) concept) != null) {
                continue;
            }
            anyNew = true;
            specMinX = Math.min(specMinX, node.getX());
            specMinY = Math.min(specMinY, node.getY());
            if (rectOverlaps(node.getX(), node.getY(), node.getWidth(), node.getHeight(), occupied)) {
                overlaps = true;
            }
        }
        if (!anyNew || !overlaps || specMinX == Integer.MAX_VALUE) {
            return new int[] { 0, 0 };
        }
        int[] origin = newFigureOrigin(diagram, DEFAULT_ELEMENT_WIDTH, DEFAULT_ELEMENT_HEIGHT);
        return new int[] { origin[0] - specMinX, origin[1] - specMinY };
    }

    /** {minX, minY, maxRight, maxBottom} of all figures on the container, or null if none. */
    private static int[] occupiedBounds(IDiagramModelContainer container) {
        int[] acc = new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        accumulateOccupied(container, acc);
        if (acc[0] == Integer.MAX_VALUE) {
            return null;
        }
        return acc;
    }

    private static void accumulateOccupied(IDiagramModelContainer container, int[] acc) {
        if (container == null || container.getChildren() == null) {
            return;
        }
        for (Object child : container.getChildren()) {
            if (child instanceof IDiagramModelObject) {
                IDiagramModelObject dmo = (IDiagramModelObject) child;
                IBounds b = dmo.getBounds();
                if (b != null) {
                    int x = b.getX();
                    int y = b.getY();
                    int right = x + b.getWidth();
                    int bottom = y + b.getHeight();
                    if (x < acc[0]) {
                        acc[0] = x;
                    }
                    if (y < acc[1]) {
                        acc[1] = y;
                    }
                    if (right > acc[2]) {
                        acc[2] = right;
                    }
                    if (bottom > acc[3]) {
                        acc[3] = bottom;
                    }
                }
            }
            if (child instanceof IDiagramModelContainer) {
                accumulateOccupied((IDiagramModelContainer) child, acc);
            }
        }
    }

    private static boolean rectOverlaps(int x, int y, int w, int h, int[] box) {
        return x < box[2] && x + w > box[0] && y < box[3] && y + h > box[1];
    }

    /**
     * Element already in the model for this spec. Same type and name wins over id, so a reused
     * example id cannot rename a different element onto a name that already exists. Id is the
     * rename path when that name is new. Callers must still map the LLM's id onto the element so
     * relationships in this payload resolve.
     */
    private static IArchimateElement existingElementForSpec(IArchimateModel model, EClass eClass, String name,
            String llmId) {
        if (!normalizeElementName(name).isEmpty()) {
            IArchimateElement byName = findElementByTypeAndName(model, eClass, name);
            if (byName != null) {
                return byName;
            }
        }
        IArchimateConcept byId = findConceptById(model, llmId);
        if (byId instanceof IArchimateElement && eClass != null && eClass.isInstance(byId)) {
            return (IArchimateElement) byId;
        }
        return null;
    }

    private static void noteAddedToView(ImportStats stats) {
        if (stats != null) {
            stats.addedToView++;
        }
    }

    private static IArchimateElement findElementByTypeAndName(IArchimateModel model, EClass eClass, String name) {
        if (model == null || eClass == null) {
            return null;
        }
        String n = normalizeElementName(name);
        if (n.isEmpty()) {
            return null;
        }
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
                if (existing != null && normalizeElementName(existing).equals(name)) {
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
