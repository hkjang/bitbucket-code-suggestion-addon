package com.jask.bitbucket.service.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jask.bitbucket.model.LlmRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;

/**
 * Ollama API 어댑터.
 * 엔드포인트 패턴: /api/chat
 */
public class OllamaAdapter implements LlmEngineAdapter {

    private static final Logger log = LoggerFactory.getLogger(OllamaAdapter.class);

    @Override
    public boolean supports(String endpoint) {
        return endpoint != null && endpoint.contains("/api/chat");
    }

    @Override
    public JsonObject buildRequestBody(LlmRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("model", request.getModel());
        body.addProperty("stream", false);

        JsonObject options = new JsonObject();
        options.addProperty("temperature", request.getTemperature());
        options.addProperty("num_predict", request.getMaxTokens());
        body.add("options", options);

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
            if (json.has("message")) {
                return json.getAsJsonObject("message").get("content").getAsString();
            }
        } catch (Exception e) {
            log.warn("Ollama 응답 파싱 실패: {}", e.getMessage());
        }
        return responseBody;
    }

    @Override
    public String getHealthCheckUrl(String endpoint) {
        return endpoint.replaceAll("/api/chat$", "/api/tags");
    }

    @Override
    public String getEngineName() {
        return "Ollama";
    }
}
