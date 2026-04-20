# Archi-LLM-plugin

Plugin to connect [Archi](https://www.archimatetool.com/) (ArchiMate modeling) with an LLM so you can extend and analyze your model using natural language. This repo provides the **ArchiGPT** plugin.

---

## Installing the plugin

1. **Get the plugin**  
   Download `ArchiGPT.archiplugin` from the **`export build/`** folder (Maven output) or from **`export/`** at the repo root when it has been refreshed to match (or from releases, if available). On **`main`**, this artifact is the **local Ollama** build only; optional **cloud** provider support lives on branch **`feature/external-llm`** and is not part of `main`.

2. **Close Archi**  
   Quit Archi completely before installing.

3. **Install in Archi**  
   - Open Archi.  
   - Go to **Help → Manage Archi Plug-ins**.  
   - Click **Install new** and select the `ArchiGPT.archiplugin` file.  
   - Restart Archi when prompted.

4. **Open the ArchiGPT view**  
   After restart: **ArchiGPT → Show ArchiGPT View** (or **Help → Show ArchiGPT View** / **Tools → ArchiGPT**).

---

## Branches

- **`main`** — Default branch: **Ollama (local LLM)** only, plus the packaged **`ArchiGPT.archiplugin`** for that build.
- **`feature/external-llm`** — Work-in-progress **external APIs** (OpenAI, Anthropic, Gemini, Azure, etc.). Merge to `main` only when you want cloud support in the default release.

---

## Prerequisites

- **Archi** — [Download Archi](https://www.archimatetool.com/download/) if you don’t have it.
- **Ollama** — The plugin talks to [Ollama](https://ollama.com) on your machine. Install Ollama and start it (e.g. run `ollama serve` or use the Ollama app). The plugin uses `http://localhost:11434` and the default model `llama3.2` unless you change settings.

---

## Using ArchiGPT

- **Open a model** — Have an ArchiMate model open in Archi. The plugin sends a summary of the model to the LLM so it can answer in context.
- **Type your request** — In the ArchiGPT tab, type what you want in the prompt box. Examples:
  - *"Add a Business Actor called Customer"*
  - *"Describe this view"*
  - *"What business processes use this application?"*
  - *"Remove this element from the diagram"*
- **Send** — Press **Enter** (or click **Ask ArchiGPT**). Use **Shift+Enter** for a new line. Click **Stop ArchiGPT** to cancel a request.
- **Result** — The LLM reply appears in the response area. If you asked to add or change the model, the plugin applies the changes and reports what was added or removed.

**Selection matters:** If you select a folder, a diagram, or an element in Archi before asking, the plugin sends that context to the LLM (e.g. “add to this diagram”, “remove this element”).

**Debug tab:** The **Debug** tab shows the plugin version and what was sent to the LLM (payload summary and XML), useful for troubleshooting.

---

## What you can do

- **Ask for analysis** — Describe the model, a view, or an element; ask about capabilities, dependencies, or impact. You get a plain-text answer.
- **Add elements and relationships** — Ask to add actors, processes, services, etc. The LLM returns structured data and the plugin adds them to your model (and optionally to the current diagram).
- **Create a new diagram** — Ask for a new view/diagram; the plugin can create it with elements and layout.
- **Remove from the model** — Ask to remove or delete an element or relationship (select it first, or name it). The plugin removes it from the model and all diagrams.
- **Remove from the diagram only** — Ask to “remove from the diagram” or “remove from this view” so only the figure is removed from the current view; the element stays in the model.
- **Remove a diagram** — Ask to remove/delete a view by name or by selecting it.
- **Export model as XML** — Ask to “return the model” or “export the model as XML”; when the reply contains full model XML, a **Save as…** button appears to save it to a file.

---

## Uninstalling the plugin

1. Close the ArchiGPT view (right‑click its tab → **Close**).
2. **Help → Manage Archi Plug-ins** → select ArchiGPT → **Uninstall**, then restart Archi.
3. If a broken ArchiGPT tab appears after uninstall, right‑click it → **Close**. If the layout keeps restoring it, use **Window → Reset Perspective** (or your Archi equivalent).

---

## Building from source

If you want to build the plugin yourself (Maven, Eclipse, or Ant), see **[docs/build.md](docs/build.md)**.

---

## License

**MIT License** — same terms family as **[Archi](https://github.com/archimatetool/archi)** (Archi is MIT-licensed). Full text: [LICENSE](LICENSE).

You may use, modify, and redistribute this plug-in; **keep the copyright and permission notice** in copies. This repository is **separate** from Archi; Archi itself remains under its authors’ license.

**Attribution:** Please credit **Archi-LLM-plugin / ArchiGPT** and link to this repo when you publish a fork or derivative.

**Note:** MIT permits commercial redistribution under law. The license is chosen to stay **compatible with Archi’s ecosystem**, not to impose restrictions that conflict with how Archi and typical Eclipse plug-ins are shared.

---

## More information

- [Open Group ArchiMate](https://www.opengroup.org/archimate-forum) and [Archi](https://www.archimatetool.com/) for modeling.
- [Developing Import and Export Plug-ins](https://github.com/archimatetool/archi/wiki/Developing-Import-and-Export-Plug-ins) for Archi plugin development.
