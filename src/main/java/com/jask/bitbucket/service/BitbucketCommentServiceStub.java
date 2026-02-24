package com.jask.bitbucket.service;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

/**
 * BitbucketCommentService의 no-op 스텁 구현체.
 *
 * 원본 BitbucketCommentServiceImpl은 Bitbucket Server API
 * (CommentService, PullRequestService, RepositoryService)에 의존하여
 * Bitbucket 6.9.1 호환성 문제로 비활성화(.java.disabled) 상태.
 *
 * 이 스텁은 외부 @ComponentImport 의존성이 없으므로 Spring 빈 생성이
 * 반드시 성공하며, CodeSuggestionResource의 DI 체인이 정상 작동하도록 보장.
 *
 * PR 코멘트 작성 기능은 향후 Bitbucket API 호환성 확보 시 활성화 예정.
 */
@ExportAsService({BitbucketCommentService.class})
@Named("bitbucketCommentService")
public class BitbucketCommentServiceStub implements BitbucketCommentService {

    private static final Logger log = LoggerFactory.getLogger(BitbucketCommentServiceStub.class);

    private static final String COMMENT_PREFIX = "\uD83E\uDD16 **AI \ucf54\ub4dc \uc81c\uc548**";
    private static final String SEVERITY_CRITICAL = "\uD83D\uDD34";
    private static final String SEVERITY_WARNING = "\uD83D\uDFE1";
    private static final String SEVERITY_INFO = "\uD83D\uDD35";
    private static final String SEVERITY_HINT = "\u26AA";

    @Override
    public long createPrComment(String projectKey, String repoSlug, long prId, String commentText) {
        log.warn("BitbucketCommentService stub: PR 코멘트 생성 미지원 (Bitbucket API 비활성화). " +
                "project={}, repo={}, prId={}", projectKey, repoSlug, prId);
        return -1;
    }

    @Override
    public long createInlineComment(String projectKey, String repoSlug, long prId,
                                     String filePath, int line, String commentText) {
        log.warn("BitbucketCommentService stub: 인라인 코멘트 생성 미지원 (Bitbucket API 비활성화). " +
                "project={}, repo={}, prId={}, file={}, line={}",
                projectKey, repoSlug, prId, filePath, line);
        return -1;
    }

    @Override
    public String formatSuggestionAsComment(SuggestionCommentData suggestion) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(COMMENT_PREFIX).append("\n\n");

        // Severity + Category
        String severityIcon = getSeverityIcon(suggestion.getSeverity());
        sb.append(severityIcon).append(" **").append(suggestion.getSeverity()).append("**");
        sb.append(" | ").append(suggestion.getCategory());
        sb.append(" | \ud655\uc2e0\ub3c4: ").append(Math.round(suggestion.getConfidence() * 100)).append("%\n\n");

        // Explanation
        sb.append(suggestion.getExplanation()).append("\n\n");

        // Code suggestion
        if (suggestion.getOriginalCode() != null && !suggestion.getOriginalCode().isEmpty()) {
            sb.append("**\uae30\uc874 \ucf54\ub4dc:**\n");
            sb.append("```\n").append(suggestion.getOriginalCode()).append("\n```\n\n");
        }

        if (suggestion.getSuggestedCode() != null && !suggestion.getSuggestedCode().isEmpty()) {
            sb.append("**\uac1c\uc120 \ucf54\ub4dc:**\n");
            sb.append("```suggestion\n").append(suggestion.getSuggestedCode()).append("\n```\n\n");
        }

        // File/line info
        if (suggestion.getFilePath() != null) {
            sb.append("\uD83D\uDCC1 `").append(suggestion.getFilePath()).append("`");
            if (suggestion.getStartLine() > 0) {
                sb.append(" (L").append(suggestion.getStartLine());
                if (suggestion.getEndLine() > suggestion.getStartLine()) {
                    sb.append("-").append(suggestion.getEndLine());
                }
                sb.append(")");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String getSeverityIcon(String severity) {
        if (severity == null) return SEVERITY_INFO;
        switch (severity.toUpperCase()) {
            case "CRITICAL": return SEVERITY_CRITICAL;
            case "WARNING": return SEVERITY_WARNING;
            case "INFO": return SEVERITY_INFO;
            case "HINT": return SEVERITY_HINT;
            default: return SEVERITY_INFO;
        }
    }
}
