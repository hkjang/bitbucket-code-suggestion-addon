package com.jask.bitbucket.service;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * AnalysisCacheService 단위 테스트.
 */
public class AnalysisCacheServiceTest {

    private AnalysisCacheService cacheService;

    @Before
    public void setUp() {
        cacheService = new AnalysisCacheService();
    }

    @Test
    public void testPutAndGet() {
        cacheService.put("diff content", "model-a", "response-1");

        String result = cacheService.get("diff content", "model-a");
        assertEquals("response-1", result);
    }

    @Test
    public void testCacheMiss() {
        String result = cacheService.get("nonexistent", "model");
        assertNull(result);
    }

    @Test
    public void testDifferentModelSeparateCache() {
        cacheService.put("diff content", "model-a", "response-a");
        cacheService.put("diff content", "model-b", "response-b");

        assertEquals("response-a", cacheService.get("diff content", "model-a"));
        assertEquals("response-b", cacheService.get("diff content", "model-b"));
    }

    @Test
    public void testInvalidateByPr() {
        // invalidate는 전체 캐시를 지우는 방식이 아닌, 특정 PR 기반
        // 현재 구현에서는 diffContent+model 기반이므로 clear 테스트
        cacheService.put("diff1", "model", "resp1");
        cacheService.put("diff2", "model", "resp2");

        cacheService.clear();

        assertNull(cacheService.get("diff1", "model"));
        assertNull(cacheService.get("diff2", "model"));
    }

    @Test
    public void testGetStats() {
        cacheService.put("d1", "m1", "r1");
        cacheService.get("d1", "m1"); // hit
        cacheService.get("d2", "m1"); // miss

        String stats = cacheService.getStats();
        assertNotNull(stats);
        assertTrue(stats.contains("size=1"));
    }

    @Test
    public void testOverwrite() {
        cacheService.put("diff", "model", "v1");
        cacheService.put("diff", "model", "v2");

        assertEquals("v2", cacheService.get("diff", "model"));
    }
}
