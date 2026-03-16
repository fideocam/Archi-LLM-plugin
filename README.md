# Archi-LLM-plugin
Plugin to connect Archi ArchiMate modeling tool to user's chosen LLMs.

## ArchiGPT plugin (`com.archimatetool.archigpt`)

ArchiGPT is a view that provides a text prompt box for interacting with your ArchiMate model via an LLM. 

**How to use**

- Download the plugin file (ArchiGPT.archiplugin) from stable (or export) folder 
- Install from **Help → Manage Archi Plug-ins**
- After restart, open the view: in the menu bar click **ArchiGPT → Show ArchiGPT View**. You may also find it under **Help → Show ArchiGPT View** or **Tools → ArchiGPT**.

**Current features:**
- **Two tabs**: **ArchiGPT** tab shows only the prompt box, a single action button, and the LLM response. **Debug** tab shows build version, "What was sent to the LLM" summary, and the exact model XML sent to the LLM (for verifying payload and troubleshooting).
- **Build version**: On the **Debug** tab you see **ArchiGPT v1.0.0.&lt;qualifier&gt;** (e.g. v1.0.0.20250304120000 after a build). Use this to confirm you are running the latest plugin.
- **Main view**: Prompt text area; **Enter** submits (Shift+Enter for new line). The button shows **Ask ArchiGPT** when idle and **Stop ArchiGPT** when a request is running; click **Stop ArchiGPT** to cancel and disconnect from Ollama.
- **Ollama integration**: Sends the prompt to local Ollama (`http://localhost:11434`). Default model: `llama3.2`. Ensure [Ollama](https://ollama.com) is running. Status messages show connection and progress; raw LLM response (truncated) is shown in the result.
- **Model as context**: The open ArchiMate model is serialized to XML (elements, relationships, views and diagrams) and sent to the LLM with every request so it can avoid duplicates and describe the actual content. **Relevant parts first**: If you select a folder, view, or element in Archi, that context is sent *first* in the XML (selected folder's contents, selected view's diagram, or the view(s) that contain the selected element), so the LLM always sees what you're working on within the 24k limit. To fit **Ollama’s context limit** (often 4k–8k tokens with default settings), at most **12,000 characters** of model XML are sent (so the full message fits typical 4k–8k token limits); if the model is larger, the rest is truncated after the priority content. The **model XML is sent first** in the user message (then "--- END OF MODEL ---", then your request and selection) so the LLM receives the ArchiMate model; the system prompt tells the model to use the XML between the start and that delimiter. The plugin requests a 32k-token context from Ollama when available (increase in **Ollama → Settings** or via `OLLAMA_NUM_CTX` if the model still doesn’t “see” the XML).
- **Debug tab**: When you click **Ask ArchiGPT**, the **Debug** tab is updated with: **(1) What was sent to the LLM (last request)** — prompt, selection context, and model XML length (with a short preview); **(2) Model XML sent to LLM (exact payload)** — the XML actually sent. Switch to the Debug tab to inspect these.
- **Analysis mode**: For analysis/description/review prompts, the LLM replies in plain text. The system prompt instructs it to use [Open Group ArchiMate XSDs](https://www.opengroup.org/xsd/archimate/) as reference, to minimize hallucinations (only refer to elements in the supplied model), and to include views and diagrams when describing the full model.
- **Return/export model**: If you ask to "return the model", "return an empty model", or "export the model as XML", the LLM responds with a full **Open Exchange XML** document (XML declaration, `<model>` root with schema references, `<archimateModel>`, folders, and `<viewsAndDiagrams>`), not the JSON format. When the response contains full model XML, a **Save as…** button appears below the response box; use it to save the XML as a `.archimate` or `.xml` file.
- **CHANGES mode**: For add/change prompts (e.g. "add an element"), the LLM returns **JSON** (elements and relationships) for the importer. The importer validates against ArchiMate 3.2 and skips elements that already exist in the model (same type and name). New elements are added to the selected folder (or the folder of the selected element). If a diagram is open or a view is selected, new elements are also added as figures on that view. **Remove from model/diagram**: You can remove or delete elements and relationships from the model (and thus from all diagrams). Select the element or relationship in the model tree or by clicking it on a diagram, then ask e.g. "remove this element", "remove from the diagram", "delete this relationship", or "remove the Customer actor". The selection context sent to the LLM includes the element/relationship id; the LLM returns JSON with "removeElementIds" and/or "removeRelationshipIds"; the plugin removes those from the model and from every diagram. **New diagram**: If the user asks for a whole new diagram/view (e.g. "create a new diagram", "add a view"), the LLM can include an optional `"diagram"` object in the same JSON with `name`, `viewpoint`, `nodes` (elementId, x, y, width, height), and `connections` (sourceElementId, targetElementId, relationshipId). The plugin then creates a new view in the model with those elements and connections laid out.
- **Background job**: Requests run in a background job so the UI stays responsive.

**Building from source:** See [build.md](build.md) for Maven, Eclipse, and Ant build instructions.

## Installing and using the plugin

1. **Get the plugin**  
   Use the pre-built `export/ArchiGPT.archiplugin` from this repo, or [build from source](build.md) to produce the JAR or `.archiplugin` file.

2. **Close Archi**  
   Quit Archi completely before installing.

3. **Install**  
   In Archi: **Help → Manage Archi Plug-ins** → **Install new** → select `ArchiGPT.archiplugin` (or copy the built JAR into Archi’s dropins folder — see [build.md](build.md) for output paths and dropins locations per OS). Restart Archi.

4. **Open the ArchiGPT view**  
   **Help → Show ArchiGPT View** (or **ArchiGPT → Show ArchiGPT View** / **Tools → ArchiGPT**). The view has two tabs: **ArchiGPT** (prompt, Ask/Stop button, response, Save as…) and **Debug** (build version and request payload). Open an ArchiMate model to use the plugin.

### Uninstalling the plugin

1. **Close the ArchiGPT view first**  
   Close the ArchiGPT tab (e.g. right‑click the tab → **Close**) before uninstalling. If you leave the view open, Archi will remember it in the layout; after the plugin is removed, that tab can reappear as an error placeholder (stop sign).

2. **Uninstall**  
   **Help → Manage Archi Plug-ins** → select ArchiGPT → **Uninstall** (or remove the JAR from the dropins folder), then restart Archi.

3. **If you already see the error tab**  
   After uninstalling, if a tab labeled ArchiGPT shows an error (stop sign): right‑click the tab → **Close**. If the layout keeps trying to restore it, use **Window → Reset Perspective** (or the equivalent in your Archi version) to clear the saved layout.


## Background

This plugin connects [Archi](https://www.archimatetool.com/) (ArchiMate modeling) with an LLM so you can extend and analyze models via natural language. It uses the Open Group ArchiMate 3.2 specification and Archi’s model API; references: [Developing Import and Export Plug-ins](https://github.com/archimatetool/archi/wiki/Developing-Import-and-Export-Plug-ins).

## Planned features

- API support for external LLMs
- Shared prompt libraries
- Multi‑turn conversations with context
- Pattern suggestions and architecture validations
