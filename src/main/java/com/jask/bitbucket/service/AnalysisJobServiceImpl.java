package com.jask.bitbucket.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.jask.bitbucket.ao.AnalysisJobEntity;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * 비동기 분석 잡 관리 서비스 구현.
 * Active Objects를 사용하여 DC 클러스터 노드 간 잡 조율을 수행합니다.
 */
@ExportAsService({AnalysisJobService.class})
@Named("analysisJobService")
public class AnalysisJobServiceImpl implements AnalysisJobService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobServiceImpl.class);

    private final ActiveObjects ao;

    @Inject
    public AnalysisJobServiceImpl(@ComponentImport ActiveObjects ao) {
        this.ao = ao;
    }

    @Override
    public long submitJob(long pullRequestId, int repositoryId,
                          String projectKey, String repositorySlug,
                          String requestPayload, String requestedBy) {
        AnalysisJobEntity job = ao.create(AnalysisJobEntity.class);
        job.setPullRequestId(pullRequestId);
        job.setRepositoryId(repositoryId);
        job.setProjectKey(projectKey);
        job.setRepositorySlug(repositorySlug);
        job.setStatus("QUEUED");
        job.setRequestPayload(requestPayload);
        job.setRequestedBy(requestedBy);
        job.setProgress(0);
        job.setProcessedFiles(0);
        job.setTotalFiles(0);
        job.setSuggestionCount(0);
        job.setCreatedAt(System.currentTimeMillis());
        job.save();

        log.info("분석 잡 생성: jobId={}, PR=#{}, repo={}, user={}",
                job.getID(), pullRequestId, repositoryId, requestedBy);
        return job.getID();
    }

    @Override
    public JobStatus getJobStatus(long jobId) {
        AnalysisJobEntity entity = ao.get(AnalysisJobEntity.class, (int) jobId);
        if (entity == null) {
            return null;
        }
        return toStatus(entity);
    }

    @Override
    public boolean cancelJob(long jobId, String cancelledBy) {
        AnalysisJobEntity entity = ao.get(AnalysisJobEntity.class, (int) jobId);
        if (entity == null) {
            return false;
        }

        String currentStatus = entity.getStatus();
        if ("QUEUED".equals(currentStatus) || "RUNNING".equals(currentStatus)) {
            entity.setStatus("CANCELLED");
            entity.setCompletedAt(System.currentTimeMillis());
            entity.save();
            log.info("분석 잡 취소: jobId={}, by={}", jobId, cancelledBy);
            return true;
        }

        return false; // 이미 완료/실패된 잡은 취소 불가
    }

    @Override
    public JobStatus getLatestJobForPr(long pullRequestId, int repositoryId) {
        AnalysisJobEntity[] entities = ao.find(AnalysisJobEntity.class,
                Query.select()
                        .where("PULL_REQUEST_ID = ? AND REPOSITORY_ID = ?", pullRequestId, repositoryId)
                        .order("CREATED_AT DESC")
                        .limit(1));

        if (entities.length == 0) {
            return null;
        }
        return toStatus(entities[0]);
    }

    @Override
    public JobStatus claimNextJob(String nodeId) {
        // QUEUED 상태에서 가장 오래된 잡을 찾아 RUNNING으로 전환
        AnalysisJobEntity[] entities = ao.find(AnalysisJobEntity.class,
                Query.select()
                        .where("STATUS = ?", "QUEUED")
                        .order("CREATED_AT ASC")
                        .limit(1));

        if (entities.length == 0) {
            return null;
        }

        AnalysisJobEntity job = entities[0];
        // 낙관적 락: 상태가 여전히 QUEUED인지 확인 후 변경
        if (!"QUEUED".equals(job.getStatus())) {
            return null; // 다른 노드가 이미 가져감
        }

        job.setStatus("RUNNING");
        job.setProcessingNode(nodeId);
        job.setStartedAt(System.currentTimeMillis());
        job.save();

        log.info("분석 잡 클레임: jobId={}, node={}", job.getID(), nodeId);
        return toStatus(job);
    }

    @Override
    public void updateProgress(long jobId, int processedFiles, int totalFiles) {
        AnalysisJobEntity entity = ao.get(AnalysisJobEntity.class, (int) jobId);
        if (entity != null && "RUNNING".equals(entity.getStatus())) {
            entity.setProcessedFiles(processedFiles);
            entity.setTotalFiles(totalFiles);
            entity.setProgress(totalFiles > 0 ? (processedFiles * 100 / totalFiles) : 0);
            entity.save();
        }
    }

    @Override
    public void completeJob(long jobId, int suggestionCount) {
        AnalysisJobEntity entity = ao.get(AnalysisJobEntity.class, (int) jobId);
        if (entity != null) {
            entity.setStatus("COMPLETED");
            entity.setSuggestionCount(suggestionCount);
            entity.setProgress(100);
            entity.setCompletedAt(System.currentTimeMillis());
            entity.save();

            long duration = entity.getCompletedAt() - entity.getStartedAt();
            log.info("분석 잡 완료: jobId={}, suggestions={}, duration={}ms",
                    jobId, suggestionCount, duration);
        }
    }

    @Override
    public void failJob(long jobId, String errorMessage) {
        AnalysisJobEntity entity = ao.get(AnalysisJobEntity.class, (int) jobId);
        if (entity != null) {
            entity.setStatus("FAILED");
            entity.setErrorMessage(errorMessage);
            entity.setCompletedAt(System.currentTimeMillis());
            entity.save();

            log.error("분석 잡 실패: jobId={}, error={}", jobId, errorMessage);
        }
    }

    /**
     * 잡의 요청 페이로드(JSON)를 반환합니다.
     * 워커에서 분석 요청 복원 시 사용합니다.
     */
    public String getRequestPayload(long jobId) {
        AnalysisJobEntity entity = ao.get(AnalysisJobEntity.class, (int) jobId);
        return entity != null ? entity.getRequestPayload() : null;
    }

    private JobStatus toStatus(AnalysisJobEntity entity) {
        JobStatus status = new JobStatus();
        status.setJobId(entity.getID());
        status.setPullRequestId(entity.getPullRequestId());
        status.setRepositoryId(entity.getRepositoryId());
        status.setStatus(entity.getStatus());
        status.setProgress(entity.getProgress());
        status.setProcessedFiles(entity.getProcessedFiles());
        status.setTotalFiles(entity.getTotalFiles());
        status.setSuggestionCount(entity.getSuggestionCount());
        status.setErrorMessage(entity.getErrorMessage());
        status.setRequestedBy(entity.getRequestedBy());
        status.setCreatedAt(entity.getCreatedAt());
        status.setStartedAt(entity.getStartedAt());
        status.setCompletedAt(entity.getCompletedAt());
        return status;
    }
}
