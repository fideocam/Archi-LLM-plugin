/**
 * Bundled tidy, view, pattern, and EA prompts (skill files under {@code skills/}).
 */
package com.archimatetool.archigpt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Loads the in-plugin prompt library. Each skill is a Markdown file with YAML-like front matter.
 * Injected into the user message only when the architect picks a tool — not appended to the system prompt.
 */
@SuppressWarnings("nls")
public final class PromptLibrary {

    public static final String NONE_LABEL = "(none) — type your own prompt";

    /** First item in the Tools-tab task combo. */
    public static final String NONE_TASK = "(none)";

    /** Cap on injected skill body so one tool cannot dominate the context window. */
    public static final int MAX_SKILL_CHARS = 8_000;

    public static final String TOOL_INSTRUCTIONS_START = "--- TOOL INSTRUCTIONS ---";
    public static final String TOOL_INSTRUCTIONS_END = "--- END TOOL INSTRUCTIONS ---";

    private static final String CATALOG_RESOURCE = "skills/catalog.txt";

    private static volatile List<Entry> cached;

    public enum Group {
        TIDY("Tidy"),
        VIEW("View"),
        PATTERN("Pattern"),
        EA("EA");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        static Group fromFrontMatter(String raw) {
            if (raw == null) {
                return EA;
            }
            String v = raw.trim().toLowerCase(Locale.ROOT);
            if ("tidy".equals(v) || "validate".equals(v)) {
                return TIDY;
            }
            if ("view".equals(v)) {
                return VIEW;
            }
            if ("pattern".equals(v)) {
                return PATTERN;
            }
            return EA;
        }
    }

    /** Tools-tab first dropdown: who the catalog is for. */
    public enum Role {
        SOLUTION_ARCHITECT("Solution architect",
                "Tidy, view, and pattern tools for a service or system slice."),
        EA("EA",
                "Echo-gap analysis against what this model already shows in other views or for peers.");

        private final String label;
        private final String tooltip;

        Role(String label, String tooltip) {
            this.label = label;
            this.tooltip = tooltip;
        }

        public String label() {
            return label;
        }

        public String tooltip() {
            return tooltip;
        }

        public static Role fromGroup(Group group) {
            return group == Group.EA ? EA : SOLUTION_ARCHITECT;
        }
    }

    public static final class Entry {
        public final String id;
        public final Group group;
        public final String title;
        public final String prompt;
        public final String skillBody;
        public final boolean analysisOnly;
        public final String catalogPath;

        Entry(String id, Group group, String title, String prompt, String skillBody, boolean analysisOnly,
                String catalogPath) {
            this.id = id;
            this.group = group;
            this.title = title;
            this.prompt = prompt;
            this.skillBody = skillBody;
            this.analysisOnly = analysisOnly;
            this.catalogPath = catalogPath;
        }

        public String comboLabel() {
            return group.label() + ": " + title;
        }

        /** Label inside a task combo that already names the role. */
        public String titleInCategory() {
            if (group == Group.EA) {
                return title;
            }
            return group.label() + ": " + title;
        }
    }

    private PromptLibrary() {}

    public static List<Entry> all() {
        List<Entry> local = cached;
        if (local != null) {
            return local;
        }
        synchronized (PromptLibrary.class) {
            if (cached == null) {
                cached = Collections.unmodifiableList(loadAll());
            }
            return cached;
        }
    }

    public static Entry findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        for (Entry e : all()) {
            if (id.equals(e.id)) {
                return e;
            }
        }
        return null;
    }

    /** Catalog entries in {@code group}, in catalog order. */
    public static List<Entry> entriesIn(Group group) {
        List<Entry> out = new ArrayList<Entry>();
        for (Entry e : all()) {
            if (e.group == group) {
                out.add(e);
            }
        }
        return out;
    }

    /** Catalog entries for a Tools-tab role, in catalog order. */
    public static List<Entry> entriesForRole(Role role) {
        List<Entry> out = new ArrayList<Entry>();
        if (role == null) {
            return out;
        }
        for (Entry e : all()) {
            if (Role.fromGroup(e.group) == role) {
                out.add(e);
            }
        }
        return out;
    }

    /** Tidy, View, and Pattern entries (solution-architect tools), in catalog order. */
    public static List<Entry> solutionArchitectEntries() {
        return entriesForRole(Role.SOLUTION_ARCHITECT);
    }

    /**
     * Truncate a skill body for injection. Empty or null yields empty string.
     */
    public static String truncateSkill(String skillBody) {
        if (skillBody == null) {
            return "";
        }
        String t = skillBody.trim();
        if (t.isEmpty()) {
            return "";
        }
        if (t.length() <= MAX_SKILL_CHARS) {
            return t;
        }
        return t.substring(0, MAX_SKILL_CHARS) + "\n[Tool instructions truncated.]\n";
    }

    static List<Entry> parseCatalog(String catalogText, ResourceOpener opener) {
        List<Entry> out = new ArrayList<Entry>();
        if (catalogText == null || opener == null) {
            return out;
        }
        String[] lines = catalogText.replace("\r\n", "\n").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String rel = lines[i].trim();
            if (rel.isEmpty() || rel.startsWith("#")) {
                continue;
            }
            String content;
            try {
                content = opener.open(rel);
            } catch (IOException e) {
                continue;
            }
            if (content == null || content.trim().isEmpty()) {
                continue;
            }
            Entry parsed = parseMarkdown(rel, content);
            if (parsed != null) {
                out.add(parsed);
            }
        }
        return out;
    }

    static Entry parseMarkdown(String catalogPath, String content) {
        if (content == null) {
            return null;
        }
        String text = content.replace("\r\n", "\n").trim();
        if (!text.startsWith("---\n")) {
            return null;
        }
        int end = text.indexOf("\n---\n", 4);
        if (end < 0) {
            return null;
        }
        String front = text.substring(4, end);
        String body = text.substring(end + 5).trim();
        Map<String, String> fields = parseFrontMatter(front);
        String id = fields.get("id");
        String title = fields.get("title");
        String prompt = fields.get("prompt");
        if (id == null || id.isEmpty() || title == null || title.isEmpty() || prompt == null || prompt.isEmpty()) {
            return null;
        }
        Group group = Group.fromFrontMatter(fields.get("group"));
        String mode = fields.get("mode");
        boolean analysisOnly = mode == null || !"changes".equals(mode.trim().toLowerCase(Locale.ROOT));
        return new Entry(id.trim(), group, title.trim(), prompt.trim(), body, analysisOnly, catalogPath);
    }

    private static Map<String, String> parseFrontMatter(String front) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        String[] lines = front.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int c = line.indexOf(':');
            if (c <= 0) {
                continue;
            }
            String key = line.substring(0, c).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(c + 1).trim();
            if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                value = value.substring(1, value.length() - 1);
            }
            fields.put(key, value);
        }
        return fields;
    }

    private static List<Entry> loadAll() {
        List<Entry> fromBundle = loadFromOsgi();
        if (!fromBundle.isEmpty()) {
            return fromBundle;
        }
        List<Entry> fromCp = loadFromClasspath();
        if (!fromCp.isEmpty()) {
            return fromCp;
        }
        return loadFromFilesystem();
    }

    private static List<Entry> loadFromOsgi() {
        try {
            Bundle bundle = FrameworkUtil.getBundle(PromptLibrary.class);
            if (bundle == null) {
                return Collections.emptyList();
            }
            URL catalogUrl = bundle.getEntry(CATALOG_RESOURCE);
            if (catalogUrl == null) {
                return Collections.emptyList();
            }
            String catalog = readUtf8(catalogUrl.openStream());
            return parseCatalog(catalog, new ResourceOpener() {
                @Override
                public String open(String relative) throws IOException {
                    URL u = bundle.getEntry("skills/" + relative);
                    if (u == null) {
                        return null;
                    }
                    return readUtf8(u.openStream());
                }
            });
        } catch (Exception e) {
            return Collections.emptyList();
        } catch (NoClassDefFoundError e) {
            return Collections.emptyList();
        }
    }

    private static List<Entry> loadFromClasspath() {
        InputStream catalogStream = PromptLibrary.class.getClassLoader().getResourceAsStream(CATALOG_RESOURCE);
        if (catalogStream == null) {
            catalogStream = PromptLibrary.class.getResourceAsStream("/" + CATALOG_RESOURCE);
        }
        if (catalogStream == null) {
            return Collections.emptyList();
        }
        try {
            String catalog = readUtf8(catalogStream);
            return parseCatalog(catalog, new ResourceOpener() {
                @Override
                public String open(String relative) throws IOException {
                    String path = "skills/" + relative;
                    InputStream in = PromptLibrary.class.getClassLoader().getResourceAsStream(path);
                    if (in == null) {
                        in = PromptLibrary.class.getResourceAsStream("/" + path);
                    }
                    if (in == null) {
                        return null;
                    }
                    return readUtf8(in);
                }
            });
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static List<Entry> loadFromFilesystem() {
        File dir = resolveSkillsDirectory();
        if (dir == null) {
            return Collections.emptyList();
        }
        File catalogFile = new File(dir, "catalog.txt");
        if (!catalogFile.isFile()) {
            return Collections.emptyList();
        }
        try {
            String catalog = readUtf8(new FileInputStream(catalogFile));
            final File skillsDir = dir;
            return parseCatalog(catalog, new ResourceOpener() {
                @Override
                public String open(String relative) throws IOException {
                    File f = new File(skillsDir, relative.replace('/', File.separatorChar));
                    if (!f.isFile()) {
                        return null;
                    }
                    return readUtf8(new FileInputStream(f));
                }
            });
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    static File resolveSkillsDirectory() {
        String userDir = System.getProperty("user.dir", ".");
        File[] candidates = new File[] {
                new File(userDir, "skills"),
                new File(userDir, "com.archimatetool.archigpt/skills"),
                new File(userDir, "../com.archimatetool.archigpt/skills")
        };
        for (int i = 0; i < candidates.length; i++) {
            File c = candidates[i];
            if (new File(c, "catalog.txt").isFile()) {
                return c;
            }
        }
        return null;
    }

    private static String readUtf8(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    interface ResourceOpener {
        String open(String relative) throws IOException;
    }
}
