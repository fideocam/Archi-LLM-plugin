package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class KnowledgeRetrieverTest {

    @Test
    public void retrieve_emptyFolder_returnsEmpty() throws IOException {
        Path dir = Files.createTempDirectory("archigpt-knowledge-");
        try {
            assertEquals("", KnowledgeRetriever.retrieve(dir.toString(), "deployment viewpoint", 8000));
        } finally {
            Files.deleteIfExists(dir);
        }
    }

    @Test
    public void retrieve_picksMatchingCollection() throws IOException {
        Path dir = Files.createTempDirectory("archigpt-knowledge-");
        Path principles = dir.resolve("principles");
        Path cmdb = dir.resolve("cmdb");
        Files.createDirectory(principles);
        Files.createDirectory(cmdb);
        Files.write(principles.resolve("naming.md"),
                "Applications are named APP-xxx. Prefer reuse of existing components.".getBytes(StandardCharsets.UTF_8));
        Files.write(cmdb.resolve("servers.csv"),
                "ci,class,name\n1,server,db01".getBytes(StandardCharsets.UTF_8));
        try {
            String text = KnowledgeRetriever.retrieve(dir.toString(), "How should applications be named APP-xxx?", 4000);
            assertTrue(text.contains("COMPANY KNOWLEDGE"));
            assertTrue(text.contains("principles/naming.md"));
            assertTrue(text.contains("APP-xxx"));
            assertFalse(text.contains("db01"));
        } finally {
            Files.delete(principles.resolve("naming.md"));
            Files.delete(cmdb.resolve("servers.csv"));
            Files.delete(principles);
            Files.delete(cmdb);
            Files.delete(dir);
        }
    }

    @Test
    public void retrieve_noQuery_includesFiles() throws IOException {
        Path dir = Files.createTempDirectory("archigpt-knowledge-");
        Files.write(dir.resolve("note.txt"),
                "Tiedonhallintamalli requires a data store inventory.".getBytes(StandardCharsets.UTF_8));
        try {
            String text = KnowledgeRetriever.retrieve(dir.toString(), "", 2000);
            assertTrue(text.contains("Tiedonhallintamalli"));
        } finally {
            Files.delete(dir.resolve("note.txt"));
            Files.delete(dir);
        }
    }

    @Test
    public void userMessage_includesKnowledgeAfterModel() {
        String msg = UserMessageBuilder.buildUserMessage("sel", "<archimate/>", "Create a technology diagram",
                "--- COMPANY KNOWLEDGE ---\nUse Node for servers.\n");
        int xml = msg.indexOf("<archimate/>");
        int know = msg.indexOf("COMPANY KNOWLEDGE");
        int req = msg.indexOf("User request:");
        assertTrue(xml >= 0 && know > xml && req > know);
    }

    @Test
    public void defaultFolder_isUnderHome() {
        String folder = KnowledgeRetriever.defaultFolder();
        assertTrue(folder.contains(".archigpt"));
        assertTrue(folder.endsWith("knowledge") || folder.endsWith("knowledge" + java.io.File.separator));
    }
}
