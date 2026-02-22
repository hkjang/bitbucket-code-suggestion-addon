package com.jask.bitbucket.service;

import java.util.List;

/**
 * Service for managing analysis result versions.
 * Tracks each analysis run and allows comparison between versions.
 */
public interface AnalysisVersionService {

    /**
     * Create a new analysis version record for a PR.
     * Auto-increments the version number.
     *
     * @return the created version info
     */
    VersionInfo createVersion(CreateVersionRequest request);

    /**
     * Get all analysis versions for a PR (newest first).
     */
    List<VersionInfo> getVersionHistory(long pullRequestId, int repositoryId);

    /**
     * Get a specific version.
     */
    VersionInfo getVersion(long pullRequestId, int repositoryId, int version);

    /**
     * Get the latest version number for a PR.
     *
     * @return latest version number, or 0 if no versions exist
     */
    int getLatestVersionNumber(long pullRequestId, int repositoryId);

    /**
     * Compare two versions and produce a diff summary.
     */
    VersionComparison compareVersions(long pullRequestId, int repositoryId,
                                       int fromVersion, int toVersion);

    // --- DTOs ---

    class CreateVersionRequest {
        private long pullRequestId;
        private int repositoryId;
        private String commitHash;
        private String sourceBranch;
        private String analyzedBy;
        private int suggestionCount;
        private int criticalCount;
        private int warningCount;
        private double qualityScore;
        private long analysisTimeMs;
        private String modelUsed;
        private String status;

        public long getPullRequestId() { return pullRequestId; }
        public void setPullRequestId(long pullRequestId) { this.pullRequestId = pullRequestId; }

        public int getRepositoryId() { return repositoryId; }
        public void setRepositoryId(int repositoryId) { this.repositoryId = repositoryId; }

        public String getCommitHash() { return commitHash; }
        public void setCommitHash(String commitHash) { this.commitHash = commitHash; }

        public String getSourceBranch() { return sourceBranch; }
        public void setSourceBranch(String sourceBranch) { this.sourceBranch = sourceBranch; }

        public String getAnalyzedBy() { return analyzedBy; }
        public void setAnalyzedBy(String analyzedBy) { this.analyzedBy = analyzedBy; }

        public int getSuggestionCount() { return suggestionCount; }
        public void setSuggestionCount(int suggestionCount) { this.suggestionCount = suggestionCount; }

        public int getCriticalCount() { return criticalCount; }
        public void setCriticalCount(int criticalCount) { this.criticalCount = criticalCount; }

        public int getWarningCount() { return warningCount; }
        public void setWarningCount(int warningCount) { this.warningCount = warningCount; }

        public double getQualityScore() { return qualityScore; }
        public void setQualityScore(double qualityScore) { this.qualityScore = qualityScore; }

        public long getAnalysisTimeMs() { return analysisTimeMs; }
        public void setAnalysisTimeMs(long analysisTimeMs) { this.analysisTimeMs = analysisTimeMs; }

        public String getModelUsed() { return modelUsed; }
        public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    class VersionInfo {
        private int id;
        private int version;
        private long pullRequestId;
        private int repositoryId;
        private String commitHash;
        private String sourceBranch;
        private String analyzedBy;
        private int suggestionCount;
        private int criticalCount;
        private int warningCount;
        private double qualityScore;
        private long analysisTimeMs;
        private String modelUsed;
        private String status;
        private long createdAt;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }

        public long getPullRequestId() { return pullRequestId; }
        public void setPullRequestId(long pullRequestId) { this.pullRequestId = pullRequestId; }

        public int getRepositoryId() { return repositoryId; }
        public void setRepositoryId(int repositoryId) { this.repositoryId = repositoryId; }

        public String getCommitHash() { return commitHash; }
        public void setCommitHash(String commitHash) { this.commitHash = commitHash; }

        public String getSourceBranch() { return sourceBranch; }
        public void setSourceBranch(String sourceBranch) { this.sourceBranch = sourceBranch; }

        public String getAnalyzedBy() { return analyzedBy; }
        public void setAnalyzedBy(String analyzedBy) { this.analyzedBy = analyzedBy; }

        public int getSuggestionCount() { return suggestionCount; }
        public void setSuggestionCount(int suggestionCount) { this.suggestionCount = suggestionCount; }

        public int getCriticalCount() { return criticalCount; }
        public void setCriticalCount(int criticalCount) { this.criticalCount = criticalCount; }

        public int getWarningCount() { return warningCount; }
        public void setWarningCount(int warningCount) { this.warningCount = warningCount; }

        public double getQualityScore() { return qualityScore; }
        public void setQualityScore(double qualityScore) { this.qualityScore = qualityScore; }

        public long getAnalysisTimeMs() { return analysisTimeMs; }
        public void setAnalysisTimeMs(long analysisTimeMs) { this.analysisTimeMs = analysisTimeMs; }

        public String getModelUsed() { return modelUsed; }
        public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    class VersionComparison {
        private int fromVersion;
        private int toVersion;
        private int suggestionDelta;
        private int criticalDelta;
        private int warningDelta;
        private double scoreDelta;
        private int newIssues;
        private int resolvedIssues;
        private String summary;

        public int getFromVersion() { return fromVersion; }
        public void setFromVersion(int fromVersion) { this.fromVersion = fromVersion; }

        public int getToVersion() { return toVersion; }
        public void setToVersion(int toVersion) { this.toVersion = toVersion; }

        public int getSuggestionDelta() { return suggestionDelta; }
        public void setSuggestionDelta(int suggestionDelta) { this.suggestionDelta = suggestionDelta; }

        public int getCriticalDelta() { return criticalDelta; }
        public void setCriticalDelta(int criticalDelta) { this.criticalDelta = criticalDelta; }

        public int getWarningDelta() { return warningDelta; }
        public void setWarningDelta(int warningDelta) { this.warningDelta = warningDelta; }

        public double getScoreDelta() { return scoreDelta; }
        public void setScoreDelta(double scoreDelta) { this.scoreDelta = scoreDelta; }

        public int getNewIssues() { return newIssues; }
        public void setNewIssues(int newIssues) { this.newIssues = newIssues; }

        public int getResolvedIssues() { return resolvedIssues; }
        public void setResolvedIssues(int resolvedIssues) { this.resolvedIssues = resolvedIssues; }

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }
}
