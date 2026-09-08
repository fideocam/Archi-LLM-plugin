package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LlmContextBudgetTest {

    @Test
    public void recommendedXmlLeavesHeadroom() {
        int n = LlmContextBudget.recommendedMaxXmlChars(8192, 8000, 500, 2048);
        assertTrue(n < 8192 * 4);
        assertTrue(n >= 6000);
    }

    @Test
    public void largeNumCtxIsClampedToXmlCeiling() {
        int n = LlmContextBudget.recommendedMaxXmlChars(LlmContextConfig.OLLAMA_NUM_CTX_MAX, 8000, 500, 8192);
        assertEquals(LlmContextConfig.getMaxXmlCharsCeiling(), n);
    }
}
