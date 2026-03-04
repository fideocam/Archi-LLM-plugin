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
- **Model as context**: The open ArchiMate model is serialized to XML (elements, relationships, views and diagrams) and sent to the LLM with every request so it can avoid duplicates and describe the actual content.
- **Analysis mode**: For analysis/description/review prompts, the LLM replies in plain text. The system prompt instructs it to use [Open Group ArchiMate XSDs](https://www.opengroup.org/xsd/archimate/) as reference, to minimize hallucinations (only refer to elements in the supplied model), and to include views and diagrams when describing the full model. The response area shows the supplied model XML first, then the analysis.
- **CHANGES mode**: For add/change prompts, the LLM returns JSON (elements and relationships). The importer validates against ArchiMate 3.2 and skips elements that already exist in the model (same type and name). New elements are added to the selected folder (or the folder of the selected element). If a diagram is open or a view is selected, new elements are also added as figures on that view.
- **Background job**: Requests run in a background job so the UI stays responsive.

**Build & deploy:** Export as a deployable plug-in from Eclipse and copy the JAR into Archi’s `dropins` folder (e.g. `~/Library/Application Support/Archi/dropins` on macOS).

- **Headless (Ant):** From repo root: `eclipse -nosplash -application org.eclipse.ant.core.antRunner -data /path/to/workspace -buildfile build.xml` (workspace must contain the project and Archi as target). Output: `build-output/`.
- **Maven:** See [Building with Maven](#building-with-maven) below.

## Building with Maven

You need a p2 repository that contains Archi’s `com.archimatetool.editor` bundle. Two options:

### If you have Archi sources

1. Clone [Archi](https://github.com/archimatetool/archi) and build it:
   ```bash
   cd archi
   mvn clean package -P product
   ```
2. Build ArchiGPT with the profile that adds the local Archi p2 repo. From this repo’s root:
   ```bash
   mvn clean package -P with-archi
   ```
   On Java 24+ set `export MAVEN_OPTS="-Djdk.xml.maxGeneralEntitySizeLimit=2147483647 -Djdk.xml.totalEntitySizeLimit=2147483647"` so Tycho can read p2 XML. To build only the plugin (skip tests): `mvn clean package -pl com.archimatetool.archigpt -P with-archi -DskipTests -Darchi.repo.path=/path/to/archi/com.archimatetool.editor.product/target/repository`.  
   **Where the p2 repo is:** The build *default* looks for a sibling folder `archi` next to this repo (i.e. `../archi/.../target/repository`). If your Archi source lives elsewhere (e.g. in OneDrive), pass `-Darchi.repo.path=` with the full path to the `repository` folder inside the Archi product build (e.g. `.../ArchiGPT/archi/com.archimatetool.editor.product/target/repository`). You can also create a symlink `archi` pointing at your Archi clone so the default path works.

### If you only have an Archi installation

Maven/Tycho cannot use a plain “plugins” folder; it needs a p2 repository. You can:

- **Option A:** Build Archi from source once (steps above), then use `-P with-archi` for ArchiGPT.
- **Option B:** Use Eclipse to build: open `archigpt.target`, add a *Directory* location pointing at your Archi installation’s `plugins` folder (e.g. `Archi.app/Contents/Eclipse/plugins` on macOS), set it as target platform, then export the plugin (File → Export → Deployable plug-ins).

The built JAR is in `com.archimatetool.archigpt/target/`. See [Installing and using the plugin](#installing-and-using-the-plugin) for how to install it in Archi’s `dropins` folder.

## Installing and using the plugin

1. **Get the plugin JAR**  
   After building, the plugin JAR is at `com.archimatetool.archigpt/target/com.archimatetool.archigpt_*.jar` (or in `build-output/` if you used the Ant build).

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