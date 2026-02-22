package com.jask.bitbucket.service;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.*;

/**
 * 정책 템플릿 서비스.
 *
 * 요건 8: 정책 템플릿
 *
 * 사전 정의된 분석 정책 템플릿을 제공하여
 * 프로젝트별 빠른 설정을 지원합니다.
 */
@ExportAsService({PolicyTemplateService.class})
@Named("policyTemplateService")
public class PolicyTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PolicyTemplateService.class);

    /** 내장 템플릿 목록 */
    private static final Map<String, PolicyTemplate> BUILT_IN_TEMPLATES = new LinkedHashMap<>();

    static {
        // 기본 템플릿
        PolicyTemplate standard = new PolicyTemplate();
        standard.setId("standard");
        standard.setName("표준 (Standard)");
        standard.setDescription("일반적인 코드 리뷰에 적합한 기본 설정");
        standard.setBuiltIn(true);
        Map<String, Object> stdSettings = new LinkedHashMap<>();
        stdSettings.put("autoAnalysisEnabled", true);
        stdSettings.put("mergeCheckEnabled", false);
        stdSettings.put("minConfidenceThreshold", 0.6);
        stdSettings.put("maxFilesPerAnalysis", 20);
        stdSettings.put("maxFileSizeKb", 500);
        stdSettings.put("excludedFilePatterns", "*.min.js,*.min.css,*.lock,*.sum");
        stdSettings.put("supportedLanguages", "java,javascript,typescript,python,go,kotlin");
        standard.setSettings(stdSettings);
        BUILT_IN_TEMPLATES.put("standard", standard);

        // 엄격 템플릿
        PolicyTemplate strict = new PolicyTemplate();
        strict.setId("strict");
        strict.setName("엄격 (Strict)");
        strict.setDescription("높은 품질 기준이 필요한 프로젝트용. 머지 체크 활성화, 높은 신뢰도 임계값.");
        strict.setBuiltIn(true);
        Map<String, Object> strictSettings = new LinkedHashMap<>();
        strictSettings.put("autoAnalysisEnabled", true);
        strictSettings.put("mergeCheckEnabled", true);
        strictSettings.put("mergeCheckMaxCritical", 0);
        strictSettings.put("minConfidenceThreshold", 0.8);
        strictSettings.put("maxFilesPerAnalysis", 30);
        strictSettings.put("maxFileSizeKb", 300);
        strictSettings.put("excludedFilePatterns", "*.min.js,*.min.css,*.lock,*.sum,*.map");
        strictSettings.put("supportedLanguages", "java,javascript,typescript,python,go,kotlin,rust,c,cpp");
        strict.setSettings(strictSettings);
        BUILT_IN_TEMPLATES.put("strict", strict);

        // 경량 템플릿
        PolicyTemplate light = new PolicyTemplate();
        light.setId("light");
        light.setName("경량 (Light)");
        light.setDescription("리소스를 최소화하는 가벼운 분석. 수동 분석만, 핵심 파일만.");
        light.setBuiltIn(true);
        Map<String, Object> lightSettings = new LinkedHashMap<>();
        lightSettings.put("autoAnalysisEnabled", false);
        lightSettings.put("mergeCheckEnabled", false);
        lightSettings.put("minConfidenceThreshold", 0.7);
        lightSettings.put("maxFilesPerAnalysis", 10);
        lightSettings.put("maxFileSizeKb", 200);
        lightSettings.put("excludedFilePatterns", "*.min.js,*.min.css,*.lock,*.sum,*.map,*.test.*,*_test.*,*.spec.*");
        lightSettings.put("supportedLanguages", "java,python,javascript");
        light.setSettings(lightSettings);
        BUILT_IN_TEMPLATES.put("light", light);

        // 보안 중심 템플릿
        PolicyTemplate security = new PolicyTemplate();
        security.setId("security");
        security.setName("보안 중심 (Security-focused)");
        security.setDescription("보안 취약점 검출에 집중하는 설정. PII 마스킹 강화, 높은 신뢰도.");
        security.setBuiltIn(true);
        Map<String, Object> secSettings = new LinkedHashMap<>();
        secSettings.put("autoAnalysisEnabled", true);
        secSettings.put("mergeCheckEnabled", true);
        secSettings.put("mergeCheckMaxCritical", 0);
        secSettings.put("minConfidenceThreshold", 0.85);
        secSettings.put("maxFilesPerAnalysis", 15);
        secSettings.put("maxFileSizeKb", 300);
        secSettings.put("excludedFilePatterns", "*.min.js,*.min.css,*.lock,*.sum,*.test.*");
        secSettings.put("supportedLanguages", "java,javascript,typescript,python,go,kotlin,rust");
        secSettings.put("enablePiiMasking", true);
        secSettings.put("enableSecretMasking", true);
        security.setSettings(secSettings);
        BUILT_IN_TEMPLATES.put("security", security);
    }

    /** 커스텀 템플릿 (인메모리, 향후 AO 확장 가능) */
    private final Map<String, PolicyTemplate> customTemplates = new LinkedHashMap<>();

    /**
     * 모든 템플릿을 조회합니다 (내장 + 커스텀).
     */
    public List<PolicyTemplate> getAllTemplates() {
        List<PolicyTemplate> all = new ArrayList<>(BUILT_IN_TEMPLATES.values());
        all.addAll(customTemplates.values());
        return all;
    }

    /**
     * 특정 템플릿을 조회합니다.
     */
    public PolicyTemplate getTemplate(String templateId) {
        PolicyTemplate template = BUILT_IN_TEMPLATES.get(templateId);
        if (template == null) {
            template = customTemplates.get(templateId);
        }
        return template;
    }

    /**
     * 커스텀 템플릿을 생성합니다.
     */
    public PolicyTemplate createCustomTemplate(String name, String description,
                                                Map<String, Object> settings) {
        String id = "custom_" + System.currentTimeMillis();
        PolicyTemplate template = new PolicyTemplate();
        template.setId(id);
        template.setName(name);
        template.setDescription(description);
        template.setBuiltIn(false);
        template.setSettings(settings);
        customTemplates.put(id, template);

        log.info("커스텀 정책 템플릿 생성: id={}, name={}", id, name);
        return template;
    }

    /**
     * 커스텀 템플릿을 삭제합니다.
     */
    public void deleteTemplate(String templateId) {
        if (BUILT_IN_TEMPLATES.containsKey(templateId)) {
            throw new IllegalArgumentException("내장 템플릿은 삭제할 수 없습니다: " + templateId);
        }
        customTemplates.remove(templateId);
    }

    // =========================================================================
    // DTO
    // =========================================================================

    public static class PolicyTemplate {
        private String id;
        private String name;
        private String description;
        private boolean builtIn;
        private Map<String, Object> settings;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isBuiltIn() { return builtIn; }
        public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }
        public Map<String, Object> getSettings() { return settings; }
        public void setSettings(Map<String, Object> settings) { this.settings = settings; }
    }
}
