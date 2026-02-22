package com.jask.bitbucket.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.gson.Gson;
import com.jask.bitbucket.ao.AnalysisVersionEntity;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AO-based implementation of AnalysisVersionService.
 * Tracks each analysis run as a version for comparison and history.
 */
@ExportAsService({AnalysisVersionService.class})
@Named("analysisVersionService")
public class AnalysisVersionServiceImpl implements AnalysisVersionService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisVersionServiceImpl.class);

    private final ActiveObjects ao;
    private final Gson gson;

    @Inject
    public AnalysisVersionServiceImpl(@ComponentImport ActiveObjects ao) {
        this.ao = ao;
        this.gson = new Gson();
    }

    @Override
    public VersionInfo createVersion(CreateVersionRequest request) {
        int nextVersion = getLatestVersionNumber(request.getPullRequestId(), request.getRepositoryId()) + 1;

        AnalysisVersionEntity entity = ao.create(AnalysisVersionEntity.class);
        entity.setPullRequestId(request.getPullRequestId());
        entity.setRepositoryId(request.getRepositoryId());
        entity.setVersion(nextVersion);
        entity.setCommitHash(request.getCommitHash());
        entity.setSourceBranch(request.getSourceBranch());
        entity.setAnalyzedBy(request.getAnalyzedBy());
        entity.setSuggestionCount(request.getSuggestionCount());
        entity.setCriticalCount(request.getCriticalCount());
        entity.setWarningCount(request.getWarningCount());
        entity.setQualityScore(request.getQualityScore());
        entity.setAnalysisTimeMs(request.getAnalysisTimeMs());
        entity.setModelUsed(request.getModelUsed());
        entity.setStatus(request.getStatus() != null ? request.getStatus() : "COMPLETED");
        entity.setCreatedAt(System.currentTimeMillis());

        // Generate change summary compared to previous version
        if (nextVersion > 1) {
            try {
                VersionInfo prev = getVersion(request.getPullRequestId(), request.getRepositoryId(), nextVersion - 1);
                if (prev != null) {
                    Map<String, Object> changes = new HashMap<>();
                    changes.put("suggestionDelta", request.getSuggestionCount() - prev.getSuggestionCount());
                    changes.put("criticalDelta", request.getCriticalCount() - prev.getCriticalCount());
                    changes.put("warningDelta", request.getWarningCount() - prev.getWarningCount());
                    changes.put("scoreDelta", request.getQualityScore() - prev.getQualityScore());
                    entity.setChangeSummary(gson.toJson(changes));
                }
            } catch (Exception e) {
                log.warn("이전 버전 비교 실패: {}", e.getMessage());
            }
        }

        entity.save();

        log.info("분석 버전 생성: PR #{} repo={} v{} (제안={}, 심각={}, 점수={})",
                request.getPullRequestId(), request.getRepositoryId(), nextVersion,
                request.getSuggestionCount(), request.getCriticalCount(), request.getQualityScore());

        return toVersionInfo(entity);
    }

    @Override
    public List<VersionInfo> getVersionHistory(long pullRequestId, int repositoryId) {
        AnalysisVersionEntity[] entities = ao.find(AnalysisVersionEntity.class,
                Query.select()
                        .where("PULL_REQUEST_ID = ? AND REPOSITORY_ID = ?", pullRequestId, repositoryId)
                        .order("VERSION DESC"));

        return Arrays.stream(entities)
                .map(this::toVersionInfo)
                .collect(Collectors.toList());
    }

    @Override
    public VersionInfo getVersion(long pullRequestId, int repositoryId, int version) {
        AnalysisVersionEntity[] entities = ao.find(AnalysisVersionEntity.class,
                Query.select()
                        .where("PULL_REQUEST_ID = ? AND REPOSITORY_ID = ? AND VERSION = ?",
                                pullRequestId, repositoryId, version)
                        .limit(1));

        if (entities.length == 0) {
            return null;
        }

        return toVersionInfo(entities[0]);
    }

    @Override
    public int getLatestVersionNumber(long pullRequestId, int repositoryId) {
        AnalysisVersionEntity[] entities = ao.find(AnalysisVersionEntity.class,
                Query.select()
                        .where("PULL_REQUEST_ID = ? AND REPOSITORY_ID = ?", pullRequestId, repositoryId)
                        .order("VERSION DESC")
                        .limit(1));

        if (entities.length == 0) {
            return 0;
        }

        return entities[0].getVersion();
    }

    @Override
    public VersionComparison compareVersions(long pullRequestId, int repositoryId,
                                              int fromVersion, int toVersion) {
        VersionInfo from = getVersion(pullRequestId, repositoryId, fromVersion);
        VersionInfo to = getVersion(pullRequestId, repositoryId, toVersion);

        VersionComparison comparison = new VersionComparison();
        comparison.setFromVersion(fromVersion);
        comparison.setToVersion(toVersion);

        if (from == null || to == null) {
            comparison.setSummary("비교할 버전을 찾을 수 없습니다.");
            return comparison;
        }

        comparison.setSuggestionDelta(to.getSuggestionCount() - from.getSuggestionCount());
        comparison.setCriticalDelta(to.getCriticalCount() - from.getCriticalCount());
        comparison.setWarningDelta(to.getWarningCount() - from.getWarningCount());
        comparison.setScoreDelta(to.getQualityScore() - from.getQualityScore());

        // Estimate new/resolved issues
        comparison.setNewIssues(Math.max(0, comparison.getSuggestionDelta()));
        comparison.setResolvedIssues(Math.max(0, -comparison.getSuggestionDelta()));

        // Build summary text
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("v%d → v%d: ", fromVersion, toVersion));

        if (comparison.getScoreDelta() > 0) {
            sb.append(String.format("품질 점수 %.1f점 상승 ↑", comparison.getScoreDelta()));
        } else if (comparison.getScoreDelta() < 0) {
            sb.append(String.format("품질 점수 %.1f점 하락 ↓", Math.abs(comparison.getScoreDelta())));
        } else {
            sb.append("품질 점수 변동 없음");
        }

        if (comparison.getCriticalDelta() != 0) {
            sb.append(String.format(", 심각 이슈 %+d", comparison.getCriticalDelta()));
        }

        if (comparison.getSuggestionDelta() != 0) {
            sb.append(String.format(", 총 제안 %+d", comparison.getSuggestionDelta()));
        }

        comparison.setSummary(sb.toString());
        return comparison;
    }

    private VersionInfo toVersionInfo(AnalysisVersionEntity entity) {
        VersionInfo info = new VersionInfo();
        info.setId(entity.getID());
        info.setVersion(entity.getVersion());
        info.setPullRequestId(entity.getPullRequestId());
        info.setRepositoryId(entity.getRepositoryId());
        info.setCommitHash(entity.getCommitHash());
        info.setSourceBranch(entity.getSourceBranch());
        info.setAnalyzedBy(entity.getAnalyzedBy());
        info.setSuggestionCount(entity.getSuggestionCount());
        info.setCriticalCount(entity.getCriticalCount());
        info.setWarningCount(entity.getWarningCount());
        info.setQualityScore(entity.getQualityScore());
        info.setAnalysisTimeMs(entity.getAnalysisTimeMs());
        info.setModelUsed(entity.getModelUsed());
        info.setStatus(entity.getStatus());
        info.setCreatedAt(entity.getCreatedAt());
        return info;
    }
}
