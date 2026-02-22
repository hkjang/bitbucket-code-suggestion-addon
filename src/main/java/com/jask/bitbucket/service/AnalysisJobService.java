package com.jask.bitbucket.service;

import java.util.List;

/**
 * 비동기 분석 잡 관리 서비스 인터페이스.
 */
public interface AnalysisJobService {

    /**
     * 새로운 분석 잡을 큐에 등록합니다.
     *
     * @return 생성된 잡 ID
     */
    long submitJob(long pullRequestId, int repositoryId,
                   String projectKey, String repositorySlug,
                   String requestPayload, String requestedBy);

    /**
     * 잡 상태를 조회합니다.
     */
    JobStatus getJobStatus(long jobId);

    /**
     * 잡을 취소합니다.
     */
    boolean cancelJob(long jobId, String cancelledBy);

    /**
     * PR에 대한 최신 잡을 조회합니다.
     */
    JobStatus getLatestJobForPr(long pullRequestId, int repositoryId);

    /**
     * QUEUED 상태의 잡을 가져와 RUNNING으로 전환합니다 (워커용).
     * DC 클러스터 환경에서 한 노드만 가져갈 수 있도록 DB 레벨 처리.
     */
    JobStatus claimNextJob(String nodeId);

    /**
     * 잡 진행 상태를 업데이트합니다.
     */
    void updateProgress(long jobId, int processedFiles, int totalFiles);

    /**
     * 잡을 완료 처리합니다.
     */
    void completeJob(long jobId, int suggestionCount);

    /**
     * 잡을 실패 처리합니다.
     */
    void failJob(long jobId, String errorMessage);

    /**
     * 잡 상태 DTO.
     */
    class JobStatus {
        private long jobId;
        private long pullRequestId;
        private int repositoryId;
        private String status; // QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
        private int progress;
        private int processedFiles;
        private int totalFiles;
        private int suggestionCount;
        private String errorMessage;
        private String requestedBy;
        private long createdAt;
        private long startedAt;
        private long completedAt;

        public long getJobId() { return jobId; }
        public void setJobId(long jobId) { this.jobId = jobId; }

        public long getPullRequestId() { return pullRequestId; }
        public void setPullRequestId(long pullRequestId) { this.pullRequestId = pullRequestId; }

        public int getRepositoryId() { return repositoryId; }
        public void setRepositoryId(int repositoryId) { this.repositoryId = repositoryId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getProgress() { return progress; }
        public void setProgress(int progress) { this.progress = progress; }

        public int getProcessedFiles() { return processedFiles; }
        public void setProcessedFiles(int processedFiles) { this.processedFiles = processedFiles; }

        public int getTotalFiles() { return totalFiles; }
        public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }

        public int getSuggestionCount() { return suggestionCount; }
        public void setSuggestionCount(int suggestionCount) { this.suggestionCount = suggestionCount; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public String getRequestedBy() { return requestedBy; }
        public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        public long getStartedAt() { return startedAt; }
        public void setStartedAt(long startedAt) { this.startedAt = startedAt; }

        public long getCompletedAt() { return completedAt; }
        public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
    }
}
