package com.jask.bitbucket.config;

/**
 * 프로젝트 단위 설정 서비스 인터페이스.
 * 전역 설정을 프로젝트별로 오버라이드할 수 있습니다.
 * 프로젝트 설정이 없으면 전역 설정이 적용됩니다.
 */
public interface ProjectSettingsService {

    /**
     * 프로젝트에서 플러그인이 활성화되어 있는지 확인합니다.
     */
    boolean isEnabledForProject(String projectKey);

    void setEnabledForProject(String projectKey, boolean enabled);

    /**
     * 프로젝트별 자동 분석 설정을 반환합니다.
     * 프로젝트 설정이 없으면 전역 설정을 따릅니다.
     */
    boolean isAutoAnalysisEnabled(String projectKey);

    void setAutoAnalysisEnabled(String projectKey, boolean enabled);

    /**
     * 프로젝트별 최소 신뢰도 임계값을 반환합니다.
     */
    double getMinConfidenceThreshold(String projectKey);

    void setMinConfidenceThreshold(String projectKey, double threshold);

    /**
     * 프로젝트별 제외 파일 패턴을 반환합니다.
     */
    String getExcludedFilePatterns(String projectKey);

    void setExcludedFilePatterns(String projectKey, String patterns);

    /**
     * 프로젝트별 지원 언어를 반환합니다.
     */
    String getSupportedLanguages(String projectKey);

    void setSupportedLanguages(String projectKey, String languages);

    /**
     * 프로젝트 설정을 초기화합니다 (전역 설정 따르기).
     */
    void resetToGlobal(String projectKey);

    /**
     * 프로젝트에 별도 설정이 있는지 확인합니다.
     */
    boolean hasProjectSettings(String projectKey);
}
