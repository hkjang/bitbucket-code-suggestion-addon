package com.jask.bitbucket.service.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 간단한 회로차단기 (Circuit Breaker) 구현.
 *
 * 상태 전이:
 * CLOSED (정상) -> 연속 실패 >= threshold -> OPEN (차단)
 * OPEN -> resetTimeout 경과 -> HALF_OPEN (시험)
 * HALF_OPEN -> 성공 -> CLOSED
 * HALF_OPEN -> 실패 -> OPEN
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final int failureThreshold;
    private final long resetTimeoutMs;

    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    public CircuitBreaker(String name, int failureThreshold, long resetTimeoutMs) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
    }

    /**
     * 요청을 허용할 수 있는지 확인합니다.
     */
    public boolean allowRequest() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                if (System.currentTimeMillis() - lastFailureTime.get() > resetTimeoutMs) {
                    state = State.HALF_OPEN;
                    log.info("회로차단기 [{}] HALF_OPEN 전환 (시험 요청 허용)", name);
                    return true;
                }
                return false;
            case HALF_OPEN:
                return true;
            default:
                return false;
        }
    }

    /**
     * 성공적인 호출을 기록합니다.
     */
    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            log.info("회로차단기 [{}] CLOSED 전환 (정상 복구)", name);
        }
        state = State.CLOSED;
        failureCount.set(0);
    }

    /**
     * 실패한 호출을 기록합니다.
     */
    public void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());

        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            log.warn("회로차단기 [{}] OPEN 전환 (HALF_OPEN 시험 실패)", name);
            return;
        }

        int currentFailures = failureCount.incrementAndGet();
        if (currentFailures >= failureThreshold) {
            state = State.OPEN;
            log.warn("회로차단기 [{}] OPEN 전환 (연속 실패 {}회)", name, currentFailures);
        }
    }

    public State getState() {
        return state;
    }

    public int getFailureCount() {
        return failureCount.get();
    }
}
