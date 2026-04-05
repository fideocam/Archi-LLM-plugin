package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Verifies the chunked analysis pattern: one {@code POST /api/chat} per model excerpt, same as
 * {@code ArchiGPTView}'s loop over planned/XML chunks with {@link ChunkAnalysisPrompt}.
 */
public class ChunkedOllamaAnalysisCallsTest {

    private HttpServer server;
    private final AtomicInteger chatPostCount = new AtomicInteger();
    private final List<String> chatRequestBodies = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();
                if ("/api/chat".equals(path) && "POST".equals(method)) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    chatRequestBodies.add(body);
                    int n = chatPostCount.incrementAndGet();
                    String responseBody = "{\"message\":{\"role\":\"assistant\",\"content\":\"Analysis part " + n + "\"}}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                    }
                    return;
                }
                if ("/api/tags".equals(path) && "GET".equals(method)) {
                    String responseBody = "{\"models\":[]}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                    }
                    return;
                }
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
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

    /** Same sequence as ArchiGPTView: one generateWithSystemPrompt per chunk with digest + scope title. */
    private static List<String> runChunkedAnalysisLikeView(OllamaClient client, List<String> xmlChunks, String digest,
            int numCtx) throws IOException {
        List<String> replies = new ArrayList<>();
        int n = xmlChunks.size();
        for (int i = 0; i < n; i++) {
            String chunkUser = ChunkAnalysisPrompt.buildChunkUserMessage(digest, "Test scope " + (i + 1), xmlChunks.get(i),
                    i + 1, n, "", "Describe this excerpt.");
            String part = client.generateWithSystemPrompt(ChunkAnalysisPrompt.SYSTEM_PROMPT, chunkUser, null, numCtx);
            replies.add(part);
        }
        return replies;
    }

    private static String largeXmlWithManyViews() {
        StringBuilder sb = new StringBuilder();
        sb.append("<root>");
        for (int i = 0; i < 80; i++) {
            sb.append("<view>n").append(i).append("</view>");
        }
        sb.append("</root>");
        return sb.toString();
    }

    @Test
    public void chunkedAnalysis_makesOneChatRequestPerXmlChunk() throws IOException {
        List<String> chunks = ModelXmlChunker.split(largeXmlWithManyViews(), 200);
        org.junit.Assume.assumeTrue("fixture should produce multiple chunks", chunks.size() >= 2);

        OllamaClient client = new OllamaClient(baseUrl(), "llama3.2");
        String digest = "MODEL DIGEST\nDiagrams/views: 0\n";

        List<String> replies = runChunkedAnalysisLikeView(client, chunks, digest, 4096);

        assertEquals(chunks.size(), chatPostCount.get());
        assertEquals(chunks.size(), replies.size());
        for (int i = 0; i < replies.size(); i++) {
            assertEquals("Analysis part " + (i + 1), replies.get(i));
        }

        assertEquals(chunks.size(), chatRequestBodies.size());
        String first = chatRequestBodies.get(0);
        assertTrue(first.contains("MODEL DIGEST"));
        assertTrue(first.contains("Excerpt 1 of " + chunks.size()));
        assertTrue(first.contains("Test scope 1"));
        assertTrue(first.contains(ChunkAnalysisPrompt.SYSTEM_PROMPT.substring(0, 20))
                || first.contains("system")); // body is JSON; system prompt is in JSON string
        assertTrue(first.contains("Describe this excerpt"));
        assertTrue(first.contains("\"role\":\"system\""));
        assertTrue(first.contains("You receive one excerpt"));

        String last = chatRequestBodies.get(chatRequestBodies.size() - 1);
        assertTrue(last.contains("Excerpt " + chunks.size() + " of " + chunks.size()));
    }

    @Test
    public void chunkedAnalysis_threeManualChunks_threeChatPosts() throws IOException {
        List<String> chunks = new ArrayList<>();
        chunks.add("<a>one</a>");
        chunks.add("<b>two</b>");
        chunks.add("<c>three</c>");

        OllamaClient client = new OllamaClient(baseUrl(), "test-model");
        List<String> replies = runChunkedAnalysisLikeView(client, chunks, "", 0);

        assertEquals(3, chatPostCount.get());
        assertEquals(3, replies.size());
    }
}
