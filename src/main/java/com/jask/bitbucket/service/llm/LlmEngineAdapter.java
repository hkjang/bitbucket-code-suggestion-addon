package com.jask.bitbucket.service.llm;

import com.google.gson.JsonObject;
import com.jask.bitbucket.model.LlmRequest;

/**
 * LLM 엔진 어댑터 인터페이스.
 * 각 LLM 프로바이더(Ollama, OpenAI, vLLM 등)는 이 인터페이스를 구현합니다.
 */
public interface LlmEngineAdapter {

    /**
     * 이 어댑터가 주어진 엔드포인트를 지원하는지 확인합니다.
     */
    boolean supports(String endpoint);

    /**
     * LLM 요청을 해당 프로바이더의 JSON 형식으로 변환합니다.
     */
    JsonObject buildRequestBody(LlmRequest request);

    /**
     * LLM 응답 JSON에서 텍스트 콘텐츠를 추출합니다.
     */
    String extractResponse(String responseBody);

    /**
     * 헬스체크용 URL을 반환합니다.
     */
    String getHealthCheckUrl(String endpoint);

    /**
     * 엔진 식별 이름 (로깅용).
     */
    String getEngineName();
}
