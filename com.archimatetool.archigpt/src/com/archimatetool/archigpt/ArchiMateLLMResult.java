/**
 * Parsed result from the LLM: ArchiMate 3.2 elements and relationships for validation and import.
 * May optionally include a diagram spec to create a whole new view with nodes and connections.
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for parsed ArchiMate JSON from the LLM (elements, relationships, and optional diagram).
 */
public final class ArchiMateLLMResult {

    private final List<ElementSpec> elements = new ArrayList<>();
    private final List<RelationshipSpec> relationships = new ArrayList<>();
    private final List<String> removeElementIds = new ArrayList<>();
    private final List<String> removeRelationshipIds = new ArrayList<>();
    private DiagramSpec diagram;
    private String error;

    public List<ElementSpec> getElements() {
        return elements;
    }

    public List<RelationshipSpec> getRelationships() {
        return relationships;
    }

    public List<String> getRemoveElementIds() {
        return removeElementIds;
    }

    public List<String> getRemoveRelationshipIds() {
        return removeRelationshipIds;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public DiagramSpec getDiagram() {
        return diagram;
    }

    public void setDiagram(DiagramSpec diagram) {
        this.diagram = diagram;
    }

    public static final class DiagramSpec {
        private String name;
        private String viewpoint;
        private final List<DiagramNodeSpec> nodes = new ArrayList<>();
        private final List<DiagramConnectionSpec> connections = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getViewpoint() { return viewpoint; }
        public void setViewpoint(String viewpoint) { this.viewpoint = viewpoint; }
        public List<DiagramNodeSpec> getNodes() { return nodes; }
        public List<DiagramConnectionSpec> getConnections() { return connections; }
    }

    public static final class DiagramNodeSpec {
        private String elementId;
        private int x = 0, y = 0, width = 120, height = 55;
        public String getElementId() { return elementId; }
        public void setElementId(String elementId) { this.elementId = elementId; }
        public int getX() { return x; }
        public void setX(int x) { this.x = x; }
        public int getY() { return y; }
        public void setY(int y) { this.y = y; }
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
    }

    public static final class DiagramConnectionSpec {
        private String sourceElementId;
        private String targetElementId;
        private String relationshipId;
        public String getSourceElementId() { return sourceElementId; }
        public void setSourceElementId(String sourceElementId) { this.sourceElementId = sourceElementId; }
        public String getTargetElementId() { return targetElementId; }
        public void setTargetElementId(String targetElementId) { this.targetElementId = targetElementId; }
        public String getRelationshipId() { return relationshipId; }
        public void setRelationshipId(String relationshipId) { this.relationshipId = relationshipId; }
    }

    public static final class ElementSpec {
        private String type;
        private String name;
        private String id;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static final class RelationshipSpec {
        private String type;
        private String source;
        private String target;
        private String name;
        private String id;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
