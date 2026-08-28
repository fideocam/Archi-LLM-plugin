package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OllamaEndpointTest {

    @Test
    public void tryNormalize_allowsLocalAndLan() {
        assertTrue(OllamaEndpoint.tryNormalize("http://localhost:11434").ok);
        assertTrue(OllamaEndpoint.tryNormalize("http://127.0.0.1:11434").ok);
        assertTrue(OllamaEndpoint.tryNormalize("https://ollama.example.com").ok);
        assertTrue(OllamaEndpoint.tryNormalize("192.168.1.10:11434").ok);
        assertTrue(OllamaEndpoint.tryNormalize("localhost").ok);
    }

    @Test
    public void tryNormalize_rejectsUnsafeUrls() {
        assertFalse(OllamaEndpoint.tryNormalize("file:///etc/passwd").ok);
        assertFalse(OllamaEndpoint.tryNormalize("ftp://localhost/ollama").ok);
        assertFalse(OllamaEndpoint.tryNormalize("http://user:secret@localhost:11434").ok);
        assertFalse(OllamaEndpoint.tryNormalize("http://169.254.169.254/latest/meta-data").ok);
        assertFalse(OllamaEndpoint.tryNormalize("http://metadata.google.internal/").ok);
        assertFalse(OllamaEndpoint.tryNormalize("javascript:alert(1)").ok);
    }

    @Test
    public void tryNormalize_stripsApiPathAndQuery() {
        OllamaEndpoint.NormalizeResult r = OllamaEndpoint.tryNormalize("http://localhost:11434/api/chat?x=1");
        assertTrue(r.error, r.ok);
        assertEquals("http://localhost:11434", r.normalized);
    }

    @Test
    public void tryNormalize_keepsReverseProxyPath() {
        OllamaEndpoint.NormalizeResult r = OllamaEndpoint.tryNormalize("https://gateway.example.com/ollama");
        assertTrue(r.error, r.ok);
        assertEquals("https://gateway.example.com/ollama", r.normalized);
    }

    @Test
    public void normalizeOrDefault_fallsBack() {
        assertEquals(OllamaClient.DEFAULT_BASE_URL, OllamaEndpoint.normalizeOrDefault("file:///tmp"));
    }

    @Test
    public void tryNormalize_lanHostsGetOllamaPortLikeArchiGpt() {
        assertEquals("http://192.168.1.10:11434", OllamaEndpoint.tryNormalize("192.168.1.10").normalized);
        assertEquals("http://192.168.1.10:11434", OllamaEndpoint.tryNormalize("192.168.1.10:11434").normalized);
        assertEquals("http://192.168.1.10:11434", OllamaEndpoint.tryNormalize("http://192.168.1.10").normalized);
        assertEquals("http://gpu-box:11434", OllamaEndpoint.tryNormalize("http://gpu-box").normalized);
        assertEquals("http://192.168.1.10:12345", OllamaEndpoint.tryNormalize("http://192.168.1.10:12345").normalized);
        assertEquals("https://ollama.example.com", OllamaEndpoint.tryNormalize("https://ollama.example.com").normalized);
        assertEquals("http://192.168.1.10:11434", OllamaEndpoint.tryNormalize("http://192.168.1.10:11434/").normalized);
        assertEquals("http://localhost:11434", OllamaEndpoint.tryNormalize("localhost").normalized);
    }

    @Test
    public void tryNormalize_rejectsMetadataEncodings() {
        String[] blocked = {
                "http://169.254.169.254/",
                "http://2852039166/",
                "http://0xa9fea9fe/",
                "http://0251.0376.0251.0376/",
                "http://0xa9.0xfe.0xa9.0xfe/",
                "http://[::ffff:169.254.169.254]/",
                "http://[fd00:ec2::254]/",
                "http://metadata.google.internal./",
                "http://100.100.100.200/",
                "http://instance-data/"
        };
        for (String raw : blocked) {
            assertFalse("should reject " + raw, OllamaEndpoint.tryNormalize(raw).ok);
        }
    }

    @Test
    public void tryNormalize_rejectsOverlongUrl() {
        StringBuilder sb = new StringBuilder("http://localhost/");
        for (int i = 0; i < OllamaEndpoint.MAX_URL_LENGTH; i++) {
            sb.append('a');
        }
        assertFalse(OllamaEndpoint.tryNormalize(sb.toString()).ok);
    }
}
