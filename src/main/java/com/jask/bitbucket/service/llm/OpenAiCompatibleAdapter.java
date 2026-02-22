package com.jask.bitbucket.service.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jask.bitbucket.model.LlmRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;

/**
 * OpenAI 호환 API 어댑터.
 * vLLM, OpenAI, Azure OpenAI, LM Studio 등 OpenAI 형식을 따르는 모든 프로바이더를 지원합니다.
 * 엔드포인트 패턴: /v1/chat/completions 또는 기타 (fallback)
 */
public class OpenAiCompatibleAdapter implements LlmEngineAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAdapter.class);

    @Override
    public boolean supports(String endpoint) {
        // Ollama가 아닌 모든 엔드포인트를 OpenAI 호환으로 처리 (fallback 역할)
        return endpoint != null && !endpoint.contains("/api/chat");
    }

    @Override
    public JsonObject buildRequestBody(LlmRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("model", request.getModel());
        body.addProperty("temperature", request.getTemperature());
        body.addProperty("max_tokens", request.getMaxTokens());

        JsonArray messages = new JsonArray();
        for (LlmRequest.Message msg : request.getMessages()) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.getRole());
            m.addProperty("content", msg.getContent());
            messages.add(m);
        }
        body.add("messages", messages);

        return body;
    }

    @Override
    public String extractResponse(String responseBody) {
        try {
            JsonObject json = new JsonParser().parse(new StringReader(responseBody)).getAsJsonObject();
            if (json.has("choices")) {
                JsonArray choices = json.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    return choices.get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();
                }
            }
        } catch (Exception e) {
            log.warn("OpenAI 호환 응답 파싱 실패: {}", e.getMessage());
        }
        return responseBody;
    }

    @Override
    public String getHealthCheckUrl(String endpoint) {
        return endpoint.replaceAll("/v1/chat/completions$", "/v1/models");
    }

    @Override
    public String getEngineName() {
        return "OpenAI-Compatible";
    }
}
