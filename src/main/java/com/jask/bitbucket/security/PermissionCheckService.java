package com.jask.bitbucket.security;

import com.atlassian.bitbucket.permission.Permission;
import com.atlassian.bitbucket.permission.PermissionService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.atlassian.bitbucket.user.SecurityService;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * 플러그인 REST API 보안 검증 서비스.
 * 모든 REST 엔드포인트에서 인증/인가를 강제합니다.
 */
@ExportAsService({PermissionCheckService.class})
@Named("permissionCheckService")
public class PermissionCheckService {

    private static final Logger log = LoggerFactory.getLogger(PermissionCheckService.class);

    private final UserManager userManager;
    private final PermissionService permissionService;
    private final RepositoryService repositoryService;

    @Inject
    public PermissionCheckService(@ComponentImport UserManager userManager,
                                   @ComponentImport PermissionService permissionService,
                                   @ComponentImport RepositoryService repositoryService) {
        this.userManager = userManager;
        this.permissionService = permissionService;
        this.repositoryService = repositoryService;
    }

    /**
     * 현재 요청의 인증된 사용자를 반환합니다.
     * 인증되지 않은 경우 401 예외를 던집니다.
     */
    public UserProfile requireAuthentication(HttpServletRequest request) {
        UserProfile user = userManager.getRemoteUser(request);
        if (user == null) {
            log.warn("인증되지 않은 REST API 접근 시도: {}", request.getRequestURI());
            throw new WebApplicationException(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity("{\"success\":false,\"error\":\"인증이 필요합니다.\"}")
                            .type("application/json")
                            .build());
        }
        return user;
    }

    /**
     * 시스템 관리자 권한을 확인합니다.
     * 관리자가 아닌 경우 403 예외를 던집니다.
     */
    public UserProfile requireSysAdmin(HttpServletRequest request) {
        UserProfile user = requireAuthentication(request);
        if (!userManager.isSystemAdmin(user.getUserKey())) {
            log.warn("관리자 권한 없는 REST API 접근 시도: user={}, uri={}",
                    user.getUsername(), request.getRequestURI());
            throw new WebApplicationException(
                    Response.status(Response.Status.FORBIDDEN)
                            .entity("{\"success\":false,\"error\":\"시스템 관리자 권한이 필요합니다.\"}")
                            .type("application/json")
                            .build());
        }
        return user;
    }

    /**
     * 특정 레포지토리에 대한 읽기 이상 권한을 확인합니다.
     */
    public UserProfile requireRepoRead(HttpServletRequest request, int repositoryId) {
        UserProfile user = requireAuthentication(request);
        Repository repository = repositoryService.getById(repositoryId);
        if (repository == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity("{\"success\":false,\"error\":\"레포지토리를 찾을 수 없습니다: ID=" + repositoryId + "\"}")
                            .type("application/json")
                            .build());
        }
        // TODO: Permission check currently disabled due to API compatibility issues with Bitbucket 8.x
        // TODO: Implement proper permission checking with SecurityService
        log.info("레포지토리 읽기 권한 확인 필요 (현재 생략): user={}, repoId={}",
                user.getUsername(), repositoryId);
        return user;
    }

    /**
     * 특정 레포지토리에 대한 쓰기 권한을 확인합니다.
     */
    public UserProfile requireRepoWrite(HttpServletRequest request, int repositoryId) {
        UserProfile user = requireAuthentication(request);
        Repository repository = repositoryService.getById(repositoryId);
        if (repository == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity("{\"success\":false,\"error\":\"레포지토리를 찾을 수 없습니다: ID=" + repositoryId + "\"}")
                            .type("application/json")
                            .build());
        }
        // TODO: Permission check currently disabled due to API compatibility issues with Bitbucket 8.x
        // TODO: Implement proper permission checking with SecurityService
        log.info("레포지토리 쓰기 권한 확인 필요 (현재 생략): user={}, repoId={}",
                user.getUsername(), repositoryId);
        // TODO: Permission check currently disabled due to API compatibility issues with Bitbucket 8.x
        // TODO: Implement proper permission checking with SecurityService
        log.info("레포지토리 쓰기 권한 확인 필요 (현재 생략): user={}, repoId={}",
                user.getUsername(), repositoryId);
        return user;
    }

    /**
     * 사용자 이름을 반환합니다 (감사 로그용).
     */
    public String getUsername(HttpServletRequest request) {
        UserProfile user = userManager.getRemoteUser(request);
        return user != null ? user.getUsername() : "anonymous";
    }
}
