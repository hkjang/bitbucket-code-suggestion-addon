package com.jask.bitbucket.service.llm;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * CircuitBreaker 단위 테스트.
 */
public class CircuitBreakerTest {

    private CircuitBreaker breaker;

    @Before
    public void setUp() {
        breaker = new CircuitBreaker();
    }

    @Test
    public void testInitialState_isClosed() {
        assertTrue(breaker.allowRequest());
    }

    @Test
    public void testClosedToOpen_afterConsecutiveFailures() {
        // 기본 설정: 5회 연속 실패로 OPEN
        for (int i = 0; i < 5; i++) {
            breaker.recordFailure();
        }

        assertFalse(breaker.allowRequest());
    }

    @Test
    public void testSuccessResetsFailureCount() {
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordSuccess(); // 실패 카운트 리셋

        // 추가 2회 실패로는 OPEN이 되지 않아야 함
        breaker.recordFailure();
        breaker.recordFailure();

        assertTrue(breaker.allowRequest());
    }

    @Test
    public void testPartialFailures_staysClosed() {
        for (int i = 0; i < 4; i++) {
            breaker.recordFailure();
        }

        // 4회 실패는 임계값(5) 미만이므로 CLOSED
        assertTrue(breaker.allowRequest());
    }

    @Test
    public void testSuccessAfterHalfOpen_closesCirucit() {
        // OPEN 상태 만들기
        for (int i = 0; i < 5; i++) {
            breaker.recordFailure();
        }
        assertFalse(breaker.allowRequest());

        // 성공 기록하면 CLOSED로 복구
        breaker.recordSuccess();
        assertTrue(breaker.allowRequest());
    }
}
