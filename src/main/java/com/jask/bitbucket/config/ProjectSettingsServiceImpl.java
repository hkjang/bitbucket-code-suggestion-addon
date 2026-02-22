package com.jask.bitbucket.config;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * 프로젝트 단위 설정 서비스 구현.
 * SAL PluginSettings를 사용하며, 프로젝트별 네임스페이스로 설정을 분리합니다.
 */
@ExportAsService({ProjectSettingsService.class})
@Named("projectSettingsService")
public class ProjectSettingsServiceImpl implements ProjectSettingsService {

    private static final Logger log = LoggerFactory.getLogger(ProjectSettingsServiceImpl.class);
    private static final String PROJECT_PREFIX = "com.jask.bitbucket.code-suggestion.project.";

    private final PluginSettingsFactory pluginSettingsFactory;
    private final PluginSettingsService globalSettings;

    @Inject
    public ProjectSettingsServiceImpl(@ComponentImport PluginSettingsFactory pluginSettingsFactory,
                                       PluginSettingsService globalSettings) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.globalSettings = globalSettings;
    }

    private PluginSettings getSettings() {
        return pluginSettingsFactory.createGlobalSettings();
    }

    private String projectKey(String projectKey, String key) {
        return PROJECT_PREFIX + projectKey + "." + key;
    }

    private String get(String projectKey, String key) {
        return (String) getSettings().get(projectKey(projectKey, key));
    }

    private void set(String projectKey, String key, String value) {
        getSettings().put(projectKey(projectKey, key), value);
    }

    private void remove(String projectKey, String key) {
        getSettings().remove(projectKey(projectKey, key));
    }

    @Override
    public boolean isEnabledForProject(String projectKey) {
        String val = get(projectKey, "enabled");
        return val == null || Boolean.parseBoolean(val); // 기본 활성화
    }

    @Override
    public void setEnabledForProject(String projectKey, boolean enabled) {
        set(projectKey, "enabled", String.valueOf(enabled));
    }

    @Override
    public boolean isAutoAnalysisEnabled(String projectKey) {
        String val = get(projectKey, "autoAnalysis");
        if (val == null) {
            return globalSettings.isAutoAnalysisEnabled(); // fallback to global
        }
        return Boolean.parseBoolean(val);
    }

    @Override
    public void setAutoAnalysisEnabled(String projectKey, boolean enabled) {
        set(projectKey, "autoAnalysis", String.valueOf(enabled));
    }

    @Override
    public double getMinConfidenceThreshold(String projectKey) {
        String val = get(projectKey, "minConfidence");
        if (val == null) {
            return globalSettings.getMinConfidenceThreshold();
        }
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return globalSettings.getMinConfidenceThreshold();
        }
    }

    @Override
    public void setMinConfidenceThreshold(String projectKey, double threshold) {
        set(projectKey, "minConfidence", String.valueOf(threshold));
    }

    @Override
    public String getExcludedFilePatterns(String projectKey) {
        String val = get(projectKey, "excludedPatterns");
        return val != null ? val : globalSettings.getExcludedFilePatterns();
    }

    @Override
    public void setExcludedFilePatterns(String projectKey, String patterns) {
        set(projectKey, "excludedPatterns", patterns);
    }

    @Override
    public String getSupportedLanguages(String projectKey) {
        String val = get(projectKey, "supportedLanguages");
        return val != null ? val : globalSettings.getSupportedLanguages();
    }

    @Override
    public void setSupportedLanguages(String projectKey, String languages) {
        set(projectKey, "supportedLanguages", languages);
    }

    @Override
    public void resetToGlobal(String projectKey) {
        remove(projectKey, "enabled");
        remove(projectKey, "autoAnalysis");
        remove(projectKey, "minConfidence");
        remove(projectKey, "excludedPatterns");
        remove(projectKey, "supportedLanguages");
        log.info("프로젝트 {} 설정 초기화 (전역 설정 따르기)", projectKey);
    }

    @Override
    public boolean hasProjectSettings(String projectKey) {
        // 프로젝트별로 하나라도 설정이 있으면 true
        return get(projectKey, "enabled") != null
                || get(projectKey, "autoAnalysis") != null
                || get(projectKey, "minConfidence") != null
                || get(projectKey, "excludedPatterns") != null
                || get(projectKey, "supportedLanguages") != null;
    }
}
