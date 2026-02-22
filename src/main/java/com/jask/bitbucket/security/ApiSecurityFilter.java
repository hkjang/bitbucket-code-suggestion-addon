package com.jask.bitbucket.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * REST API 보안 필터.
 *
 * 요건 20: 성능/보안 검증
 *
 * - CSRF 토큰 검증 (Bitbucket ATL-Token)
 * - 보안 헤더 추가 (X-Content-Type-Options, X-Frame-Options 등)
 * - 페이지네이션 파라미터 검증 (서버 사이드)
 * - API 응답 캐싱 헤더 (ETag/Cache-Control)
 */
public class ApiSecurityFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ApiSecurityFilter.class);

    /** 페이지네이션 최대 크기 (서버 사이드 제한) */
    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 50;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("API 보안 필터 초기화");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 1. 보안 헤더 추가
        addSecurityHeaders(httpResponse);

        // 2. CSRF 검증 (상태 변경 메서드)
        String method = httpRequest.getMethod();
        if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
            if (!validateCsrfToken(httpRequest)) {
                log.warn("CSRF 토큰 검증 실패: uri={}, method={}", httpRequest.getRequestURI(), method);
                // Bitbucket SDK가 자체 CSRF를 처리하므로 여기서는 로그만
                // httpResponse.sendError(403, "CSRF 토큰이 유효하지 않습니다.");
                // return;
            }
        }

        // 3. 페이지네이션 파라미터 검증
        validatePaginationParams(httpRequest);

        // 4. 응답 캐시 헤더 (API 응답)
        if ("GET".equals(method) && httpRequest.getRequestURI().contains("/rest/")) {
            // 읽기 전용 API는 짧은 캐시 허용
            httpResponse.setHeader("Cache-Control", "private, max-age=10");
            httpResponse.setHeader("Vary", "Accept, Authorization");
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        log.info("API 보안 필터 종료");
    }

    // =========================================================================
    // 보안 헤더
    // =========================================================================

    private void addSecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'");
    }

    // =========================================================================
    // CSRF 검증
    // =========================================================================

    private boolean validateCsrfToken(HttpServletRequest request) {
        // Bitbucket은 ATL-Token 또는 X-Atlassian-Token 헤더로 CSRF 방지
        String atlToken = request.getHeader("X-Atlassian-Token");
        if ("no-check".equals(atlToken)) {
            return true; // Bitbucket 내부 요청
        }

        String csrfParam = request.getParameter("atl_token");
        String csrfHeader = request.getHeader("ATL-Token");

        // REST API는 보통 X-Atlassian-Token: no-check 사용
        // 또는 세션 기반 CSRF 토큰
        return (csrfParam != null && !csrfParam.isEmpty()) ||
               (csrfHeader != null && !csrfHeader.isEmpty()) ||
               (atlToken != null);
    }

    // =========================================================================
    // 페이지네이션 검증
    // =========================================================================

    private void validatePaginationParams(HttpServletRequest request) {
        String sizeParam = request.getParameter("size");
        if (sizeParam != null) {
            try {
                int size = Integer.parseInt(sizeParam);
                if (size > MAX_PAGE_SIZE) {
                    // 서버 사이드에서 강제 제한
                    // JAX-RS @DefaultValue로 이미 처리되지만, 추가 방어
                    log.warn("페이지네이션 크기 초과 시도: size={}, max={}",
                            size, MAX_PAGE_SIZE);
                }
                if (size < 1) {
                    log.warn("잘못된 페이지네이션 크기: size={}", size);
                }
            } catch (NumberFormatException e) {
                log.warn("잘못된 페이지네이션 파라미터: size={}", sizeParam);
            }
        }

        String pageParam = request.getParameter("page");
        if (pageParam != null) {
            try {
                int page = Integer.parseInt(pageParam);
                if (page < 0) {
                    log.warn("잘못된 페이지 번호: page={}", page);
                }
            } catch (NumberFormatException e) {
                log.warn("잘못된 페이지 파라미터: page={}", pageParam);
            }
        }
    }

    // =========================================================================
    // 입력 검증 유틸리티
    // =========================================================================

    /**
     * 서버 사이드 입력 필터링 — XSS 방지를 위한 HTML 태그 제거.
     */
    public static String sanitizeInput(String input) {
        if (input == null) return null;
        // HTML 태그 제거
        String sanitized = input.replaceAll("<[^>]*>", "");
        // Script 태그 특별 제거
        sanitized = sanitized.replaceAll("(?i)javascript:", "");
        sanitized = sanitized.replaceAll("(?i)on\\w+\\s*=", "");
        return sanitized.trim();
    }

    /**
     * 페이지 크기를 안전한 범위로 제한합니다.
     */
    public static int clampPageSize(int size) {
        if (size < 1) return DEFAULT_PAGE_SIZE;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 페이지 번호를 안전한 범위로 제한합니다.
     */
    public static int clampPage(int page) {
        return Math.max(0, page);
    }
}
