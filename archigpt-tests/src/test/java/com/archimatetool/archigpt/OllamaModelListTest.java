package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * Model dropdown is built only from {@code /api/tags}; {@link OllamaClient#DEFAULT_MODEL} is never invented.
 */
public class OllamaModelListTest {

    @Test
    public void fromServerNames_sortsAndDeduplicatesWithoutInventingDefault() {
        List<String> items = OllamaModelList.fromServerNames(Arrays.asList("mistral:latest", "  phi3  ", "mistral:latest", ""));
        assertEquals(Arrays.asList("mistral:latest", "phi3"), items);
        assertFalse(items.contains(OllamaClient.DEFAULT_MODEL));
    }

    @Test
    public void fromServerNames_emptyWhenServerListsNothing() {
        assertTrue(OllamaModelList.fromServerNames(null).isEmpty());
        assertTrue(OllamaModelList.fromServerNames(Collections.<String>emptyList()).isEmpty());
        assertTrue(OllamaModelList.fromServerNames(Arrays.asList("  ", null)).isEmpty());
    }

    @Test
    public void indexOfModel_prefersExactThenLatestSuffix() {
        List<String> items = Arrays.asList("llama3.2:latest", "mistral:latest");
        assertEquals(0, OllamaModelList.indexOfModel(items, "llama3.2"));
        assertEquals(0, OllamaModelList.indexOfModel(items, "llama3.2:latest"));
        assertEquals(1, OllamaModelList.indexOfModel(items, "mistral:latest"));
        assertEquals(-1, OllamaModelList.indexOfModel(items, "phi3"));
        assertEquals(-1, OllamaModelList.indexOfModel(null, "llama3.2"));
        assertEquals(-1, OllamaModelList.indexOfModel(items, ""));
    }

    @Test
    public void selectionAfterRefresh_keepsPreviousWhenServerListsIt() {
        List<String> items = OllamaModelList.fromServerNames(Arrays.asList("phi3:latest", "mistral:latest"));
        assertEquals("mistral:latest", OllamaModelList.selectionAfterRefresh(items, "mistral:latest"));
    }

    @Test
    public void selectionAfterRefresh_matchesBareNameToLatestTag() {
        List<String> items = OllamaModelList.fromServerNames(Arrays.asList("llama3.2:latest", "mistral"));
        assertEquals("llama3.2:latest", OllamaModelList.selectionAfterRefresh(items, "llama3.2"));
    }

    @Test
    public void selectionAfterRefresh_fallsBackToFirstServerModelNotDefault() {
        List<String> items = OllamaModelList.fromServerNames(Arrays.asList("mistral:latest", "phi3"));
        assertEquals("mistral:latest", OllamaModelList.selectionAfterRefresh(items, OllamaClient.DEFAULT_MODEL));
        assertFalse(OllamaClient.DEFAULT_MODEL.equals(OllamaModelList.selectionAfterRefresh(items, "gone")));
    }

    @Test
    public void selectionAfterRefresh_emptyWhenServerHasNoModels() {
        assertEquals("", OllamaModelList.selectionAfterRefresh(Collections.<String>emptyList(), "llama3.2"));
        assertEquals("", OllamaModelList.selectionAfterRefresh(null, OllamaClient.DEFAULT_MODEL));
    }
}
