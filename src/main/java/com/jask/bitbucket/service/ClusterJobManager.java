package com.jask.bitbucket.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.jask.bitbucket.ao.AnalysisJobEntity;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * DC 클러스터 환경에서 잡을 관리하는 매니저.
 *
 * 기능:
 * 1. 스톨(Stale) 잡 감지 및 복구 - RUNNING 상태가 타임아웃을 초과하면 QUEUED로 재설정
 * 2. 오래된 완료 잡 정리 (7일 이상)
 * 3. 노드별 잡 처리 현황 모니터링
 */
@Named("clusterJobManager")
public class ClusterJobManager {

    private static final Logger log = LoggerFactory.getLogger(ClusterJobManager.class);

    /** RUNNING 잡이 이 시간을 초과하면 스톨로 간주 (10분) */
    private static final long STALE_JOB_TIMEOUT_MS = 10 * 60 * 1000;

    /** 완료된 잡 보관 기간 (7일) */
    private static final long COMPLETED_JOB_RETENTION_MS = 7 * 24 * 60 * 60 * 1000L;

    /** 관리 작업 주기 (2분) */
    private static final long MAINTENANCE_INTERVAL_SECONDS = 120;

    /** 스톨 잡 최대 재시도 횟수 */
    private static final int MAX_RETRY_COUNT = 3;

    private final ActiveObjects ao;
    private final String nodeId;
    private ScheduledExecutorService scheduler;

    @Inject
    public ClusterJobManager(@ComponentImport ActiveObjects ao) {
        this.ao = ao;
        this.nodeId = resolveNodeId();
    }

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jask-cluster-job-manager");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(
                this::runMaintenance,
                60, // 1분 뒤 첫 실행
                MAINTENANCE_INTERVAL_SECONDS,
                TimeUnit.SECONDS);

        log.info("클러스터 잡 매니저 시작: nodeId={}", nodeId);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("클러스터 잡 매니저 종료: nodeId={}", nodeId);
    }

    /**
     * 주기적 관리 작업 실행.
     */
    private void runMaintenance() {
        try {
            int recoveredJobs = recoverStaleJobs();
            int cleanedJobs = cleanupOldJobs();

            if (recoveredJobs > 0 || cleanedJobs > 0) {
                log.info("잡 관리 완료: 복구={}, 정리={}", recoveredJobs, cleanedJobs);
            }
        } catch (Exception e) {
            log.error("잡 관리 작업 중 오류: {}", e.getMessage(), e);
        }
    }

    /**
     * 스톨된 RUNNING 잡을 감지하여 QUEUED로 재설정합니다.
     * 타임아웃 기간을 초과한 RUNNING 잡은 해당 노드가 비정상 종료된 것으로 간주합니다.
     *
     * @return 복구된 잡 수
     */
    private int recoverStaleJobs() {
        long cutoffTime = System.currentTimeMillis() - STALE_JOB_TIMEOUT_MS;
        int recovered = 0;

        AnalysisJobEntity[] staleJobs = ao.find(AnalysisJobEntity.class,
                Query.select()
                        .where("STATUS = ? AND UPDATED_AT < ?", "RUNNING", cutoffTime));

        for (AnalysisJobEntity job : staleJobs) {
            try {
                int retryCount = parseRetryCount(job);

                if (retryCount >= MAX_RETRY_COUNT) {
                    // 최대 재시도 초과: FAILED로 전환
                    job.setStatus("FAILED");
                    job.setErrorMessage("최대 재시도 횟수 초과 (스톨 잡)");
                    job.setUpdatedAt(System.currentTimeMillis());
                    job.save();
                    log.warn("스톨 잡 실패 처리: jobId={}, retryCount={}", job.getID(), retryCount);
                } else {
                    // QUEUED로 재설정하여 다른 노드가 다시 처리하도록
                    job.setStatus("QUEUED");
                    job.setNodeId(null); // 노드 할당 해제
                    job.setUpdatedAt(System.currentTimeMillis());
                    job.setErrorMessage("retry:" + (retryCount + 1)); // 재시도 횟수 기록
                    job.save();
                    recovered++;
                    log.info("스톨 잡 복구: jobId={}, previousNode={}, retry={}",
                            job.getID(), job.getNodeId(), retryCount + 1);
                }
            } catch (Exception e) {
                log.error("스톨 잡 복구 실패: jobId={}", job.getID(), e);
            }
        }

        return recovered;
    }

    /**
     * 오래된 완료/실패 잡을 삭제합니다.
     *
     * @return 삭제된 잡 수
     */
    private int cleanupOldJobs() {
        long cutoffTime = System.currentTimeMillis() - COMPLETED_JOB_RETENTION_MS;
        int cleaned = 0;

        AnalysisJobEntity[] oldJobs = ao.find(AnalysisJobEntity.class,
                Query.select()
                        .where("(STATUS = ? OR STATUS = ? OR STATUS = ?) AND CREATED_AT < ?",
                                "COMPLETED", "FAILED", "CANCELLED", cutoffTime));

        for (AnalysisJobEntity job : oldJobs) {
            try {
                ao.delete(job);
                cleaned++;
            } catch (Exception e) {
                log.error("잡 정리 실패: jobId={}", job.getID(), e);
            }
        }

        return cleaned;
    }

    /**
     * 에러 메시지에서 retry 횟수를 파싱합니다.
     */
    private int parseRetryCount(AnalysisJobEntity job) {
        String errMsg = job.getErrorMessage();
        if (errMsg != null && errMsg.startsWith("retry:")) {
            try {
                return Integer.parseInt(errMsg.substring(6));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 현재 클러스터 잡 통계를 반환합니다.
     */
    public ClusterJobStats getClusterStats() {
        ClusterJobStats stats = new ClusterJobStats();

        try {
            stats.queuedJobs = ao.count(AnalysisJobEntity.class,
                    Query.select().where("STATUS = ?", "QUEUED"));
            stats.runningJobs = ao.count(AnalysisJobEntity.class,
                    Query.select().where("STATUS = ?", "RUNNING"));
            stats.completedToday = ao.count(AnalysisJobEntity.class,
                    Query.select().where("STATUS = ? AND UPDATED_AT > ?",
                            "COMPLETED", System.currentTimeMillis() - 86400000));
            stats.failedToday = ao.count(AnalysisJobEntity.class,
                    Query.select().where("STATUS = ? AND UPDATED_AT > ?",
                            "FAILED", System.currentTimeMillis() - 86400000));
            stats.currentNodeId = nodeId;
        } catch (Exception e) {
            log.error("클러스터 통계 조회 실패: {}", e.getMessage());
        }

        return stats;
    }

    private String resolveNodeId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "node-" + System.identityHashCode(this);
        }
    }

    /**
     * 클러스터 잡 통계 DTO.
     */
    public static class ClusterJobStats {
        public int queuedJobs;
        public int runningJobs;
        public int completedToday;
        public int failedToday;
        public String currentNodeId;
    }
}
