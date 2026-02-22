package com.jask.bitbucket.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 코드 내 비밀정보(API 키, 토큰, 비밀번호 등)를 마스킹합니다.
 * LLM에 전송하기 전에 반드시 이 클래스를 통해 마스킹해야 합니다.
 */
public final class SecretMasker {

    private static final Logger log = LoggerFactory.getLogger(SecretMasker.class);
    private static final String MASK = "[MASKED]";

    private static final List<PatternRule> PATTERNS;

    static {
        List<PatternRule> rules = new ArrayList<>();

        // === 비밀번호/시크릿 할당 패턴 ===
        rules.add(new PatternRule("password_assignment",
                Pattern.compile("(?i)(password|passwd|pwd|secret|api[_\\-]?key|access[_\\-]?key|private[_\\-]?key|auth[_\\-]?token|client[_\\-]?secret)\\s*[=:]\\s*['\"]([^'\"\\s]{4,})['\"]"),
                "$1 = " + MASK));

        // === Bearer 토큰 ===
        rules.add(new PatternRule("bearer_token",
                Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9\\-._~+/]{20,}=*"),
                "$1" + MASK));

        // === AWS Access Key ID ===
        rules.add(new PatternRule("aws_access_key",
                Pattern.compile("AKIA[0-9A-Z]{16}"),
                MASK));

        // === AWS Secret Key ===
        rules.add(new PatternRule("aws_secret_key",
                Pattern.compile("(?i)(aws[_\\-]?secret[_\\-]?access[_\\-]?key\\s*[=:]\\s*['\"]?)([A-Za-z0-9/+=]{40})"),
                "$1" + MASK));

        // === GitHub Personal Access Token ===
        rules.add(new PatternRule("github_pat",
                Pattern.compile("gh[ps]_[A-Za-z0-9]{36,}"),
                MASK));

        // === GitLab Token ===
        rules.add(new PatternRule("gitlab_token",
                Pattern.compile("glpat-[A-Za-z0-9\\-]{20,}"),
                MASK));

        // === Generic API Key patterns ===
        rules.add(new PatternRule("generic_api_key",
                Pattern.compile("(?i)(api[_\\-]?key|apikey|api_secret)\\s*[=:]\\s*['\"]?([A-Za-z0-9\\-._]{16,})['\"]?"),
                "$1 = " + MASK));

        // === JWT Token ===
        rules.add(new PatternRule("jwt_token",
                Pattern.compile("eyJ[A-Za-z0-9\\-_]+\\.eyJ[A-Za-z0-9\\-_]+\\.[A-Za-z0-9\\-_.+/=]+"),
                MASK));

        // === Private Key Block ===
        rules.add(new PatternRule("private_key",
                Pattern.compile("(?s)-----BEGIN\\s+(RSA\\s+|EC\\s+|DSA\\s+|OPENSSH\\s+)?PRIVATE\\s+KEY-----.*?-----END\\s+(RSA\\s+|EC\\s+|DSA\\s+|OPENSSH\\s+)?PRIVATE\\s+KEY-----"),
                MASK));

        // === Connection String ===
        rules.add(new PatternRule("connection_string",
                Pattern.compile("(?i)(jdbc:|mongodb(\\+srv)?://|redis://|amqp://|mysql://|postgresql://)[^\\s'\"]{10,}"),
                "$1" + MASK));

        // === Slack Token ===
        rules.add(new PatternRule("slack_token",
                Pattern.compile("xox[bpors]-[0-9A-Za-z\\-]{10,}"),
                MASK));

        // === IP + Port 형태의 내부 서버 주소 (프라이빗 네트워크) ===
        rules.add(new PatternRule("internal_ip",
                Pattern.compile("(?:10\\.|172\\.(?:1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.)[0-9.]+:[0-9]{2,5}"),
                MASK));

        PATTERNS = Collections.unmodifiableList(rules);
    }

    private SecretMasker() {
        // utility class
    }

    /**
     * 주어진 텍스트 내의 비밀정보를 마스킹합니다.
     *
     * @param content 원문 텍스트 (코드, diff 등)
     * @return 마스킹된 텍스트
     */
    public static String mask(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String masked = content;
        int totalMasked = 0;

        for (PatternRule rule : PATTERNS) {
            Matcher matcher = rule.pattern.matcher(masked);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            if (count > 0) {
                masked = rule.pattern.matcher(masked).replaceAll(rule.replacement);
                totalMasked += count;
            }
        }

        if (totalMasked > 0) {
            log.info("시크릿 마스킹 완료: {} 건 마스킹 적용", totalMasked);
        }

        return masked;
    }

    /**
     * 주어진 텍스트에 비밀정보가 포함되어 있는지 감지합니다.
     *
     * @param content 검사할 텍스트
     * @return 비밀정보 감지 시 true
     */
    public static boolean containsSecrets(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        for (PatternRule rule : PATTERNS) {
            if (rule.pattern.matcher(content).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 감지된 시크릿 유형 목록을 반환합니다.
     *
     * @param content 검사할 텍스트
     * @return 감지된 패턴 이름 목록
     */
    public static List<String> detectSecretTypes(String content) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> detected = new ArrayList<>();
        for (PatternRule rule : PATTERNS) {
            if (rule.pattern.matcher(content).find()) {
                detected.add(rule.name);
            }
        }
        return detected;
    }

    private static class PatternRule {
        final String name;
        final Pattern pattern;
        final String replacement;

        PatternRule(String name, Pattern pattern, String replacement) {
            this.name = name;
            this.pattern = pattern;
            this.replacement = replacement;
        }
    }
}
