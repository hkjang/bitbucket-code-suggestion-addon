package com.jask.bitbucket.service;

import com.google.gson.Gson;
import com.jask.bitbucket.config.PluginSettingsService;
import com.jask.bitbucket.model.AnalysisRequest;
import com.jask.bitbucket.model.AnalysisResponse;
import com.jask.bitbucket.model.CodeSuggestion;
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
 * 비동기 분석 잡 워커.
 * 주기적으로 큐를 폴링하여 QUEUED 상태의 잡을 가져와 분석을 수행합니다.
 * DC 클러스터 환경에서 각 노드가 독립적으로 워커를 실행하며,
 * DB 레벨에서 잡 충돌을 방지합니다.
 */
@Named("analysisJobWorker")
public class AnalysisJobWorker {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobWorker.class);
    private static final long POLL_INTERVAL_SECONDS = 5;

    private final AnalysisJobService jobService;
    private final CodeAnalysisService codeAnalysisService;
    private final SuggestionService suggestionService;
    private final AnalysisVersionService versionService;
    private final Gson gson;
    private final String nodeId;

    private ScheduledExecutorService scheduler;

    @Inject
    public AnalysisJobWorker(AnalysisJobService jobService,
                              CodeAnalysisService codeAnalysisService,
                              SuggestionService suggestionService,
                              AnalysisVersionService versionService) {
        this.jobService = jobService;
        this.codeAnalysisService = codeAnalysisService;
        this.suggestionService = suggestionService;
        this.versionService = versionService;
        this.gson = new Gson();
        this.nodeId = resolveNodeId();
    }

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jask-analysis-worker");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(
                this::pollAndProcess,
                10, // 시작 대기
                POLL_INTERVAL_SECONDS,
                TimeUnit.SECONDS);

        log.info("분석 잡 워커 시작: nodeId={}", nodeId);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("분석 잡 워커 종료: nodeId={}", nodeId);
    }

    private void pollAndProcess() {
        try {
            AnalysisJobService.JobStatus job = jobService.claimNextJob(nodeId);
            if (job == null) {
                return; // 대기 중인 잡 없음
            }

            log.info("잡 처리 시작: jobId={}, PR=#{}, repo={}",
                    job.getJobId(), job.getPullRequestId(), job.getRepositoryId());

            processJob(job);
        } catch (Exception e) {
            log.error("잡 폴링/처리 중 오류: {}", e.getMessage(), e);
        }
    }

    private void processJob(AnalysisJobService.JobStatus job) {
        long jobId = job.getJobId();

        try {
            // 요청 페이로드에서 AnalysisRequest 복원
            String payload = getRequestPayload(jobId);
            if (payload == null) {
                jobService.failJob(jobId, "요청 페이로드를 찾을 수 없습니다.");
                return;
            }

            AnalysisRequest request = gson.fromJson(payload, AnalysisRequest.class);

            // 총 파일 수 업데이트
            int totalFiles = request.getFileDiffs() != null ? request.getFileDiffs().size() : 0;
            jobService.updateProgress(jobId, 0, totalFiles);

            // 분석 수행
            AnalysisResponse response = codeAnalysisService.analyze(request);

            // 잡이 취소되었는지 확인
            AnalysisJobService.JobStatus currentStatus = jobService.getJobStatus(jobId);
            if (currentStatus != null && "CANCELLED".equals(currentStatus.getStatus())) {
                log.info("잡이 취소됨: jobId={}", jobId);
                return;
            }

            if (response.isSuccess() && response.getSuggestions() != null) {
                int suggestionCount = response.getSuggestions().size();

                // 제안 저장
                if (!response.getSuggestions().isEmpty()) {
                    suggestionService.saveSuggestions(
                            request.getPullRequestId(),
                            request.getRepositoryId(),
                            response.getSuggestions());
                }

                // 버전 기록 생성
                recordVersion(request, response, "COMPLETED");

                jobService.completeJob(jobId, suggestionCount);
            } else {
                String errorMsg = response.getError() != null ? response.getError() : "분석 결과 없음";

                // 실패 버전도 기록
                recordVersion(request, response, "FAILED");

                jobService.failJob(jobId, errorMsg);
            }

        } catch (Exception e) {
            log.error("잡 처리 실패: jobId={}, error={}", jobId, e.getMessage(), e);
            jobService.failJob(jobId, e.getMessage());
        }
    }

    /**
     * AO에서 요청 페이로드를 조회합니다.
     * JobStatus DTO에는 페이로드가 없으므로, 별도로 조회합니다.
     */
    private String getRequestPayload(long jobId) {
        // AnalysisJobServiceImpl에서 직접 페이로드를 가져오는 메서드 사용
        if (jobService instanceof AnalysisJobServiceImpl) {
            return ((AnalysisJobServiceImpl) jobService).getRequestPayload(jobId);
        }
        return null;
    }

    /**
     * 분석 버전 기록을 생성합니다.
     */
    private void recordVersion(AnalysisRequest request, AnalysisResponse response, String status) {
        try {
            AnalysisVersionService.CreateVersionRequest vReq = new AnalysisVersionService.CreateVersionRequest();
            vReq.setPullRequestId(request.getPullRequestId());
            vReq.setRepositoryId(request.getRepositoryId());
            vReq.setAnalyzedBy(request.getProjectKey() != null ? request.getProjectKey() : "system");
            vReq.setStatus(status);

            if (response != null) {
                int total = 0, critical = 0, warning = 0;
                if (response.getSuggestions() != null) {
                    total = response.getSuggestions().size();
                    for (CodeSuggestion s : response.getSuggestions()) {
                        if (s.getSeverity() == CodeSuggestion.Severity.CRITICAL) critical++;
                        if (s.getSeverity() == CodeSuggestion.Severity.WARNING) warning++;
                    }
                }
                vReq.setSuggestionCount(total);
                vReq.setCriticalCount(critical);
                vReq.setWarningCount(warning);
                vReq.setAnalysisTimeMs(response.getAnalysisTimeMs());

                if (response.getSummary() != null) {
                    vReq.setQualityScore(response.getSummary().getOverallScore());
                }
            }

            versionService.createVersion(vReq);
        } catch (Exception e) {
            log.warn("버전 기록 생성 실패: {}", e.getMessage());
        }
    }

    private String resolveNodeId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "node-" + System.identityHashCode(this);
        }
    }
}
