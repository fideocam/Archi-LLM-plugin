/**
 * Parsed result from the LLM: ArchiMate 3.2 elements and relationships for validation and import.
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for parsed ArchiMate JSON from the LLM (elements and relationships).
 */
public final class ArchiMateLLMResult {

    private final List<ElementSpec> elements = new ArrayList<>();
    private final List<RelationshipSpec> relationships = new ArrayList<>();
    private String error;

    public List<ElementSpec> getElements() {
        return elements;
    }

    public List<RelationshipSpec> getRelationships() {
        return relationships;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
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
