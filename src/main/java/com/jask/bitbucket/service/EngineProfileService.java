package com.jask.bitbucket.service;

import java.util.List;
import java.util.Map;

/**
 * 엔진 프로파일 관리 서비스 인터페이스.
 *
 * 요건 5: 엔진 프로파일 CRUD + 다중 프로파일 관리
 * 요건 6: 자격증명 암호화 저장
 * 요건 7: 연결 테스트 (DNS→TLS→인증→모델 검증)
 */
public interface EngineProfileService {

    /**
     * 프로파일을 생성합니다.
     */
    EngineProfileInfo createProfile(CreateProfileRequest request, String createdBy);

    /**
     * 프로파일을 수정합니다.
     */
    EngineProfileInfo updateProfile(int profileId, UpdateProfileRequest request);

    /**
     * 프로파일을 삭제합니다.
     */
    void deleteProfile(int profileId);

    /**
     * 프로파일 목록을 조회합니다.
     */
    List<EngineProfileInfo> getAllProfiles();

    /**
     * 활성화된 프로파일만 조회합니다.
     */
    List<EngineProfileInfo> getEnabledProfiles();

    /**
     * 특정 프로파일을 조회합니다.
     */
    EngineProfileInfo getProfile(int profileId);

    /**
     * 기본 프로파일을 조회합니다.
     */
    EngineProfileInfo getDefaultProfile();

    /**
     * 기본 프로파일을 설정합니다.
     */
    void setDefaultProfile(int profileId);

    /**
     * 연결 테스트를 수행합니다.
     * 단계: DNS 해석 → TLS 검증 → 인증 확인 → 모델 가용성
     */
    ConnectionTestResult testConnection(int profileId);

    /**
     * 프로파일의 우선순위를 변경합니다.
     */
    void updatePriority(int profileId, int newPriority);

    // =========================================================================
    // DTOs
    // =========================================================================

    class CreateProfileRequest {
        private String profileName;
        private String engineType;
        private String endpointUrl;
        private String apiKey;
        private String defaultModel;
        private double temperature = 0.3;
        private int maxTokens = 2048;
        private int timeoutSeconds = 60;
        private int priority = 10;
        private String customHeaders;
        private String description;

        public String getProfileName() { return profileName; }
        public void setProfileName(String profileName) { this.profileName = profileName; }
        public String getEngineType() { return engineType; }
        public void setEngineType(String engineType) { this.engineType = engineType; }
        public String getEndpointUrl() { return endpointUrl; }
        public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getDefaultModel() { return defaultModel; }
        public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public String getCustomHeaders() { return customHeaders; }
        public void setCustomHeaders(String customHeaders) { this.customHeaders = customHeaders; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    class UpdateProfileRequest extends CreateProfileRequest {
        private Boolean enabled;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }

    class EngineProfileInfo {
        private int id;
        private String profileName;
        private String engineType;
        private String endpointUrl;
        private boolean hasApiKey;
        private String defaultModel;
        private double temperature;
        private int maxTokens;
        private int timeoutSeconds;
        private boolean enabled;
        private boolean defaultProfile;
        private int priority;
        private String lastTestResult;
        private long lastTestTime;
        private String lastTestDetails;
        private long lastTestLatencyMs;
        private String customHeaders;
        private String description;
        private long createdAt;
        private long updatedAt;
        private String createdBy;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getProfileName() { return profileName; }
        public void setProfileName(String profileName) { this.profileName = profileName; }
        public String getEngineType() { return engineType; }
        public void setEngineType(String engineType) { this.engineType = engineType; }
        public String getEndpointUrl() { return endpointUrl; }
        public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
        public boolean isHasApiKey() { return hasApiKey; }
        public void setHasApiKey(boolean hasApiKey) { this.hasApiKey = hasApiKey; }
        public String getDefaultModel() { return defaultModel; }
        public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isDefaultProfile() { return defaultProfile; }
        public void setDefaultProfile(boolean defaultProfile) { this.defaultProfile = defaultProfile; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public String getLastTestResult() { return lastTestResult; }
        public void setLastTestResult(String lastTestResult) { this.lastTestResult = lastTestResult; }
        public long getLastTestTime() { return lastTestTime; }
        public void setLastTestTime(long lastTestTime) { this.lastTestTime = lastTestTime; }
        public String getLastTestDetails() { return lastTestDetails; }
        public void setLastTestDetails(String lastTestDetails) { this.lastTestDetails = lastTestDetails; }
        public long getLastTestLatencyMs() { return lastTestLatencyMs; }
        public void setLastTestLatencyMs(long lastTestLatencyMs) { this.lastTestLatencyMs = lastTestLatencyMs; }
        public String getCustomHeaders() { return customHeaders; }
        public void setCustomHeaders(String customHeaders) { this.customHeaders = customHeaders; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    }

    class ConnectionTestResult {
        private boolean success;
        private long totalLatencyMs;
        private List<TestStep> steps;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public long getTotalLatencyMs() { return totalLatencyMs; }
        public void setTotalLatencyMs(long totalLatencyMs) { this.totalLatencyMs = totalLatencyMs; }
        public List<TestStep> getSteps() { return steps; }
        public void setSteps(List<TestStep> steps) { this.steps = steps; }
    }

    class TestStep {
        private String name;
        private boolean passed;
        private String message;
        private long durationMs;

        public TestStep() {}

        public TestStep(String name, boolean passed, String message, long durationMs) {
            this.name = name;
            this.passed = passed;
            this.message = message;
            this.durationMs = durationMs;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }
}
