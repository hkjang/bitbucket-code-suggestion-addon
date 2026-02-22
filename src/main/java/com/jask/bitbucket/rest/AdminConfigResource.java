package com.jask.bitbucket.rest;

import com.google.gson.Gson;
import com.jask.bitbucket.config.PluginSettingsService;
import com.jask.bitbucket.security.EndpointValidator;
import com.jask.bitbucket.security.PermissionCheckService;
import com.jask.bitbucket.security.PermissionCheckService.AdminSection;
import com.jask.bitbucket.service.*;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * REST resource for admin configuration management.
 *
 * Base path: /rest/code-suggestion/1.0/admin
 *
 * RBAC 기반 접근 제어:
 * - SYS_ADMIN: 전체 접근
 * - PROJECT_ADMIN: 프로젝트 정책, 감사 로그
 * - AUDIT_VIEWER: 감사 로그 읽기
 * - READ_ONLY: 사용량/진단 조회
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
    private final EngineProfileService engineProfileService;
    private final UsageQuotaService usageQuotaService;
    private final AlertService alertService;
    private final SettingsBackupService settingsBackupService;
    private final ChangeApprovalService changeApprovalService;
    private final DryRunService dryRunService;
    private final DiagnosticsService diagnosticsService;
    private final PolicyTemplateService policyTemplateService;
    private final MaskingRuleService maskingRuleService;
    private final Gson gson;

    @Context
    private HttpServletRequest httpRequest;

    @Inject
    public AdminConfigResource(PluginSettingsService settingsService,
                                LlmClientService llmClientService,
                                PermissionCheckService permissionCheck,
                                AuditLogService auditLogService,
                                ClusterJobManager clusterJobManager,
                                MetricsService metricsService,
                                EngineProfileService engineProfileService,
                                UsageQuotaService usageQuotaService,
                                AlertService alertService,
                                SettingsBackupService settingsBackupService,
                                ChangeApprovalService changeApprovalService,
                                DryRunService dryRunService,
                                DiagnosticsService diagnosticsService,
                                PolicyTemplateService policyTemplateService,
                                MaskingRuleService maskingRuleService) {
        this.settingsService = settingsService;
        this.llmClientService = llmClientService;
        this.permissionCheck = permissionCheck;
        this.auditLogService = auditLogService;
        this.clusterJobManager = clusterJobManager;
        this.metricsService = metricsService;
        this.engineProfileService = engineProfileService;
        this.usageQuotaService = usageQuotaService;
        this.alertService = alertService;
        this.settingsBackupService = settingsBackupService;
        this.changeApprovalService = changeApprovalService;
        this.dryRunService = dryRunService;
        this.diagnosticsService = diagnosticsService;
        this.policyTemplateService = policyTemplateService;
        this.maskingRuleService = maskingRuleService;
        this.gson = new Gson();
    }

    // =========================================================================
    // 관리자 네비게이션 (요건 1: IA 구조)
    // =========================================================================

    /**
     * 현재 사용자가 접근 가능한 관리 섹션 목록을 반환합니다.
     * UI에서 좌측 네비게이션 렌더링에 사용합니다.
     *
     * GET /rest/code-suggestion/1.0/admin/navigation
     */
    @GET
    @Path("/navigation")
    public Response getNavigation() {
        permissionCheck.requireAuthentication(httpRequest);

        Set<AdminSection> sections = permissionCheck.getAccessibleSections(httpRequest);

        List<Map<String, Object>> navItems = new ArrayList<>();
        for (AdminSection section : AdminSection.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", section.name());
            item.put("label", getSectionLabel(section));
            item.put("icon", getSectionIcon(section));
            item.put("accessible", sections.contains(section));
            navItems.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sections", navItems);
        result.put("pendingApprovals", changeApprovalService.getPendingCount());
        result.put("unreadAlerts", alertService.getUnacknowledgedCount());

        return Response.ok(gson.toJson(result)).build();
    }

    // =========================================================================
    // 전역 설정 (요건 3)
    // =========================================================================

    @GET
    @Path("/settings")
    public Response getSettings() {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);

        Map<String, Object> settings = new HashMap<>();
        settings.put("llmEndpoint", settingsService.getLlmEndpoint());
        settings.put("llmModel", settingsService.getLlmModel());
        settings.put("llmTemperature", settingsService.getLlmTemperature());
        settings.put("llmMaxTokens", settingsService.getLlmMaxTokens());
        settings.put("llmHasApiKey", !settingsService.getLlmApiKey().isEmpty());
        settings.put("autoAnalysisEnabled", settingsService.isAutoAnalysisEnabled());
        settings.put("mergeCheckEnabled", settingsService.isMergeCheckEnabled());
        settings.put("mergeCheckMaxCritical", settingsService.getMergeCheckMaxCritical());
        settings.put("minConfidenceThreshold", settingsService.getMinConfidenceThreshold());
        settings.put("excludedFilePatterns", settingsService.getExcludedFilePatterns());
        settings.put("supportedLanguages", settingsService.getSupportedLanguages());
        settings.put("maxFilesPerAnalysis", settingsService.getMaxFilesPerAnalysis());
        settings.put("maxFileSizeKb", settingsService.getMaxFileSizeKb());

        return Response.ok(gson.toJson(settings)).build();
    }

    @PUT
    @Path("/settings")
    @SuppressWarnings("unchecked")
    public Response updateSettings(String requestBody) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);

        try {
            Map<String, Object> settings = gson.fromJson(requestBody, Map.class);

            if (settings.containsKey("llmEndpoint")) {
                String endpoint = (String) settings.get("llmEndpoint");
                EndpointValidator.ValidationResult validation =
                        EndpointValidator.validate(endpoint, true);
                if (!validation.isValid()) {
                    return errorResponse(Response.Status.BAD_REQUEST,
                            "엔드포인트 검증 실패: " + validation.getMessage());
                }
                settingsService.setLlmEndpoint(endpoint);
            }
            if (settings.containsKey("llmApiKey")) {
                String apiKey = (String) settings.get("llmApiKey");
                if (apiKey != null && !apiKey.isEmpty()) {
                    settingsService.setLlmApiKey(apiKey);
                }
            }
            if (settings.containsKey("llmModel"))
                settingsService.setLlmModel((String) settings.get("llmModel"));
            if (settings.containsKey("llmTemperature"))
                settingsService.setLlmTemperature(((Number) settings.get("llmTemperature")).doubleValue());
            if (settings.containsKey("llmMaxTokens"))
                settingsService.setLlmMaxTokens(((Number) settings.get("llmMaxTokens")).intValue());
            if (settings.containsKey("autoAnalysisEnabled"))
                settingsService.setAutoAnalysisEnabled((Boolean) settings.get("autoAnalysisEnabled"));
            if (settings.containsKey("mergeCheckEnabled"))
                settingsService.setMergeCheckEnabled((Boolean) settings.get("mergeCheckEnabled"));
            if (settings.containsKey("mergeCheckMaxCritical"))
                settingsService.setMergeCheckMaxCritical(((Number) settings.get("mergeCheckMaxCritical")).intValue());
            if (settings.containsKey("minConfidenceThreshold"))
                settingsService.setMinConfidenceThreshold(((Number) settings.get("minConfidenceThreshold")).doubleValue());
            if (settings.containsKey("excludedFilePatterns"))
                settingsService.setExcludedFilePatterns((String) settings.get("excludedFilePatterns"));
            if (settings.containsKey("supportedLanguages"))
                settingsService.setSupportedLanguages((String) settings.get("supportedLanguages"));
            if (settings.containsKey("maxFilesPerAnalysis"))
                settingsService.setMaxFilesPerAnalysis(((Number) settings.get("maxFilesPerAnalysis")).intValue());
            if (settings.containsKey("maxFileSizeKb"))
                settingsService.setMaxFileSizeKb(((Number) settings.get("maxFileSizeKb")).intValue());

            auditLog("SETTINGS_CHANGED", "GLOBAL_SETTINGS", null, requestBody);
            return successResponse("설정이 저장되었습니다.");
        } catch (WebApplicationException e) { throw e; }
        catch (Exception e) { return serverError("설정 저장 실패: " + e.getMessage()); }
    }

    // =========================================================================
    // 엔진 프로파일 관리 (요건 5, 6, 7)
    // =========================================================================

    @GET
    @Path("/engines")
    public Response getEngineProfiles() {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.ENGINE_CONNECTION);
        return Response.ok(gson.toJson(engineProfileService.getAllProfiles())).build();
    }

    @POST
    @Path("/engines")
    public Response createEngineProfile(String requestBody) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.ENGINE_CONNECTION);
        try {
            EngineProfileService.CreateProfileRequest req = gson.fromJson(requestBody,
                    EngineProfileService.CreateProfileRequest.class);
            String username = permissionCheck.getUsername(httpRequest);
            EngineProfileService.EngineProfileInfo profile = engineProfileService.createProfile(req, username);
            auditLog("ENGINE_CREATED", "ENGINE", profile.getProfileName(), null);
            return Response.status(Response.Status.CREATED).entity(gson.toJson(profile)).build();
        } catch (IllegalArgumentException e) { return errorResponse(Response.Status.BAD_REQUEST, e.getMessage()); }
        catch (Exception e) { return serverError("엔진 프로파일 생성 실패: " + e.getMessage()); }
    }

    @PUT
    @Path("/engines/{id}")
    public Response updateEngineProfile(@PathParam("id") int id, String requestBody) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.ENGINE_CONNECTION);
        try {
            EngineProfileService.UpdateProfileRequest req = gson.fromJson(requestBody,
                    EngineProfileService.UpdateProfileRequest.class);
            EngineProfileService.EngineProfileInfo profile = engineProfileService.updateProfile(id, req);
            auditLog("ENGINE_UPDATED", "ENGINE", profile.getProfileName(), null);
            return Response.ok(gson.toJson(profile)).build();
        } catch (IllegalArgumentException e) { return errorResponse(Response.Status.BAD_REQUEST, e.getMessage()); }
        catch (Exception e) { return serverError("엔진 프로파일 수정 실패: " + e.getMessage()); }
    }

    @DELETE
    @Path("/engines/{id}")
    public Response deleteEngineProfile(@PathParam("id") int id) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.ENGINE_CONNECTION);
        try {
            engineProfileService.deleteProfile(id);
            auditLog("ENGINE_DELETED", "ENGINE", String.valueOf(id), null);
            return successResponse("엔진 프로파일이 삭제되었습니다.");
        } catch (Exception e) { return errorResponse(Response.Status.BAD_REQUEST, e.getMessage()); }
    }

    @POST
    @Path("/engines/{id}/test")
    public Response testEngineConnection(@PathParam("id") int id) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.ENGINE_CONNECTION);
        try {
            EngineProfileService.ConnectionTestResult result = engineProfileService.testConnection(id);
            return Response.ok(gson.toJson(result)).build();
        } catch (Exception e) { return serverError("연결 테스트 실패: " + e.getMessage()); }
    }

    @PUT
    @Path("/engines/{id}/default")
    public Response setDefaultEngine(@PathParam("id") int id) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.ENGINE_CONNECTION);
        try {
            engineProfileService.setDefaultProfile(id);
            auditLog("ENGINE_DEFAULT_CHANGED", "ENGINE", String.valueOf(id), null);
            return successResponse("기본 프로파일이 변경되었습니다.");
        } catch (Exception e) { return errorResponse(Response.Status.BAD_REQUEST, e.getMessage()); }
    }

    // =========================================================================
    // 마스킹 규칙 관리 (요건 9, 10)
    // =========================================================================

    @GET
    @Path("/masking-rules")
    public Response getMaskingRules() {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.SECURITY_MASKING);
        return Response.ok(gson.toJson(maskingRuleService.getAllRules())).build();
    }

    @POST
    @Path("/masking-rules")
    @SuppressWarnings("unchecked")
    public Response addMaskingRule(String requestBody) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.SECURITY_MASKING);
        try {
            Map<String, String> req = gson.fromJson(requestBody, Map.class);
            MaskingRuleService.MaskingRule rule = maskingRuleService.addCustomRule(
                    req.get("name"), req.get("pattern"), req.get("replacement"),
                    req.getOrDefault("category", "CUSTOM"));
            auditLog("MASKING_RULE_CREATED", "MASKING", rule.getId(), null);
            return Response.status(Response.Status.CREATED).entity(gson.toJson(rule)).build();
        } catch (IllegalArgumentException e) { return errorResponse(Response.Status.BAD_REQUEST, e.getMessage()); }
        catch (Exception e) { return serverError("마스킹 규칙 추가 실패: " + e.getMessage()); }
    }

    @PUT
    @Path("/masking-rules/{id}")
    @SuppressWarnings("unchecked")
    public Response updateMaskingRule(@PathParam("id") String id, String requestBody) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.SECURITY_MASKING);
        try {
            Map<String, Object> req = gson.fromJson(requestBody, Map.class);
            MaskingRuleService.MaskingRule rule = maskingRuleService.updateCustomRule(
                    id, (String) req.get("name"), (String) req.get("pattern"),
                    (String) req.get("replacement"),
                    req.containsKey("enabled") ? (Boolean) req.get("enabled") : true,
                    (String) req.getOrDefault("category", "CUSTOM"));
            auditLog("MASKING_RULE_UPDATED", "MASKING", id, null);
            return Response.ok(gson.toJson(rule)).build();
        } catch (IllegalArgumentException e) { return errorResponse(Response.Status.BAD_REQUEST, e.getMessage()); }
        catch (Exception e) { return serverError("마스킹 규칙 수정 실패: " + e.getMessage()); }
    }

    @DELETE
    @Path("/masking-rules/{id}")
    public Response deleteMaskingRule(@PathParam("id") String id) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.SECURITY_MASKING);
        try {
            maskingRuleService.deleteCustomRule(id);
            auditLog("MASKING_RULE_DELETED", "MASKING", id, null);
            return successResponse("마스킹 규칙이 삭제되었습니다.");
        } catch (IllegalArgumentException e) { return errorResponse(Response.Status.BAD_REQUEST, e.getMessage()); }
    }

    @POST
    @Path("/masking-rules/test")
    @SuppressWarnings("unchecked")
    public Response testMaskingPattern(String requestBody) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.SECURITY_MASKING);
        Map<String, String> req = gson.fromJson(requestBody, Map.class);
        MaskingRuleService.PatternTestResult result = maskingRuleService.testPattern(
                req.get("pattern"), req.get("replacement"), req.get("testInput"));
        return Response.ok(gson.toJson(result)).build();
    }

    // =========================================================================
    // 정책 템플릿 (요건 8)
    // =========================================================================

    @GET
    @Path("/policy-templates")
    public Response getPolicyTemplates() {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);
        return Response.ok(gson.toJson(policyTemplateService.getAllTemplates())).build();
    }

    @GET
    @Path("/policy-templates/{id}")
    public Response getPolicyTemplate(@PathParam("id") String id) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);
        PolicyTemplateService.PolicyTemplate template = policyTemplateService.getTemplate(id);
        if (template == null) {
            return errorResponse(Response.Status.NOT_FOUND, "템플릿을 찾을 수 없습니다: " + id);
        }
        return Response.ok(gson.toJson(template)).build();
    }

    // =========================================================================
    // 사용량/비용 관리 (요건 11)
    // =========================================================================

    @GET
    @Path("/quotas")
    public Response getQuotas() {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.USAGE_COST);
        return Response.ok(gson.toJson(usageQuotaService.getAllQuotas())).build();
    }

    @POST
    @Path("/quotas")
    public Response createQuota(String requestBody) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.USAGE_COST);
        try {
            UsageQuotaService.QuotaCreateRequest req = gson.fromJson(requestBody,
                    UsageQuotaService.QuotaCreateRequest.class);
            UsageQuotaService.QuotaInfo quota = usageQuotaService.createQuota(req);
            auditLog("QUOTA_CREATED", "QUOTA", quota.getScope() + "/" + quota.getScopeKey(), null);
            return Response.status(Response.Status.CREATED).entity(gson.toJson(quota)).build();
        } catch (Exception e) { return serverError("한도 설정 생성 실패: " + e.getMessage()); }
    }

    @PUT
    @Path("/quotas/{id}")
    public Response updateQuota(@PathParam("id") int id, String requestBody) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.USAGE_COST);
        try {
            UsageQuotaService.QuotaCreateRequest req = gson.fromJson(requestBody,
                    UsageQuotaService.QuotaCreateRequest.class);
            UsageQuotaService.QuotaInfo quota = usageQuotaService.updateQuota(id, req);
            auditLog("QUOTA_UPDATED", "QUOTA", String.valueOf(id), null);
            return Response.ok(gson.toJson(quota)).build();
        } catch (Exception e) { return serverError("한도 설정 수정 실패: " + e.getMessage()); }
    }

    @DELETE
    @Path("/quotas/{id}")
    public Response deleteQuota(@PathParam("id") int id) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.USAGE_COST);
        try {
            usageQuotaService.deleteQuota(id);
            auditLog("QUOTA_DELETED", "QUOTA", String.valueOf(id), null);
            return successResponse("한도 설정이 삭제되었습니다.");
        } catch (Exception e) { return errorResponse(Response.Status.BAD_REQUEST, e.getMessage()); }
    }

    @GET
    @Path("/usage-summary")
    public Response getUsageSummary(@QueryParam("scope") @DefaultValue("GLOBAL") String scope,
                                     @QueryParam("scopeKey") @DefaultValue("global") String scopeKey,
                                     @QueryParam("period") @DefaultValue("MONTHLY") String period) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.USAGE_COST);
        UsageQuotaService.UsageSummary summary = usageQuotaService.getUsageSummary(scope, scopeKey, period);
        return Response.ok(gson.toJson(summary)).build();
    }

    @GET
    @Path("/usage-stats")
    public Response getUsageStats(@QueryParam("scope") @DefaultValue("GLOBAL") String scope,
                                    @QueryParam("scopeKey") @DefaultValue("global") String scopeKey,
                                    @QueryParam("days") @DefaultValue("30") int days) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.USAGE_COST);
        List<UsageQuotaService.DailyUsageStat> stats = usageQuotaService.getDailyUsageStats(scope, scopeKey, days);
        return Response.ok(gson.toJson(stats)).build();
    }

    // =========================================================================
    // 모니터링 대시보드 (요건 12)
    // =========================================================================

    @GET
    @Path("/metrics")
    public Response getMetrics() {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.USAGE_COST);
        try {
            Map<String, Object> metrics = metricsService.getAllMetrics();
            return Response.ok(gson.toJson(metrics)).build();
        } catch (Exception e) { return serverError("메트릭 조회 실패: " + e.getMessage()); }
    }

    @DELETE
    @Path("/metrics")
    public Response resetMetrics() {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);
        metricsService.reset();
        auditLog("METRICS_RESET", "METRICS", null, null);
        return successResponse("메트릭이 초기화되었습니다.");
    }

    @GET
    @Path("/cluster-stats")
    public Response getClusterStats() {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.USAGE_COST);
        try {
            ClusterJobManager.ClusterJobStats stats = clusterJobManager.getClusterStats();
            return Response.ok(gson.toJson(stats)).build();
        } catch (Exception e) { return serverError("클러스터 통계 조회 실패: " + e.getMessage()); }
    }

    // =========================================================================
    // 감사 로그 (요건 13)
    // =========================================================================

    @GET
    @Path("/audit-log")
    public Response getAuditLog(@QueryParam("page") @DefaultValue("0") int page,
                                 @QueryParam("size") @DefaultValue("50") int size,
                                 @QueryParam("user") String user,
                                 @QueryParam("type") String eventType) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.AUDIT_LOG);
        try {
            List<AuditLogService.AuditEntry> entries;
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
        } catch (WebApplicationException e) { throw e; }
        catch (Exception e) { return serverError("감사 로그 조회 실패: " + e.getMessage()); }
    }

    // =========================================================================
    // 알림 (요건 14)
    // =========================================================================

    @GET
    @Path("/alerts")
    public Response getAlerts(@QueryParam("limit") @DefaultValue("50") int limit) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.USAGE_COST);
        Map<String, Object> result = new HashMap<>();
        result.put("alerts", alertService.getRecentAlerts(limit));
        result.put("unacknowledged", alertService.getUnacknowledgedCount());
        return Response.ok(gson.toJson(result)).build();
    }

    @POST
    @Path("/alerts/{id}/acknowledge")
    public Response acknowledgeAlert(@PathParam("id") String alertId) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.USAGE_COST);
        alertService.acknowledgeAlert(alertId);
        return successResponse("알림이 확인되었습니다.");
    }

    @POST
    @Path("/alerts/acknowledge-all")
    public Response acknowledgeAllAlerts() {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.USAGE_COST);
        alertService.acknowledgeAll();
        return successResponse("모든 알림이 확인되었습니다.");
    }

    // =========================================================================
    // 변경 승인 (요건 15)
    // =========================================================================

    @GET
    @Path("/changes/pending")
    public Response getPendingChanges() {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);
        return Response.ok(gson.toJson(changeApprovalService.getPendingChanges())).build();
    }

    @GET
    @Path("/changes")
    public Response getAllChanges(@QueryParam("page") @DefaultValue("0") int page,
                                   @QueryParam("size") @DefaultValue("20") int size) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.AUDIT_LOG);
        return Response.ok(gson.toJson(changeApprovalService.getAllChanges(page, size))).build();
    }

    @GET
    @Path("/changes/{id}")
    public Response getChangeDetail(@PathParam("id") int id) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);
        try {
            return Response.ok(gson.toJson(changeApprovalService.getChangeDetail(id))).build();
        } catch (Exception e) { return errorResponse(Response.Status.NOT_FOUND, e.getMessage()); }
    }

    @POST
    @Path("/changes/{id}/approve")
    @SuppressWarnings("unchecked")
    public Response approveChange(@PathParam("id") int id, String requestBody) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);
        try {
            Map<String, String> req = requestBody != null ?
                    gson.fromJson(requestBody, Map.class) : Collections.emptyMap();
            String username = permissionCheck.getUsername(httpRequest);
            ChangeApprovalService.ChangeRequestInfo result =
                    changeApprovalService.approveChange(id, username, req.get("comment"));
            return Response.ok(gson.toJson(result)).build();
        } catch (Exception e) { return errorResponse(Response.Status.BAD_REQUEST, e.getMessage()); }
    }

    @POST
    @Path("/changes/{id}/reject")
    @SuppressWarnings("unchecked")
    public Response rejectChange(@PathParam("id") int id, String requestBody) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);
        try {
            Map<String, String> req = requestBody != null ?
                    gson.fromJson(requestBody, Map.class) : Collections.emptyMap();
            String username = permissionCheck.getUsername(httpRequest);
            ChangeApprovalService.ChangeRequestInfo result =
                    changeApprovalService.rejectChange(id, username, req.get("comment"));
            return Response.ok(gson.toJson(result)).build();
        } catch (Exception e) { return errorResponse(Response.Status.BAD_REQUEST, e.getMessage()); }
    }

    // =========================================================================
    // Dry-Run (요건 16)
    // =========================================================================

    @POST
    @Path("/dry-run")
    @SuppressWarnings("unchecked")
    public Response performDryRun(String requestBody) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);
        try {
            Map<String, String> req = gson.fromJson(requestBody, Map.class);
            DryRunService.DryRunResult result = dryRunService.analyze(
                    req.get("targetSection"), req.get("changeType"),
                    req.get("beforeJson"), req.get("afterJson"));
            return Response.ok(gson.toJson(result)).build();
        } catch (Exception e) { return serverError("Dry-run 분석 실패: " + e.getMessage()); }
    }

    // =========================================================================
    // 백업/복원 (요건 17)
    // =========================================================================

    @GET
    @Path("/backups")
    public Response listBackups() {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);
        return Response.ok(gson.toJson(settingsBackupService.listSnapshots())).build();
    }

    @POST
    @Path("/backups")
    @SuppressWarnings("unchecked")
    public Response createBackup(String requestBody) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);
        try {
            Map<String, String> req = requestBody != null ?
                    gson.fromJson(requestBody, Map.class) : Collections.emptyMap();
            String username = permissionCheck.getUsername(httpRequest);
            SettingsBackupService.SnapshotInfo snapshot = settingsBackupService.createSnapshot(
                    req.get("name"), "MANUAL", req.get("description"), username);
            auditLog("BACKUP_CREATED", "BACKUP", snapshot.getSnapshotName(), null);
            return Response.status(Response.Status.CREATED).entity(gson.toJson(snapshot)).build();
        } catch (Exception e) { return serverError("백업 생성 실패: " + e.getMessage()); }
    }

    @GET
    @Path("/backups/{id}")
    public Response getBackupDetail(@PathParam("id") int id) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);
        try {
            return Response.ok(gson.toJson(settingsBackupService.getSnapshotDetail(id))).build();
        } catch (Exception e) { return errorResponse(Response.Status.NOT_FOUND, e.getMessage()); }
    }

    @POST
    @Path("/backups/{id}/restore")
    @SuppressWarnings("unchecked")
    public Response restoreBackup(@PathParam("id") int id, String requestBody) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);
        try {
            Map<String, Object> req = requestBody != null ?
                    gson.fromJson(requestBody, Map.class) : Collections.emptyMap();
            String username = permissionCheck.getUsername(httpRequest);
            List<String> sections = req.containsKey("sections") ?
                    (List<String>) req.get("sections") : null;
            SettingsBackupService.RestoreResult result =
                    settingsBackupService.restoreFromSnapshot(id, sections, username);
            auditLog("BACKUP_RESTORED", "BACKUP", String.valueOf(id), null);
            return Response.ok(gson.toJson(result)).build();
        } catch (Exception e) { return serverError("복원 실패: " + e.getMessage()); }
    }

    @DELETE
    @Path("/backups/{id}")
    public Response deleteBackup(@PathParam("id") int id) {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.GLOBAL_SETTINGS);
        try {
            settingsBackupService.deleteSnapshot(id);
            auditLog("BACKUP_DELETED", "BACKUP", String.valueOf(id), null);
            return successResponse("스냅샷이 삭제되었습니다.");
        } catch (Exception e) { return errorResponse(Response.Status.BAD_REQUEST, e.getMessage()); }
    }

    // =========================================================================
    // 진단 (요건 18)
    // =========================================================================

    @GET
    @Path("/diagnostics")
    public Response getDiagnostics() {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.DIAGNOSTICS);
        try {
            DiagnosticsService.DiagnosticsReport report = diagnosticsService.runFullDiagnostics();
            return Response.ok(gson.toJson(report)).build();
        } catch (Exception e) { return serverError("진단 실행 실패: " + e.getMessage()); }
    }

    @GET
    @Path("/diagnostics/tables")
    public Response getTableStats() {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.DIAGNOSTICS);
        return Response.ok(gson.toJson(diagnosticsService.getTableStats())).build();
    }

    // =========================================================================
    // RBAC 관리 (요건 2)
    // =========================================================================

    @GET
    @Path("/roles")
    public Response getRoleAssignments() {
        permissionCheck.requireSysAdmin(httpRequest);
        Map<String, Object> result = new HashMap<>();
        result.put("assignments", permissionCheck.getAllRoleAssignments());
        result.put("availableRoles", PermissionCheckService.AdminRole.values());
        result.put("availableSections", AdminSection.values());
        return Response.ok(gson.toJson(result)).build();
    }

    @PUT
    @Path("/roles/{username}")
    @SuppressWarnings("unchecked")
    public Response assignRole(@PathParam("username") String username, String requestBody) {
        permissionCheck.requireSysAdmin(httpRequest);
        try {
            Map<String, String> req = gson.fromJson(requestBody, Map.class);
            PermissionCheckService.AdminRole role =
                    PermissionCheckService.AdminRole.valueOf(req.get("role"));
            permissionCheck.assignRole(username, role);
            auditLog("ROLE_ASSIGNED", "RBAC", username, "role=" + role);
            return successResponse("역할이 할당되었습니다: " + username + " → " + role);
        } catch (Exception e) { return errorResponse(Response.Status.BAD_REQUEST, e.getMessage()); }
    }

    @DELETE
    @Path("/roles/{username}")
    public Response removeRole(@PathParam("username") String username) {
        permissionCheck.requireSysAdmin(httpRequest);
        permissionCheck.removeRole(username);
        auditLog("ROLE_REMOVED", "RBAC", username, null);
        return successResponse("역할이 제거되었습니다: " + username);
    }

    // =========================================================================
    // 연결 테스트 (기존 호환)
    // =========================================================================

    @POST
    @Path("/test-connection")
    public Response testConnection() {
        permissionCheck.requireAdminSection(httpRequest, AdminSection.ENGINE_CONNECTION);
        Map<String, Object> result = new HashMap<>();
        boolean healthy = llmClientService.healthCheck();
        result.put("success", healthy);
        result.put("endpoint", settingsService.getLlmEndpoint());
        result.put("model", settingsService.getLlmModel());
        result.put("message", healthy ?
                "LLM 서비스 연결에 성공했습니다." :
                "LLM 서비스 연결에 실패했습니다. 엔드포인트 및 네트워크 설정을 확인해주세요.");
        return Response.ok(gson.toJson(result)).build();
    }

    // =========================================================================
    // 공통 헬퍼
    // =========================================================================

    private Response successResponse(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", message);
        return Response.ok(gson.toJson(result)).build();
    }

    private Response errorResponse(Response.Status status, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        return Response.status(status).entity(gson.toJson(error)).build();
    }

    private Response serverError(String message) {
        return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, message);
    }

    private void auditLog(String eventType, String targetType, String targetId, String details) {
        try {
            String username = permissionCheck.getUsername(httpRequest);
            String ip = httpRequest != null ? httpRequest.getRemoteAddr() : "unknown";
            auditLogService.log(eventType, username, targetType, targetId, details, ip);
        } catch (Exception e) {
            // 감사 로그 기록 실패가 주요 기능에 영향을 주면 안 됨
        }
    }

    private String getSectionLabel(AdminSection section) {
        switch (section) {
            case GLOBAL_SETTINGS: return "전역 설정";
            case PROJECT_POLICY: return "프로젝트 정책";
            case ENGINE_CONNECTION: return "엔진 연결";
            case SECURITY_MASKING: return "보안·마스킹";
            case USAGE_COST: return "사용량·비용";
            case AUDIT_LOG: return "로그·감사";
            case DIAGNOSTICS: return "진단";
            default: return section.name();
        }
    }

    private String getSectionIcon(AdminSection section) {
        switch (section) {
            case GLOBAL_SETTINGS: return "aui-iconfont-configure";
            case PROJECT_POLICY: return "aui-iconfont-plan-disabled";
            case ENGINE_CONNECTION: return "aui-iconfont-link";
            case SECURITY_MASKING: return "aui-iconfont-lock-filled";
            case USAGE_COST: return "aui-iconfont-graph-bar";
            case AUDIT_LOG: return "aui-iconfont-file-txt";
            case DIAGNOSTICS: return "aui-iconfont-info-circle";
            default: return "aui-iconfont-page-default";
        }
    }
}
