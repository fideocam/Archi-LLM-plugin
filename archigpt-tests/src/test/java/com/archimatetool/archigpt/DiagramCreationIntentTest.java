package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiagramCreationIntentTest {

    @Test
    public void addToThisView_isNotBrandNew() {
        assertFalse(DiagramCreationIntent.userAskedForBrandNewView("Add a BusinessActor to this view"));
        assertFalse(DiagramCreationIntent.userAskedForBrandNewView("put a process on the current diagram"));
    }

    @Test
    public void addElementOnly_isNotBrandNew() {
        assertFalse(DiagramCreationIntent.userAskedForBrandNewView("Add a BusinessActor called Customer"));
    }

    @Test
    public void explicitNewDiagram_isBrandNew() {
        assertTrue(DiagramCreationIntent.userAskedForBrandNewView("Create a new diagram for HR"));
        assertTrue(DiagramCreationIntent.userAskedForBrandNewView("add a new view"));
        assertTrue(DiagramCreationIntent.userAskedForBrandNewView("add a diagram that describes recruitment"));
    }

    @Test
    public void retarget_withoutOpenViewLeavesDiagram() {
        ArchiMateLLMResult result = new ArchiMateLLMResult();
        ArchiMateLLMResult.DiagramSpec spec = new ArchiMateLLMResult.DiagramSpec();
        spec.setName("Invented View");
        ArchiMateLLMResult.DiagramNodeSpec node = new ArchiMateLLMResult.DiagramNodeSpec();
        node.setElementId("id-a1b2c3d4e5f67890abcdef1234567890");
        spec.getNodes().add(node);
        result.setDiagram(spec);
        assertFalse(DiagramCreationIntent.retargetDiagramToOpenView(result, null, "add data objects to this view"));
        assertEquals("Invented View", result.getDiagram().getName());
    }

    @Test
    public void retarget_newViewPromptLeavesDiagram() {
        ArchiMateLLMResult result = new ArchiMateLLMResult();
        ArchiMateLLMResult.DiagramSpec spec = new ArchiMateLLMResult.DiagramSpec();
        spec.setName("Invented View");
        ArchiMateLLMResult.DiagramNodeSpec node = new ArchiMateLLMResult.DiagramNodeSpec();
        node.setElementId("id-a1b2c3d4e5f67890abcdef1234567890");
        spec.getNodes().add(node);
        result.setDiagram(spec);
        assertFalse(DiagramCreationIntent.retargetDiagramToOpenView(result, null, "create a new view"));
        assertEquals("Invented View", result.getDiagram().getName());
    }
}
