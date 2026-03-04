# Building ArchiGPT

Build output: the plugin JAR is in `com.archimatetool.archigpt/target/` (or `build-output/` if you use the Ant build). See [README.md](README.md#installing-and-using-the-plugin) for how to install it in Archi.

## Eclipse export

Export as a deployable plug-in from Eclipse and copy the JAR into Archi’s `dropins` folder (e.g. `~/Library/Application Support/Archi/dropins` on macOS). Open `archigpt.target`, set it as target platform, then **File → Export → Deployable plug-ins**.

## Headless (Ant)

From repo root (workspace must contain the project and Archi as target):

```bash
eclipse -nosplash -application org.eclipse.ant.core.antRunner -data /path/to/workspace -buildfile build.xml
```

Output: `build-output/`.

## Building with Maven

You need a p2 repository that contains Archi’s `com.archimatetool.editor` bundle.

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
   On Java 24+ set `export MAVEN_OPTS="-Djdk.xml.maxGeneralEntitySizeLimit=2147483647 -Djdk.xml.totalEntitySizeLimit=2147483647"` so Tycho can read p2 XML.

   To build only the plugin (skip tests):
   ```bash
   mvn clean package -pl com.archimatetool.archigpt -P with-archi -DskipTests -Darchi.repo.path=/path/to/archi/com.archimatetool.editor.product/target/repository
   ```

   **Where the p2 repo is:** The build *default* looks for a sibling folder `archi` next to this repo (i.e. `../archi/.../target/repository`). If your Archi source lives elsewhere (e.g. in OneDrive), pass `-Darchi.repo.path=` with the full path to the `repository` folder inside the Archi product build. You can also create a symlink `archi` pointing at your Archi clone so the default path works.

### If you only have an Archi installation

Maven/Tycho cannot use a plain “plugins” folder; it needs a p2 repository. You can:

- **Option A:** Build Archi from source once (steps above), then use `-P with-archi` for ArchiGPT.
- **Option B:** Use Eclipse to build: open `archigpt.target`, add a *Directory* location pointing at your Archi installation’s `plugins` folder (e.g. `Archi.app/Contents/Eclipse/plugins` on macOS), set it as target platform, then export the plugin (File → Export → Deployable plug-ins).
