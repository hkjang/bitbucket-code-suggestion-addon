package com.jask.bitbucket.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.Table;

/**
 * 사용량 한도 설정 Active Objects entity.
 * 프로젝트/전역 단위로 LLM 호출 횟수 및 비용 한도를 관리합니다.
 *
 * 요건 11: 비용/사용량 한도
 */
@Table("JASK_USAGE_QUOTA")
@Preload
public interface UsageQuotaEntity extends Entity {

    /**
     * 한도 적용 범위: GLOBAL, PROJECT
     */
    @Indexed
    String getScope();
    void setScope(String scope);

    /**
     * 범위 키 (GLOBAL이면 "global", PROJECT이면 프로젝트 키)
     */
    @Indexed
    String getScopeKey();
    void setScopeKey(String scopeKey);

    /**
     * 한도 기간: DAILY, WEEKLY, MONTHLY
     */
    String getPeriod();
    void setPeriod(String period);

    /**
     * 최대 LLM 호출 횟수 한도
     */
    int getMaxCalls();
    void setMaxCalls(int maxCalls);

    /**
     * 최대 토큰 사용량 한도
     */
    long getMaxTokens();
    void setMaxTokens(long maxTokens);

    /**
     * 경고 임계값 (%, 예: 80 → 80%에서 경고)
     */
    int getWarningThresholdPercent();
    void setWarningThresholdPercent(int warningThresholdPercent);

    /**
     * 한도 초과 시 동작: BLOCK, WARN_ONLY, THROTTLE
     */
    String getExceedAction();
    void setExceedAction(String exceedAction);

    /**
     * 활성화 여부
     */
    boolean isEnabled();
    void setEnabled(boolean enabled);

    long getCreatedAt();
    void setCreatedAt(long createdAt);

    long getUpdatedAt();
    void setUpdatedAt(long updatedAt);
}
