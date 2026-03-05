/**
 * Validates parsed LLM output against the Open Group ArchiMate 3.2 schema
 * using Archi's IArchimatePackage (EClass types).
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;

import com.archimatetool.model.IArchimatePackage;

/**
 * Validates ArchiMateLLMResult: element and relationship types must exist in
 * ArchiMate 3.2 (IArchimatePackage), and relationship source/target must reference element ids.
 */
@SuppressWarnings("nls")
public final class ArchiMateSchemaValidator {

    private ArchiMateSchemaValidator() {}

    /**
     * Validate the parsed result. Returns a list of error messages (empty if valid).
     */
    public static List<String> validate(ArchiMateLLMResult result) {
        List<String> errors = new ArrayList<>();
        Set<String> elementIds = new HashSet<>();

        for (ArchiMateLLMResult.ElementSpec e : result.getElements()) {
            if (e.getId() == null || e.getId().isEmpty()) {
                errors.add("Element missing id: type=" + e.getType() + ", name=" + e.getName());
                continue;
            }
            if (e.getType() == null || e.getType().isEmpty()) {
                errors.add("Element missing type: id=" + e.getId());
                continue;
            }
            EClass eClass = (EClass) IArchimatePackage.eINSTANCE.getEClassifier(e.getType());
            if (eClass == null || !IArchimatePackage.eINSTANCE.getArchimateElement().isSuperTypeOf(eClass)) {
                String hint = "Diagram".equalsIgnoreCase(e.getType()) || "View".equalsIgnoreCase(e.getType())
                        ? ". Use the \"diagram\" object for a new view (not an element type)"
                        : "";
                errors.add("Invalid ArchiMate element type: " + e.getType() + " (id=" + e.getId() + ")" + hint);
                continue;
            }
            elementIds.add(e.getId());
        }

        for (ArchiMateLLMResult.RelationshipSpec r : result.getRelationships()) {
            if (r.getType() == null || r.getType().isEmpty()) {
                errors.add("Relationship missing type: source=" + r.getSource() + " target=" + r.getTarget());
                continue;
            }
            EClass rClass = (EClass) IArchimatePackage.eINSTANCE.getEClassifier(r.getType());
            if (rClass == null || !IArchimatePackage.eINSTANCE.getArchimateRelationship().isSuperTypeOf(rClass)) {
                errors.add("Invalid ArchiMate relationship type: " + r.getType());
                continue;
            }
            if (r.getSource() == null || r.getSource().isEmpty()) {
                errors.add("Relationship missing source: type=" + r.getType());
                continue;
            }
            if (r.getTarget() == null || r.getTarget().isEmpty()) {
                errors.add("Relationship missing target: type=" + r.getType());
                continue;
            }
            if (!elementIds.contains(r.getSource())) {
                errors.add("Relationship source id not found: " + r.getSource() + " (type=" + r.getType() + ")");
            }
            if (!elementIds.contains(r.getTarget())) {
                errors.add("Relationship target id not found: " + r.getTarget() + " (type=" + r.getType() + ")");
            }
        }

        return errors;
    }
}
