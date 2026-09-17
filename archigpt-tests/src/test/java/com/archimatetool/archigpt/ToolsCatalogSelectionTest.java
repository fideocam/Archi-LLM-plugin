package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * Tools tab: Role then Task, independent of a freeform ArchiGPT-tab prompt.
 */
@SuppressWarnings("nls")
public class ToolsCatalogSelectionTest {

    @Test
    public void roleLabels_areSolutionThenTechnologyThenEnterpriseThenHygiene() {
        String[] labels = ToolsCatalogSelection.roleLabels();
        assertEquals(4, labels.length);
        assertEquals("Solution architect", labels[0]);
        assertEquals("Technology architect", labels[1]);
        assertEquals("Enterprise architect", labels[2]);
        assertEquals("Model hygiene", labels[3]);
        assertEquals(PromptLibrary.Role.SOLUTION_ARCHITECT, ToolsCatalogSelection.roleAt(0));
        assertEquals(PromptLibrary.Role.TECHNOLOGY_ARCHITECT, ToolsCatalogSelection.roleAt(1));
        assertEquals(PromptLibrary.Role.EA, ToolsCatalogSelection.roleAt(2));
        assertEquals(PromptLibrary.Role.HYGIENE, ToolsCatalogSelection.roleAt(3));
        assertEquals(PromptLibrary.Role.SOLUTION_ARCHITECT, ToolsCatalogSelection.roleAt(-1));
        assertEquals(PromptLibrary.Role.SOLUTION_ARCHITECT, ToolsCatalogSelection.roleAt(99));
    }

    @Test
    public void defaultRole_isSolutionArchitectWithNoTask() {
        ToolsCatalogSelection sel = new ToolsCatalogSelection();
        assertEquals(PromptLibrary.Role.SOLUTION_ARCHITECT, sel.role());
        assertEquals(0, sel.taskIndex());
        assertNull(sel.selectedTask());
        assertEquals("", sel.chosenPrompt());
        List<String> tasks = sel.taskLabels();
        assertEquals(PromptLibrary.NONE_TASK, tasks.get(0));
        assertEquals(1 + PromptLibrary.entriesForRole(PromptLibrary.Role.SOLUTION_ARCHITECT).size(), tasks.size());
    }

    @Test
    public void selectTask_fillsChosenPrompt_andInvalidIndexClears() {
        ToolsCatalogSelection sel = new ToolsCatalogSelection();
        List<PromptLibrary.Entry> solution = PromptLibrary.entriesForRole(PromptLibrary.Role.SOLUTION_ARCHITECT);
        sel.selectTask(1);
        assertSame(solution.get(0), sel.selectedTask());
        assertEquals(solution.get(0).prompt, sel.chosenPrompt());
        assertEquals(solution.get(0).titleInCategory(), sel.taskLabels().get(1));

        sel.selectTask(solution.size());
        assertSame(solution.get(solution.size() - 1), sel.selectedTask());

        sel.selectTask(0);
        assertNull(sel.selectedTask());
        assertEquals("", sel.chosenPrompt());

        sel.selectTask(1);
        sel.selectTask(solution.size() + 1);
        assertNull(sel.selectedTask());
        sel.selectTask(-3);
        assertNull(sel.selectedTask());
    }

    @Test
    public void changingRole_resetsTaskAndShowsOnlyThatRole() {
        ToolsCatalogSelection sel = new ToolsCatalogSelection();
        sel.selectTask(1);
        assertNotNull(sel.selectedTask());

        sel.selectRole(1);
        assertEquals(PromptLibrary.Role.TECHNOLOGY_ARCHITECT, sel.role());
        assertEquals("tech-redundancy", PromptLibrary.entriesForRole(sel.role()).get(0).id);

        sel.selectRole(2);
        assertEquals(PromptLibrary.Role.EA, sel.role());
        assertEquals(0, sel.taskIndex());
        assertNull(sel.selectedTask());
        assertEquals("", sel.chosenPrompt());

        List<PromptLibrary.Entry> ea = PromptLibrary.entriesForRole(PromptLibrary.Role.EA);
        List<String> labels = sel.taskLabels();
        assertEquals(1 + ea.size(), labels.size());
        for (int i = 0; i < ea.size(); i++) {
            assertTrue(ea.get(i).id, ea.get(i).forRole(PromptLibrary.Role.EA));
            assertEquals(ea.get(i).titleInCategory(), labels.get(i + 1));
        }

        sel.selectTask(2);
        PromptLibrary.Entry eaTask = sel.selectedTask();
        assertNotNull(eaTask);
        assertTrue(eaTask.forRole(PromptLibrary.Role.EA));

        sel.selectRole(3);
        assertEquals(PromptLibrary.Role.HYGIENE, sel.role());
        assertNull(sel.selectedTask());
        assertEquals("tidy-duplicate-names", PromptLibrary.entriesForRole(sel.role()).get(0).id);

        sel.selectRole(0);
        assertEquals(PromptLibrary.Role.SOLUTION_ARCHITECT, sel.role());
        assertNull(sel.selectedTask());
        for (PromptLibrary.Entry e : PromptLibrary.entriesForRole(sel.role())) {
            assertTrue(e.id, e.forRole(PromptLibrary.Role.SOLUTION_ARCHITECT));
            assertFalse(e.id, e.forRole(PromptLibrary.Role.EA));
        }
    }

    @Test
    public void rolesPartitionTheCatalogWithoutOverlap() {
        List<PromptLibrary.Entry> solution = PromptLibrary.entriesForRole(PromptLibrary.Role.SOLUTION_ARCHITECT);
        List<PromptLibrary.Entry> technology = PromptLibrary.entriesForRole(PromptLibrary.Role.TECHNOLOGY_ARCHITECT);
        List<PromptLibrary.Entry> ea = PromptLibrary.entriesForRole(PromptLibrary.Role.EA);
        List<PromptLibrary.Entry> hygiene = PromptLibrary.entriesForRole(PromptLibrary.Role.HYGIENE);
        assertEquals(PromptLibrary.all().size(), solution.size() + technology.size() + ea.size() + hygiene.size());
        Set<String> ids = new HashSet<String>();
        for (PromptLibrary.Entry e : solution) {
            assertTrue(ids.add(e.id));
        }
        for (PromptLibrary.Entry e : technology) {
            assertTrue(e.id, ids.add(e.id));
        }
        for (PromptLibrary.Entry e : ea) {
            assertTrue(e.id, ids.add(e.id));
        }
        for (PromptLibrary.Entry e : hygiene) {
            assertTrue(e.id, ids.add(e.id));
        }
        assertTrue(PromptLibrary.entriesForRole(null).isEmpty());
    }

    @Test
    public void archiGptTabSend_ignoresSelectedToolsTask() {
        ToolsCatalogSelection sel = new ToolsCatalogSelection();
        sel.selectTask(1);
        PromptLibrary.Entry task = sel.selectedTask();
        assertNotNull(task);
        assertEquals("view-this-diagram", task.id);

        assertNull(ToolsCatalogSelection.toolForRequest(false, task));
        assertSame(task, ToolsCatalogSelection.toolForRequest(true, task));
        assertNull(ToolsCatalogSelection.toolForRequest(true, null));

        String mainPrompt = "Add a Business Actor called Customer";
        String toolsPrompt = sel.chosenPrompt();
        assertFalse(mainPrompt.equals(toolsPrompt));

        String mainMsg = UserMessageBuilder.buildUserMessage("sel", "<model/>", mainPrompt,
                skillBody(ToolsCatalogSelection.toolForRequest(false, task)));
        assertTrue(mainMsg.contains("User request: " + mainPrompt));
        assertFalse(mainMsg.contains(PromptLibrary.TOOL_INSTRUCTIONS_START));

        String toolsMsg = UserMessageBuilder.buildUserMessage("sel", "<model/>", toolsPrompt,
                skillBody(ToolsCatalogSelection.toolForRequest(true, task)));
        assertTrue(toolsMsg.contains("User request: " + toolsPrompt));
        assertTrue(toolsMsg.contains(PromptLibrary.TOOL_INSTRUCTIONS_START));
        assertTrue(toolsMsg.contains(PromptLibrary.TOOL_INSTRUCTIONS_END));
        assertTrue(toolsMsg.contains("On the canvas") || toolsMsg.contains("elementRef"));
    }

    private static String skillBody(PromptLibrary.Entry tool) {
        return tool != null ? tool.skillBody : "";
    }
}
