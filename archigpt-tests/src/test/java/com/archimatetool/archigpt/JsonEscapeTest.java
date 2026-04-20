package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JsonEscapeTest {

    @Test
    public void escape_empty() {
        assertEquals("", JsonEscape.escapeString(null));
        assertEquals("", JsonEscape.escapeString(""));
    }

    @Test
    public void escape_quotesAndNewlines() {
        assertEquals("a\\\\b\\\"c\\nd", JsonEscape.escapeString("a\\b\"c\nd"));
    }

    @Test
    public void escape_controlChar() {
        assertEquals("x\\u0001y", JsonEscape.escapeString("x\u0001y"));
    }
}
