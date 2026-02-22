package com.jask.bitbucket.service;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 마스킹 규칙 편집 서비스.
 *
 * 요건 9: 마스킹 규칙 편집 UI — 정규식 패턴 + 치환 텍스트 관리
 * 요건 10: 데이터 최소화 — LLM에 전송하는 코드의 민감 정보 제거
 *
 * SAL PluginSettings에 JSON 형태로 마스킹 규칙을 저장합니다.
 */
@ExportAsService({MaskingRuleService.class})
@Named("maskingRuleService")
public class MaskingRuleService {

    private static final Logger log = LoggerFactory.getLogger(MaskingRuleService.class);
    private static final String SETTINGS_KEY = "com.jask.bitbucket.masking.rules";
    private static final String PLUGIN_KEY = "com.jask.bitbucket.code-suggestion-addon";

    private final PluginSettingsFactory pluginSettingsFactory;
    private final Gson gson;

    /** 기본 내장 규칙 */
    private static final List<MaskingRule> DEFAULT_RULES = new ArrayList<>();

    static {
        DEFAULT_RULES.add(new MaskingRule("builtin_email", "이메일 주소",
                "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "[EMAIL_MASKED]",
                true, true, "PII"));
        DEFAULT_RULES.add(new MaskingRule("builtin_phone_kr", "한국 전화번호",
                "0[1-9][0-9]-[0-9]{3,4}-[0-9]{4}", "[PHONE_MASKED]",
                true, true, "PII"));
        DEFAULT_RULES.add(new MaskingRule("builtin_api_key", "API 키 패턴",
                "(?i)(api[_-]?key|apikey|api_secret)\\s*[:=]\\s*['\"]?[a-zA-Z0-9_\\-]{16,}",
                "[API_KEY_MASKED]", true, true, "SECRET"));
        DEFAULT_RULES.add(new MaskingRule("builtin_password", "비밀번호 필드",
                "(?i)(password|passwd|pwd)\\s*[:=]\\s*['\"]?[^'\"\\s]+",
                "[PASSWORD_MASKED]", true, true, "SECRET"));
        DEFAULT_RULES.add(new MaskingRule("builtin_jwt", "JWT 토큰",
                "eyJ[a-zA-Z0-9_-]+\\.eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+",
                "[JWT_MASKED]", true, true, "SECRET"));
        DEFAULT_RULES.add(new MaskingRule("builtin_private_key", "개인 키",
                "-----BEGIN (?:RSA |EC |DSA )?PRIVATE KEY-----",
                "[PRIVATE_KEY_MASKED]", true, true, "SECRET"));
        DEFAULT_RULES.add(new MaskingRule("builtin_ip", "IP 주소",
                "\\b(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b",
                "[IP_MASKED]", true, true, "PII"));
        DEFAULT_RULES.add(new MaskingRule("builtin_ssn_kr", "주민등록번호",
                "\\d{6}-[1-4]\\d{6}", "[SSN_MASKED]",
                true, true, "PII"));
    }

    @Inject
    public MaskingRuleService(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.gson = new Gson();
    }

    /**
     * 모든 마스킹 규칙을 조회합니다 (내장 + 커스텀).
     */
    public List<MaskingRule> getAllRules() {
        List<MaskingRule> all = new ArrayList<>(DEFAULT_RULES);
        all.addAll(getCustomRules());
        return all;
    }

    /**
     * 활성화된 마스킹 규칙만 조회합니다.
     */
    public List<MaskingRule> getEnabledRules() {
        List<MaskingRule> enabled = new ArrayList<>();
        for (MaskingRule rule : getAllRules()) {
            if (rule.isEnabled()) {
                enabled.add(rule);
            }
        }
        return enabled;
    }

    /**
     * 커스텀 규칙을 추가합니다.
     */
    public MaskingRule addCustomRule(String name, String pattern, String replacement,
                                     String category) {
        // 정규식 유효성 검증
        validatePattern(pattern);

        String id = "custom_" + System.currentTimeMillis();
        MaskingRule rule = new MaskingRule(id, name, pattern, replacement, true, false, category);

        List<MaskingRule> customs = getCustomRules();
        customs.add(rule);
        saveCustomRules(customs);

        log.info("커스텀 마스킹 규칙 추가: id={}, name={}, pattern={}", id, name, pattern);
        return rule;
    }

    /**
     * 커스텀 규칙을 수정합니다.
     */
    public MaskingRule updateCustomRule(String ruleId, String name, String pattern,
                                        String replacement, boolean enabled, String category) {
        if (ruleId.startsWith("builtin_")) {
            throw new IllegalArgumentException("내장 규칙은 수정할 수 없습니다: " + ruleId);
        }
        validatePattern(pattern);

        List<MaskingRule> customs = getCustomRules();
        for (int i = 0; i < customs.size(); i++) {
            if (customs.get(i).getId().equals(ruleId)) {
                MaskingRule updated = new MaskingRule(ruleId, name, pattern, replacement,
                        enabled, false, category);
                customs.set(i, updated);
                saveCustomRules(customs);
                log.info("마스킹 규칙 수정: id={}", ruleId);
                return updated;
            }
        }
        throw new IllegalArgumentException("규칙을 찾을 수 없습니다: " + ruleId);
    }

    /**
     * 커스텀 규칙을 삭제합니다.
     */
    public void deleteCustomRule(String ruleId) {
        if (ruleId.startsWith("builtin_")) {
            throw new IllegalArgumentException("내장 규칙은 삭제할 수 없습니다: " + ruleId);
        }

        List<MaskingRule> customs = getCustomRules();
        customs.removeIf(r -> r.getId().equals(ruleId));
        saveCustomRules(customs);
        log.info("마스킹 규칙 삭제: id={}", ruleId);
    }

    /**
     * 패턴 테스트 — 입력 텍스트에 규칙을 적용한 결과를 미리보기합니다.
     */
    public PatternTestResult testPattern(String pattern, String replacement, String testInput) {
        PatternTestResult result = new PatternTestResult();
        try {
            Pattern compiled = Pattern.compile(pattern);
            java.util.regex.Matcher matcher = compiled.matcher(testInput);

            List<String> matches = new ArrayList<>();
            while (matcher.find()) {
                matches.add(matcher.group());
            }

            String masked = compiled.matcher(testInput).replaceAll(replacement);

            result.setValid(true);
            result.setMatchCount(matches.size());
            result.setMatches(matches);
            result.setMaskedOutput(masked);
        } catch (PatternSyntaxException e) {
            result.setValid(false);
            result.setError("잘못된 정규식: " + e.getMessage());
        }
        return result;
    }

    /**
     * 텍스트에 모든 활성 규칙을 적용합니다.
     */
    public String applyMasking(String text) {
        if (text == null || text.isEmpty()) return text;

        String result = text;
        for (MaskingRule rule : getEnabledRules()) {
            try {
                result = Pattern.compile(rule.getPattern()).matcher(result)
                        .replaceAll(rule.getReplacement());
            } catch (Exception e) {
                log.warn("마스킹 규칙 적용 실패: rule={}, error={}", rule.getId(), e.getMessage());
            }
        }
        return result;
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    private List<MaskingRule> getCustomRules() {
        try {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            String json = (String) settings.get(SETTINGS_KEY);
            if (json != null && !json.isEmpty()) {
                return gson.fromJson(json, new TypeToken<List<MaskingRule>>(){}.getType());
            }
        } catch (Exception e) {
            log.error("커스텀 마스킹 규칙 로드 실패: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    private void saveCustomRules(List<MaskingRule> rules) {
        try {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            settings.put(SETTINGS_KEY, gson.toJson(rules));
        } catch (Exception e) {
            log.error("커스텀 마스킹 규칙 저장 실패: {}", e.getMessage());
            throw new RuntimeException("마스킹 규칙 저장 실패", e);
        }
    }

    private void validatePattern(String pattern) {
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("잘못된 정규식 패턴: " + e.getMessage());
        }
    }

    // =========================================================================
    // DTOs
    // =========================================================================

    public static class MaskingRule {
        private String id;
        private String name;
        private String pattern;
        private String replacement;
        private boolean enabled;
        private boolean builtIn;
        private String category; // PII, SECRET, CUSTOM

        public MaskingRule() {}

        public MaskingRule(String id, String name, String pattern, String replacement,
                           boolean enabled, boolean builtIn, String category) {
            this.id = id;
            this.name = name;
            this.pattern = pattern;
            this.replacement = replacement;
            this.enabled = enabled;
            this.builtIn = builtIn;
            this.category = category;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public String getReplacement() { return replacement; }
        public void setReplacement(String replacement) { this.replacement = replacement; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isBuiltIn() { return builtIn; }
        public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }

    public static class PatternTestResult {
        private boolean valid;
        private int matchCount;
        private List<String> matches;
        private String maskedOutput;
        private String error;

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public int getMatchCount() { return matchCount; }
        public void setMatchCount(int matchCount) { this.matchCount = matchCount; }
        public List<String> getMatches() { return matches; }
        public void setMatches(List<String> matches) { this.matches = matches; }
        public String getMaskedOutput() { return maskedOutput; }
        public void setMaskedOutput(String maskedOutput) { this.maskedOutput = maskedOutput; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
