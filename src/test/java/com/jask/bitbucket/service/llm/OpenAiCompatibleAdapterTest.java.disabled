package com.jask.bitbucket.service.llm;

import com.jask.bitbucket.model.LlmRequest;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * OpenAiCompatibleAdapter 단위 테스트.
 */
public class OpenAiCompatibleAdapterTest {

    private OpenAiCompatibleAdapter adapter;

    @Before
    public void setUp() {
        adapter = new OpenAiCompatibleAdapter();
    }

    @Test
    public void testSupports_anyEndpoint() {
        // Fallback adapter - supports all endpoints
        assertTrue(adapter.supports("https://api.openai.com/v1/chat/completions"));
        assertTrue(adapter.supports("http://vllm-server:8000/v1/completions"));
        assertTrue(adapter.supports("https://custom-llm.example.com/api"));
    }

    @Test
    public void testBuildRequestBody() {
        LlmRequest request = new LlmRequest();
        request.setMessages(Arrays.asList(
                new LlmRequest.Message("system", "System prompt"),
                new LlmRequest.Message("user", "User message")
        ));

        String body = adapter.buildRequestBody(request, "gpt-4", 0.3, 4096);

        assertNotNull(body);
        assertTrue(body.contains("gpt-4"));
        assertTrue(body.contains("System prompt"));
        assertTrue(body.contains("User message"));
        assertTrue(body.contains("4096") || body.contains("max_tokens"));
    }

    @Test
    public void testExtractResponse_validOpenAiResponse() {
        String responseBody = "{\"choices\":[{\"message\":{\"content\":\"code review result\"}}]}";
        String extracted = adapter.extractResponse(responseBody);

        assertEquals("code review result", extracted);
    }

    @Test
    public void testExtractResponse_emptyChoices() {
        String responseBody = "{\"choices\":[]}";
        String extracted = adapter.extractResponse(responseBody);

        // 빈 choices의 경우 빈 문자열 또는 null
        assertTrue(extracted == null || extracted.isEmpty());
    }

    @Test
    public void testGetHealthCheckUrl() {
        String url = adapter.getHealthCheckUrl("https://api.example.com/v1/chat/completions");
        assertTrue(url.contains("/v1/models"));
    }

    @Test
    public void testGetEngineName() {
        assertEquals("OpenAI Compatible", adapter.getEngineName());
    }
}
