/**
 * Pulls a small set of local knowledge files into the LLM prompt (lightweight RAG).
 * Put Markdown/text under the knowledge folder; retrieval is keyword overlap for now.
 * Ported from EaGPT (fideocam/sparxgpt).
 */
package com.archimatetool.archigpt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@SuppressWarnings("nls")
public final class KnowledgeRetriever {

    public static final int DEFAULT_MAX_CHARS = 8000;
    public static final int MAX_FILES = 200;
    public static final int MAX_FILE_CHARS = 32_000;
    public static final int MAX_CHUNKS = 8;
    public static final String[] ALLOWED_EXTENSIONS = { ".md", ".txt", ".csv" };

    private KnowledgeRetriever() {}

    public static String defaultFolder() {
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) {
            home = System.getProperty("java.io.tmpdir", ".");
        }
        return Paths.get(home, ".archigpt", "knowledge").toString();
    }

    public static String retrieve(String folder, String query, int maxChars) {
        if (maxChars < 500) {
            maxChars = 500;
        }
        if (maxChars > 40_000) {
            maxChars = 40_000;
        }
        if (folder == null || folder.trim().isEmpty()) {
            return "";
        }
        Path root;
        try {
            root = Paths.get(folder).toAbsolutePath().normalize();
        } catch (Exception e) {
            return "";
        }
        if (!Files.isDirectory(root)) {
            return "";
        }
        List<Path> files = new ArrayList<>();
        try {
            collectFiles(root, files);
        } catch (IOException e) {
            return "";
        }
        if (files.isEmpty()) {
            return "";
        }
        if (files.size() > MAX_FILES) {
            files = files.subList(0, MAX_FILES);
        }
        String[] terms = tokenize(query);
        List<Scored> scored = new ArrayList<>();
        String rootFull = root.toString();
        for (Path file : files) {
            try {
                Path full = file.toAbsolutePath().normalize();
                if (!full.startsWith(root)) {
                    continue;
                }
                byte[] raw = Files.readAllBytes(full);
                String body = new String(raw, StandardCharsets.UTF_8);
                if (body.length() > MAX_FILE_CHARS) {
                    body = body.substring(0, MAX_FILE_CHARS);
                }
                String rel = full.toString().substring(rootFull.length());
                rel = rel.replace('\\', '/');
                while (rel.startsWith("/")) {
                    rel = rel.substring(1);
                }
                int score = score(rel, body, terms);
                if (score > 0 || terms.length == 0) {
                    scored.add(new Scored(score, rel, body.trim()));
                }
            } catch (IOException ignored) {
            }
        }
        if (scored.isEmpty()) {
            return "";
        }
        Collections.sort(scored, new Comparator<Scored>() {
            @Override
            public int compare(Scored a, Scored b) {
                return Integer.compare(b.score, a.score);
            }
        });
        StringBuilder sb = new StringBuilder();
        sb.append("--- COMPANY KNOWLEDGE (retrieved for this request) ---\n");
        int used = sb.length();
        int n = 0;
        for (Scored item : scored) {
            if (n >= MAX_CHUNKS) {
                break;
            }
            String header = "### " + item.rel + "\n";
            int budget = maxChars - used - 80;
            if (budget < 120) {
                break;
            }
            String body = item.body;
            if (header.length() + body.length() + 2 > budget) {
                int take = Math.max(0, budget - header.length() - 20);
                body = body.substring(0, take) + "\n[truncated]";
            }
            sb.append(header).append(body).append("\n\n");
            used = sb.length();
            n++;
        }
        sb.append("--- END OF KNOWLEDGE ---\n");
        return sb.toString();
    }

    static String[] tokenize(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new String[0];
        }
        List<String> list = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String q = query.toLowerCase(Locale.ROOT);
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                current.append(c);
            } else if (current.length() > 0) {
                addTerm(list, current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            addTerm(list, current.toString());
        }
        Set<String> distinct = new LinkedHashSet<String>(list);
        return distinct.toArray(new String[0]);
    }

    private static void addTerm(List<String> list, String term) {
        if (term.length() < 3) {
            return;
        }
        if ("the".equals(term) || "and".equals(term) || "for".equals(term) || "with".equals(term)
                || "this".equals(term) || "that".equals(term) || "add".equals(term)
                || "jaa".equals(term) || "että".equals(term) || "kun".equals(term)) {
            return;
        }
        list.add(term);
    }

    static int score(String relativePath, String body, String[] terms) {
        if (terms.length == 0) {
            return 1;
        }
        String hay = (relativePath + "\n" + body).toLowerCase(Locale.ROOT);
        String relLower = relativePath.toLowerCase(Locale.ROOT);
        int total = 0;
        for (int t = 0; t < terms.length; t++) {
            String term = terms[t];
            if (relLower.contains(term)) {
                total += 8;
            }
            int from = 0;
            int hits = 0;
            while (hits < 12) {
                int i = hay.indexOf(term, from);
                if (i < 0) {
                    break;
                }
                hits++;
                from = i + term.length();
            }
            total += hits;
        }
        return total;
    }

    private static void collectFiles(Path dir, List<Path> out) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) {
                    collectFiles(p, out);
                } else if (Files.isRegularFile(p) && allowedExtension(p.getFileName().toString())) {
                    out.add(p);
                }
            }
        }
    }

    private static boolean allowedExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < ALLOWED_EXTENSIONS.length; i++) {
            if (lower.endsWith(ALLOWED_EXTENSIONS[i])) {
                return true;
            }
        }
        return false;
    }

    private static final class Scored {
        final int score;
        final String rel;
        final String body;

        Scored(int score, String rel, String body) {
            this.score = score;
            this.rel = rel;
            this.body = body;
        }
    }
}
