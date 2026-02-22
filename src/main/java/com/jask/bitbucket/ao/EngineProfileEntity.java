package com.jask.bitbucket.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * 엔진 프로파일 Active Objects entity.
 * 여러 LLM 엔진(Ollama, vLLM, OpenAI 호환 등)의 연결 정보를 관리합니다.
 *
 * 요건 5: 엔진 프로파일 관리 (다중 프로파일)
 * 요건 6: 자격증명 저장 (AES-256-GCM 암호화)
 * 요건 7: 연결 테스트 (DNS, TLS, 인증, 모델 검증)
 */
@Table("JASK_ENGINE_PROF")
@Preload
public interface EngineProfileEntity extends Entity {

    /**
     * 프로파일 고유 이름 (예: "Ollama-로컬", "vLLM-GPU서버")
     */
    @Indexed
    String getProfileName();
    void setProfileName(String profileName);

    /**
     * 엔진 유형: OLLAMA, VLLM, OPENAI_COMPATIBLE
     */
    @Indexed
    String getEngineType();
    void setEngineType(String engineType);

    /**
     * 엔드포인트 URL (예: http://localhost:11434)
     */
    String getEndpointUrl();
    void setEndpointUrl(String endpointUrl);

    /**
     * API 키 (AES-256-GCM 암호화된 값, ENC: 접두사)
     */
    @StringLength(StringLength.UNLIMITED)
    String getApiKey();
    void setApiKey(String apiKey);

    /**
     * 기본 모델 이름 (예: codellama:13b)
     */
    String getDefaultModel();
    void setDefaultModel(String defaultModel);

    /**
     * Temperature (0.0 ~ 2.0)
     */
    double getTemperature();
    void setTemperature(double temperature);

    /**
     * Max tokens (응답 최대 토큰)
     */
    int getMaxTokens();
    void setMaxTokens(int maxTokens);

    /**
     * 요청 타임아웃 (초)
     */
    int getTimeoutSeconds();
    void setTimeoutSeconds(int timeoutSeconds);

    /**
     * 활성화 여부
     */
    @Indexed
    boolean isEnabled();
    void setEnabled(boolean enabled);

    /**
     * 기본 프로파일 여부 (하나만 기본)
     */
    boolean isDefaultProfile();
    void setDefaultProfile(boolean defaultProfile);

    /**
     * 우선순위 (낮을수록 높은 우선순위, fallback 순서)
     */
    int getPriority();
    void setPriority(int priority);

    /**
     * 마지막 연결 테스트 결과: SUCCESS, FAILURE, NEVER_TESTED
     */
    String getLastTestResult();
    void setLastTestResult(String lastTestResult);

    /**
     * 마지막 연결 테스트 시간 (epoch ms)
     */
    long getLastTestTime();
    void setLastTestTime(long lastTestTime);

    /**
     * 마지막 연결 테스트 상세 메시지
     */
    @StringLength(StringLength.UNLIMITED)
    String getLastTestDetails();
    void setLastTestDetails(String lastTestDetails);

    /**
     * 마지막 연결 테스트 응답 시간 (ms)
     */
    long getLastTestLatencyMs();
    void setLastTestLatencyMs(long lastTestLatencyMs);

    /**
     * 커스텀 헤더 (JSON 형식: {"key": "value"})
     */
    @StringLength(StringLength.UNLIMITED)
    String getCustomHeaders();
    void setCustomHeaders(String customHeaders);

    /**
     * 설명/메모
     */
    @StringLength(StringLength.UNLIMITED)
    String getDescription();
    void setDescription(String description);

    /**
     * 생성 시각 (epoch ms)
     */
    long getCreatedAt();
    void setCreatedAt(long createdAt);

    /**
     * 수정 시각 (epoch ms)
     */
    long getUpdatedAt();
    void setUpdatedAt(long updatedAt);

    /**
     * 생성자
     */
    String getCreatedBy();
    void setCreatedBy(String createdBy);
}
