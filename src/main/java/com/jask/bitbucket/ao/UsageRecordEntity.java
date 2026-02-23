package com.jask.bitbucket.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.Table;

/**
 * 사용량 기록 Active Objects entity.
 * LLM API 호출 시마다 기록되어 한도 관리의 기반이 됩니다.
 *
 * 요건 11: 비용/사용량 한도 (누적 추적)
 */
@Table("JASK_USAGE_REC")
@Preload
public interface UsageRecordEntity extends Entity {

    /**
     * 프로젝트 키 (전역 호출이면 "global")
     */
    @Indexed
    String getProjectKey();
    void setProjectKey(String projectKey);

    /**
     * 사용자
     */
    @Indexed
    String getUsername();
    void setUsername(String username);

    /**
     * 사용한 엔진 프로파일 이름
     */
    String getEngineProfile();
    void setEngineProfile(String engineProfile);

    /**
     * 요청 토큰 수
     */
    int getInputTokens();
    void setInputTokens(int inputTokens);

    /**
     * 응답 토큰 수
     */
    int getOutputTokens();
    void setOutputTokens(int outputTokens);

    /**
     * 예상 비용 (USD 기준, x10000 정수 → $0.01 = 100)
     */
    long getEstimatedCostMicro();
    void setEstimatedCostMicro(long estimatedCostMicro);

    /**
     * 응답 시간 (ms)
     */
    long getLatencyMs();
    void setLatencyMs(long latencyMs);

    /**
     * 호출 성공 여부
     */
    boolean isSuccess();
    void setSuccess(boolean success);

    /**
     * 기록 시각 (epoch ms)
     */
    @Indexed
    long getRecordedAt();
    void setRecordedAt(long recordedAt);
}
