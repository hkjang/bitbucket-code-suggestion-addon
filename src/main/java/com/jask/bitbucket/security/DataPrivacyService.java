package com.jask.bitbucket.security;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.jask.bitbucket.config.PluginSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 개인정보 보호 서비스.
 *
 * 기능:
 * 1. LLM 전송 전 PII(개인식별정보) 감지 및 마스킹
 * 2. 코드 내 민감 데이터 패턴 감지
 * 3. 데이터 보존 정책 관리
 * 4. 사용자 동의 확인
 */
@ExportAsService({DataPrivacyService.class})
@Named("dataPrivacyService")
public class DataPrivacyService {

    private static final Logger log = LoggerFactory.getLogger(DataPrivacyService.class);

    // --- PII 패턴 ---
    private static final Pattern[] PII_PATTERNS = {
            // 이메일
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z]{2,}\\b", Pattern.CASE_INSENSITIVE),
            // 전화번호 (한국)
            Pattern.compile("\\b0[12]0[-.]?\\d{3,4}[-.]?\\d{4}\\b"),
            // 전화번호 (국제)
            Pattern.compile("\\+\\d{1,3}[-.]?\\d{2,4}[-.]?\\d{3,4}[-.]?\\d{3,4}\\b"),
            // 주민등록번호 (한국)
            Pattern.compile("\\b\\d{6}[-]?[1-4]\\d{6}\\b"),
            // 신용카드 번호
            Pattern.compile("\\b(?:\\d{4}[-\\s]?){3}\\d{4}\\b"),
            // IP 주소 (의도적인 설정 코드와 구분하기 위해 문자열 리터럴 내에서만)
            Pattern.compile("\"\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\""),
            // 한국 사업자 등록번호
            Pattern.compile("\\b\\d{3}[-]?\\d{2}[-]?\\d{5}\\b"),
    };

    // --- 마스킹 레이블 ---
    private static final String[] PII_LABELS = {
            "[EMAIL_MASKED]",
            "[PHONE_MASKED]",
            "[PHONE_MASKED]",
            "[SSN_MASKED]",
            "[CARD_MASKED]",
            "[IP_MASKED]",
            "[BIZ_NO_MASKED]",
    };

    private final PluginSettingsService settingsService;

    @Inject
    public DataPrivacyService(PluginSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * 코드에서 PII를 감지하고 마스킹합니다.
     *
     * @param content 원본 코드 내용
     * @return 마스킹된 코드 내용
     */
    public String maskPii(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String result = content;
        for (int i = 0; i < PII_PATTERNS.length; i++) {
            result = PII_PATTERNS[i].matcher(result).replaceAll(PII_LABELS[i]);
        }

        return result;
    }

    /**
     * 코드에서 PII를 감지합니다 (마스킹 없이 감지만).
     *
     * @param content 코드 내용
     * @return 감지된 PII 목록
     */
    public List<PiiDetection> detectPii(String content) {
        List<PiiDetection> detections = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            return detections;
        }

        String[] piiTypes = {"EMAIL", "PHONE_KR", "PHONE_INTL", "SSN_KR", "CREDIT_CARD", "IP_ADDRESS", "BIZ_REG_NO"};

        for (int i = 0; i < PII_PATTERNS.length; i++) {
            Matcher matcher = PII_PATTERNS[i].matcher(content);
            while (matcher.find()) {
                PiiDetection detection = new PiiDetection();
                detection.setType(piiTypes[i]);
                detection.setPosition(matcher.start());
                // 마스킹된 값만 기록 (실제 값은 저장하지 않음)
                String matched = matcher.group();
                detection.setMaskedValue(matched.length() > 4 ?
                        matched.substring(0, 2) + "***" + matched.substring(matched.length() - 2) :
                        "***");
                detections.add(detection);
            }
        }

        return detections;
    }

    /**
     * LLM 전송 전 전체 데이터 정제 (SecretMasker + PII 마스킹).
     */
    public String sanitizeForLlm(String content) {
        if (content == null) return null;

        // Step 1: 시크릿 마스킹 (API 키, 비밀번호 등)
        String step1 = SecretMasker.mask(content);

        // Step 2: PII 마스킹
        return maskPii(step1);
    }

    /**
     * 데이터 보존 정책 정보를 반환합니다.
     */
    public DataRetentionPolicy getRetentionPolicy() {
        DataRetentionPolicy policy = new DataRetentionPolicy();

        // 분석 결과 보존 기간 (기본 90일)
        policy.setSuggestionRetentionDays(90);
        // 감사 로그 보존 기간 (기본 365일)
        policy.setAuditLogRetentionDays(365);
        // 분석 잡 로그 보존 기간 (기본 7일)
        policy.setJobRetentionDays(7);
        // LLM 전송 데이터 저장 여부 (기본: 저장하지 않음)
        policy.setStoreLlmPayloads(false);
        // 코드 컨텍스트 LLM 전송 여부 (기본: diff만 전송)
        policy.setSendFullFileContext(false);

        return policy;
    }

    /**
     * 개인정보 처리 방침 텍스트를 반환합니다.
     */
    public String getPrivacyPolicyText() {
        return "## AI 코드 제안 플러그인 개인정보 처리 방침\n\n" +
                "### 1. 수집하는 데이터\n" +
                "- Pull Request의 코드 변경사항 (diff)\n" +
                "- 분석 요청 사용자의 Bitbucket 사용자명\n" +
                "- 분석 결과 및 제안 내용\n\n" +
                "### 2. 데이터 사용 목적\n" +
                "- 코드 품질 개선 제안 생성\n" +
                "- 분석 결과 이력 관리\n" +
                "- 플러그인 성능 및 품질 모니터링\n\n" +
                "### 3. 외부 전송\n" +
                "- 코드 diff는 설정된 LLM 엔드포인트로 전송됩니다.\n" +
                "- 전송 전 시크릿 정보 및 개인식별정보(PII)가 자동 마스킹됩니다.\n" +
                "- TLS(HTTPS) 사용을 권장합니다.\n\n" +
                "### 4. 데이터 보존\n" +
                "- 분석 결과: 90일\n" +
                "- 감사 로그: 365일\n" +
                "- 분석 잡 로그: 7일\n" +
                "- LLM 요청/응답 원문: 저장하지 않음\n\n" +
                "### 5. 데이터 삭제\n" +
                "- 관리자는 언제든지 분석 결과를 삭제할 수 있습니다.\n" +
                "- PR 삭제 시 관련 분석 데이터가 자동 삭제됩니다.\n\n" +
                "### 6. 보안 조치\n" +
                "- API 키는 AES-256-GCM으로 암호화하여 저장\n" +
                "- SSRF 방지를 위한 엔드포인트 검증\n" +
                "- 코드 내 시크릿 자동 마스킹\n" +
                "- 역할 기반 접근 제어 (RBAC)\n";
    }

    // --- DTOs ---

    public static class PiiDetection {
        private String type;
        private int position;
        private String maskedValue;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }

        public String getMaskedValue() { return maskedValue; }
        public void setMaskedValue(String maskedValue) { this.maskedValue = maskedValue; }
    }

    public static class DataRetentionPolicy {
        private int suggestionRetentionDays;
        private int auditLogRetentionDays;
        private int jobRetentionDays;
        private boolean storeLlmPayloads;
        private boolean sendFullFileContext;

        public int getSuggestionRetentionDays() { return suggestionRetentionDays; }
        public void setSuggestionRetentionDays(int d) { this.suggestionRetentionDays = d; }

        public int getAuditLogRetentionDays() { return auditLogRetentionDays; }
        public void setAuditLogRetentionDays(int d) { this.auditLogRetentionDays = d; }

        public int getJobRetentionDays() { return jobRetentionDays; }
        public void setJobRetentionDays(int d) { this.jobRetentionDays = d; }

        public boolean isStoreLlmPayloads() { return storeLlmPayloads; }
        public void setStoreLlmPayloads(boolean b) { this.storeLlmPayloads = b; }

        public boolean isSendFullFileContext() { return sendFullFileContext; }
        public void setSendFullFileContext(boolean b) { this.sendFullFileContext = b; }
    }
}
