# Archi-LLM-plugin
Plugin to connect Archi ArchiMate modeling tool to user's chosen LLMs.

## ArchiGPT plugin (`com.archimatetool.archigpt`)

ArchiGPT is a view that provides a text prompt box for interacting with your ArchiMate model via an LLM. **To open it in Archi:** use **Help → Show ArchiGPT View**. If your Archi has a Window menu, you can also use **Window → Show View → Other… → ArchiGPT → ArchiGPT**.

**Current features:**
- View with a prompt text area and "Ask ArchiGPT" button
- **Ollama integration**: sends the prompt to local Ollama (`http://localhost:11434`) and shows the response. Default model: `llama3.2`. Ensure [Ollama](https://ollama.com) is running.
- Requests run in a background job so the UI stays responsive
- Ready to extend with model context (opened ArchiMate model) and preferences for base URL/model

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
   On Java 24+ set `export MAVEN_OPTS="-Djdk.xml.maxGeneralEntitySizeLimit=2147483647 -Djdk.xml.totalEntitySizeLimit=2147483647"` so Tycho can read p2 XML. To build only the plugin (skip tests): `mvn clean package -pl com.archimatetool.archigpt -P with-archi -Darchi.repo.path=/path/to/archi/com.archimatetool.editor.product/target/repository`. By default the path is `../archi/.../target/repository`. If your Archi build puts the p2 repo elsewhere, set `archi.repo.path` to that folder.

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

## Goal

Create a tool integrated into Archi that helps enterprise architects change ArchiMate models using a graphical user interface with a simple textbox for the prompt.

Reference: [Developing Import and Export Plug-ins](https://github.com/archimatetool/archi/wiki/Developing-Import-and-Export-Plug-ins).

## Planned LLM support

- Create an API call to an LLM (local Ollama or external)
- Send the relevant part of the opened ArchiMate model to the LLM
- LLM responses must adhere to the OpenGroup ArchiMate 3.2 spec and schema