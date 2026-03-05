package com.archimatetool.archigpt;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Verifies that a selected view/diagram or model element is actually passed as part of the prompt to the LLM.
 * Tests that UserMessageBuilder includes selection context in the user message, and that typical selection
 * descriptions (folder, element, view) appear in the final prompt.
 */
public class SelectionInPromptTest {

    /** When selection context is provided, the user message sent to the LLM must contain it. */
    @Test
    public void userMessageContainsSelectionContext() {
        String selectionContext = "Current selection in the model:\n- Folder \"Business\"\n";
        String modelXml = "<model/>";
        String prompt = "add an actor";
        String userMessage = UserMessageBuilder.buildUserMessage(selectionContext, modelXml, prompt);
        assertTrue("User message must contain selection context so LLM receives it",
                userMessage.contains(selectionContext.trim()));
        assertTrue("User message must contain the selection description",
                userMessage.contains("Folder \"Business\""));
        assertTrue("User message must still contain the user prompt", userMessage.contains(prompt));
    }

    /** When selection context is empty, the user message does not contain the selection header. */
    @Test
    public void userMessageWithoutSelection_doesNotContainSelectionHeader() {
        String userMessage = UserMessageBuilder.buildUserMessage("", "<model/>", "analyze this");
        assertTrue("Message must contain prompt", userMessage.contains("analyze this"));
        assertTrue("Message must not contain selection header when no selection",
                !userMessage.contains("Current selection in the model:"));
    }

    /** Selected folder: the prompt to the LLM must include the folder selection context. */
    @Test
    public void selectedFolder_isIncludedInPromptToLLM() {
        String selectionContext = "Current selection in the model:\n- Folder \"Business Layer\"\n";
        String userMessage = UserMessageBuilder.buildUserMessage(selectionContext, "<model/>", "add a process");
        assertTrue("Prompt to LLM must contain selection context", userMessage.contains("Current selection in the model:"));
        assertTrue("Prompt to LLM must contain folder description", userMessage.contains("Folder \"Business Layer\""));
    }

    /** Selected element: the prompt to the LLM must include the element selection context. */
    @Test
    public void selectedElement_isIncludedInPromptToLLM() {
        String selectionContext = "Current selection in the model:\n- Element BusinessActor \"Customer\" (id=abc-123)\n";
        String userMessage = UserMessageBuilder.buildUserMessage(selectionContext, "<model/>", "add a relationship");
        assertTrue("Prompt to LLM must contain selection context", userMessage.contains("Current selection in the model:"));
        assertTrue("Prompt to LLM must contain element type", userMessage.contains("BusinessActor"));
        assertTrue("Prompt to LLM must contain element name", userMessage.contains("Customer"));
    }

    /** Selected view/diagram: the prompt to the LLM must include the view selection context. */
    @Test
    public void selectedViewOrDiagram_isIncludedInPromptToLLM() {
        String selectionContext = "Current selection in the model:\n- View/Diagram \"Application Overview\" (viewpoint: Application)\n";
        String userMessage = UserMessageBuilder.buildUserMessage(selectionContext, "<model/>", "describe this view");
        assertTrue("Prompt to LLM must contain selection context", userMessage.contains("Current selection in the model:"));
        assertTrue("Prompt to LLM must contain View/Diagram label", userMessage.contains("View/Diagram"));
        assertTrue("Prompt to LLM must contain diagram name", userMessage.contains("Application Overview"));
    }

    /** User request appears first in the message so the LLM sees the task even if the tail (e.g. XML) is truncated by context limits. */
    @Test
    public void userRequestAppearsFirstThenSelectionThenXml() {
        String selectionContext = "Current selection in the model:\n- Folder \"Technology\"\n";
        String prompt = "add a node";
        String userMessage = UserMessageBuilder.buildUserMessage(selectionContext, "<model/>", prompt);
        int requestPos = userMessage.indexOf("User request: " + prompt);
        int selectionPos = userMessage.indexOf("Current selection in the model:");
        assertTrue("User request must appear in message", requestPos >= 0);
        assertTrue("Selection context must appear in message", selectionPos >= 0);
        assertTrue("User request must appear first so LLM sees the task if context is truncated",
                requestPos < selectionPos);
    }
}
