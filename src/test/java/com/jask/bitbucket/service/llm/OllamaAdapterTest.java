package com.jask.bitbucket.service.llm;

import com.jask.bitbucket.model.LlmRequest;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * OllamaAdapter 단위 테스트.
 */
public class OllamaAdapterTest {

    private OllamaAdapter adapter;

    @Before
    public void setUp() {
        adapter = new OllamaAdapter();
    }

    @Test
    public void testSupports_ollamaEndpoint() {
        assertTrue(adapter.supports("http://localhost:11434/api/chat"));
        assertTrue(adapter.supports("http://192.168.1.10:11434/api/chat"));
    }

    @Test
    public void testSupports_nonOllama() {
        assertFalse(adapter.supports("https://api.openai.com/v1/chat/completions"));
        assertFalse(adapter.supports("http://localhost:8080/v1/completions"));
    }

    @Test
    public void testBuildRequestBody() {
        LlmRequest request = new LlmRequest();
        request.setMessages(Arrays.asList(
                new LlmRequest.Message("system", "You are a reviewer."),
                new LlmRequest.Message("user", "Review this code.")
        ));

        String body = adapter.buildRequestBody(request, "codellama", 0.7, 2048);

        assertNotNull(body);
        assertTrue(body.contains("codellama"));
        assertTrue(body.contains("Review this code"));
        assertTrue(body.contains("\"stream\":false") || body.contains("\"stream\": false"));
    }

    @Test
    public void testExtractResponse_validOllamaResponse() {
        String responseBody = "{\"message\":{\"content\":\"suggestion here\"},\"done\":true}";
        String extracted = adapter.extractResponse(responseBody);

        assertEquals("suggestion here", extracted);
    }

    @Test
    public void testGetHealthCheckUrl() {
        String url = adapter.getHealthCheckUrl("http://localhost:11434/api/chat");
        assertTrue(url.contains("/api/tags"));
    }

    @Test
    public void testGetEngineName() {
        assertEquals("Ollama", adapter.getEngineName());
    }
}
