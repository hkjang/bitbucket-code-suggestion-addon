package com.jask.bitbucket.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Active Objects entity for tracking analysis result versions per PR.
 * Each analysis run creates a new version entry.
 */
@Table("JASK_ANALYSIS_VER")
@Preload
public interface AnalysisVersionEntity extends Entity {

    @Indexed
    long getPullRequestId();
    void setPullRequestId(long pullRequestId);

    @Indexed
    int getRepositoryId();
    void setRepositoryId(int repositoryId);

    /** Version number (auto-incremented per PR) */
    int getVersion();
    void setVersion(int version);

    /** Git commit hash at the time of analysis */
    String getCommitHash();
    void setCommitHash(String commitHash);

    /** Source branch name */
    String getSourceBranch();
    void setSourceBranch(String sourceBranch);

    /** Who triggered the analysis */
    String getAnalyzedBy();
    void setAnalyzedBy(String analyzedBy);

    /** Total number of suggestions in this version */
    int getSuggestionCount();
    void setSuggestionCount(int suggestionCount);

    /** Critical issue count */
    int getCriticalCount();
    void setCriticalCount(int criticalCount);

    /** Warning count */
    int getWarningCount();
    void setWarningCount(int warningCount);

    /** Overall quality score */
    double getQualityScore();
    void setQualityScore(double qualityScore);

    /** Analysis duration in milliseconds */
    long getAnalysisTimeMs();
    void setAnalysisTimeMs(long analysisTimeMs);

    /** LLM model used for analysis */
    String getModelUsed();
    void setModelUsed(String modelUsed);

    /** Summary of changes compared to previous version (JSON) */
    @StringLength(StringLength.UNLIMITED)
    String getChangeSummary();
    void setChangeSummary(String changeSummary);

    /** Analysis status: COMPLETED, FAILED */
    String getStatus();
    void setStatus(String status);

    long getCreatedAt();
    void setCreatedAt(long createdAt);
}
