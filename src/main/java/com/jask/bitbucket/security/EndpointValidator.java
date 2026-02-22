package com.jask.bitbucket.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * LLM 엔드포인트 URL의 SSRF 방지 검증.
 * 내부 네트워크, 메타데이터 서비스, 로컬호스트(설정에 따라 허용) 등을 차단합니다.
 */
public final class EndpointValidator {

    private static final Logger log = LoggerFactory.getLogger(EndpointValidator.class);

    private static final Set<String> ALLOWED_SCHEMES = new HashSet<>(Arrays.asList("http", "https"));

    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
            "169.254.169.254",  // AWS/GCP metadata
            "metadata.google.internal",
            "metadata.internal",
            "100.100.100.200"   // Alibaba Cloud metadata
    ));

    private static final int[] BLOCKED_PORTS = {22, 25, 53, 445, 3389, 5432, 3306, 6379, 27017};

    private EndpointValidator() {}

    /**
     * 엔드포인트 URL을 검증합니다.
     *
     * @param endpoint 검증할 URL 문자열
     * @param allowLocalhost localhost 허용 여부 (개발/Ollama 용)
     * @return 검증 결과
     */
    public static ValidationResult validate(String endpoint, boolean allowLocalhost) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return ValidationResult.fail("엔드포인트 URL이 비어있습니다.");
        }

        URL url;
        try {
            url = new URL(endpoint.trim());
        } catch (MalformedURLException e) {
            return ValidationResult.fail("유효하지 않은 URL 형식입니다: " + e.getMessage());
        }

        // 스키마 검증
        String scheme = url.getProtocol().toLowerCase();
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            return ValidationResult.fail("허용되지 않는 프로토콜입니다: " + scheme + " (http/https만 허용)");
        }

        String host = url.getHost().toLowerCase();

        // 빈 호스트 차단
        if (host.isEmpty()) {
            return ValidationResult.fail("호스트가 지정되지 않았습니다.");
        }

        // 메타데이터 서비스 차단
        if (BLOCKED_HOSTS.contains(host)) {
            return ValidationResult.fail("차단된 호스트입니다: " + host);
        }

        // 포트 검증
        int port = url.getPort();
        if (port > 0) {
            for (int blockedPort : BLOCKED_PORTS) {
                if (port == blockedPort) {
                    return ValidationResult.fail("차단된 포트입니다: " + port);
                }
            }
        }

        // localhost/loopback 검증
        if (!allowLocalhost) {
            if (isLoopback(host)) {
                return ValidationResult.fail("localhost/loopback 주소는 허용되지 않습니다.");
            }
        }

        // 내부 네트워크 IP 차단 (localhost 허용과 별개)
        if (isInternalNetwork(host) && !isLoopback(host)) {
            return ValidationResult.fail("내부 네트워크 주소는 허용되지 않습니다: " + host);
        }

        // DNS rebinding 방지를 위한 IP 해석 검증
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (BLOCKED_HOSTS.contains(addr.getHostAddress())) {
                    return ValidationResult.fail("DNS가 차단된 IP로 해석됩니다: " + addr.getHostAddress());
                }
                if (!allowLocalhost && addr.isLoopbackAddress()) {
                    return ValidationResult.fail("DNS가 loopback으로 해석됩니다.");
                }
                if (addr.isLinkLocalAddress()) {
                    return ValidationResult.fail("링크-로컬 주소로 해석됩니다.");
                }
            }
        } catch (UnknownHostException e) {
            // DNS 해석 실패는 허용 (네트워크 문제일 수 있음)
            log.warn("엔드포인트 DNS 해석 실패 (설정은 허용): {}", host);
        }

        return ValidationResult.ok();
    }

    /**
     * TLS가 적용되었는지 확인합니다.
     */
    public static boolean isTlsEndpoint(String endpoint) {
        return endpoint != null && endpoint.trim().toLowerCase().startsWith("https://");
    }

    private static boolean isLoopback(String host) {
        return "localhost".equals(host) ||
               "127.0.0.1".equals(host) ||
               "::1".equals(host) ||
               host.startsWith("127.");
    }

    private static boolean isInternalNetwork(String host) {
        // RFC 1918 사설 IP 범위
        if (host.startsWith("10.")) return true;
        if (host.startsWith("192.168.")) return true;
        if (host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*")) return true;
        // RFC 6598 Carrier-grade NAT
        if (host.startsWith("100.64.") || host.matches("100\\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\\..*")) return true;
        return false;
    }

    /**
     * 검증 결과를 나타내는 레코드.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, "유효한 엔드포인트입니다.");
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }
}
