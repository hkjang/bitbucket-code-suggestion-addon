package com.jask.bitbucket.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jask.bitbucket.config.PluginSettingsService;
import com.jask.bitbucket.model.LlmRequest;
import com.jask.bitbucket.security.EndpointValidator;
import com.jask.bitbucket.service.llm.*;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * LLM client implementation with engine adapter, circuit breaker, and retry.
 */
@ExportAsService({LlmClientService.class})
@Named("llmClientService")
public class LlmClientServiceImpl implements LlmClientService {

    private static final Logger log = LoggerFactory.getLogger(LlmClientServiceImpl.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 120;

    private final PluginSettingsService settingsService;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final List<LlmEngineAdapter> adapters;
    private final CircuitBreaker circuitBreaker;
    private final RetryPolicy retryPolicy;

    @Inject
    public LlmClientServiceImpl(PluginSettingsService settingsService) {
        this.settingsService = settingsService;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.adapters = Arrays.asList(
                new OllamaAdapter(),
                new OpenAiCompatibleAdapter()
        );
        // 5회 연속 실패 시 회로 차단, 60초 후 재시도
        this.circuitBreaker = new CircuitBreaker("llm", 5, 60_000);
        // 최대 3회 재시도, 초기 2초 대기, 2배씩 증가
        this.retryPolicy = new RetryPolicy(3, 2000, 2.0);
    }

    @Override
    public String chat(LlmRequest request) throws LlmException {
        // 회로차단기 확인
        if (!circuitBreaker.allowRequest()) {
            throw new LlmException("LLM 서비스 일시 중단 (회로차단기 OPEN). 잠시 후 다시 시도해주세요.");
        }

        String endpoint = settingsService.getLlmEndpoint();

        // SSRF 재검증
        EndpointValidator.ValidationResult validation =
                EndpointValidator.validate(endpoint, true);
        if (!validation.isValid()) {
            throw new LlmException("엔드포인트 보안 검증 실패: " + validation.getMessage());
        }

        String apiKey = settingsService.getLlmApiKey();

        if (request.getModel() == null || request.getModel().isEmpty()) {
            request.setModel(settingsService.getLlmModel());
        }
        if (request.getTemperature() == 0) {
            request.setTemperature(settingsService.getLlmTemperature());
        }
        if (request.getMaxTokens() == 0) {
            request.setMaxTokens(settingsService.getLlmMaxTokens());
        }

        LlmEngineAdapter adapter = resolveAdapter(endpoint);
        log.debug("LLM 엔진 어댑터 선택: {} (endpoint={})", adapter.getEngineName(), endpoint);

        JsonObject bodyJson = adapter.buildRequestBody(request);
        String requestBody = gson.toJson(bodyJson);

        // 재시도 루프
        LlmException lastException = null;
        for (int attempt = 0; attempt <= retryPolicy.getMaxRetries(); attempt++) {
            if (attempt > 0) {
                retryPolicy.waitBeforeRetry(attempt - 1);

                // 재시도 시에도 회로차단기 확인
                if (!circuitBreaker.allowRequest()) {
                    throw new LlmException("LLM 서비스 일시 중단 (회로차단기 OPEN).");
                }
            }

            try {
                String result = executeRequest(endpoint, requestBody, apiKey, adapter);
                circuitBreaker.recordSuccess();
                return result;
            } catch (LlmException e) {
                lastException = e;
                circuitBreaker.recordFailure();

                boolean retryable = (e.getStatusCode() > 0 && retryPolicy.isRetryable(e.getStatusCode()))
                        || (e.getCause() != null && retryPolicy.isRetryable((Exception) e.getCause()));

                if (!retryable || attempt >= retryPolicy.getMaxRetries()) {
                    log.error("LLM 호출 최종 실패: engine={}, attempts={}, error={}",
                            adapter.getEngineName(), attempt + 1, e.getMessage());
                    throw e;
                }

                log.warn("LLM 호출 실패 (재시도 예정): engine={}, attempt={}/{}, error={}",
                        adapter.getEngineName(), attempt + 1, retryPolicy.getMaxRetries() + 1,
                        e.getMessage());
            }
        }

        throw lastException != null ? lastException : new LlmException("알 수 없는 오류");
    }

    private String executeRequest(String endpoint, String requestBody,
                                   String apiKey, LlmEngineAdapter adapter) throws LlmException {
        Request.Builder httpRequestBuilder = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE));

        if (apiKey != null && !apiKey.isEmpty()) {
            httpRequestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }
        httpRequestBuilder.addHeader("Content-Type", "application/json");

        try (Response response = httpClient.newCall(httpRequestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "응답 없음";
                log.error("LLM API 호출 실패: engine={}, status={}, body={}",
                        adapter.getEngineName(), response.code(), errorBody);
                throw new LlmException("LLM API 호출 실패: " + response.code(), response.code());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            return adapter.extractResponse(responseBody);
        } catch (IOException e) {
            log.error("LLM API 통신 오류 ({}): {}", adapter.getEngineName(), e.getMessage(), e);
            throw new LlmException("LLM API 통신 오류: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean healthCheck() {
        String endpoint = settingsService.getLlmEndpoint();
        LlmEngineAdapter adapter = resolveAdapter(endpoint);
        String healthUrl = adapter.getHealthCheckUrl(endpoint);

        Request request = new Request.Builder()
                .url(healthUrl)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            boolean healthy = response.isSuccessful();
            log.debug("LLM 헬스체크 ({}): {}", adapter.getEngineName(), healthy ? "OK" : "FAIL");
            return healthy;
        } catch (IOException e) {
            log.warn("LLM 헬스체크 실패 ({}): {}", adapter.getEngineName(), e.getMessage());
            return false;
        }
    }

    /**
     * 회로차단기 상태를 반환합니다 (모니터링용).
     */
    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }

    private LlmEngineAdapter resolveAdapter(String endpoint) {
        for (LlmEngineAdapter adapter : adapters) {
            if (adapter.supports(endpoint)) {
                return adapter;
            }
        }
        throw new LlmException("지원되지 않는 LLM 엔드포인트: " + endpoint);
    }
}
