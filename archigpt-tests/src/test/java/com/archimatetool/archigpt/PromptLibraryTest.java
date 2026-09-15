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
 * Bundled tidy, view, pattern, and EA prompts load from {@code skills/} and keep ANALYSIS vs CHANGES distinct.
 */
@SuppressWarnings("nls")
public class PromptLibraryTest {

    @Test
    public void catalogLoadsExpectedGroupsAndUniqueIds() {
        List<PromptLibrary.Entry> all = PromptLibrary.all();
        assertEquals("catalog.txt entries", 26, all.size());
        Set<String> ids = new HashSet<String>();
        int tidy = 0, view = 0, pattern = 0, ea = 0;
        for (PromptLibrary.Entry e : all) {
            assertNotNull(e.id);
            assertTrue("duplicate id " + e.id, ids.add(e.id));
            assertNotNull(e.title);
            assertFalse(e.title.trim().isEmpty());
            assertFalse(e.prompt.trim().isEmpty());
            assertFalse(e.skillBody.trim().isEmpty());
            assertTrue(e.comboLabel().startsWith(e.group.label() + ": "));
            if (e.group == PromptLibrary.Group.TIDY) {
                tidy++;
            } else if (e.group == PromptLibrary.Group.VIEW) {
                view++;
            } else if (e.group == PromptLibrary.Group.PATTERN) {
                pattern++;
            } else {
                assertEquals(e.id, PromptLibrary.Group.EA, e.group);
                ea++;
            }
        }
        assertEquals(5, tidy);
        assertEquals(3, view);
        assertEquals(9, pattern);
        assertEquals(9, ea);
    }

    @Test
    public void reportOnlyGroupsStayAnalysis() {
        for (PromptLibrary.Entry e : PromptLibrary.all()) {
            if (e.group == PromptLibrary.Group.PATTERN && !e.analysisOnly) {
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
    public void tidyAndViewSkillsStayOnCatalogHygiene() {
        PromptLibrary.Entry dups = PromptLibrary.findById("tidy-duplicate-names");
        assertNotNull(dups);
        assertTrue(dups.skillBody.contains("duplicates"));

        PromptLibrary.Entry rels = PromptLibrary.findById("tidy-relationships-not-on-views");
        assertNotNull(rels);
        assertTrue(rels.skillBody.contains("relationshipRef"));

        PromptLibrary.Entry service = PromptLibrary.findById("view-business-service");
        assertNotNull(service);
        assertTrue(service.skillBody.toLowerCase().contains("peer"));
        assertTrue(service.skillBody.toLowerCase().contains("convention"));
    }

    @Test
    public void eaToolsFindEchoGapsNotMetamodelChecklists() {
        String[] eaIds = {
                "ea-cross-view-gaps", "ea-uneven-service-stack", "ea-uneven-application-support",
                "ea-uneven-realization", "ea-uneven-assignment", "ea-uneven-technology",
                "ea-uneven-data", "ea-uneven-motivation", "ea-uneven-alternatives"
        };
        for (int i = 0; i < eaIds.length; i++) {
            PromptLibrary.Entry e = PromptLibrary.findById(eaIds[i]);
            assertNotNull(eaIds[i], e);
            assertEquals(eaIds[i], PromptLibrary.Group.EA, e.group);
            assertTrue(eaIds[i], e.analysisOnly);
            String body = e.skillBody.toLowerCase();
            assertFalse(eaIds[i], body.contains("highest-value next"));
            assertFalse(eaIds[i], body.contains("assess the enterprise-architecture maturity"));
            assertTrue(eaIds[i], body.contains("do not"));
            assertTrue(eaIds[i] + " should stop when the pattern is absent",
                    body.contains("stop") || body.contains("peer") || body.contains("another view"));
        }
        PromptLibrary.Entry realization = PromptLibrary.findById("ea-uneven-realization");
        assertTrue(realization.skillBody.contains("Do not list every Capability without Realization"));
    }

    @Test
    public void parseMarkdown_readsFrontMatter() {
        String md = "---\n"
                + "id: demo-tool\n"
                + "group: tidy\n"
                + "title: Demo\n"
                + "prompt: Review this model.\n"
                + "mode: analysis\n"
                + "---\n\n"
                + "Body text.\n";
        PromptLibrary.Entry e = PromptLibrary.parseMarkdown("tidy/demo.md", md);
        assertNotNull(e);
        assertEquals("demo-tool", e.id);
        assertEquals(PromptLibrary.Group.TIDY, e.group);
        assertEquals("Demo", e.title);
        assertEquals("Review this model.", e.prompt);
        assertTrue(e.analysisOnly);
        assertEquals("Body text.", e.skillBody);
        assertEquals("Tidy: Demo", e.comboLabel());
    }

    @Test
    public void parseMarkdown_mapsLegacyValidateAndAnalyseGroups() {
        PromptLibrary.Entry v = PromptLibrary.parseMarkdown("x.md", "---\n"
                + "id: old-v\n"
                + "group: validate\n"
                + "title: Old\n"
                + "prompt: Check names.\n"
                + "mode: analysis\n"
                + "---\n\nBody.\n");
        assertEquals(PromptLibrary.Group.TIDY, v.group);

        PromptLibrary.Entry a = PromptLibrary.parseMarkdown("x.md", "---\n"
                + "id: old-a\n"
                + "group: analyse\n"
                + "title: Old\n"
                + "prompt: Which processes use this?\n"
                + "mode: analysis\n"
                + "---\n\nBody.\n");
        assertEquals(PromptLibrary.Group.EA, a.group);
    }

    @Test
    public void userMessageInjectsTruncatedSkill() {
        PromptLibrary.Entry e = PromptLibrary.findById("tidy-drawn-vs-catalog");
        assertNotNull(e);
        String msg = UserMessageBuilder.buildUserMessage("sel", "<model/>", e.prompt, e.skillBody);
        assertTrue(msg.contains("User request: " + e.prompt));
        assertTrue(msg.contains(PromptLibrary.TOOL_INSTRUCTIONS_START));
        assertTrue(msg.contains("Orphan drawings"));
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
