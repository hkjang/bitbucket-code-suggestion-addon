package com.jask.bitbucket.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Active Objects entity for tracking asynchronous analysis jobs.
 * DC 클러스터 환경에서 노드 간 잡 조율을 위해 공유 DB를 사용합니다.
 */
@Table("JASK_ANALYSIS_JOB")
@Preload
public interface AnalysisJobEntity extends Entity {

    @Indexed
    long getPullRequestId();
    void setPullRequestId(long pullRequestId);

    @Indexed
    int getRepositoryId();
    void setRepositoryId(int repositoryId);

    String getProjectKey();
    void setProjectKey(String projectKey);

    String getRepositorySlug();
    void setRepositorySlug(String repositorySlug);

    /**
     * QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
     */
    @Indexed
    String getStatus();
    void setStatus(String status);

    /**
     * 분석 요청 JSON (FileDiff 목록)
     */
    @StringLength(StringLength.UNLIMITED)
    String getRequestPayload();
    void setRequestPayload(String requestPayload);

    /**
     * 처리 중인 노드 식별자 (DC 클러스터 환경)
     */
    String getProcessingNode();
    void setProcessingNode(String processingNode);

    /**
     * 분석 진행률 (0~100)
     */
    int getProgress();
    void setProgress(int progress);

    /**
     * 처리된 파일 수
     */
    int getProcessedFiles();
    void setProcessedFiles(int processedFiles);

    /**
     * 총 파일 수
     */
    int getTotalFiles();
    void setTotalFiles(int totalFiles);

    /**
     * 생성된 제안 수
     */
    int getSuggestionCount();
    void setSuggestionCount(int suggestionCount);

    /**
     * 에러 메시지 (실패 시)
     */
    @StringLength(StringLength.UNLIMITED)
    String getErrorMessage();
    void setErrorMessage(String errorMessage);

    /**
     * 요청한 사용자
     */
    String getRequestedBy();
    void setRequestedBy(String requestedBy);

    long getCreatedAt();
    void setCreatedAt(long createdAt);

    long getStartedAt();
    void setStartedAt(long startedAt);

    long getCompletedAt();
    void setCompletedAt(long completedAt);

    /**
     * 마지막 업데이트 시각 (스톨 잡 감지용)
     */
    @Indexed
    long getUpdatedAt();
    void setUpdatedAt(long updatedAt);

    /**
     * 처리 노드 ID (DC 클러스터 환경)
     */
    String getNodeId();
    void setNodeId(String nodeId);
}
