package com.jask.bitbucket.rest;

import com.google.gson.Gson;
import com.jask.bitbucket.model.AnalysisRequest;
import com.jask.bitbucket.model.AnalysisResponse;
import com.jask.bitbucket.model.CodeSuggestion;
import com.jask.bitbucket.security.PermissionCheckService;
import com.jask.bitbucket.service.AnalysisJobService;
import com.jask.bitbucket.service.AnalysisVersionService;
import com.jask.bitbucket.service.BitbucketCommentService;
import com.jask.bitbucket.service.CodeAnalysisService;
import com.jask.bitbucket.service.SuggestionService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST resource for code suggestion operations.
 *
 * Base path: /rest/code-suggestion/1.0
 *
 * 모든 엔드포인트는 인증 필수이며, 레포지토리 권한을 검증합니다.
 */
@Named("codeSuggestionResource")
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CodeSuggestionResource {

    private final CodeAnalysisService codeAnalysisService;
    private final SuggestionService suggestionService;
    private final AnalysisJobService analysisJobService;
    private final AnalysisVersionService analysisVersionService;
    private final BitbucketCommentService commentService;
    private final PermissionCheckService permissionCheck;
    private final Gson gson;

    @Context
    private HttpServletRequest httpRequest;

    @Inject
    public CodeSuggestionResource(CodeAnalysisService codeAnalysisService,
                                   SuggestionService suggestionService,
                                   AnalysisJobService analysisJobService,
                                   AnalysisVersionService analysisVersionService,
                                   BitbucketCommentService commentService,
                                   PermissionCheckService permissionCheck) {
        this.codeAnalysisService = codeAnalysisService;
        this.suggestionService = suggestionService;
        this.analysisJobService = analysisJobService;
        this.analysisVersionService = analysisVersionService;
        this.commentService = commentService;
        this.permissionCheck = permissionCheck;
        this.gson = new Gson();
    }

    /**
     * Trigger async code analysis for a pull request.
     *
     * POST /rest/code-suggestion/1.0/analyze
     * 필요 권한: 레포지토리 REPO_WRITE
     *
     * 비동기로 잡을 큐에 등록하고 202 Accepted + jobId를 반환합니다.
     * 클라이언트는 GET /analyze/{jobId} 로 진행 상태를 폴링합니다.
     */
    @POST
    @Path("/analyze")
    public Response analyzeCode(String requestBody) {
        try {
            AnalysisRequest request = gson.fromJson(requestBody, AnalysisRequest.class);

            if (request.getPullRequestId() == 0 || request.getRepositoryId() == 0) {
                return errorResponse(Response.Status.BAD_REQUEST,
                        "pullRequestId와 repositoryId는 필수입니다.");
            }

            // 권한 검증: 분석 실행은 REPO_WRITE 이상 필요
            permissionCheck.requireRepoWrite(httpRequest, request.getRepositoryId());
            String currentUser = permissionCheck.getUsername(httpRequest);

            // 비동기 잡 등록
            long jobId = analysisJobService.submitJob(
                    request.getPullRequestId(),
                    request.getRepositoryId(),
                    request.getProjectKey(),
                    request.getRepositorySlug(),
                    requestBody,
                    currentUser);

            Map<String, Object> result = new HashMap<>();
            result.put("jobId", jobId);
            result.put("status", "QUEUED");
            result.put("message", "분석 잡이 큐에 등록되었습니다.");

            return Response.status(Response.Status.ACCEPTED)
                    .entity(gson.toJson(result))
                    .header("Location", "/rest/code-suggestion/1.0/analyze/" + jobId)
                    .build();
        } catch (WebApplicationException e) {
            throw e; // 권한 예외는 그대로 전파
        } catch (Exception e) {
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "코드 분석 요청 실패: " + e.getMessage());
        }
    }

    /**
     * Get analysis job status.
     *
     * GET /rest/code-suggestion/1.0/analyze/{jobId}
     * 필요 권한: 인증된 사용자
     */
    @GET
    @Path("/analyze/{jobId}")
    public Response getJobStatus(@PathParam("jobId") long jobId) {
        try {
            permissionCheck.requireAuthentication(httpRequest);

            AnalysisJobService.JobStatus status = analysisJobService.getJobStatus(jobId);
            if (status == null) {
                return errorResponse(Response.Status.NOT_FOUND, "잡을 찾을 수 없습니다: " + jobId);
            }

            return Response.ok(gson.toJson(status)).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "잡 상태 조회 실패: " + e.getMessage());
        }
    }

    /**
     * Cancel an analysis job.
     *
     * DELETE /rest/code-suggestion/1.0/analyze/{jobId}
     * 필요 권한: 인증된 사용자
     */
    @DELETE
    @Path("/analyze/{jobId}")
    public Response cancelJob(@PathParam("jobId") long jobId) {
        try {
            permissionCheck.requireAuthentication(httpRequest);
            String currentUser = permissionCheck.getUsername(httpRequest);

            boolean cancelled = analysisJobService.cancelJob(jobId, currentUser);

            Map<String, Object> result = new HashMap<>();
            if (cancelled) {
                result.put("success", true);
                result.put("message", "분석 잡이 취소되었습니다.");
            } else {
                result.put("success", false);
                result.put("message", "잡을 취소할 수 없습니다 (이미 완료/실패/취소된 잡).");
            }

            return Response.ok(gson.toJson(result)).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "잡 취소 실패: " + e.getMessage());
        }
    }

    /**
     * Get all suggestions for a pull request.
     *
     * GET /rest/code-suggestion/1.0/suggestions/{repoId}/{prId}
     * 필요 권한: 레포지토리 REPO_READ
     */
    @GET
    @Path("/suggestions/{repoId}/{prId}")
    public Response getSuggestions(@PathParam("repoId") int repoId,
                                    @PathParam("prId") long prId) {
        try {
            permissionCheck.requireRepoRead(httpRequest, repoId);

            List<CodeSuggestion> suggestions = suggestionService.getSuggestions(prId, repoId);

            Map<String, Object> result = new HashMap<>();
            result.put("suggestions", suggestions);
            result.put("total", suggestions.size());
            result.put("stats", suggestionService.getStats(prId, repoId));

            return Response.ok(gson.toJson(result)).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "제안 목록 조회 실패: " + e.getMessage());
        }
    }

    /**
     * Get suggestions for a specific file in a pull request.
     *
     * GET /rest/code-suggestion/1.0/suggestions/{repoId}/{prId}/file?path=...
     * 필요 권한: 레포지토리 REPO_READ
     */
    @GET
    @Path("/suggestions/{repoId}/{prId}/file")
    public Response getSuggestionsForFile(@PathParam("repoId") int repoId,
                                           @PathParam("prId") long prId,
                                           @QueryParam("path") String filePath) {
        try {
            permissionCheck.requireRepoRead(httpRequest, repoId);

            if (filePath == null || filePath.isEmpty()) {
                return errorResponse(Response.Status.BAD_REQUEST, "파일 경로가 필요합니다.");
            }

            List<CodeSuggestion> suggestions =
                    suggestionService.getSuggestionsForFile(prId, repoId, filePath);

            return Response.ok(gson.toJson(suggestions)).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "파일 제안 조회 실패: " + e.getMessage());
        }
    }

    /**
     * Update suggestion status (accept/reject/dismiss).
     *
     * PUT /rest/code-suggestion/1.0/suggestions/{suggestionId}/status
     * 필요 권한: 인증된 사용자 (제안 소속 레포 권한은 서비스 레이어에서 검증)
     */
    @PUT
    @Path("/suggestions/{suggestionId}/status")
    public Response updateSuggestionStatus(@PathParam("suggestionId") long suggestionId,
                                            String requestBody) {
        try {
            permissionCheck.requireAuthentication(httpRequest);
            String currentUser = permissionCheck.getUsername(httpRequest);

            Map<String, String> body = gson.fromJson(requestBody, Map.class);
            String status = body.get("status");

            if (status == null || status.isEmpty()) {
                return errorResponse(Response.Status.BAD_REQUEST, "status는 필수입니다.");
            }

            if (!isValidStatus(status)) {
                return errorResponse(Response.Status.BAD_REQUEST,
                        "유효하지 않은 status입니다. (ACCEPTED, REJECTED, DISMISSED 중 하나)");
            }

            CodeSuggestion updated = suggestionService.updateSuggestionStatus(
                    suggestionId, status.toUpperCase(), currentUser);

            return Response.ok(gson.toJson(updated)).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return errorResponse(Response.Status.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "제안 상태 업데이트 실패: " + e.getMessage());
        }
    }

    /**
     * Get suggestion statistics for a pull request.
     *
     * GET /rest/code-suggestion/1.0/stats/{repoId}/{prId}
     * 필요 권한: 레포지토리 REPO_READ
     */
    @GET
    @Path("/stats/{repoId}/{prId}")
    public Response getStats(@PathParam("repoId") int repoId,
                              @PathParam("prId") long prId) {
        try {
            permissionCheck.requireRepoRead(httpRequest, repoId);

            SuggestionService.SuggestionStats stats = suggestionService.getStats(prId, repoId);
            return Response.ok(gson.toJson(stats)).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "통계 조회 실패: " + e.getMessage());
        }
    }

    /**
     * Delete all suggestions for a pull request (re-analyze).
     *
     * DELETE /rest/code-suggestion/1.0/suggestions/{repoId}/{prId}
     * 필요 권한: 레포지토리 REPO_WRITE
     */
    @DELETE
    @Path("/suggestions/{repoId}/{prId}")
    public Response deleteSuggestions(@PathParam("repoId") int repoId,
                                      @PathParam("prId") long prId) {
        try {
            permissionCheck.requireRepoWrite(httpRequest, repoId);

            suggestionService.deleteSuggestions(prId, repoId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "PR #" + prId + "의 제안이 모두 삭제되었습니다.");

            return Response.ok(gson.toJson(result)).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "제안 삭제 실패: " + e.getMessage());
        }
    }

    /**
     * Get analysis version history for a PR.
     *
     * GET /rest/code-suggestion/1.0/versions/{repoId}/{prId}
     * 필요 권한: 레포지토리 REPO_READ
     */
    @GET
    @Path("/versions/{repoId}/{prId}")
    public Response getVersionHistory(@PathParam("repoId") int repoId,
                                       @PathParam("prId") long prId) {
        try {
            permissionCheck.requireRepoRead(httpRequest, repoId);

            List<AnalysisVersionService.VersionInfo> versions =
                    analysisVersionService.getVersionHistory(prId, repoId);

            Map<String, Object> result = new HashMap<>();
            result.put("versions", versions);
            result.put("total", versions.size());
            result.put("latestVersion", versions.isEmpty() ? 0 : versions.get(0).getVersion());

            return Response.ok(gson.toJson(result)).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "버전 히스토리 조회 실패: " + e.getMessage());
        }
    }

    /**
     * Compare two analysis versions.
     *
     * GET /rest/code-suggestion/1.0/versions/{repoId}/{prId}/compare?from=1&to=2
     * 필요 권한: 레포지토리 REPO_READ
     */
    @GET
    @Path("/versions/{repoId}/{prId}/compare")
    public Response compareVersions(@PathParam("repoId") int repoId,
                                     @PathParam("prId") long prId,
                                     @QueryParam("from") int fromVersion,
                                     @QueryParam("to") int toVersion) {
        try {
            permissionCheck.requireRepoRead(httpRequest, repoId);

            if (fromVersion <= 0 || toVersion <= 0) {
                return errorResponse(Response.Status.BAD_REQUEST,
                        "from과 to 버전 번호가 필요합니다.");
            }

            AnalysisVersionService.VersionComparison comparison =
                    analysisVersionService.compareVersions(prId, repoId, fromVersion, toVersion);

            return Response.ok(gson.toJson(comparison)).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "버전 비교 실패: " + e.getMessage());
        }
    }

    /**
     * Insert a suggestion as a PR inline comment.
     *
     * POST /rest/code-suggestion/1.0/suggestions/{suggestionId}/comment
     * 필요 권한: 인증된 사용자
     *
     * Body: { "projectKey": "...", "repoSlug": "...", "prId": 123 }
     */
    @POST
    @Path("/suggestions/{suggestionId}/comment")
    public Response insertSuggestionAsComment(@PathParam("suggestionId") long suggestionId,
                                               String requestBody) {
        try {
            permissionCheck.requireAuthentication(httpRequest);

            Map<String, String> body = gson.fromJson(requestBody, Map.class);
            String projectKey = body.get("projectKey");
            String repoSlug = body.get("repoSlug");
            String prIdStr = body.get("prId");

            if (projectKey == null || repoSlug == null || prIdStr == null) {
                return errorResponse(Response.Status.BAD_REQUEST,
                        "projectKey, repoSlug, prId는 필수입니다.");
            }

            long prId = Long.parseLong(prIdStr);

            // 제안 정보 조회 (SuggestionService 통해서)
            // 일단 간단한 방식: 모든 제안에서 ID로 찾기
            List<CodeSuggestion> suggestions = suggestionService.getSuggestions(prId, 0);
            CodeSuggestion target = null;
            for (CodeSuggestion s : suggestions) {
                if (s.getId() == suggestionId) {
                    target = s;
                    break;
                }
            }

            if (target == null) {
                return errorResponse(Response.Status.NOT_FOUND, "제안을 찾을 수 없습니다: " + suggestionId);
            }

            // Format as comment
            BitbucketCommentService.SuggestionCommentData commentData =
                    new BitbucketCommentService.SuggestionCommentData();
            commentData.setFilePath(target.getFilePath());
            commentData.setStartLine(target.getStartLine());
            commentData.setEndLine(target.getEndLine());
            commentData.setOriginalCode(target.getOriginalCode());
            commentData.setSuggestedCode(target.getSuggestedCode());
            commentData.setExplanation(target.getExplanation());
            commentData.setSeverity(target.getSeverity() != null ? target.getSeverity().name() : "INFO");
            commentData.setCategory(target.getCategory() != null ? target.getCategory().name() : "BEST_PRACTICE");
            commentData.setConfidence(target.getConfidence());

            String commentText = commentService.formatSuggestionAsComment(commentData);

            // Create inline comment (if line info available) or general comment
            long commentId;
            if (target.getStartLine() > 0 && target.getFilePath() != null) {
                commentId = commentService.createInlineComment(
                        projectKey, repoSlug, prId,
                        target.getFilePath(), target.getStartLine(), commentText);
            } else {
                commentId = commentService.createPrComment(projectKey, repoSlug, prId, commentText);
            }

            Map<String, Object> result = new HashMap<>();
            if (commentId > 0) {
                result.put("success", true);
                result.put("commentId", commentId);
                result.put("message", "코멘트가 생성되었습니다.");
            } else {
                result.put("success", false);
                result.put("message", "코멘트 생성에 실패했습니다.");
            }

            return Response.ok(gson.toJson(result)).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "코멘트 삽입 실패: " + e.getMessage());
        }
    }

    private boolean isValidStatus(String status) {
        String upper = status.toUpperCase();
        return "ACCEPTED".equals(upper) || "REJECTED".equals(upper) || "DISMISSED".equals(upper);
    }

    private Response errorResponse(Response.Status status, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        return Response.status(status).entity(gson.toJson(error)).build();
    }
}
