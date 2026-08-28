package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class MutationPolicyTest {

    private static final String ID = "id-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    public void isDestructive_onlyModelDeletes() {
        ArchiMateLLMResult add = new ArchiMateLLMResult();
        ArchiMateLLMResult.ElementSpec e = new ArchiMateLLMResult.ElementSpec();
        e.setType("BusinessActor");
        e.setName("A");
        e.setId(ID);
        add.getElements().add(e);
        assertFalse(MutationPolicy.isDestructive(add));

        ArchiMateLLMResult fromDiagram = new ArchiMateLLMResult();
        fromDiagram.getRemoveElementFromDiagramIds().add(ID);
        assertFalse(MutationPolicy.isDestructive(fromDiagram));

        ArchiMateLLMResult del = new ArchiMateLLMResult();
        del.getRemoveElementIds().add(ID);
        assertTrue(MutationPolicy.isDestructive(del));

        ArchiMateLLMResult diagrams = new ArchiMateLLMResult();
        diagrams.getRemoveDiagramNames().add("Idea");
        assertTrue(MutationPolicy.isDestructive(diagrams));
    }

    @Test
    public void checkLimits_flagsOversizedBatches() {
        ArchiMateLLMResult result = new ArchiMateLLMResult();
        for (int i = 0; i < MutationPolicy.MAX_ELEMENTS + 1; i++) {
            ArchiMateLLMResult.ElementSpec el = new ArchiMateLLMResult.ElementSpec();
            el.setType("BusinessActor");
            el.setName("N" + i);
            el.setId(ID);
            result.getElements().add(el);
        }
        List<String> errors = MutationPolicy.checkLimits(result);
        assertTrue(errors.toString(), errors.stream().anyMatch(s -> s.contains("Too many elements")));
    }

    @Test
    public void checkLimits_okForSmallPayload() {
        ArchiMateLLMResult result = new ArchiMateLLMResult();
        ArchiMateLLMResult.ElementSpec el = new ArchiMateLLMResult.ElementSpec();
        el.setType("BusinessActor");
        el.setName("Customer");
        el.setId(ID);
        result.getElements().add(el);
        assertTrue(MutationPolicy.checkLimits(result).isEmpty());
    }

    @Test
    public void destructiveSummary_includesCounts() {
        ArchiMateLLMResult result = new ArchiMateLLMResult();
        result.getRemoveElementIds().add("a");
        result.getRemoveDiagramNames().add("Idea");
        String summary = MutationPolicy.destructiveSummary(result);
        assertTrue(summary.contains("1 element"));
        assertTrue(summary.contains("1 diagram"));
    }

    @Test
    public void checkLimits_flagsOversizedNames() {
        ArchiMateLLMResult result = new ArchiMateLLMResult();
        ArchiMateLLMResult.ElementSpec el = new ArchiMateLLMResult.ElementSpec();
        el.setType("BusinessActor");
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < MutationPolicy.MAX_NAME_CHARS + 1; i++) {
            name.append('N');
        }
        el.setName(name.toString());
        el.setId(ID);
        result.getElements().add(el);
        List<String> errors = MutationPolicy.checkLimits(result);
        assertTrue(errors.toString(), errors.stream().anyMatch(s -> s.contains("name is too long")));
    }

    @Test
    public void checkLimits_flagsOversizedIds() {
        ArchiMateLLMResult result = new ArchiMateLLMResult();
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < MutationPolicy.MAX_ID_CHARS + 1; i++) {
            id.append('a');
        }
        result.getRemoveElementIds().add(id.toString());
        List<String> errors = MutationPolicy.checkLimits(result);
        assertTrue(errors.toString(), errors.stream().anyMatch(s -> s.contains("id is too long")));
    }

    @Test
    public void maxReplyChars_constantMatchesEaGpt() {
        assertEquals(200_000, MutationPolicy.MAX_REPLY_CHARS);
    }
}
