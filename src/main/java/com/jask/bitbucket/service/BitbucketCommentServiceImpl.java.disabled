package com.jask.bitbucket.service;

import com.atlassian.bitbucket.comment.AddFileCommentRequest;
import com.atlassian.bitbucket.comment.CommentService;
import com.atlassian.bitbucket.comment.AddCommentRequest;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Bitbucket Server REST API를 이용한 PR 코멘트 생성 서비스.
 * 제안을 Markdown 형식의 인라인 코멘트로 변환하여 PR에 삽입합니다.
 */
@ExportAsService({BitbucketCommentService.class})
@Named("bitbucketCommentService")
public class BitbucketCommentServiceImpl implements BitbucketCommentService {

    private static final Logger log = LoggerFactory.getLogger(BitbucketCommentServiceImpl.class);

    private static final String COMMENT_PREFIX = "\uD83E\uDD16 **AI 코드 제안**";
    private static final String SEVERITY_CRITICAL = "\uD83D\uDD34";
    private static final String SEVERITY_WARNING = "\uD83D\uDFE1";
    private static final String SEVERITY_INFO = "\uD83D\uDD35";
    private static final String SEVERITY_HINT = "\u26AA";

    private final CommentService commentService;
    private final PullRequestService pullRequestService;
    private final RepositoryService repositoryService;

    @Inject
    public BitbucketCommentServiceImpl(@ComponentImport CommentService commentService,
                                        @ComponentImport PullRequestService pullRequestService,
                                        @ComponentImport RepositoryService repositoryService) {
        this.commentService = commentService;
        this.pullRequestService = pullRequestService;
        this.repositoryService = repositoryService;
    }

    @Override
    public long createPrComment(String projectKey, String repoSlug, long prId, String commentText) {
        try {
            Repository repo = repositoryService.getBySlug(projectKey, repoSlug);
            if (repo == null) {
                log.error("레포지토리를 찾을 수 없음: {}/{}", projectKey, repoSlug);
                return -1;
            }

            PullRequest pr = pullRequestService.getById(repo.getId(), prId);
            if (pr == null) {
                log.error("PR을 찾을 수 없음: PR #{}", prId);
                return -1;
            }

            AddCommentRequest request = new AddCommentRequest.Builder(pr, commentText).build();
            com.atlassian.bitbucket.comment.Comment comment = commentService.addComment(request);

            log.info("PR 코멘트 생성: PR #{}, commentId={}", prId, comment.getId());
            return comment.getId();
        } catch (Exception e) {
            log.error("PR 코멘트 생성 실패: {}", e.getMessage(), e);
            return -1;
        }
    }

    @Override
    public long createInlineComment(String projectKey, String repoSlug, long prId,
                                     String filePath, int line, String commentText) {
        try {
            Repository repo = repositoryService.getBySlug(projectKey, repoSlug);
            if (repo == null) {
                log.error("레포지토리를 찾을 수 없음: {}/{}", projectKey, repoSlug);
                return -1;
            }

            PullRequest pr = pullRequestService.getById(repo.getId(), prId);
            if (pr == null) {
                log.error("PR을 찾을 수 없음: PR #{}", prId);
                return -1;
            }

            AddFileCommentRequest request = new AddFileCommentRequest.Builder(
                    pr, commentText, filePath, line).build();
            com.atlassian.bitbucket.comment.Comment comment = commentService.addComment(request);

            log.info("인라인 코멘트 생성: PR #{}, file={}, line={}, commentId={}",
                    prId, filePath, line, comment.getId());
            return comment.getId();
        } catch (Exception e) {
            log.error("인라인 코멘트 생성 실패: {}", e.getMessage(), e);
            return -1;
        }
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
        sb.append(" | 확신도: ").append(Math.round(suggestion.getConfidence() * 100)).append("%\n\n");

        // Explanation
        sb.append(suggestion.getExplanation()).append("\n\n");

        // Code suggestion
        if (suggestion.getOriginalCode() != null && !suggestion.getOriginalCode().isEmpty()) {
            sb.append("**기존 코드:**\n");
            sb.append("```\n").append(suggestion.getOriginalCode()).append("\n```\n\n");
        }

        if (suggestion.getSuggestedCode() != null && !suggestion.getSuggestedCode().isEmpty()) {
            sb.append("**개선 코드:**\n");
            sb.append("```suggestion\n").append(suggestion.getSuggestedCode()).append("\n```\n\n");
        }

        // File/line info
        if (suggestion.getFilePath() != null) {
            sb.append("📁 `").append(suggestion.getFilePath()).append("`");
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
