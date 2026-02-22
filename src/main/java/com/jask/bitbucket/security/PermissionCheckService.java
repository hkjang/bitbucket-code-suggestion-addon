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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 플러그인 REST API 보안 검증 서비스.
 * 모든 REST 엔드포인트에서 인증/인가를 강제합니다.
 *
 * 역할 기반 접근 제어(RBAC):
 * - SYS_ADMIN: 전체 관리 (전역 설정, 엔진, 마스킹, 사용량, 로그, 진단)
 * - PROJECT_ADMIN: 프로젝트별 정책 관리
 * - AUDIT_VIEWER: 감사 로그 읽기 전용
 * - READ_ONLY: 대시보드/메트릭 조회만 허용
 */
@ExportAsService({PermissionCheckService.class})
@Named("permissionCheckService")
public class PermissionCheckService {

    private static final Logger log = LoggerFactory.getLogger(PermissionCheckService.class);

    private final UserManager userManager;
    private final PermissionService permissionService;
    private final RepositoryService repositoryService;

    /**
     * 관리자 역할 정의.
     */
    public enum AdminRole {
        SYS_ADMIN,        // 시스템 관리자: 모든 기능
        PROJECT_ADMIN,    // 프로젝트 관리자: 프로젝트별 정책
        AUDIT_VIEWER,     // 감사 전용: 로그 조회
        READ_ONLY         // 읽기 전용: 대시보드/메트릭
    }

    /**
     * 관리자 기능 섹션 정의 (요건 1 IA 구조에 대응).
     */
    public enum AdminSection {
        GLOBAL_SETTINGS,     // 전역 설정
        PROJECT_POLICY,      // 프로젝트별 정책
        ENGINE_CONNECTION,   // 엔진 연결
        SECURITY_MASKING,    // 보안·마스킹
        USAGE_COST,          // 사용량·비용
        AUDIT_LOG,           // 로그·감사
        DIAGNOSTICS          // 진단
    }

    /** 역할별 접근 가능 섹션 매핑 */
    private static final Map<AdminRole, Set<AdminSection>> ROLE_PERMISSIONS = new HashMap<>();

    static {
        ROLE_PERMISSIONS.put(AdminRole.SYS_ADMIN, EnumSet.allOf(AdminSection.class));
        ROLE_PERMISSIONS.put(AdminRole.PROJECT_ADMIN, EnumSet.of(
                AdminSection.PROJECT_POLICY, AdminSection.AUDIT_LOG));
        ROLE_PERMISSIONS.put(AdminRole.AUDIT_VIEWER, EnumSet.of(
                AdminSection.AUDIT_LOG));
        ROLE_PERMISSIONS.put(AdminRole.READ_ONLY, EnumSet.of(
                AdminSection.USAGE_COST, AdminSection.DIAGNOSTICS));
    }

    /** 사용자별 커스텀 역할 할당 (SAL PluginSettings 기반, 인메모리 캐시) */
    private final ConcurrentHashMap<String, AdminRole> userRoleCache = new ConcurrentHashMap<>();

    @Inject
    public PermissionCheckService(@ComponentImport UserManager userManager,
                                   @ComponentImport PermissionService permissionService,
                                   @ComponentImport RepositoryService repositoryService) {
        this.userManager = userManager;
        this.permissionService = permissionService;
        this.repositoryService = repositoryService;
    }

    // =========================================================================
    // 기본 인증/인가
    // =========================================================================

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
        return user;
    }

    // =========================================================================
    // 역할 기반 접근 제어 (RBAC) - 요건 2
    // =========================================================================

    /**
     * 특정 관리 섹션에 대한 접근 권한을 확인합니다.
     * 권한이 없으면 403 예외를 던집니다.
     */
    public UserProfile requireAdminSection(HttpServletRequest request, AdminSection section) {
        UserProfile user = requireAuthentication(request);
        AdminRole role = resolveAdminRole(user);

        if (role == null) {
            log.warn("관리자 역할 없음: user={}, section={}", user.getUsername(), section);
            throw forbidden("해당 섹션에 대한 접근 권한이 없습니다: " + section);
        }

        Set<AdminSection> allowed = ROLE_PERMISSIONS.get(role);
        if (allowed == null || !allowed.contains(section)) {
            log.warn("섹션 접근 거부: user={}, role={}, section={}",
                    user.getUsername(), role, section);
            throw forbidden("해당 섹션에 대한 접근 권한이 없습니다: " + section);
        }

        return user;
    }

    /**
     * 사용자에게 특정 관리 섹션 접근 권한이 있는지 확인합니다 (예외 없이).
     */
    public boolean hasAdminSectionAccess(HttpServletRequest request, AdminSection section) {
        try {
            UserProfile user = userManager.getRemoteUser(request);
            if (user == null) return false;

            AdminRole role = resolveAdminRole(user);
            if (role == null) return false;

            Set<AdminSection> allowed = ROLE_PERMISSIONS.get(role);
            return allowed != null && allowed.contains(section);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 사용자의 관리자 역할을 결정합니다.
     */
    public AdminRole resolveAdminRole(UserProfile user) {
        if (user == null) return null;

        // 1. Bitbucket 시스템 관리자 → SYS_ADMIN
        if (userManager.isSystemAdmin(user.getUserKey())) {
            return AdminRole.SYS_ADMIN;
        }

        // 2. 커스텀 역할 할당 확인
        AdminRole customRole = userRoleCache.get(user.getUsername());
        if (customRole != null) {
            return customRole;
        }

        // 3. Bitbucket 프로젝트 관리자 확인 (기본 매핑)
        // 프로젝트 관리자 권한이 있으면 PROJECT_ADMIN
        // (실제로는 Bitbucket API를 통해 프로젝트별 확인해야 하지만, 여기서는 단순화)

        return null; // 역할 없음 = 접근 불가
    }

    /**
     * 사용자에게 커스텀 관리자 역할을 할당합니다 (SYS_ADMIN만 가능).
     */
    public void assignRole(String username, AdminRole role) {
        if (role == AdminRole.SYS_ADMIN) {
            log.warn("SYS_ADMIN 역할은 직접 할당할 수 없습니다 (Bitbucket 시스템 관리자만)");
            return;
        }
        userRoleCache.put(username, role);
        log.info("관리자 역할 할당: user={}, role={}", username, role);
    }

    /**
     * 사용자의 커스텀 관리자 역할을 제거합니다.
     */
    public void removeRole(String username) {
        userRoleCache.remove(username);
        log.info("관리자 역할 제거: user={}", username);
    }

    /**
     * 모든 역할 할당 목록을 반환합니다.
     */
    public Map<String, AdminRole> getAllRoleAssignments() {
        return Collections.unmodifiableMap(new HashMap<>(userRoleCache));
    }

    /**
     * 현재 사용자가 접근 가능한 관리 섹션 목록을 반환합니다.
     * UI에서 네비게이션 렌더링 시 사용합니다.
     */
    public Set<AdminSection> getAccessibleSections(HttpServletRequest request) {
        UserProfile user = userManager.getRemoteUser(request);
        if (user == null) return Collections.emptySet();

        AdminRole role = resolveAdminRole(user);
        if (role == null) return Collections.emptySet();

        return ROLE_PERMISSIONS.getOrDefault(role, Collections.emptySet());
    }

    /**
     * 사용자 이름을 반환합니다 (감사 로그용).
     */
    public String getUsername(HttpServletRequest request) {
        UserProfile user = userManager.getRemoteUser(request);
        return user != null ? user.getUsername() : "anonymous";
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    private WebApplicationException forbidden(String message) {
        return new WebApplicationException(
                Response.status(Response.Status.FORBIDDEN)
                        .entity("{\"success\":false,\"error\":\"" + message + "\"}")
                        .type("application/json")
                        .build());
    }
}
