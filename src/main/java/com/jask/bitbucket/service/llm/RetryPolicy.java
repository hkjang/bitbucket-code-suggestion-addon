package com.jask.bitbucket.service.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM 호출 재시도 정책.
 * 지수 백오프(exponential backoff)를 사용합니다.
 */
public class RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

    private final int maxRetries;
    private final long initialDelayMs;
    private final double backoffMultiplier;

    public RetryPolicy(int maxRetries, long initialDelayMs, double backoffMultiplier) {
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.backoffMultiplier = backoffMultiplier;
    }

    /**
     * 재시도 가능한 HTTP 상태 코드인지 확인합니다.
     */
    public boolean isRetryable(int statusCode) {
        // 429 Too Many Requests, 502 Bad Gateway, 503 Service Unavailable, 504 Gateway Timeout
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    /**
     * 재시도 가능한 예외인지 확인합니다.
     */
    public boolean isRetryable(Exception e) {
        // IOException 계열은 재시도 가능 (네트워크 오류)
        return e instanceof java.io.IOException;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * n번째 재시도의 대기 시간을 반환합니다.
     */
    public long getDelayMs(int attempt) {
        return (long) (initialDelayMs * Math.pow(backoffMultiplier, attempt));
    }

    /**
     * 재시도 전 대기합니다.
     */
    public void waitBeforeRetry(int attempt) {
        long delay = getDelayMs(attempt);
        log.info("재시도 대기: attempt={}, delay={}ms", attempt + 1, delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
