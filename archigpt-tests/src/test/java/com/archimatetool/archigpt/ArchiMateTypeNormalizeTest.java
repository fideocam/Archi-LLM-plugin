package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * Type aliases used when the LLM invents or uses ArchiMate 2 names.
 * Does not require the Archi model on the classpath.
 */
public class ArchiMateTypeNormalizeTest {

    @Test
    public void interactionRelationship_mapsToAssociation() {
        assertEquals("AssociationRelationship",
                ArchiMateSchemaValidator.normalizeRelationshipType("InteractionRelationship"));
        assertEquals("AssociationRelationship",
                ArchiMateSchemaValidator.normalizeRelationshipType("interaction"));
        assertEquals("AssociationRelationship",
                ArchiMateSchemaValidator.normalizeRelationshipType("Interaction Relationship"));
    }

    @Test
    public void usedByRelationship_mapsToServing() {
        assertEquals("ServingRelationship",
                ArchiMateSchemaValidator.normalizeRelationshipType("UsedByRelationship"));
        assertEquals("ServingRelationship",
                ArchiMateSchemaValidator.normalizeRelationshipType("uses"));
    }

    @Test
    public void officialRelationshipNames_unchanged() {
        assertEquals("AssignmentRelationship",
                ArchiMateSchemaValidator.normalizeRelationshipType("AssignmentRelationship"));
        assertEquals("FlowRelationship",
                ArchiMateSchemaValidator.normalizeRelationshipType("FlowRelationship"));
        assertEquals("AssignmentRelationship",
                ArchiMateSchemaValidator.normalizeRelationshipType("Assignment"));
    }

    @Test
    public void interactionElement_mapsToBusinessInteraction() {
        assertEquals("BusinessInteraction", ArchiMateSchemaValidator.normalizeElementType("Interaction"));
        assertEquals("BusinessActor", ArchiMateSchemaValidator.normalizeElementType("BusinessActor"));
    }

    @Test
    public void unqualifiedElementNames_mapToBusinessOrApplicationLayer() {
        assertEquals("BusinessActor", ArchiMateSchemaValidator.normalizeElementType("Actor"));
        assertEquals("BusinessProcess", ArchiMateSchemaValidator.normalizeElementType("Process"));
        assertEquals("BusinessService", ArchiMateSchemaValidator.normalizeElementType("Service"));
        assertEquals("ApplicationComponent", ArchiMateSchemaValidator.normalizeElementType("Component"));
        assertEquals("ApplicationInterface", ArchiMateSchemaValidator.normalizeElementType("Interface"));
        assertEquals("Node", ArchiMateSchemaValidator.normalizeElementType("Server"));
        assertEquals("DataObject", ArchiMateSchemaValidator.normalizeElementType("Database"));
        assertEquals("CommunicationNetwork", ArchiMateSchemaValidator.normalizeElementType("Network"));
    }

    @Test
    public void archimate2Infrastructure_mapsToTechnology() {
        assertEquals("TechnologyService", ArchiMateSchemaValidator.normalizeElementType("InfrastructureService"));
        assertEquals("TechnologyInterface", ArchiMateSchemaValidator.normalizeElementType("InfrastructureInterface"));
        assertEquals("Path", ArchiMateSchemaValidator.normalizeElementType("CommunicationPath"));
        assertEquals("Node", ArchiMateSchemaValidator.normalizeElementType("TechnologyNode"));
    }

    @Test
    public void britishSpelling_relationships() {
        assertEquals("RealizationRelationship",
                ArchiMateSchemaValidator.normalizeRelationshipType("RealisationRelationship"));
        assertEquals("SpecializationRelationship",
                ArchiMateSchemaValidator.normalizeRelationshipType("SpecialisationRelationship"));
        assertEquals("AssociationRelationship",
                ArchiMateSchemaValidator.normalizeRelationshipType("RelatedTo"));
    }

    @Test
    public void officialNames_caseInsensitive() {
        assertEquals("BusinessActor", ArchiMateSchemaValidator.normalizeElementType("businessactor"));
        assertEquals("ServingRelationship",
                ArchiMateSchemaValidator.normalizeRelationshipType("servingrelationship"));
    }

    @Test
    public void systemPromptFile_forbidsInventedInteractionRelationship() throws Exception {
        Path file = Paths.get("..", "com.archimatetool.archigpt", "system-prompt.txt");
        if (!Files.isRegularFile(file)) {
            file = Paths.get("com.archimatetool.archigpt", "system-prompt.txt");
        }
        org.junit.Assume.assumeTrue("system-prompt.txt not found from test cwd", Files.isRegularFile(file));
        String p = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(p.contains("InteractionRelationship"));
        assertTrue(p.contains("BusinessInteraction"));
        assertTrue(p.contains("AssociationRelationship"));
    }
}
