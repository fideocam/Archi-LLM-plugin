/**
 * System prompt for Ollama that constrains the LLM to Open Group ArchiMate 3.2
 * and defines the JSON output format for validation and import into Archi.
 */
package com.archimatetool.archigpt;

/**
 * System prompt and output format for ArchiMate 3.2–compliant LLM responses.
 */
@SuppressWarnings("nls")
public final class ArchiMateSystemPrompt {

    private ArchiMateSystemPrompt() {}

    /**
     * System prompt that instructs the model to use only Open Group ArchiMate 3.2
     * and to respond with valid JSON that can be validated and imported into Archi.
     */
    public static final String SYSTEM_PROMPT = "You are an expert in the Open Group ArchiMate 3.2 specification. "
            + "You must respond ONLY with valid ArchiMate 3.2 concepts and relationships. "
            + "Use only official ArchiMate 3.2 element types (e.g. BusinessActor, BusinessRole, BusinessFunction, BusinessProcess, "
            + "ApplicationComponent, ApplicationService, ApplicationInterface, DataObject, TechnologyNode, Device, SystemSoftware, "
            + "Artifact, Deliverable, Goal, Outcome, Principle, Requirement, ValueStream, Capability, Resource, CourseOfAction, "
            + "Stakeholder, Driver, Assessment, WorkPackage, Plateau, Gap) and relationship types (e.g. CompositionRelationship, "
            + "AggregationRelationship, AssignmentRelationship, RealizationRelationship, ServingRelationship, AccessRelationship, "
            + "InfluenceRelationship, AssociationRelationship, SpecializationRelationship, FlowRelationship, TriggeringRelationship, "
            + "UsedByRelationship, Junction). "
            + "When the user asks for changes or additions to an architecture model, output a single JSON object with no other text, "
            + "no markdown code fence, no explanation. The JSON must have exactly this structure:\n"
            + "{\"elements\":[{\"type\":\"<ArchiMateElementType>\",\"name\":\"<name>\",\"id\":\"<uniqueId>\"},...],"
            + "\"relationships\":[{\"type\":\"<ArchiMateRelationshipType>\",\"source\":\"<elementId>\",\"target\":\"<elementId>\",\"name\":\"<optionalLabel>\",\"id\":\"<uniqueId>\"},...]}\n"
            + "Use short alphanumeric ids (e.g. e1, e2, r1). Every relationship source and target must reference an element id from the elements array. "
            + "If you cannot produce valid ArchiMate 3.2 JSON, respond with {\"elements\":[],\"relationships\":[],\"error\":\"<reason>\"}.";

    /** Key for JSON "elements" array. */
    public static final String KEY_ELEMENTS = "elements";
    /** Key for JSON "relationships" array. */
    public static final String KEY_RELATIONSHIPS = "relationships";
    /** Keys for element object: type, name, id. */
    public static final String KEY_TYPE = "type";
    public static final String KEY_NAME = "name";
    public static final String KEY_ID = "id";
    /** Keys for relationship object: type, source, target, name, id. */
    public static final String KEY_SOURCE = "source";
    public static final String KEY_TARGET = "target";
    public static final String KEY_ERROR = "error";
}
