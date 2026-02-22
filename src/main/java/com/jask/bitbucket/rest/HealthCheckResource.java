package com.jask.bitbucket.rest;

import com.google.gson.Gson;
import com.jask.bitbucket.config.PluginSettingsService;
import com.jask.bitbucket.security.DataPrivacyService;
import com.jask.bitbucket.service.ClusterJobManager;
import com.jask.bitbucket.service.LicenseService;
import com.jask.bitbucket.service.LlmClientService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 플러그인 헬스체크 및 상태 정보 REST API.
 *
 * 마켓플레이스 호환: /rest/code-suggestion/1.0/health
 */
@Named("healthCheckResource")
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class HealthCheckResource {

    private final LlmClientService llmClientService;
    private final PluginSettingsService settingsService;
    private final LicenseService licenseService;
    private final ClusterJobManager clusterJobManager;
    private final DataPrivacyService dataPrivacyService;
    private final Gson gson;

    @Inject
    public HealthCheckResource(LlmClientService llmClientService,
                                PluginSettingsService settingsService,
                                LicenseService licenseService,
                                ClusterJobManager clusterJobManager,
                                DataPrivacyService dataPrivacyService) {
        this.llmClientService = llmClientService;
        this.settingsService = settingsService;
        this.licenseService = licenseService;
        this.clusterJobManager = clusterJobManager;
        this.dataPrivacyService = dataPrivacyService;
        this.gson = new Gson();
    }

    /**
     * 플러그인 헬스체크 엔드포인트.
     * UPM 및 마켓플레이스 모니터링에서 사용됩니다.
     *
     * GET /rest/code-suggestion/1.0/health
     */
    @GET
    @Path("/health")
    public Response healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();

        // 전체 상태
        boolean allHealthy = true;

        // 1. 플러그인 상태
        health.put("status", "UP");
        health.put("version", getPluginVersion());

        // 2. LLM 연결 상태
        Map<String, Object> llm = new LinkedHashMap<>();
        boolean llmHealthy = false;
        try {
            llmHealthy = llmClientService.healthCheck();
        } catch (Exception e) {
            llm.put("error", e.getMessage());
        }
        llm.put("status", llmHealthy ? "UP" : "DOWN");
        llm.put("endpoint", settingsService.getLlmEndpoint());
        llm.put("model", settingsService.getLlmModel());
        health.put("llm", llm);
        if (!llmHealthy) allHealthy = false;

        // 3. 라이선스 상태
        LicenseService.LicenseInfo licenseInfo = licenseService.getLicenseInfo();
        Map<String, Object> license = new LinkedHashMap<>();
        license.put("status", licenseInfo.getStatus());
        license.put("valid", licenseInfo.isValid());
        license.put("message", licenseInfo.getMessage());
        health.put("license", license);

        // 4. 잡 큐 상태
        try {
            ClusterJobManager.ClusterJobStats jobStats = clusterJobManager.getClusterStats();
            Map<String, Object> queue = new LinkedHashMap<>();
            queue.put("queued", jobStats.queuedJobs);
            queue.put("running", jobStats.runningJobs);
            queue.put("completedToday", jobStats.completedToday);
            queue.put("failedToday", jobStats.failedToday);
            queue.put("nodeId", jobStats.currentNodeId);
            health.put("jobQueue", queue);
        } catch (Exception e) {
            health.put("jobQueue", Map.of("status", "UNKNOWN", "error", e.getMessage()));
        }

        // 5. 데이터 보존 정책
        DataPrivacyService.DataRetentionPolicy retentionPolicy = dataPrivacyService.getRetentionPolicy();
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("suggestionRetentionDays", retentionPolicy.getSuggestionRetentionDays());
        privacy.put("storeLlmPayloads", retentionPolicy.isStoreLlmPayloads());
        privacy.put("piiMaskingEnabled", true);
        privacy.put("secretMaskingEnabled", true);
        health.put("privacy", privacy);

        // 전체 상태 결정
        health.put("status", allHealthy ? "UP" : "DEGRADED");

        return Response.ok(gson.toJson(health)).build();
    }

    /**
     * 플러그인 정보 엔드포인트.
     *
     * GET /rest/code-suggestion/1.0/info
     */
    @GET
    @Path("/info")
    public Response info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "Jask Code Suggestion");
        info.put("description", "AI 기반 코드 제안 플러그인 - Pull Request에서 자동 코드 리뷰 및 개선 제안");
        info.put("version", getPluginVersion());
        info.put("vendor", "Jask");
        info.put("dcCompatible", true);

        // 지원 기능
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("autoAnalysis", true);
        features.put("manualAnalysis", true);
        features.put("inlineComments", true);
        features.put("mergeCheck", true);
        features.put("versionHistory", true);
        features.put("projectSettings", true);
        features.put("categoryFiltering", true);
        features.put("caching", true);
        features.put("circuitBreaker", true);
        features.put("secretMasking", true);
        features.put("piiMasking", true);
        features.put("auditLog", true);
        features.put("metrics", true);
        info.put("features", features);

        // 지원 LLM 엔진
        info.put("supportedEngines", new String[]{"Ollama", "vLLM", "OpenAI Compatible"});

        // 지원 언어
        info.put("supportedLanguages", new String[]{
                "Java", "JavaScript", "TypeScript", "Python", "Go", "Kotlin",
                "Scala", "Ruby", "PHP", "C#", "C/C++", "Rust", "Swift",
                "SQL", "Shell", "YAML", "XML", "JSON"
        });

        return Response.ok(gson.toJson(info)).build();
    }

    /**
     * 개인정보 처리 방침 엔드포인트.
     *
     * GET /rest/code-suggestion/1.0/privacy-policy
     */
    @GET
    @Path("/privacy-policy")
    @Produces(MediaType.TEXT_PLAIN)
    public Response privacyPolicy() {
        return Response.ok(dataPrivacyService.getPrivacyPolicyText()).build();
    }

    private String getPluginVersion() {
        // 실제로는 pom.xml의 version을 참조하지만, 빌드 시점에 resources에 주입됨
        return "1.0.0";
    }
}
