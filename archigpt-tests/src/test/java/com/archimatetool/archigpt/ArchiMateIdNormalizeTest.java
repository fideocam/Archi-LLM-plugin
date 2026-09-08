package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ArchiMateIdNormalizeTest {

    @Test
    public void normalizeLookupId_acceptsHyphenatedUuid() {
        assertEquals("id-a1b2c3d4e5f67890abcdef1234567890",
                ArchiMateLLMImporter.normalizeLookupId("a1b2c3d4-e5f6-7890-abcd-ef1234567890"));
    }

    @Test
    public void normalizeLookupId_leavesArchiId() {
        String id = "id-a1b2c3d4e5f67890abcdef1234567890";
        assertEquals(id, ArchiMateLLMImporter.normalizeLookupId(id));
    }

    @Test
    public void normalizeLookupId_ignoresNames() {
        assertNull(ArchiMateLLMImporter.normalizeLookupId("Customer"));
        assertNull(ArchiMateLLMImporter.normalizeLookupId(""));
        assertNull(ArchiMateLLMImporter.normalizeLookupId(null));
    }
}
