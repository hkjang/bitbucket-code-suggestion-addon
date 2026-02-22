package com.jask.bitbucket.rest;

import com.google.gson.Gson;
import com.jask.bitbucket.config.PluginSettingsService;
import com.jask.bitbucket.security.EndpointValidator;
import com.jask.bitbucket.security.PermissionCheckService;
import com.jask.bitbucket.service.AuditLogService;
import com.jask.bitbucket.service.ClusterJobManager;
import com.jask.bitbucket.service.LlmClientService;
import com.jask.bitbucket.service.MetricsService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

/**
 * REST resource for admin configuration management.
 *
 * Base path: /rest/code-suggestion/1.0/admin
 *
 * 모든 엔드포인트는 시스템 관리자(SYS_ADMIN) 권한이 필요합니다.
 */
@Named("adminConfigResource")
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminConfigResource {

    private final PluginSettingsService settingsService;
    private final LlmClientService llmClientService;
    private final PermissionCheckService permissionCheck;
    private final AuditLogService auditLogService;
    private final ClusterJobManager clusterJobManager;
    private final MetricsService metricsService;
    private final Gson gson;

    @Context
    private HttpServletRequest httpRequest;

    @Inject
    public AdminConfigResource(PluginSettingsService settingsService,
                                LlmClientService llmClientService,
                                PermissionCheckService permissionCheck,
                                AuditLogService auditLogService,
                                ClusterJobManager clusterJobManager,
                                MetricsService metricsService) {
        this.settingsService = settingsService;
        this.llmClientService = llmClientService;
        this.permissionCheck = permissionCheck;
        this.auditLogService = auditLogService;
        this.clusterJobManager = clusterJobManager;
        this.metricsService = metricsService;
        this.gson = new Gson();
    }

    /**
     * Get current plugin settings.
     *
     * GET /rest/code-suggestion/1.0/admin/settings
     * 필요 권한: SYS_ADMIN
     */
    @GET
    @Path("/settings")
    public Response getSettings() {
        permissionCheck.requireSysAdmin(httpRequest);

        Map<String, Object> settings = new HashMap<>();

        // LLM settings
        settings.put("llmEndpoint", settingsService.getLlmEndpoint());
        settings.put("llmModel", settingsService.getLlmModel());
        settings.put("llmTemperature", settingsService.getLlmTemperature());
        settings.put("llmMaxTokens", settingsService.getLlmMaxTokens());
        settings.put("llmHasApiKey", !settingsService.getLlmApiKey().isEmpty());

        // Analysis settings
        settings.put("autoAnalysisEnabled", settingsService.isAutoAnalysisEnabled());
        settings.put("mergeCheckEnabled", settingsService.isMergeCheckEnabled());
        settings.put("mergeCheckMaxCritical", settingsService.getMergeCheckMaxCritical());
        settings.put("minConfidenceThreshold", settingsService.getMinConfidenceThreshold());

        // File settings
        settings.put("excludedFilePatterns", settingsService.getExcludedFilePatterns());
        settings.put("supportedLanguages", settingsService.getSupportedLanguages());
        settings.put("maxFilesPerAnalysis", settingsService.getMaxFilesPerAnalysis());
        settings.put("maxFileSizeKb", settingsService.getMaxFileSizeKb());

        return Response.ok(gson.toJson(settings)).build();
    }

    /**
     * Update plugin settings.
     *
     * PUT /rest/code-suggestion/1.0/admin/settings
     * 필요 권한: SYS_ADMIN
     */
    @PUT
    @Path("/settings")
    public Response updateSettings(String requestBody) {
        permissionCheck.requireSysAdmin(httpRequest);

        try {
            Map<String, Object> settings = gson.fromJson(requestBody, Map.class);

            // LLM settings (SSRF 방지 검증 포함)
            if (settings.containsKey("llmEndpoint")) {
                String endpoint = (String) settings.get("llmEndpoint");
                // Ollama 등 로컬 LLM을 위해 localhost 허용
                EndpointValidator.ValidationResult validation =
                        EndpointValidator.validate(endpoint, true);
                if (!validation.isValid()) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", "엔드포인트 검증 실패: " + validation.getMessage());
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(gson.toJson(error)).build();
                }
                if (!EndpointValidator.isTlsEndpoint(endpoint)) {
                    // TLS가 아닌 경우 경고 (차단은 하지 않음, Ollama 등 로컬 사용 시)
                    // 클라이언트에게 경고를 전달하기 위해 로그만 기록
                    org.slf4j.LoggerFactory.getLogger(AdminConfigResource.class)
                            .warn("TLS가 적용되지 않은 LLM 엔드포인트: {}", endpoint);
                }
                settingsService.setLlmEndpoint(endpoint);
            }
            if (settings.containsKey("llmApiKey")) {
                String apiKey = (String) settings.get("llmApiKey");
                if (apiKey != null && !apiKey.isEmpty()) {
                    settingsService.setLlmApiKey(apiKey);
                }
            }
            if (settings.containsKey("llmModel")) {
                settingsService.setLlmModel((String) settings.get("llmModel"));
            }
            if (settings.containsKey("llmTemperature")) {
                settingsService.setLlmTemperature(((Number) settings.get("llmTemperature")).doubleValue());
            }
            if (settings.containsKey("llmMaxTokens")) {
                settingsService.setLlmMaxTokens(((Number) settings.get("llmMaxTokens")).intValue());
            }

            // Analysis settings
            if (settings.containsKey("autoAnalysisEnabled")) {
                settingsService.setAutoAnalysisEnabled((Boolean) settings.get("autoAnalysisEnabled"));
            }
            if (settings.containsKey("mergeCheckEnabled")) {
                settingsService.setMergeCheckEnabled((Boolean) settings.get("mergeCheckEnabled"));
            }
            if (settings.containsKey("mergeCheckMaxCritical")) {
                settingsService.setMergeCheckMaxCritical(((Number) settings.get("mergeCheckMaxCritical")).intValue());
            }
            if (settings.containsKey("minConfidenceThreshold")) {
                settingsService.setMinConfidenceThreshold(((Number) settings.get("minConfidenceThreshold")).doubleValue());
            }

            // File settings
            if (settings.containsKey("excludedFilePatterns")) {
                settingsService.setExcludedFilePatterns((String) settings.get("excludedFilePatterns"));
            }
            if (settings.containsKey("supportedLanguages")) {
                settingsService.setSupportedLanguages((String) settings.get("supportedLanguages"));
            }
            if (settings.containsKey("maxFilesPerAnalysis")) {
                settingsService.setMaxFilesPerAnalysis(((Number) settings.get("maxFilesPerAnalysis")).intValue());
            }
            if (settings.containsKey("maxFileSizeKb")) {
                settingsService.setMaxFileSizeKb(((Number) settings.get("maxFileSizeKb")).intValue());
            }

            // 감사 로그 기록
            String username = permissionCheck.getUsername(httpRequest);
            String ip = httpRequest != null ? httpRequest.getRemoteAddr() : "unknown";
            auditLogService.log("SETTINGS_CHANGED", username, "GLOBAL_SETTINGS",
                    null, requestBody, ip);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "설정이 저장되었습니다.");
            return Response.ok(gson.toJson(result)).build();

        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "설정 저장 실패: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(gson.toJson(error)).build();
        }
    }

    /**
     * Get audit logs.
     *
     * GET /rest/code-suggestion/1.0/admin/audit-log?page=0&size=50&user=...&type=...
     * 필요 권한: SYS_ADMIN
     */
    @GET
    @Path("/audit-log")
    public Response getAuditLog(@QueryParam("page") @DefaultValue("0") int page,
                                 @QueryParam("size") @DefaultValue("50") int size,
                                 @QueryParam("user") String user,
                                 @QueryParam("type") String eventType) {
        permissionCheck.requireSysAdmin(httpRequest);

        try {
            java.util.List<AuditLogService.AuditEntry> entries;
            if (user != null && !user.isEmpty()) {
                entries = auditLogService.getAuditLogByUser(user, page, size);
            } else if (eventType != null && !eventType.isEmpty()) {
                entries = auditLogService.getAuditLogByEventType(eventType, page, size);
            } else {
                entries = auditLogService.getAuditLog(page, size);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("entries", entries);
            result.put("total", auditLogService.getTotalCount());
            result.put("page", page);
            result.put("size", size);

            return Response.ok(gson.toJson(result)).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "감사 로그 조회 실패: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(gson.toJson(error)).build();
        }
    }

    /**
     * Get cluster job statistics.
     *
     * GET /rest/code-suggestion/1.0/admin/cluster-stats
     * 필요 권한: SYS_ADMIN
     */
    @GET
    @Path("/cluster-stats")
    public Response getClusterStats() {
        permissionCheck.requireSysAdmin(httpRequest);

        try {
            ClusterJobManager.ClusterJobStats stats = clusterJobManager.getClusterStats();
            return Response.ok(gson.toJson(stats)).build();
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "클러스터 통계 조회 실패: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(gson.toJson(error)).build();
        }
    }

    /**
     * Test LLM connection.
     *
     * POST /rest/code-suggestion/1.0/admin/test-connection
     * 필요 권한: SYS_ADMIN
     */
    @POST
    @Path("/test-connection")
    public Response testConnection() {
        permissionCheck.requireSysAdmin(httpRequest);

        Map<String, Object> result = new HashMap<>();

        boolean healthy = llmClientService.healthCheck();
        result.put("success", healthy);
        result.put("endpoint", settingsService.getLlmEndpoint());
        result.put("model", settingsService.getLlmModel());

        if (healthy) {
            result.put("message", "LLM 서비스 연결에 성공했습니다.");
        } else {
            result.put("message", "LLM 서비스 연결에 실패했습니다. 엔드포인트 및 네트워크 설정을 확인해주세요.");
        }

        return Response.ok(gson.toJson(result)).build();
    }

    /**
     * Get plugin metrics.
     *
     * GET /rest/code-suggestion/1.0/admin/metrics
     * 필요 권한: SYS_ADMIN
     */
    @GET
    @Path("/metrics")
    public Response getMetrics() {
        permissionCheck.requireSysAdmin(httpRequest);

        try {
            Map<String, Object> metrics = metricsService.getAllMetrics();
            return Response.ok(gson.toJson(metrics)).build();
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "메트릭 조회 실패: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(gson.toJson(error)).build();
        }
    }

    /**
     * Reset plugin metrics.
     *
     * DELETE /rest/code-suggestion/1.0/admin/metrics
     * 필요 권한: SYS_ADMIN
     */
    @DELETE
    @Path("/metrics")
    public Response resetMetrics() {
        permissionCheck.requireSysAdmin(httpRequest);

        metricsService.reset();

        String username = permissionCheck.getUsername(httpRequest);
        String ip = httpRequest != null ? httpRequest.getRemoteAddr() : "unknown";
        auditLogService.log("METRICS_RESET", username, "METRICS", null, null, ip);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "메트릭이 초기화되었습니다.");
        return Response.ok(gson.toJson(result)).build();
    }
}
