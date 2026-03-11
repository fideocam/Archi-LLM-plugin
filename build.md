# Building the ArchiGPT plugin

This document describes how to build the ArchiGPT plugin from source. For using and installing the plugin, see [README.md](README.md).

## Build output

- **Maven:** `com.archimatetool.archigpt/target/com.archimatetool.archigpt_*.jar`
- **Ant:** `build-output/`
- **Installable package:** `export/ArchiGPT.archiplugin` (for **Help → Manage Archi Plug-ins**)

After building, see README for [installing and using](README.md#installing-and-using-the-plugin) the plugin in Archi.

---

## Maven (recommended)

You need a p2 repository that contains Archi’s `com.archimatetool.editor` bundle.

### Prerequisites

- [Archi](https://github.com/archimatetool/archi) built from source, or an existing Archi p2 repository.

### If you have Archi sources

1. Clone Archi (if needed) so the Archi source is next to this repo (e.g. `../archi` or `archi/` inside the same parent folder). Then build Archi from the **Archi repo root** (the folder that contains Archi’s `pom.xml` and `com.archimatetool.editor.product/`):
   ```bash
   cd path/to/archi
   mvn clean package -P product -DskipTests
   ```
   Example if `archi` is next to the plugin repo: `cd ../archi`. Example if you’re in the plugin repo and archi is in `archi/`: `cd archi`.

2. From this repository root, build ArchiGPT:
   ```bash
   mvn clean package -P with-archi
   ```

   **Java 24+:** Set JAXP limits so Tycho can read p2 XML:
   ```bash
   export MAVEN_OPTS="-Djdk.xml.maxGeneralEntitySizeLimit=2147483647 -Djdk.xml.totalEntitySizeLimit=2147483647"
   mvn clean package -P with-archi
   ```

   **Skip tests:**
   ```bash
   mvn clean package -pl com.archimatetool.archigpt -P with-archi -DskipTests
   ```

   **Run tests:** (requires Archi p2 repo as above; tests that need Archi classes are skipped if not on classpath)
   ```bash
   export MAVEN_OPTS="-Djdk.xml.maxGeneralEntitySizeLimit=2147483647 -Djdk.xml.totalEntitySizeLimit=2147483647"
   mvn clean test -P with-archi
   ```

   **Archi in a different path:** Pass the path to the Archi product p2 repository:
   ```bash
   mvn clean package -P with-archi -Darchi.repo.path=/path/to/archi/com.archimatetool.editor.product/target/repository
   ```
   **Archi is a sibling of the plugin repo** (e.g. `Archi-LLM-plugin/archi` and `Archi-LLM-plugin/Archi-LLM-plugin/`): the default `../archi` is resolved from the plugin module, so use the absolute path:
   ```bash
   mvn clean package -P with-archi -DskipTests -Darchi.repo.path=/full/path/to/archi/com.archimatetool.editor.product/target/repository
   ```
   Example (macOS): `-Darchi.repo.path=$HOME/Desktop/Archi-LLM-plugin/archi/com.archimatetool.editor.product/target/repository`

### Using a p2 folder inside this repo

By default the build looks for a p2 repository in the **`p2/`** folder in this repo. You can copy a valid p2 repo there and run:

```bash
mvn clean package -P with-archi -DskipTests
```

**Important:** The **p2 folder from inside the Archi application** (e.g. `Archi.app/Contents/Eclipse/p2`) is the *runtime cache*, not a p2 repository. It does not contain the bundles needed to build the plugin. You need the **product build repository**: when you build Archi from source, it creates a folder like `com.archimatetool.editor.product/target/repository` with **`content.xml`** and **`artifacts.xml`** at the root. Copy that entire folder into this repo as `p2/` (replacing the contents of `p2/`), then build. If you only have an Archi installation and no source, use [Eclipse export](#eclipse-export) instead.

### Creating the installable .archiplugin

When you run `mvn clean package -P with-archi` from the repo root, the build creates `export/ArchiGPT.archiplugin` automatically after the plugin JAR is built.

To create or update it manually (e.g. after a previous build), run from the repo root:

```bash
./scripts/create-archiplugin.sh
```

(or `sh scripts/create-archiplugin.sh` if the script is not executable). This produces `export/ArchiGPT.archiplugin` for **Help → Manage Archi Plug-ins**.

**Auto-build on push:** To have the plugin built and the export updated every time you run `git push`, install the pre-push hook: `cp scripts/pre-push .git/hooks/pre-push && chmod +x .git/hooks/pre-push`. The hook runs the Maven build and refreshes `export/ArchiGPT.archiplugin`; if the export changed, it commits it before pushing. Skip the hook once with `git push --no-verify`.

### If you only have an Archi installation (no source)

Maven/Tycho requires a p2 repository, not a plain `plugins` folder. You can:

- **Option A:** Build Archi from source once (steps above), then use `-P with-archi` for ArchiGPT.
- **Option B:** Build with Eclipse (see [Eclipse export](#eclipse-export) below).

---

## Eclipse export

1. Open the project in Eclipse (with PDE/target platform support).
2. Open `archigpt.target`, add a *Directory* location pointing at your Archi installation’s `plugins` folder (e.g. `Archi.app/Contents/Eclipse/plugins` on macOS).
3. Set it as the target platform.
4. **File → Export → Deployable plug-ins and fragments** and export the ArchiGPT plugin.

Copy the resulting JAR into Archi’s `dropins` folder (see README for paths per OS).

---

## Headless (Ant)

From the repository root (workspace must contain the project and Archi as target):

```bash
eclipse -nosplash -application org.eclipse.ant.core.antRunner -data /path/to/workspace -buildfile build.xml
```

Output: `build-output/`.
