package com.jask.bitbucket.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 분석 결과 캐시 서비스.
 * 동일한 diff 내용에 대한 LLM 중복 호출을 방지합니다.
 * 인메모리 LRU 캐시를 사용하며 TTL 기반으로 만료됩니다.
 */
@Named("analysisCacheService")
public class AnalysisCacheService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisCacheService.class);
    private static final int MAX_CACHE_SIZE = 500;
    private static final long DEFAULT_TTL_MS = 30 * 60 * 1000; // 30분

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 캐시에서 분석 결과를 조회합니다.
     *
     * @param diffContent diff 내용
     * @param model 사용된 LLM 모델
     * @return 캐시된 결과 또는 null
     */
    public String get(String diffContent, String model) {
        String key = computeKey(diffContent, model);
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            cache.remove(key);
            return null;
        }

        log.debug("캐시 히트: key={}", key.substring(0, 16) + "...");
        entry.touch();
        return entry.value;
    }

    /**
     * 분석 결과를 캐시에 저장합니다.
     */
    public void put(String diffContent, String model, String result) {
        evictIfNeeded();
        String key = computeKey(diffContent, model);
        cache.put(key, new CacheEntry(result, DEFAULT_TTL_MS));
        log.debug("캐시 저장: key={}", key.substring(0, 16) + "...");
    }

    /**
     * 특정 PR의 캐시를 무효화합니다.
     */
    public void invalidate(long pullRequestId, int repositoryId) {
        String prefix = "pr:" + pullRequestId + ":repo:" + repositoryId;
        cache.keySet().removeIf(k -> k.startsWith(prefix));
        log.debug("캐시 무효화: PR=#{}, repo={}", pullRequestId, repositoryId);
    }

    /**
     * 전체 캐시를 초기화합니다.
     */
    public void clear() {
        cache.clear();
        log.info("분석 캐시 전체 초기화");
    }

    /**
     * 캐시 통계를 반환합니다.
     */
    public CacheStats getStats() {
        long expired = cache.values().stream().filter(CacheEntry::isExpired).count();
        return new CacheStats(cache.size(), cache.size() - expired, expired);
    }

    private String computeKey(String diffContent, String model) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(model.getBytes(StandardCharsets.UTF_8));
            md.update(diffContent.getBytes(StandardCharsets.UTF_8));
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256은 항상 사용 가능
            return diffContent.hashCode() + ":" + model;
        }
    }

    private void evictIfNeeded() {
        if (cache.size() < MAX_CACHE_SIZE) {
            return;
        }

        // 만료된 항목 먼저 제거
        cache.entrySet().removeIf(e -> e.getValue().isExpired());

        // 여전히 크면 가장 오래된 항목 제거
        if (cache.size() >= MAX_CACHE_SIZE) {
            cache.entrySet().stream()
                    .min((a, b) -> Long.compare(a.getValue().lastAccess, b.getValue().lastAccess))
                    .ifPresent(oldest -> cache.remove(oldest.getKey()));
        }
    }

    private static class CacheEntry {
        final String value;
        final long expiresAt;
        volatile long lastAccess;

        CacheEntry(String value, long ttlMs) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
            this.lastAccess = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        void touch() {
            this.lastAccess = System.currentTimeMillis();
        }
    }

    public static class CacheStats {
        private final int totalEntries;
        private final long activeEntries;
        private final long expiredEntries;

        CacheStats(int totalEntries, long activeEntries, long expiredEntries) {
            this.totalEntries = totalEntries;
            this.activeEntries = activeEntries;
            this.expiredEntries = expiredEntries;
        }

        public int getTotalEntries() { return totalEntries; }
        public long getActiveEntries() { return activeEntries; }
        public long getExpiredEntries() { return expiredEntries; }
    }
}
