package com.jask.bitbucket.service;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 플러그인 메트릭 수집 서비스.
 *
 * 수집 항목:
 * - LLM API 호출 횟수/시간/성공률
 * - 분석 잡 처리 횟수/시간/큐 대기 시간
 * - 제안 생성/수락/거부 통계
 * - 캐시 히트율
 * - 에러 발생 횟수
 *
 * 메모리 기반 (서버 재시작 시 초기화).
 * DC 클러스터에서는 노드별 통계이며, 관리자 API를 통해 조회 가능.
 */
@ExportAsService({MetricsService.class})
@Named("metricsService")
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    // --- Counters ---
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    // --- Timing (average ms) ---
    private final ConcurrentHashMap<String, TimingStat> timings = new ConcurrentHashMap<>();

    // --- Predefined counter keys ---
    public static final String LLM_CALLS_TOTAL = "llm.calls.total";
    public static final String LLM_CALLS_SUCCESS = "llm.calls.success";
    public static final String LLM_CALLS_FAILURE = "llm.calls.failure";
    public static final String LLM_CALLS_CIRCUIT_OPEN = "llm.calls.circuit_open";
    public static final String LLM_CALLS_RETRY = "llm.calls.retry";

    public static final String ANALYSIS_JOBS_SUBMITTED = "analysis.jobs.submitted";
    public static final String ANALYSIS_JOBS_COMPLETED = "analysis.jobs.completed";
    public static final String ANALYSIS_JOBS_FAILED = "analysis.jobs.failed";
    public static final String ANALYSIS_JOBS_CANCELLED = "analysis.jobs.cancelled";

    public static final String SUGGESTIONS_GENERATED = "suggestions.generated";
    public static final String SUGGESTIONS_ACCEPTED = "suggestions.accepted";
    public static final String SUGGESTIONS_REJECTED = "suggestions.rejected";
    public static final String SUGGESTIONS_DISMISSED = "suggestions.dismissed";
    public static final String SUGGESTIONS_AS_COMMENT = "suggestions.as_comment";

    public static final String CACHE_HITS = "cache.hits";
    public static final String CACHE_MISSES = "cache.misses";

    public static final String ERRORS_TOTAL = "errors.total";

    // --- Predefined timing keys ---
    public static final String TIMING_LLM_CALL = "timing.llm_call_ms";
    public static final String TIMING_ANALYSIS_JOB = "timing.analysis_job_ms";
    public static final String TIMING_JOB_QUEUE_WAIT = "timing.job_queue_wait_ms";

    /**
     * Increment a counter by 1.
     */
    public void increment(String key) {
        counters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Increment a counter by a specific amount.
     */
    public void incrementBy(String key, long amount) {
        counters.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(amount);
    }

    /**
     * Get counter value.
     */
    public long getCounter(String key) {
        AtomicLong counter = counters.get(key);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Record a timing value.
     */
    public void recordTiming(String key, long durationMs) {
        timings.computeIfAbsent(key, k -> new TimingStat()).record(durationMs);
    }

    /**
     * Get timing statistics for a key.
     */
    public TimingStat getTiming(String key) {
        return timings.getOrDefault(key, new TimingStat());
    }

    /**
     * Get all metrics as a map (for REST API).
     */
    public Map<String, Object> getAllMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // Counters
        Map<String, Long> counterMap = new TreeMap<>();
        counters.forEach((key, value) -> counterMap.put(key, value.get()));
        metrics.put("counters", counterMap);

        // Timings
        Map<String, Map<String, Object>> timingMap = new TreeMap<>();
        timings.forEach((key, stat) -> {
            Map<String, Object> timingDetail = new LinkedHashMap<>();
            timingDetail.put("count", stat.getCount());
            timingDetail.put("avgMs", stat.getAverageMs());
            timingDetail.put("minMs", stat.getMinMs());
            timingDetail.put("maxMs", stat.getMaxMs());
            timingDetail.put("p95Ms", stat.getP95Ms());
            timingMap.put(key, timingDetail);
        });
        metrics.put("timings", timingMap);

        // Derived metrics
        Map<String, Object> derived = new LinkedHashMap<>();

        long llmTotal = getCounter(LLM_CALLS_TOTAL);
        long llmSuccess = getCounter(LLM_CALLS_SUCCESS);
        derived.put("llm.success_rate", llmTotal > 0 ?
                String.format("%.1f%%", (double) llmSuccess / llmTotal * 100) : "N/A");

        long cacheHits = getCounter(CACHE_HITS);
        long cacheMisses = getCounter(CACHE_MISSES);
        long cacheTotal = cacheHits + cacheMisses;
        derived.put("cache.hit_rate", cacheTotal > 0 ?
                String.format("%.1f%%", (double) cacheHits / cacheTotal * 100) : "N/A");

        long sugGenerated = getCounter(SUGGESTIONS_GENERATED);
        long sugAccepted = getCounter(SUGGESTIONS_ACCEPTED);
        derived.put("suggestions.acceptance_rate", sugGenerated > 0 ?
                String.format("%.1f%%", (double) sugAccepted / sugGenerated * 100) : "N/A");

        metrics.put("derived", derived);
        metrics.put("uptimeMs", System.currentTimeMillis() - startTime);

        return metrics;
    }

    /**
     * Reset all metrics.
     */
    public void reset() {
        counters.clear();
        timings.clear();
        startTime = System.currentTimeMillis();
        log.info("메트릭 초기화 완료");
    }

    private long startTime = System.currentTimeMillis();

    /**
     * Timing statistics accumulator.
     */
    public static class TimingStat {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalMs = new AtomicLong(0);
        private volatile long minMs = Long.MAX_VALUE;
        private volatile long maxMs = 0;
        // 간단한 P95 근사치를 위한 최근 값들
        private final long[] recentValues = new long[100];
        private volatile int writeIndex = 0;

        public synchronized void record(long durationMs) {
            count.incrementAndGet();
            totalMs.addAndGet(durationMs);
            if (durationMs < minMs) minMs = durationMs;
            if (durationMs > maxMs) maxMs = durationMs;
            recentValues[writeIndex % recentValues.length] = durationMs;
            writeIndex++;
        }

        public long getCount() { return count.get(); }

        public double getAverageMs() {
            long c = count.get();
            return c > 0 ? (double) totalMs.get() / c : 0;
        }

        public long getMinMs() {
            return count.get() > 0 ? minMs : 0;
        }

        public long getMaxMs() { return maxMs; }

        public long getP95Ms() {
            int n = (int) Math.min(count.get(), recentValues.length);
            if (n == 0) return 0;

            long[] sorted = new long[n];
            System.arraycopy(recentValues, 0, sorted, 0, n);
            Arrays.sort(sorted);
            int idx = (int) Math.ceil(n * 0.95) - 1;
            return sorted[Math.max(0, idx)];
        }
    }
}
