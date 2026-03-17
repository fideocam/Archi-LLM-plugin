# Plan: Support for External Online LLM Services

This document describes what would be needed to support **external online LLM services** (e.g. OpenAI, Anthropic, Azure OpenAI, Google AI) in addition to the current **local Ollama** integration.

---

## 1. Current state

- **Single backend:** The plugin uses `OllamaClient` only, talking to `http://localhost:11434` (Ollama’s default).
- **No configuration UI:** Base URL and model are fixed in code (`DEFAULT_BASE_URL`, `DEFAULT_MODEL`). There is no preferences/settings screen for the user.
- **Chat API:** Requests are sent to Ollama’s `/api/chat` with a JSON body: `model`, `stream: false`, `messages: [ { role: "system", content: "..." }, { role: "user", content: "..." } ]`, and optional `options.num_ctx`.
- **No API key:** Ollama is local and does not require authentication.
- **View:** `ArchiGPTView` constructs the user message (model XML + prompt), calls `client.generateWithSystemPrompt(systemPrompt, userMessage, ...)`, then parses the response (JSON or plain text) and runs the importer or shows analysis.

---

## 2. Goal

Allow users to choose an **LLM provider** and send requests to **external online services** (and/or keep using local Ollama). Requirements:

- User can select a provider (e.g. “Ollama (local)”, “OpenAI”, “Anthropic”, “Azure OpenAI”, “Google AI”, or “Custom (OpenAI-compatible)”).
- For external services: user supplies **API key** and optionally **base URL** and **model name**.
- Same plugin behaviour: same system prompt, same user message format, same parsing and import flow. Only the HTTP endpoint, headers, and request/response shape may differ per provider.

---

## 3. What you would need

### 3.1 Configuration and persistence

- **Stored settings** (per user or per workspace, not in the repo):
  - **Provider** — e.g. `ollama` | `openai` | `anthropic` | `azure_openai` | `google` | `custom`
  - **API key** — for external services; leave empty for Ollama.
  - **Base URL** — optional override (e.g. `https://api.openai.com/v1`, or a proxy URL). For Ollama, default `http://localhost:11434`.
  - **Model** — model name (e.g. `gpt-4o`, `claude-3-5-sonnet`, `llama3.2` for Ollama).
  - Optional: **max tokens**, **timeout**, **context size** (where supported by the API).
- **Where to store:** Use Eclipse/Archi preferences (e.g. `IEclipsePreferences` / `InstanceScope` or the plugin’s preference store) so the API key is stored on the user’s machine and never committed. Do **not** store API keys in the project or in plain text in the repo.
- **Defaults:** If no provider is configured, keep current behaviour: Ollama at `http://localhost:11434` with default model.

### 3.2 Provider abstraction (client interface)

- **Interface** (e.g. `LLMClient` or `ChatCompletionClient`) with a small set of methods, for example:
  - `boolean checkConnection()` — can we reach the service? (and optionally validate API key).
  - `String generateWithSystemPrompt(String systemPrompt, String userMessage, ...)` — returns the assistant’s reply text (and optionally support cancellation via a connection/handler holder).
- **Implementations:**
  - **OllamaClient** — refactor the current class to implement this interface (same URL/model from config or defaults).
  - **OpenAI-compatible client** — for OpenAI, Azure OpenAI, and other services that use the [OpenAI Chat Completions](https://platform.openai.com/docs/api-reference/chat) format: `POST .../chat/completions` with `Authorization: Bearer <api_key>`, body `{ "model", "messages": [ {"role","content"} ], "max_tokens", ... }`, response `choices[0].message.content`.
  - **Anthropic client** — [Anthropic Messages API](https://docs.anthropic.com/en/api/messages): different URL, header `x-api-key`, body shape `model`, `max_tokens`, `messages`, `system`; response in `content[0].text`.
  - **Google AI client** — e.g. [Google AI Gemini API](https://ai.google.dev/api): different URL and request/response shape.
- **Factory or registry:** From the stored “provider” and settings, the view (or a small service) obtains the correct client implementation and calls the same `generateWithSystemPrompt`-style method so the rest of the flow (parsing, import, analysis) stays unchanged.

### 3.3 Request/response mapping

- **Input:** The plugin always has the same logical input: one system prompt and one user message (or a list of messages for future chat). Each client implementation must:
  - Map system + user (and optional history) into the **provider’s** message format.
  - Send the correct HTTP method, URL, and headers (including API key where required).
- **Output:** All providers return “assistant text” in some form. Each client must:
  - Parse the HTTP response (JSON) and extract the **single assistant reply string** (e.g. `choices[0].message.content` for OpenAI, `content[0].text` for Anthropic).
  - Return that string to the plugin so the existing parser (JSON vs analysis) and importer logic can run unchanged.
- **Streaming:** Optional later enhancement; for a first version, non-streaming is enough (as with current Ollama).

### 3.4 Authentication and security

- **API keys:** For external services, the API key must be sent in the request (e.g. `Authorization: Bearer <key>` or provider-specific header). It must **never** be logged, shown in the UI in full, or stored in version control. Store only in the Eclipse preference store (or equivalent secure storage).
- **HTTPS:** External services must be called over HTTPS. Reject or warn if the user sets a non-HTTPS URL for a “cloud” provider.
- **User warning:** In the UI or README, state that when using an external provider, **model content and prompts are sent to that service**; users should be aware of data and privacy policies of the chosen provider.

### 3.5 Error handling and robustness

- **Network errors:** Timeouts, connection refused, DNS failures. Show a clear message (e.g. “Cannot reach … Check URL and network”).
- **HTTP errors:** 401 (invalid API key), 429 (rate limit), 5xx (server error). Parse error response body if present and show a short message to the user.
- **Retries:** Optional: retry with backoff on 429 or transient 5xx (respect provider’s retry-after if present).
- **Timeouts:** Configurable connect/read timeout (e.g. 30s connect, 120s read for long completions).

### 3.6 User interface

- **Preferences / Settings:** A way for the user to open “ArchiGPT settings” (e.g. from the view’s toolbar, or **Window → Preferences** under an “ArchiGPT” or “Archi” section). In that screen:
  - **Provider** — dropdown or list: Ollama (local), OpenAI, Anthropic, Azure OpenAI, Google AI, Custom.
  - **API key** — password-style field (masked); optional for Ollama.
  - **Base URL** — optional; pre-filled for known providers (e.g. `https://api.openai.com/v1`), editable for “Custom”.
  - **Model** — text field; pre-filled with a default per provider (e.g. `gpt-4o`, `claude-3-5-sonnet`), user can change.
  - Optional: **Test connection** button that calls `checkConnection()` and shows success or error.
- **In the main view:** Either show the current provider/model in the status area or keep the UI generic (“Ask ArchiGPT”); error messages should mention the configured provider/URL when a request fails.

### 3.7 Context and model limits

- External APIs have **context limits** (e.g. 8k–128k tokens depending on model). The plugin already truncates model XML (e.g. 12k characters). You may need to:
  - Make the truncation limit configurable per provider/model, or
  - Use a single conservative limit that works for the smallest target model.
- Some APIs accept a **max_tokens** (or equivalent) parameter; expose that in settings and pass it in the request.

### 3.8 Documentation and legal

- **README / user docs:** Explain how to choose a provider, where to get API keys, and that data is sent to the chosen service when not using Ollama.
- **Privacy / terms:** If you distribute the plugin, consider a short note that users are responsible for compliance with each provider’s terms and data policies.

---

## 4. Implementation phases (suggested)

| Phase | Scope | Deliverables |
|-------|--------|--------------|
| **1. Config and preferences** | Store provider, API key, URL, model in Eclipse preferences; no UI yet (or a simple dialog). | Preference keys and a small `ArchiGPTPreferences` (or similar) helper to read/write them. |
| **2. Client abstraction** | Define `LLMClient` interface; refactor `OllamaClient` to implement it and take URL/model from preferences. | Interface + current Ollama behaviour behind it; view uses the interface. |
| **3. OpenAI-compatible client** | Implement a client for OpenAI Chat Completions (and optionally Azure OpenAI with the same format). | New class; factory returns it when provider is `openai` or `azure_openai`. |
| **4. Preferences UI** | Add ArchiGPT preferences page: provider, API key, URL, model, test connection. | User can switch to OpenAI (or custom endpoint) and set API key. |
| **5. Anthropic / Google** | Implement clients for Anthropic and Google if desired; map their request/response to the same interface. | More provider options in the dropdown. |
| **6. Polish** | Error messages, HTTPS check, optional retries, README update. | Production-ready behaviour and docs. |

---

## 5. Provider-specific notes (reference)

- **OpenAI:** `POST https://api.openai.com/v1/chat/completions`, `Authorization: Bearer <key>`, body `{ "model", "messages", "max_tokens" }`, response `choices[0].message.content`.
- **Azure OpenAI:** Same schema; URL like `https://<resource>.openai.azure.com/openai/deployments/<deployment>/chat/completions?api-version=...`; API key in header `api-key` or `Authorization`.
- **Anthropic:** `POST https://api.anthropic.com/v1/messages`; header `x-api-key`, `anthropic-version`; body has `model`, `max_tokens`, `system`, `messages`; response `content[0].text`.
- **Google AI (Gemini):** Different base URL and JSON shape; need to map “system + user” into Gemini’s format and read the reply from the response structure.
- **Custom:** Many services expose an “OpenAI-compatible” endpoint; a single “OpenAI-compatible” client with configurable URL and API key can cover these.

---

## 6. Summary

To support external online LLM services you need:

1. **Stored configuration** — provider, API key, base URL, model (Eclipse preferences, never in repo).
2. **Provider abstraction** — one interface, multiple implementations (Ollama, OpenAI-compatible, Anthropic, etc.).
3. **Request/response mapping** — same logical “system + user → assistant text” in and out; each client adapts to the provider’s HTTP and JSON shape.
4. **Security** — API keys only in secure storage; HTTPS for external calls; clear user messaging about data leaving the machine.
5. **Preferences UI** — so users can select provider and enter API key and model.
6. **Error handling** — network and HTTP errors with clear feedback.
7. **Docs** — how to get API keys and what “external” means for privacy and terms.

The existing flow (build user message → get reply → parse → import or show analysis) stays the same; only the “get reply” step is implemented by different clients behind a common interface.
