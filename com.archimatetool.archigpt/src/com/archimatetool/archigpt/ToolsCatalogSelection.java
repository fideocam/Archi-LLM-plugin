/**
 * Role-then-task state for the Tools tab. Independent of the ArchiGPT tab prompt.
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("nls")
public final class ToolsCatalogSelection {

    private PromptLibrary.Role role = PromptLibrary.Role.SOLUTION_ARCHITECT;
    private int taskIndex;

    public PromptLibrary.Role role() {
        return role;
    }

    public static PromptLibrary.Role roleAt(int index) {
        PromptLibrary.Role[] roles = PromptLibrary.Role.values();
        if (index < 0 || index >= roles.length) {
            return PromptLibrary.Role.SOLUTION_ARCHITECT;
        }
        return roles[index];
    }

    public static String[] roleLabels() {
        PromptLibrary.Role[] roles = PromptLibrary.Role.values();
        String[] labels = new String[roles.length];
        for (int i = 0; i < roles.length; i++) {
            labels[i] = roles[i].label();
        }
        return labels;
    }

    /** Changing role resets the task to {@link PromptLibrary#NONE_TASK}. */
    public void selectRole(int index) {
        role = roleAt(index);
        taskIndex = 0;
    }

    public void selectTask(int index) {
        int max = PromptLibrary.entriesForRole(role).size();
        if (index < 0 || index > max) {
            taskIndex = 0;
        } else {
            taskIndex = index;
        }
    }

    public int taskIndex() {
        return taskIndex;
    }

    public List<String> taskLabels() {
        List<String> labels = new ArrayList<String>();
        labels.add(PromptLibrary.NONE_TASK);
        List<PromptLibrary.Entry> entries = PromptLibrary.entriesForRole(role);
        for (int i = 0; i < entries.size(); i++) {
            labels.add(entries.get(i).titleInCategory());
        }
        return labels;
    }

    public PromptLibrary.Entry selectedTask() {
        if (taskIndex <= 0) {
            return null;
        }
        List<PromptLibrary.Entry> entries = PromptLibrary.entriesForRole(role);
        if (taskIndex > entries.size()) {
            return null;
        }
        return entries.get(taskIndex - 1);
    }

    public String chosenPrompt() {
        PromptLibrary.Entry e = selectedTask();
        return e != null ? e.prompt : "";
    }

    /**
     * The ArchiGPT tab never injects a Tools-tab skill. Tools injects the selected task, if any.
     */
    public static PromptLibrary.Entry toolForRequest(boolean fromTools, PromptLibrary.Entry selectedTask) {
        return fromTools ? selectedTask : null;
    }
}
