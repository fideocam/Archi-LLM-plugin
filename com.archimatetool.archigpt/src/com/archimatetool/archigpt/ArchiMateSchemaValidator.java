/**
 * Validates parsed LLM output against the Open Group ArchiMate 3.2 schema
 * using Archi's IArchimatePackage (EClass types).
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /**
     * Official ArchiMate 3.2 element EClass names plus LLM / ArchiMate 2 aliases.
     * Keys are lower-case with spaces removed.
     */
    private static final Map<String, String> ELEMENT_ALIASES;

    /**
     * Official relationship EClass names plus LLM / ArchiMate 2 aliases.
     * Keys are lower-case with spaces removed, including the {@code Relationship} suffix.
     */
    private static final Map<String, String> RELATIONSHIP_ALIASES;

    static {
        Map<String, String> el = new HashMap<>();
        registerCanonical(el,
                "Junction",
                "ApplicationCollaboration", "ApplicationComponent", "ApplicationEvent", "ApplicationFunction",
                "ApplicationInteraction", "ApplicationInterface", "ApplicationProcess", "ApplicationService",
                "Artifact", "Assessment",
                "BusinessActor", "BusinessCollaboration", "BusinessEvent", "BusinessFunction",
                "BusinessInteraction", "BusinessInterface", "BusinessObject", "BusinessProcess",
                "BusinessRole", "BusinessService",
                "Capability", "CommunicationNetwork", "Contract", "Constraint", "CourseOfAction",
                "DataObject", "Deliverable", "Device", "DistributionNetwork", "Driver",
                "Equipment", "Facility", "Gap", "Goal", "Grouping", "ImplementationEvent",
                "Location", "Material", "Meaning", "Node", "Outcome", "Path", "Plateau",
                "Principle", "Product", "Representation", "Requirement", "Resource",
                "Stakeholder", "SystemSoftware",
                "TechnologyCollaboration", "TechnologyEvent", "TechnologyFunction", "TechnologyInteraction",
                "TechnologyInterface", "TechnologyProcess", "TechnologyService",
                "Value", "ValueStream", "WorkPackage");
        // Unqualified names (models omit the layer prefix).
        alias(el, "Actor", "BusinessActor");
        alias(el, "Person", "BusinessActor");
        alias(el, "Organisation", "BusinessActor");
        alias(el, "Organization", "BusinessActor");
        alias(el, "OrganizationalUnit", "BusinessActor");
        alias(el, "OrganisationalUnit", "BusinessActor");
        alias(el, "Department", "BusinessActor");
        alias(el, "Role", "BusinessRole");
        alias(el, "Process", "BusinessProcess");
        alias(el, "Activity", "BusinessProcess");
        alias(el, "BusinessActivity", "BusinessProcess");
        alias(el, "UseCase", "BusinessProcess");
        alias(el, "Function", "BusinessFunction");
        alias(el, "Service", "BusinessService");
        alias(el, "Event", "BusinessEvent");
        alias(el, "Object", "BusinessObject");
        alias(el, "Collaboration", "BusinessCollaboration");
        alias(el, "Interaction", "BusinessInteraction");
        alias(el, "Component", "ApplicationComponent");
        alias(el, "Application", "ApplicationComponent");
        alias(el, "Module", "ApplicationComponent");
        alias(el, "Microservice", "ApplicationComponent");
        alias(el, "Interface", "ApplicationInterface");
        alias(el, "API", "ApplicationInterface");
        alias(el, "Data", "DataObject");
        alias(el, "Entity", "DataObject");
        alias(el, "DataEntity", "DataObject");
        alias(el, "Database", "DataObject");
        alias(el, "Software", "SystemSoftware");
        alias(el, "OperatingSystem", "SystemSoftware");
        alias(el, "OS", "SystemSoftware");
        alias(el, "Server", "Node");
        alias(el, "Host", "Node");
        alias(el, "VM", "Node");
        alias(el, "TechnologyNode", "Node");
        alias(el, "InfrastructureNode", "Node");
        alias(el, "Network", "CommunicationNetwork");
        alias(el, "CommunicationPath", "Path");
        // ArchiMate 2 infrastructure layer → technology layer.
        alias(el, "InfrastructureService", "TechnologyService");
        alias(el, "InfrastructureFunction", "TechnologyFunction");
        alias(el, "InfrastructureInterface", "TechnologyInterface");
        alias(el, "InfrastructureProcess", "TechnologyProcess");
        alias(el, "InfrastructureEvent", "TechnologyEvent");
        alias(el, "InfrastructureInteraction", "TechnologyInteraction");
        alias(el, "InfrastructureCollaboration", "TechnologyCollaboration");
        alias(el, "AndJunction", "Junction");
        alias(el, "OrJunction", "Junction");
        ELEMENT_ALIASES = Collections.unmodifiableMap(el);

        Map<String, String> rel = new HashMap<>();
        registerCanonical(rel,
                "AccessRelationship", "AggregationRelationship", "AssignmentRelationship",
                "AssociationRelationship", "CompositionRelationship", "FlowRelationship",
                "InfluenceRelationship", "RealizationRelationship", "ServingRelationship",
                "SpecializationRelationship", "TriggeringRelationship");
        alias(rel, "InteractionRelationship", "AssociationRelationship");
        alias(rel, "InteractsWithRelationship", "AssociationRelationship");
        alias(rel, "CollaborationRelationship", "AssociationRelationship");
        alias(rel, "CommunicationRelationship", "AssociationRelationship");
        alias(rel, "CommunicatesWithRelationship", "AssociationRelationship");
        alias(rel, "ConnectionRelationship", "AssociationRelationship");
        alias(rel, "DependencyRelationship", "AssociationRelationship");
        alias(rel, "DependsOnRelationship", "AssociationRelationship");
        alias(rel, "AssociatedWithRelationship", "AssociationRelationship");
        alias(rel, "RelatedToRelationship", "AssociationRelationship");
        alias(rel, "RelatesToRelationship", "AssociationRelationship");
        alias(rel, "LinkedToRelationship", "AssociationRelationship");
        alias(rel, "LinksToRelationship", "AssociationRelationship");
        alias(rel, "LinkRelationship", "AssociationRelationship");
        alias(rel, "ConnectsToRelationship", "AssociationRelationship");
        alias(rel, "UsedByRelationship", "ServingRelationship");
        alias(rel, "UsesRelationship", "ServingRelationship");
        alias(rel, "UseRelationship", "ServingRelationship");
        alias(rel, "ServesRelationship", "ServingRelationship");
        alias(rel, "SupportsRelationship", "ServingRelationship");
        alias(rel, "CallsRelationship", "ServingRelationship");
        alias(rel, "AccessesRelationship", "AccessRelationship");
        alias(rel, "ReadsRelationship", "AccessRelationship");
        alias(rel, "WritesRelationship", "AccessRelationship");
        alias(rel, "AssignedToRelationship", "AssignmentRelationship");
        alias(rel, "AssignedRelationship", "AssignmentRelationship");
        alias(rel, "RealizesRelationship", "RealizationRelationship");
        alias(rel, "RealisationRelationship", "RealizationRelationship");
        alias(rel, "ImplementsRelationship", "RealizationRelationship");
        alias(rel, "ImplementationRelationship", "RealizationRelationship");
        alias(rel, "FulfillsRelationship", "RealizationRelationship");
        alias(rel, "FulfilsRelationship", "RealizationRelationship");
        alias(rel, "TriggersRelationship", "TriggeringRelationship");
        alias(rel, "TriggerRelationship", "TriggeringRelationship");
        alias(rel, "FlowsToRelationship", "FlowRelationship");
        alias(rel, "FlowsRelationship", "FlowRelationship");
        alias(rel, "SendsRelationship", "FlowRelationship");
        alias(rel, "InfluencesRelationship", "InfluenceRelationship");
        alias(rel, "ContainsRelationship", "CompositionRelationship");
        alias(rel, "ComposedOfRelationship", "CompositionRelationship");
        alias(rel, "IncludesRelationship", "CompositionRelationship");
        alias(rel, "HasRelationship", "CompositionRelationship");
        alias(rel, "AggregatesRelationship", "AggregationRelationship");
        alias(rel, "SpecialisationRelationship", "SpecializationRelationship");
        alias(rel, "InheritsRelationship", "SpecializationRelationship");
        alias(rel, "InheritanceRelationship", "SpecializationRelationship");
        alias(rel, "IsARelationship", "SpecializationRelationship");
        alias(rel, "ExtendsRelationship", "SpecializationRelationship");
        alias(rel, "GeneralizationRelationship", "SpecializationRelationship");
        alias(rel, "GeneralisationRelationship", "SpecializationRelationship");
        RELATIONSHIP_ALIASES = Collections.unmodifiableMap(rel);
    }

    private ArchiMateSchemaValidator() {}

    private static void registerCanonical(Map<String, String> map, String... names) {
        for (String name : names) {
            map.put(name.toLowerCase(Locale.ROOT), name);
        }
    }

    private static void alias(Map<String, String> map, String from, String to) {
        map.put(from.replaceAll("\\s+", "").toLowerCase(Locale.ROOT), to);
    }

    /**
     * Maps LLM/spec type names to IArchimatePackage EClass names. Archi EClass names are PascalCase with no spaces.
     * Handles: "Technology Node" or "TechnologyNode" -> "Node"; "Business Actor" -> "BusinessActor";
     * unqualified names such as Actor/Process/Service; ArchiMate 2 Infrastructure* types.
     */
    public static String normalizeElementType(String type) {
        if (type == null || type.isEmpty()) return type;
        String t = type.trim().replaceAll("\\s+", "");
        if (t.isEmpty()) return type;
        String aliased = ELEMENT_ALIASES.get(t.toLowerCase(Locale.ROOT));
        return aliased != null ? aliased : t;
    }

    /**
     * Maps LLM/spec relationship type names to IArchimatePackage EClass names (PascalCase, no spaces).
     * Accepts names without the {@code Relationship} suffix and common aliases
     * (e.g. {@code InteractionRelationship} → {@code AssociationRelationship},
     * {@code UsedByRelationship} → {@code ServingRelationship}).
     */
    public static String normalizeRelationshipType(String type) {
        if (type == null || type.isEmpty()) return type;
        String t = type.trim().replaceAll("\\s+", "");
        if (t.isEmpty()) {
            return type;
        }
        if (!t.toLowerCase(Locale.ROOT).endsWith("relationship")
                && !"Junction".equalsIgnoreCase(t)) {
            t = t + "Relationship";
        }
        String aliased = RELATIONSHIP_ALIASES.get(t.toLowerCase(Locale.ROOT));
        return aliased != null ? aliased : t;
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
