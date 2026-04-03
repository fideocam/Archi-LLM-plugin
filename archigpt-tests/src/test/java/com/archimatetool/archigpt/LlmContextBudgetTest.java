package com.archimatetool.archigpt;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LlmContextBudgetTest {

    @Test
    public void recommendedXmlLeavesHeadroom() {
        int n = LlmContextBudget.recommendedMaxXmlChars(8192, 8000, 500, 2048);
        assertTrue(n < 8192 * 4);
        assertTrue(n >= 6000);
    }
}
