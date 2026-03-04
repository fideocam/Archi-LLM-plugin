package com.archimatetool.archigpt;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Tests for calling Ollama (via mock server) and handling good/faulty responses.
 */
public class OllamaClientTest {

    private HttpServer server;
    private String lastRequestPath;
    private String lastRequestBody;
    private int responseCode = 200;
    private String responseBody = "{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"elements\\\":[],\\\"relationships\\\":[]}\"}}";

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                lastRequestPath = exchange.getRequestURI().getPath();
                if ("POST".equals(exchange.getRequestMethod())) {
                    lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseCode, responseBody.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                }
            }
        });
        server.start();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    public void generateWithSystemPrompt_callsChatApiAndReturnsContent() throws IOException {
        OllamaClient client = new OllamaClient(baseUrl(), "llama3.2");
        String result = client.generateWithSystemPrompt("You are ArchiMate expert.", "Add a BusinessActor.");
        assertEquals("{\"elements\":[],\"relationships\":[]}", result);
        assertEquals("/api/chat", lastRequestPath);
        assertTrue(lastRequestBody != null && lastRequestBody.contains("\"role\":\"system\""));
        assertTrue(lastRequestBody.contains("\"role\":\"user\""));
        assertTrue(lastRequestBody.contains("You are ArchiMate expert."));
        assertTrue(lastRequestBody.contains("Add a BusinessActor."));
    }

    @Test
    public void generateWithSystemPrompt_returnsEscapedContent() throws IOException {
        responseBody = "{\"message\":{\"role\":\"assistant\",\"content\":\"Hello\\nWorld\"}}";
        OllamaClient client = new OllamaClient(baseUrl(), "llama3.2");
        String result = client.generateWithSystemPrompt("Sys", "User");
        assertEquals("Hello\nWorld", result);
    }

    @Test(expected = IOException.class)
    public void generateWithSystemPrompt_whenServerReturns500_throws() throws IOException {
        responseCode = 500;
        responseBody = "{\"error\":\"internal\"}";
        OllamaClient client = new OllamaClient(baseUrl(), "llama3.2");
        client.generateWithSystemPrompt("Sys", "User");
    }

    @Test(expected = IOException.class)
    public void generateWithSystemPrompt_whenServerReturns404_throws() throws IOException {
        responseCode = 404;
        OllamaClient client = new OllamaClient(baseUrl(), "llama3.2");
        client.generateWithSystemPrompt("Sys", "User");
    }

    @Test
    public void generateWithSystemPrompt_whenResponseHasNoMessage_returnsRawBody() throws IOException {
        responseBody = "{\"other\":\"data\"}";
        OllamaClient client = new OllamaClient(baseUrl(), "llama3.2");
        String result = client.generateWithSystemPrompt("Sys", "User");
        assertNotNull(result);
        assertTrue(result.contains("other") || result.contains("data"));
    }

    @Test
    public void generate_simplePrompt_callsGenerateApi() throws IOException {
        responseBody = "{\"response\":\"Generated text\"}";
        server.removeContext("/");
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                lastRequestPath = exchange.getRequestURI().getPath();
                if ("POST".equals(exchange.getRequestMethod())) {
                    lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                }
            }
        });
        OllamaClient client = new OllamaClient(baseUrl(), "llama3.2");
        String result = client.generate("Hello");
        assertEquals("Generated text", result);
        assertEquals("/api/generate", lastRequestPath);
        assertTrue(lastRequestBody.contains("\"prompt\":\"Hello\""));
    }
}
