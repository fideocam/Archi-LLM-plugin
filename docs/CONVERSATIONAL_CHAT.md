# Conversational Chat Support — What Would Need to Change

The plugin currently works as **single-turn Q&A**: each request sends (system prompt + one user message with model XML + prompt), and the response is shown once. There is no conversation history.

To support **multi-turn conversational chat**, the following would need to change.

---

## 1. **Conversation history storage**

- **Where:** New (or extended) state in the view or a small service.
- **What:** Store a list of turns, e.g.:
  - `List<ChatTurn>` where each turn has:
    - `String userMessage` (what the user sent, including the built “user message” with model XML if you keep that format)
    - `String assistantResponse` (raw LLM reply)
    - Optionally: snapshot of selection/model context at send time (for clarity or re-send).
- **Scope:** Decide whether:
  - One conversation per **view** (global), or
  - One conversation per **open model** (clear when model is closed), or
  - One conversation per **session** with a “New chat” button to clear.

---

## 2. **Ollama client: send multiple messages**

- **File:** `OllamaClient.java`
- **Current:** `buildChatRequestJson(systemPrompt, userPrompt, numCtx)` builds:
  - `messages: [ { role: "system", content: systemPrompt }, { role: "user", content: userPrompt } ]`
- **Change:** Add an overload or new method that accepts a **list of messages** (e.g. `List<ChatMessage>` with `role` and `content`), and build:
  - `messages: [ { role: "system", content: systemPrompt }, { role: "user", content: msg1 }, { role: "assistant", content: msg2 }, ... ]`
- **API:** Ollama `/api/chat` already supports a `messages` array; no API change, only request building.
- **Context:** With many turns, the payload can exceed the model context. You’ll need a strategy (see §5).

---

## 3. **UI: chat-style view**

- **File:** `ArchiGPTView.java` (and possibly new helpers/widgets).
- **Current:** One prompt `Text` and one response `Text`; each send replaces the response.
- **Change:**
  - **Option A — Replace:** Replace the main area with a chat-style list (e.g. scrollable composite with “bubbles” or styled labels for user vs assistant). Input stays at the bottom; Enter sends, Shift+Enter newline.
  - **Option B — Add tab:** Keep current “Single question” tab and add a “Chat” tab with the conversation UI and its own history.
- **Details:**
  - Each **user** message: show the user text (and optionally a shortened “Model + selection” summary).
  - Each **assistant** message: show raw response; if it’s import JSON, you can still show “Imported …” and optionally a snippet, and keep the full reply in the bubble.
  - “New chat” / “Clear history” button to reset the conversation list (and in-memory history).
  - Optional: “Stop” during generation (you already have cancellation support; just hook it to the chat job).

---

## 4. **Send flow: build messages from history**

- **Where:** The code that currently builds `userMessage` and calls `client.generateWithSystemPrompt(..., userMessage, ...)` (in `ArchiGPTView`).
- **Change:**
  - Build the **current** user message as today (selection + model XML + user prompt).
  - Build the **full** `messages` list for the request:
    - `[ system, userTurn1, assistantTurn1, userTurn2, assistantTurn2, ..., currentUserMessage ]`
  - Call the new Ollama client method that sends this list (e.g. `generateWithConversation(systemPrompt, conversationHistory, currentUserMessage, connectionHolder, numCtx)`).
- **Model XML in each turn:**  
  - **Option 1:** Send full model XML only in the **first** user message; later turns are “User request: &lt;follow-up&gt;” only (saves tokens; model may “forget” later changes).  
  - **Option 2:** Send **current** model XML with **every** user message (LLM always sees up-to-date model; higher token use and need for truncation/summary).  
  - **Option 3:** First turn = full model; later turns = short “Summary of changes since last turn” or “Model unchanged” plus user text.

---

## 5. **Context window and truncation**

- **Issue:** With conversation history, you can exceed the model’s context (e.g. 8k–32k tokens).
- **Options:**
  - **Sliding window:** Send only the last N turns (e.g. last 5 user + 5 assistant) plus the current user message (and optionally a “summary” system line like “Earlier in the conversation the user asked about…”).
  - **Summarize old turns:** Run a separate (short) prompt to summarize older turns into a few sentences and send that as context (more work, better for long chats).
  - **Token budget:** Reserve a budget for “model XML” (e.g. 6k tokens) and a budget for “conversation” (e.g. 2k tokens), and truncate or drop oldest turns when over.
- **Implementation:** When building the `messages` list before the API call, either trim the list (sliding window) or replace old turns with a single “summary” user/assistant message.

---

## 6. **System prompt**

- **File:** `system-prompt.txt` / `ArchiMateSystemPrompt.java`
- **Change:** Small addition so the model knows it’s in a conversation, e.g.:
  - “You are in a multi-turn conversation about the user’s ArchiMate model. The user may ask follow-up questions. Answer based on the latest model content and the conversation so far.”
- No need to change the ANALYSIS vs CHANGES rules; they still apply to each reply.

---

## 7. **Persistence (optional)**

- **What:** Save conversation history so it survives restart.
- **Where:** On dispose or periodically, serialize `List<ChatTurn>` to a file (e.g. JSON in a plugin state location or next to the model file).
- **Load:** On view open or “restore last chat”, load and display the last conversation (and optionally allow “New chat” to start fresh).
- **Scope:** If you tie conversation to “current model”, you could store one file per model (e.g. by model file path or id).

---

## 8. **Import flow in chat**

- **Current:** After receiving the response, the view parses it and, if it’s CHANGES JSON, runs the importer and shows “Imported …”.
- **In chat:** Keep the same behavior: when the **latest** assistant message contains valid CHANGES JSON, parse it and run the importer once (for that turn). Show the “Imported …” text in or below that assistant bubble. No need to re-import old turns; only the latest reply can trigger an import.

---

## Summary table

| Area              | Current behavior              | Change for chat                                      |
|-------------------|-------------------------------|------------------------------------------------------|
| **OllamaClient**  | Single user message           | Accept list of messages; build `messages` array      |
| **ArchiGPTView**  | One prompt, one response box  | Chat UI (scrollable history + input at bottom)      |
| **State**         | None                          | List of (user, assistant) turns; optional persist   |
| **Send flow**     | userMessage → one API call    | history + currentUserMessage → one API call          |
| **Model XML**     | In every request              | Decide: every turn vs first only vs summary          |
| **Context**       | One-shot                      | Sliding window or summarization of old turns         |
| **System prompt** | Static                        | Add one sentence: “multi-turn conversation”          |
| **Import**        | On response                   | Unchanged: parse latest response, import if JSON     |

---

## Minimal first step

A minimal path to “conversation” without a full chat UI:

1. **OllamaClient:** Add `generateWithMessages(String systemPrompt, List<ChatMessage> messages, ...)` and build the `messages` array (system + list of user/assistant).
2. **View state:** Keep a `List<ChatTurn>` (e.g. last 10 turns); on Send, append current user message, call Ollama with full list, append assistant reply.
3. **UI:** Keep the same layout but **append** each new exchange to the response text (e.g. “User: …\n\nAssistant: …\n\n———\n\n”) so the user at least sees a linear history. Add “Clear history” to reset the list and the response text.

Then, in a second step, replace the single response text with a proper chat widget (bubbles, scroll, “New chat” button) and add persistence if desired.
