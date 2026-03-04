/**
 * Imports validated ArchiMateLLMResult into an open Archi model (elements and relationships).
 */
package com.archimatetool.archigpt;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IFolder;

/**
 * Adds elements and relationships from a validated ArchiMateLLMResult to an existing IArchimateModel.
 */
@SuppressWarnings("nls")
public final class ArchiMateLLMImporter {

    private ArchiMateLLMImporter() {}

    /**
     * Import the validated result into the given model. Elements are created first, then relationships.
     * Call ArchiMateSchemaValidator.validate() before calling this.
     */
    public static void importIntoModel(ArchiMateLLMResult result, IArchimateModel model) {
        Map<String, IArchimateConcept> idToConcept = new HashMap<>();

        for (ArchiMateLLMResult.ElementSpec e : result.getElements()) {
            EClass eClass = (EClass) IArchimatePackage.eINSTANCE.getEClassifier(e.getType());
            if (eClass == null || !IArchimatePackage.eINSTANCE.getArchimateElement().isSuperTypeOf(eClass)) {
                continue;
            }
            IArchimateElement element = (IArchimateElement) IArchimateFactory.eINSTANCE.create(eClass);
            element.setName(e.getName() != null ? e.getName() : "");
            element.setId(e.getId());
            IFolder folder = model.getDefaultFolderForObject(element);
            folder.getElements().add(element);
            idToConcept.put(e.getId(), element);
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
            }
            rel.setSource(source);
            rel.setTarget(target);
            IFolder folder = model.getDefaultFolderForObject(rel);
            folder.getElements().add(rel);
        }
    }
}
