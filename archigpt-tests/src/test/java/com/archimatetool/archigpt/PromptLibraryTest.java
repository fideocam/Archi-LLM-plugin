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
 * Bundled tidy, view, pattern, technology, and EA prompts load from {@code skills/} and keep ANALYSIS vs CHANGES distinct.
 */
@SuppressWarnings("nls")
public class PromptLibraryTest {

    @Test
    public void catalogLoadsExpectedGroupsAndUniqueIds() {
        List<PromptLibrary.Entry> all = PromptLibrary.all();
        assertEquals("catalog.txt entries", 33, all.size());
        Set<String> ids = new HashSet<String>();
        int tidy = 0, view = 0, pattern = 0, ea = 0, tech = 0;
        for (PromptLibrary.Entry e : all) {
            assertNotNull(e.id);
            assertTrue("duplicate id " + e.id, ids.add(e.id));
            assertNotNull(e.title);
            assertFalse(e.title.trim().isEmpty());
            assertFalse(e.prompt.trim().isEmpty());
            assertFalse(e.skillBody.trim().isEmpty());
            assertTrue(e.comboLabel().startsWith(e.group.label() + ": "));
            assertEquals(e.title, e.titleInCategory());
            assertFalse(e.roles.isEmpty());
            assertTrue(e.id, e.forRole(e.roles.get(0)));
            if (e.group == PromptLibrary.Group.TIDY) {
                tidy++;
            } else if (e.group == PromptLibrary.Group.VIEW) {
                view++;
            } else if (e.group == PromptLibrary.Group.PATTERN) {
                pattern++;
            } else if (e.group == PromptLibrary.Group.TECHNOLOGY) {
                tech++;
            } else {
                assertEquals(e.id, PromptLibrary.Group.EA, e.group);
                ea++;
            }
        }
        assertEquals(5, tidy);
        assertEquals(3, view);
        assertEquals(9, pattern);
        assertEquals(7, tech);
        assertEquals(9, ea);
        List<PromptLibrary.Entry> solution = PromptLibrary.entriesForRole(PromptLibrary.Role.SOLUTION_ARCHITECT);
        List<PromptLibrary.Entry> technology = PromptLibrary.entriesForRole(PromptLibrary.Role.TECHNOLOGY_ARCHITECT);
        List<PromptLibrary.Entry> eaRole = PromptLibrary.entriesForRole(PromptLibrary.Role.EA);
        List<PromptLibrary.Entry> hygiene = PromptLibrary.entriesForRole(PromptLibrary.Role.HYGIENE);
        assertEquals(9, solution.size());
        assertEquals(7, technology.size());
        assertEquals(12, eaRole.size());
        assertEquals(5, hygiene.size());
        assertEquals(solution, PromptLibrary.solutionArchitectEntries());
        assertEquals(PromptLibrary.entriesIn(PromptLibrary.Group.EA).size() + 3, eaRole.size());
        Set<String> roleIds = new HashSet<String>();
        for (PromptLibrary.Entry e : solution) {
            assertTrue(e.id, e.forRole(PromptLibrary.Role.SOLUTION_ARCHITECT));
            assertTrue(roleIds.add(e.id));
        }
        for (PromptLibrary.Entry e : technology) {
            assertTrue(e.id, e.forRole(PromptLibrary.Role.TECHNOLOGY_ARCHITECT));
            assertTrue(roleIds.add(e.id));
        }
        for (PromptLibrary.Entry e : eaRole) {
            assertTrue(e.id, e.forRole(PromptLibrary.Role.EA));
            assertTrue(e.id, roleIds.add(e.id));
        }
        for (PromptLibrary.Entry e : hygiene) {
            assertTrue(e.id, e.forRole(PromptLibrary.Role.HYGIENE));
            assertTrue(e.id, roleIds.add(e.id));
        }
        assertEquals(all.size(), roleIds.size());
        assertEquals(4, PromptLibrary.Role.values().length);
        assertEquals("view-this-diagram", solution.get(0).id);
        assertEquals("tech-redundancy", technology.get(0).id);
        assertEquals("Gaps on this service diagram", PromptLibrary.findById("view-business-service").title);
        assertTrue(PromptLibrary.findById("pattern-capability-map").forRole(PromptLibrary.Role.EA));
        assertFalse(PromptLibrary.findById("pattern-capability-map").forRole(PromptLibrary.Role.SOLUTION_ARCHITECT));
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
        assertTrue(rels.skillBody.contains("PLUGIN FINDINGS"));

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
    public void technologyArchitectSkillsStayEchoGapsNotChecklists() {
        String[] techIds = {
                "tech-redundancy", "tech-security-zones", "tech-continuity", "tech-serving-without-path",
                "tech-standard-runtime", "tech-shared-platform", "tech-hosting-stack"
        };
        for (int i = 0; i < techIds.length; i++) {
            PromptLibrary.Entry e = PromptLibrary.findById(techIds[i]);
            assertNotNull(techIds[i], e);
            assertEquals(techIds[i], PromptLibrary.Group.TECHNOLOGY, e.group);
            assertTrue(techIds[i], e.forRole(PromptLibrary.Role.TECHNOLOGY_ARCHITECT));
            assertTrue(techIds[i], e.analysisOnly);
            assertTrue(techIds[i], AnalysisPromptIntent.likelyAnalysisOnly(e.prompt));
            String body = e.skillBody.toLowerCase();
            assertTrue(techIds[i], body.contains("stop"));
            assertTrue(techIds[i], body.contains("do not"));
            assertFalse(techIds[i], body.contains("iso 27001"));
            assertFalse(techIds[i], body.contains("cis benchmark"));
            assertFalse(techIds[i], body.contains("zero-trust architecture"));
        }
        PromptLibrary.Entry security = PromptLibrary.findById("tech-security-zones");
        assertTrue(security.skillBody.contains("If no security control is modelled"));
        PromptLibrary.Entry redundancy = PromptLibrary.findById("tech-redundancy");
        assertTrue(redundancy.skillBody.contains("Do not score the file against an external high-availability checklist"));
        PromptLibrary.Entry runtime = PromptLibrary.findById("tech-standard-runtime");
        assertTrue(runtime.skillBody.contains("Do not recommend a vendor, version, cloud service"));
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
        assertTrue(e.forRole(PromptLibrary.Role.HYGIENE));
        assertFalse(e.forRole(PromptLibrary.Role.SOLUTION_ARCHITECT));
    }

    @Test
    public void parseMarkdown_rolesOverrideGroupDefault() {
        PromptLibrary.Entry e = PromptLibrary.parseMarkdown("pattern/cap.md", "---\n"
                + "id: pattern-cap\n"
                + "group: pattern\n"
                + "roles: ea\n"
                + "title: Add a capability map slice\n"
                + "prompt: Add a capability-map slice.\n"
                + "mode: changes\n"
                + "---\n\nBody.\n");
        assertEquals(PromptLibrary.Group.PATTERN, e.group);
        assertTrue(e.forRole(PromptLibrary.Role.EA));
        assertFalse(e.forRole(PromptLibrary.Role.SOLUTION_ARCHITECT));
        assertFalse(e.analysisOnly);
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
