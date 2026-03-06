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

1. Clone and build Archi:
   ```bash
   cd archi
   mvn clean package -P product
   ```

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

   **Archi in a different path:** Pass the path to the Archi product p2 repository:
   ```bash
   mvn clean package -P with-archi -Darchi.repo.path=/path/to/archi/com.archimatetool.editor.product/target/repository
   ```
   By default the build looks for a sibling folder `archi` next to this repo. You can use a symlink if needed.

### Creating the installable .archiplugin

After a successful Maven build, create or update the file used by **Help → Manage Archi Plug-ins**:

```bash
./scripts/create-archiplugin.sh
```

This produces `export/ArchiGPT.archiplugin`. If you use the local `./push` script (in `.gitignore`), it runs the Maven build and then creates this file before committing and pushing.

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
