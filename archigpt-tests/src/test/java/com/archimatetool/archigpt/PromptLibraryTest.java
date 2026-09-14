package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * Bundled validation and pattern prompts load from {@code skills/} and keep ANALYSIS vs CHANGES distinct.
 */
@SuppressWarnings("nls")
public class PromptLibraryTest {

    @Test
    public void catalogLoadsExpectedGroupsAndUniqueIds() {
        List<PromptLibrary.Entry> all = PromptLibrary.all();
        assertEquals("catalog.txt entries", 26, all.size());
        Set<String> ids = new HashSet<String>();
        int validate = 0, pattern = 0, analyse = 0;
        for (PromptLibrary.Entry e : all) {
            assertNotNull(e.id);
            assertTrue("duplicate id " + e.id, ids.add(e.id));
            assertNotNull(e.title);
            assertFalse(e.title.trim().isEmpty());
            assertFalse(e.prompt.trim().isEmpty());
            assertFalse(e.skillBody.trim().isEmpty());
            assertTrue(e.comboLabel().startsWith(e.group.label() + ": "));
            if (e.group == PromptLibrary.Group.VALIDATE) {
                validate++;
            } else if (e.group == PromptLibrary.Group.PATTERN) {
                pattern++;
            } else {
                analyse++;
            }
        }
        assertEquals(9, validate);
        assertEquals(9, pattern);
        assertEquals(8, analyse);
    }

    @Test
    public void validationToolsAreReportOnlyAndClassifiedAsAnalysis() {
        for (PromptLibrary.Entry e : PromptLibrary.all()) {
            if (e.group != PromptLibrary.Group.VALIDATE) {
                continue;
            }
            assertTrue(e.id, e.analysisOnly);
            assertTrue(e.id + " prompt should stay ANALYSIS: " + e.prompt,
                    AnalysisPromptIntent.likelyAnalysisOnly(e.prompt));
            assertTrue(e.id, e.skillBody.contains("ANALYSIS"));
            assertFalse(e.id, e.skillBody.toLowerCase().contains("respond only with changes json"));
        }
    }

    @Test
    public void patternSuggestIsAnalysis_instantiateToolsAreChanges() {
        PromptLibrary.Entry suggest = PromptLibrary.findById("pattern-suggest");
        assertNotNull(suggest);
        assertTrue(suggest.analysisOnly);
        assertTrue(AnalysisPromptIntent.likelyAnalysisOnly(suggest.prompt));

        String[] instantiateIds = {
                "pattern-service-sandwich", "pattern-application-contract", "pattern-process-collaboration",
                "pattern-capability-map", "pattern-event-driven", "pattern-integration-slice",
                "pattern-motivation-chain", "pattern-migration-slice"
        };
        for (int i = 0; i < instantiateIds.length; i++) {
            PromptLibrary.Entry e = PromptLibrary.findById(instantiateIds[i]);
            assertNotNull(instantiateIds[i], e);
            assertFalse(instantiateIds[i], e.analysisOnly);
            assertFalse(instantiateIds[i] + " should route to CHANGES: " + e.prompt,
                    AnalysisPromptIntent.likelyAnalysisOnly(e.prompt));
            assertTrue(instantiateIds[i], e.skillBody.contains("CHANGES JSON"));
        }
    }

    @Test
    public void parseMarkdown_readsFrontMatter() {
        String md = "---\n"
                + "id: demo-tool\n"
                + "group: validate\n"
                + "title: Demo\n"
                + "prompt: Review this model.\n"
                + "mode: analysis\n"
                + "---\n\n"
                + "Body text.\n";
        PromptLibrary.Entry e = PromptLibrary.parseMarkdown("validate/demo.md", md);
        assertNotNull(e);
        assertEquals("demo-tool", e.id);
        assertEquals(PromptLibrary.Group.VALIDATE, e.group);
        assertEquals("Demo", e.title);
        assertEquals("Review this model.", e.prompt);
        assertTrue(e.analysisOnly);
        assertEquals("Body text.", e.skillBody);
        assertEquals("Validate: Demo", e.comboLabel());
    }

    @Test
    public void userMessageInjectsTruncatedSkill() {
        PromptLibrary.Entry e = PromptLibrary.findById("validate-discrepancies");
        assertNotNull(e);
        String msg = UserMessageBuilder.buildUserMessage("sel", "<model/>", e.prompt, e.skillBody);
        assertTrue(msg.contains("User request: " + e.prompt));
        assertTrue(msg.contains(PromptLibrary.TOOL_INSTRUCTIONS_START));
        assertTrue(msg.contains("Cross-view relationship mismatch"));
        assertTrue(msg.contains(PromptLibrary.TOOL_INSTRUCTIONS_END));
        assertTrue(UserMessageBuilder.estimateNonXmlOverheadChars("sel", e.prompt, e.skillBody)
                > UserMessageBuilder.estimateNonXmlOverheadChars("sel", e.prompt, null));
    }

    @Test
    public void truncateSkill_capsLongBody() {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < PromptLibrary.MAX_SKILL_CHARS + 50) {
            sb.append("x");
        }
        String t = PromptLibrary.truncateSkill(sb.toString());
        assertTrue(t.contains("[Tool instructions truncated.]"));
        assertTrue(t.length() < sb.length());
    }
}
