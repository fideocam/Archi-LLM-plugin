/**
 * Validates parsed LLM output against the Open Group ArchiMate 3.2 schema
 * using Archi's IArchimatePackage (EClass types).
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EClass;

import com.archimatetool.model.IArchimatePackage;

/**
 * Validates ArchiMateLLMResult: element and relationship types must exist in
 * ArchiMate 3.2 (IArchimatePackage). Relationship source/target must be non-empty;
 * they may refer to elements in this response or to existing model elements (resolved at import).
 * Normalizes type names from spec/LLM (e.g. "TechnologyNode") to Archi EClass names (e.g. "Node").
 */
@SuppressWarnings("nls")
public final class ArchiMateSchemaValidator {

    private ArchiMateSchemaValidator() {}

    /**
     * Maps LLM/spec type names to IArchimatePackage EClass names. Archi EClass names are PascalCase with no spaces.
     * Handles: "Technology Node" or "TechnologyNode" -> "Node"; "Business Actor" -> "BusinessActor"; etc.
     */
    public static String normalizeElementType(String type) {
        if (type == null || type.isEmpty()) return type;
        String t = type.trim().replaceAll("\\s+", "");
        if (t.isEmpty()) return type;
        // Archi uses "Node" for Technology layer node, not "TechnologyNode"
        if ("TechnologyNode".equals(t)) return "Node";
        return t;
    }

    /**
     * Maps LLM/spec relationship type names to IArchimatePackage EClass names (PascalCase, no spaces).
     */
    public static String normalizeRelationshipType(String type) {
        if (type == null || type.isEmpty()) return type;
        String t = type.trim().replaceAll("\\s+", "");
        return t.isEmpty() ? type : t;
    }

    /**
     * Validate the parsed result. Returns a list of error messages (empty if valid).
     */
    public static List<String> validate(ArchiMateLLMResult result) {
        List<String> errors = new ArrayList<>();

        for (ArchiMateLLMResult.ElementSpec e : result.getElements()) {
            // View and Diagram are not ArchiMate element types; the importer skips them. Do not fail validation.
            if ("View".equalsIgnoreCase(e.getType()) || "Diagram".equalsIgnoreCase(e.getType())) {
                continue;
            }
            if (e.getId() == null || e.getId().isEmpty()) {
                errors.add("Element missing id: type=" + e.getType() + ", name=" + e.getName());
                continue;
            }
            if (e.getType() == null || e.getType().isEmpty()) {
                errors.add("Element missing type: id=" + e.getId());
                continue;
            }
            String normalizedType = normalizeElementType(e.getType());
            EClass eClass = (EClass) IArchimatePackage.eINSTANCE.getEClassifier(normalizedType);
            if (eClass == null || !IArchimatePackage.eINSTANCE.getArchimateElement().isSuperTypeOf(eClass)) {
                errors.add("Invalid ArchiMate element type: " + e.getType() + " (id=" + e.getId() + ")");
            }
        }

        for (ArchiMateLLMResult.RelationshipSpec r : result.getRelationships()) {
            if (r.getType() == null || r.getType().isEmpty()) {
                errors.add("Relationship missing type: source=" + r.getSource() + " target=" + r.getTarget());
                continue;
            }
            String relType = normalizeRelationshipType(r.getType());
            EClass rClass = (EClass) IArchimatePackage.eINSTANCE.getEClassifier(relType);
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
            }
        }

        return errors;
    }
}
