package com.archimatetool.archigpt;

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
}
