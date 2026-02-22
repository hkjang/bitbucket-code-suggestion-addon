package com.jask.bitbucket.rest;

import com.google.gson.Gson;
import com.jask.bitbucket.config.ProjectSettingsService;
import com.jask.bitbucket.security.PermissionCheckService;

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
 * REST resource for project-level configuration.
 *
 * Base path: /rest/code-suggestion/1.0/project-config/{projectKey}
 * 필요 권한: 프로젝트 ADMIN 또는 SYS_ADMIN
 */
@Named("projectConfigResource")
@Path("/project-config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectConfigResource {

    private final ProjectSettingsService projectSettings;
    private final PermissionCheckService permissionCheck;
    private final Gson gson;

    @Context
    private HttpServletRequest httpRequest;

    @Inject
    public ProjectConfigResource(ProjectSettingsService projectSettings,
                                  PermissionCheckService permissionCheck) {
        this.projectSettings = projectSettings;
        this.permissionCheck = permissionCheck;
        this.gson = new Gson();
    }

    /**
     * Get project settings.
     *
     * GET /rest/code-suggestion/1.0/project-config/{projectKey}
     */
    @GET
    @Path("/{projectKey}")
    public Response getProjectSettings(@PathParam("projectKey") String projectKey) {
        permissionCheck.requireSysAdmin(httpRequest);

        Map<String, Object> settings = new HashMap<>();
        settings.put("projectKey", projectKey);
        settings.put("hasProjectSettings", projectSettings.hasProjectSettings(projectKey));
        settings.put("enabled", projectSettings.isEnabledForProject(projectKey));
        settings.put("autoAnalysisEnabled", projectSettings.isAutoAnalysisEnabled(projectKey));
        settings.put("minConfidenceThreshold", projectSettings.getMinConfidenceThreshold(projectKey));
        settings.put("excludedFilePatterns", projectSettings.getExcludedFilePatterns(projectKey));
        settings.put("supportedLanguages", projectSettings.getSupportedLanguages(projectKey));

        return Response.ok(gson.toJson(settings)).build();
    }

    /**
     * Update project settings.
     *
     * PUT /rest/code-suggestion/1.0/project-config/{projectKey}
     */
    @PUT
    @Path("/{projectKey}")
    public Response updateProjectSettings(@PathParam("projectKey") String projectKey,
                                           String requestBody) {
        permissionCheck.requireSysAdmin(httpRequest);

        try {
            Map<String, Object> settings = gson.fromJson(requestBody, Map.class);

            if (settings.containsKey("enabled")) {
                projectSettings.setEnabledForProject(projectKey, (Boolean) settings.get("enabled"));
            }
            if (settings.containsKey("autoAnalysisEnabled")) {
                projectSettings.setAutoAnalysisEnabled(projectKey,
                        (Boolean) settings.get("autoAnalysisEnabled"));
            }
            if (settings.containsKey("minConfidenceThreshold")) {
                projectSettings.setMinConfidenceThreshold(projectKey,
                        ((Number) settings.get("minConfidenceThreshold")).doubleValue());
            }
            if (settings.containsKey("excludedFilePatterns")) {
                projectSettings.setExcludedFilePatterns(projectKey,
                        (String) settings.get("excludedFilePatterns"));
            }
            if (settings.containsKey("supportedLanguages")) {
                projectSettings.setSupportedLanguages(projectKey,
                        (String) settings.get("supportedLanguages"));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "프로젝트 " + projectKey + " 설정이 저장되었습니다.");
            return Response.ok(gson.toJson(result)).build();

        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "프로젝트 설정 저장 실패: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(gson.toJson(error)).build();
        }
    }

    /**
     * Reset project settings to global defaults.
     *
     * DELETE /rest/code-suggestion/1.0/project-config/{projectKey}
     */
    @DELETE
    @Path("/{projectKey}")
    public Response resetProjectSettings(@PathParam("projectKey") String projectKey) {
        permissionCheck.requireSysAdmin(httpRequest);

        projectSettings.resetToGlobal(projectKey);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "프로젝트 " + projectKey + " 설정이 전역 기본값으로 초기화되었습니다.");
        return Response.ok(gson.toJson(result)).build();
    }
}
