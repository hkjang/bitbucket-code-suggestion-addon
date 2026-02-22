package com.jask.bitbucket.servlet;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.atlassian.soy.renderer.SoyException;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet for the admin configuration page.
 */
@Named("adminConfigServlet")
public class AdminConfigServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AdminConfigServlet.class);

    private final UserManager userManager;
    private final LoginUriProvider loginUriProvider;
    private final SoyTemplateRenderer soyRenderer;

    @Inject
    public AdminConfigServlet(@ComponentImport UserManager userManager,
                               @ComponentImport LoginUriProvider loginUriProvider,
                               @ComponentImport SoyTemplateRenderer soyRenderer) {
        this.userManager = userManager;
        this.loginUriProvider = loginUriProvider;
        this.soyRenderer = soyRenderer;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Check authentication
        UserProfile user = userManager.getRemoteUser(req);
        if (user == null) {
            redirectToLogin(req, resp);
            return;
        }

        // Check admin permission
        if (!userManager.isSystemAdmin(user.getUserKey())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "시스템 관리자 권한이 필요합니다.");
            return;
        }

        // Render admin page
        resp.setContentType("text/html;charset=UTF-8");

        try {
            Map<String, Object> context = new HashMap<>();
            context.put("currentUser", user.getUsername());

            soyRenderer.render(resp.getWriter(),
                    "com.jask.bitbucket.code-suggestion-addon:admin-resources",
                    "jask.admin.configPage",
                    context);
        } catch (SoyException e) {
            log.error("관리자 설정 페이지 렌더링 실패: {}", e.getMessage(), e);
            // Fallback to raw HTML
            renderFallbackHtml(resp);
        }
    }

    private void redirectToLogin(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        URI currentUri = URI.create(req.getRequestURL().toString());
        resp.sendRedirect(loginUriProvider.getLoginUri(currentUri).toASCIIString());
    }

    private void renderFallbackHtml(HttpServletResponse resp) throws IOException {
        resp.getWriter().write(buildAdminHtml());
    }

    private String buildAdminHtml() {
        try {
            // Java 8 호환성: 클래스패스에서 HTML 파일 읽기
            java.io.InputStream is = this.getClass().getResourceAsStream("/templates/admin-config.html");
            if (is != null) {
                java.util.Scanner scanner = new java.util.Scanner(is);
                scanner.useDelimiter("\\A");
                String html = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                if (!html.isEmpty()) {
                    return html;
                }
            }
        } catch (Exception e) {
            log.warn("HTML 파일 읽기 실패: {}", e.getMessage());
        }
        // 폴백: 간단한 HTML 반환
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>AI 코드 제안 설정</title></head>" +
               "<body><div style=\"text-align:center;margin:50px;\"><h1>설정 페이지</h1>" +
               "<p>관리자만 접근 가능합니다.</p></div></body></html>";
    }
}
