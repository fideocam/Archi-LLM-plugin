package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/**
 * {@link LlmContextConfig} must parse JVM properties without breaking defaults.
 */
public class LlmContextConfigTest {

    @After
    public void clearProps() {
        System.clearProperty(LlmContextConfig.PROP_MAX_XML_CHARS);
        System.clearProperty(LlmContextConfig.PROP_OLLAMA_NUM_CTX);
        System.clearProperty(LlmContextConfig.PROP_OLLAMA_READ_TIMEOUT_MS);
        System.clearProperty(LlmContextConfig.PROP_OLLAMA_REPORTED_CTX_CAP);
    }

    @Test
    public void defaults_whenNoProperties() {
        assertEquals(LlmContextConfig.DEFAULT_MAX_XML_CHARS, LlmContextConfig.maxXmlChars());
        assertEquals(LlmContextConfig.DEFAULT_NUM_CTX, LlmContextConfig.ollamaNumCtx());
        assertEquals(LlmContextConfig.DEFAULT_OLLAMA_READ_TIMEOUT_MS, LlmContextConfig.resolveOllamaReadTimeoutMs());
        assertEquals(LlmContextConfig.DEFAULT_OLLAMA_REPORTED_CTX_CAP,
                LlmContextConfig.resolveOllamaNumCtx(131_072));
        assertEquals(LlmContextConfig.DEFAULT_OLLAMA_REPORTED_CTX_CAP,
                LlmContextConfig.resolveOllamaNumCtx(0));
    }

    @Test
    public void resolveOllamaNumCtx_smallReportedUnchanged() {
        assertEquals(8192, LlmContextConfig.resolveOllamaNumCtx(8192));
    }

    @Test
    public void resolveOllamaNumCtx_explicitPropertyIgnoresCap() {
        System.setProperty(LlmContextConfig.PROP_OLLAMA_NUM_CTX, "131072");
        assertEquals(131_072, LlmContextConfig.resolveOllamaNumCtx(4096));
    }

    @Test
    public void ollamaReportedCtxCap_readsProperty() {
        System.setProperty(LlmContextConfig.PROP_OLLAMA_REPORTED_CTX_CAP, "65536");
        assertEquals(65_536, LlmContextConfig.resolveOllamaNumCtx(131_072));
    }

    @Test
    public void ollamaReadTimeout_readsProperty() {
        System.setProperty(LlmContextConfig.PROP_OLLAMA_READ_TIMEOUT_MS, "240000");
        assertEquals(240_000, LlmContextConfig.resolveOllamaReadTimeoutMs());
    }

    @Test
    public void ollamaReadTimeout_zeroMeansUnlimited() {
        System.setProperty(LlmContextConfig.PROP_OLLAMA_READ_TIMEOUT_MS, "0");
        assertEquals(0, LlmContextConfig.resolveOllamaReadTimeoutMs());
    }

    @Test
    public void ollamaReadTimeout_clampedToTwoHours() {
        System.setProperty(LlmContextConfig.PROP_OLLAMA_READ_TIMEOUT_MS, "999999999");
        assertEquals(7_200_000, LlmContextConfig.resolveOllamaReadTimeoutMs());
    }

    @Test
    public void maxXmlChars_readsProperty() {
        System.setProperty(LlmContextConfig.PROP_MAX_XML_CHARS, "50000");
        assertEquals(50_000, LlmContextConfig.maxXmlChars());
    }

    @Test
    public void ollamaNumCtx_readsProperty() {
        System.setProperty(LlmContextConfig.PROP_OLLAMA_NUM_CTX, "131072");
        assertEquals(131_072, LlmContextConfig.ollamaNumCtx());
    }

    @Test
    public void ollamaNumCtx_clampedTo256k() {
        System.setProperty(LlmContextConfig.PROP_OLLAMA_NUM_CTX, "999999");
        assertEquals(LlmContextConfig.OLLAMA_NUM_CTX_MAX, LlmContextConfig.ollamaNumCtx());
    }

    @Test
    public void contextPerformanceWarning_emptyForSmallPayload() {
        assertEquals("", LlmContextConfig.contextPerformanceWarning(1000, 1000, 2000, 5000, 65_536));
    }

    @Test
    public void contextPerformanceWarning_whenTruncated() {
        String w = LlmContextConfig.contextPerformanceWarning(100_000, 36_000, 40_000, 20_000, 65_536);
        assertTrue(w.contains("WARNING"));
        assertTrue(w.contains("36"));
        assertTrue(w.contains("100"));
    }

    @Test
    public void contextPerformanceWarning_whenLargeFullXml() {
        String w = LlmContextConfig.contextPerformanceWarning(50_000, 50_000, 52_000, 20_000, 65_536);
        assertTrue(w.contains("WARNING"));
        assertTrue(w.contains("50000") || w.contains("50"));
    }
}
