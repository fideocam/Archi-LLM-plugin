package com.archimatetool.archigpt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Documentation and other in-place edits must be classified as CHANGES, not analysis.
 */
public class AnalysisPromptIntentTest {

    @Test
    public void addDocumentation_isChanges() {
        assertFalse(AnalysisPromptIntent.likelyAnalysisOnly("Add documentation into the documentation fields."));
    }

    @Test
    public void fillDocumentationFields_isChanges() {
        assertFalse(AnalysisPromptIntent.likelyAnalysisOnly("Fill in the documentation fields for every process."));
    }

    @Test
    public void updateDocumentation_isChanges() {
        assertFalse(AnalysisPromptIntent.likelyAnalysisOnly("Update the documentation of Architecture Design."));
    }

    @Test
    public void writeNotes_isChanges() {
        assertFalse(AnalysisPromptIntent.likelyAnalysisOnly("Write notes for the selected application components."));
    }

    @Test
    public void missingDocumentationReview_isAnalysis() {
        assertTrue(AnalysisPromptIntent.likelyAnalysisOnly(
                "List potential single points of failure or missing documentation in this landscape."));
    }

    @Test
    public void describeDocumentation_isAnalysis() {
        assertTrue(AnalysisPromptIntent.likelyAnalysisOnly("What does the documentation of this process say?"));
    }

    @Test
    public void rename_isChanges() {
        assertFalse(AnalysisPromptIntent.likelyAnalysisOnly("Rename Customer to Client"));
    }

    @Test
    public void describeModel_isAnalysis() {
        assertTrue(AnalysisPromptIntent.likelyAnalysisOnly("Describe this architecture."));
    }
}
