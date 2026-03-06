# Archi-LLM-plugin
Plugin to connect Archi ArchiMate modeling tool to user's chosen LLMs.

## ArchiGPT plugin (`com.archimatetool.archigpt`)

ArchiGPT is a view that provides a text prompt box for interacting with your ArchiMate model via an LLM. 

**How to use**

- Download the plugin file (ArchiGPT.archiplugin) from export folder 
- Install from **Help → Manage Archi Plug-ins**
- After restart, open the view: in the menu bar click **ArchiGPT → Show ArchiGPT View**. You may also find it under **Help → Show ArchiGPT View** or **Tools → ArchiGPT**.

**Current features:**
- **View**: Prompt text area, "Ask ArchiGPT" button, and **Enter** to submit (Shift+Enter for new line). **Stop** button cancels the request and disconnects Ollama.
- **Ollama integration**: Sends the prompt to local Ollama (`http://localhost:11434`). Default model: `llama3.2`. Ensure [Ollama](https://ollama.com) is running. Status messages show connection and progress; raw LLM response (truncated) is shown in the result.
- **Model as context**: The open ArchiMate model is serialized to XML (elements, relationships, views and diagrams) and sent to the LLM with every request so it can avoid duplicates and describe the actual content. **Relevant parts first**: If you select a folder, view, or element in Archi, that context is sent *first* in the XML (selected folder's contents, selected view's diagram, or the view(s) that contain the selected element), so the LLM always sees what you're working on within the 24k limit. To fit **Ollama’s context limit** (often 4k–8k tokens with default settings), at most **12,000 characters** of model XML are sent (so the full message fits typical 4k–8k token limits); if the model is larger, the rest is truncated after the priority content. The **model XML is sent first** in the user message (then "--- END OF MODEL ---", then your request and selection) so the LLM receives the ArchiMate model; the system prompt tells the model to use the XML between the start and that delimiter. The plugin requests a 32k-token context from Ollama when available (increase in **Ollama → Settings** or via `OLLAMA_NUM_CTX` if the model still doesn’t “see” the XML).
- **What was sent (always visible)**: When you click **Ask ArchiGPT**, the exact payload is shown in the GUI: **(1) What was sent to the LLM (last request)** — prompt, selection context, and model XML length; **(2) Model XML sent to LLM (exact payload)** — the XML actually sent (relevant parts first, capped at 12k chars so it fits Ollama's context). The **Response** area below then shows only the LLM reply. So you can always see what the LLM received without scrolling.
- **Analysis mode**: For analysis/description/review prompts, the LLM replies in plain text. The system prompt instructs it to use [Open Group ArchiMate XSDs](https://www.opengroup.org/xsd/archimate/) as reference, to minimize hallucinations (only refer to elements in the supplied model), and to include views and diagrams when describing the full model.
- **Return/export model**: If you ask to "return the model", "return an empty model", or "export the model as XML", the LLM responds with a full **Open Exchange XML** document (XML declaration, `<model>` root with schema references, `<archimateModel>`, folders, and `<viewsAndDiagrams>`), not the JSON format. When the response contains full model XML, a **Save as…** button appears next to the Response label; use it to save the XML as a `.archimate` or `.xml` file.
- **CHANGES mode**: For add/change prompts (e.g. "add an element"), the LLM returns **JSON** (elements and relationships) for the importer. The importer validates against ArchiMate 3.2 and skips elements that already exist in the model (same type and name). New elements are added to the selected folder (or the folder of the selected element). If a diagram is open or a view is selected, new elements are also added as figures on that view. **Remove from model**: You can ask to remove or delete elements or relationships (e.g. "remove the Customer actor", "delete this relationship"). The LLM returns the same JSON with optional "removeElementIds" and "removeRelationshipIds" (ids from the supplied model); the plugin removes those concepts from the model and from all diagrams. **New diagram**: If the user asks for a whole new diagram/view (e.g. "create a new diagram", "add a view"), the LLM can include an optional `"diagram"` object in the same JSON with `name`, `viewpoint`, `nodes` (elementId, x, y, width, height), and `connections` (sourceElementId, targetElementId, relationshipId). The plugin then creates a new view in the model with those elements and connections laid out.
- **Background job**: Requests run in a background job so the UI stays responsive.

To build from source, see [build.md](build.md).

## Installing and using the plugin

1. **Get the plugin JAR**  
   After building (see [build.md](build.md)), the plugin JAR is at `com.archimatetool.archigpt/target/com.archimatetool.archigpt_*.jar` (or in `build-output/` if you used the Ant build).

2. **Close Archi**  
   Quit Archi completely before installing the plugin.

3. **Install the plugin** (either method):
   - **Via Help → Manage Archi Plug-ins:** Use the packaged `export/ArchiGPT.archiplugin` file. In Archi choose **Help → Manage Archi Plug-ins**, click **Install new**, select `ArchiGPT.archiplugin`, then restart Archi. The `.archiplugin` file is a zip that contains the magic entry `archi-plugin` plus the plugin JAR (create it with `scripts/create-archiplugin.sh` after building).
   - **Manual:** Copy the JAR into Archi's dropins folder. Create the folder if it does not exist:

   | OS      | Dropins folder |
   |---------|-----------------------------------------------|
   | macOS   | `~/Library/Application Support/Archi/dropins` |
   | Windows | `%APPDATA%\Archi\dropins` (e.g. `C:\Users\<you>\AppData\Roaming\Archi\dropins`) |
   | Linux   | `~/.archi/dropins` |

   You can also create a `dropins` folder next to the Archi installation's `plugins` folder and put the JAR there.

4. **Start Archi**  
   Launch Archi. The plugin is loaded at startup.

5. **Open the ArchiGPT view**  
   In the menu choose **Help → Show ArchiGPT View**. (Alternatively, if Archi shows a Window menu: **Window → Show View → Other…** → **ArchiGPT** → **ArchiGPT**.)  
   The view shows a prompt text box and an "Ask ArchiGPT" button. Open an ArchiMate model to use it with the plugin.

### Uninstalling the plugin

1. **Close the ArchiGPT view first**  
   Close the ArchiGPT tab (e.g. right‑click the tab → **Close**) before uninstalling. If you leave the view open, Archi will remember it in the layout; after the plugin is removed, that tab can reappear as an error placeholder (stop sign).

2. **Uninstall**  
   **Help → Manage Archi Plug-ins** → select ArchiGPT → **Uninstall** (or remove the JAR from the dropins folder), then restart Archi.

3. **If you already see the error tab**  
   After uninstalling, if a tab labeled ArchiGPT shows an error (stop sign): right‑click the tab → **Close**. If the layout keeps trying to restore it, use **Window → Reset Perspective** (or the equivalent in your Archi version) to clear the saved layout.

## Goal

Create a tool integrated into Archi that helps enterprise architects change ArchiMate models using a graphical user interface with a simple textbox for the prompt.

Reference: [Developing Import and Export Plug-ins](https://github.com/archimatetool/archi/wiki/Developing-Import-and-Export-Plug-ins).

## Planned features 

- Create an API call to an LLM (local Ollama or external)
- Send the relevant part of the opened ArchiMate model to the LLM
- LLM responses must adhere to the OpenGroup ArchiMate 3.2 spec and schema