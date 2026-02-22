package com.jask.bitbucket.service;

import java.util.List;
import java.util.Map;

/**
 * 사용량/비용 한도 관리 서비스 인터페이스.
 *
 * 요건 11: 비용/사용량 한도 (일/주/월 단위)
 * 요건 14: 운영 알림 (임계값 기반)
 */
public interface UsageQuotaService {

    // =========================================================================
    // 한도 설정 관리
    // =========================================================================

    /**
     * 한도 설정을 생성합니다.
     */
    QuotaInfo createQuota(QuotaCreateRequest request);

    /**
     * 한도 설정을 수정합니다.
     */
    QuotaInfo updateQuota(int quotaId, QuotaCreateRequest request);

    /**
     * 한도 설정을 삭제합니다.
     */
    void deleteQuota(int quotaId);

    /**
     * 모든 한도 설정을 조회합니다.
     */
    List<QuotaInfo> getAllQuotas();

    /**
     * 특정 범위의 한도를 조회합니다.
     */
    QuotaInfo getQuotaByScope(String scope, String scopeKey);

    // =========================================================================
    // 사용량 기록/조회
    // =========================================================================

    /**
     * 사용량을 기록합니다 (LLM 호출 시마다).
     */
    void recordUsage(UsageRecord record);

    /**
     * 사용량 현황을 조회합니다 (현재 기간의 누적).
     */
    UsageSummary getUsageSummary(String scope, String scopeKey, String period);

    /**
     * 한도 초과 여부를 확인합니다.
     * 초과 시 exceedAction에 따라 BLOCK/WARN/THROTTLE 반환.
     */
    QuotaCheckResult checkQuota(String projectKey, String username);

    /**
     * 기간별 사용량 통계를 조회합니다 (차트용).
     */
    List<DailyUsageStat> getDailyUsageStats(String scope, String scopeKey, int days);

    // =========================================================================
    // DTOs
    // =========================================================================

    class QuotaCreateRequest {
        private String scope;        // GLOBAL, PROJECT
        private String scopeKey;     // "global" 또는 프로젝트 키
        private String period;       // DAILY, WEEKLY, MONTHLY
        private int maxCalls;
        private long maxTokens;
        private int warningThresholdPercent = 80;
        private String exceedAction = "WARN_ONLY"; // BLOCK, WARN_ONLY, THROTTLE
        private boolean enabled = true;

        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public String getScopeKey() { return scopeKey; }
        public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public int getMaxCalls() { return maxCalls; }
        public void setMaxCalls(int maxCalls) { this.maxCalls = maxCalls; }
        public long getMaxTokens() { return maxTokens; }
        public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }
        public int getWarningThresholdPercent() { return warningThresholdPercent; }
        public void setWarningThresholdPercent(int v) { this.warningThresholdPercent = v; }
        public String getExceedAction() { return exceedAction; }
        public void setExceedAction(String exceedAction) { this.exceedAction = exceedAction; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    class QuotaInfo {
        private int id;
        private String scope;
        private String scopeKey;
        private String period;
        private int maxCalls;
        private long maxTokens;
        private int warningThresholdPercent;
        private String exceedAction;
        private boolean enabled;
        private long createdAt;
        private long updatedAt;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public String getScopeKey() { return scopeKey; }
        public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public int getMaxCalls() { return maxCalls; }
        public void setMaxCalls(int maxCalls) { this.maxCalls = maxCalls; }
        public long getMaxTokens() { return maxTokens; }
        public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }
        public int getWarningThresholdPercent() { return warningThresholdPercent; }
        public void setWarningThresholdPercent(int v) { this.warningThresholdPercent = v; }
        public String getExceedAction() { return exceedAction; }
        public void setExceedAction(String exceedAction) { this.exceedAction = exceedAction; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    class UsageRecord {
        private String projectKey;
        private String username;
        private String engineProfile;
        private int inputTokens;
        private int outputTokens;
        private long estimatedCostMicro;
        private long latencyMs;
        private boolean success;

        public String getProjectKey() { return projectKey; }
        public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEngineProfile() { return engineProfile; }
        public void setEngineProfile(String engineProfile) { this.engineProfile = engineProfile; }
        public int getInputTokens() { return inputTokens; }
        public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }
        public int getOutputTokens() { return outputTokens; }
        public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }
        public long getEstimatedCostMicro() { return estimatedCostMicro; }
        public void setEstimatedCostMicro(long v) { this.estimatedCostMicro = v; }
        public long getLatencyMs() { return latencyMs; }
        public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }

    class UsageSummary {
        private String scope;
        private String scopeKey;
        private String period;
        private int totalCalls;
        private long totalInputTokens;
        private long totalOutputTokens;
        private long totalCostMicro;
        private int maxCalls;
        private long maxTokens;
        private double usagePercent;
        private String status; // NORMAL, WARNING, EXCEEDED

        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public String getScopeKey() { return scopeKey; }
        public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public int getTotalCalls() { return totalCalls; }
        public void setTotalCalls(int totalCalls) { this.totalCalls = totalCalls; }
        public long getTotalInputTokens() { return totalInputTokens; }
        public void setTotalInputTokens(long v) { this.totalInputTokens = v; }
        public long getTotalOutputTokens() { return totalOutputTokens; }
        public void setTotalOutputTokens(long v) { this.totalOutputTokens = v; }
        public long getTotalCostMicro() { return totalCostMicro; }
        public void setTotalCostMicro(long v) { this.totalCostMicro = v; }
        public int getMaxCalls() { return maxCalls; }
        public void setMaxCalls(int maxCalls) { this.maxCalls = maxCalls; }
        public long getMaxTokens() { return maxTokens; }
        public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }
        public double getUsagePercent() { return usagePercent; }
        public void setUsagePercent(double usagePercent) { this.usagePercent = usagePercent; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    class QuotaCheckResult {
        private boolean allowed;
        private String action;  // ALLOW, BLOCK, WARN, THROTTLE
        private String message;
        private double usagePercent;

        public boolean isAllowed() { return allowed; }
        public void setAllowed(boolean allowed) { this.allowed = allowed; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public double getUsagePercent() { return usagePercent; }
        public void setUsagePercent(double usagePercent) { this.usagePercent = usagePercent; }

        public static QuotaCheckResult allow() {
            QuotaCheckResult r = new QuotaCheckResult();
            r.setAllowed(true);
            r.setAction("ALLOW");
            return r;
        }
    }

    class DailyUsageStat {
        private String date; // yyyy-MM-dd
        private int calls;
        private long inputTokens;
        private long outputTokens;
        private long costMicro;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public int getCalls() { return calls; }
        public void setCalls(int calls) { this.calls = calls; }
        public long getInputTokens() { return inputTokens; }
        public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }
        public long getOutputTokens() { return outputTokens; }
        public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
        public long getCostMicro() { return costMicro; }
        public void setCostMicro(long costMicro) { this.costMicro = costMicro; }
    }
}
