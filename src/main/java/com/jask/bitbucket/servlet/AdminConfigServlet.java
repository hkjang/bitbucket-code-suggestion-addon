package com.jask.bitbucket.servlet;

import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.component.ComponentLocator;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;

/**
 * Servlet for the admin configuration page.
 *
 * Spring Scanner의 @Named/@Inject/@ComponentImport 어노테이션을 사용하지 않음.
 * Bitbucket 6.x의 플러그인 프레임워크가 <servlet> 모듈 디스크립터를 처리할 때
 * 리플렉션으로 기본 생성자를 호출하여 인스턴스를 생성하므로,
 * SAL ComponentLocator를 통해 서비스를 수동 조회함.
 */
public class AdminConfigServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AdminConfigServlet.class);

    private volatile UserManager userManager;
    private volatile LoginUriProvider loginUriProvider;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            this.userManager = ComponentLocator.getComponent(UserManager.class);
            this.loginUriProvider = ComponentLocator.getComponent(LoginUriProvider.class);
            log.info("AdminConfigServlet 초기화 완료 - UserManager: {}, LoginUriProvider: {}",
                    userManager != null, loginUriProvider != null);
        } catch (Exception e) {
            log.error("AdminConfigServlet 초기화 중 서비스 조회 실패: {}", e.getMessage(), e);
        }
    }

    private void ensureServices() {
        if (userManager == null) {
            userManager = ComponentLocator.getComponent(UserManager.class);
        }
        if (loginUriProvider == null) {
            loginUriProvider = ComponentLocator.getComponent(LoginUriProvider.class);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Lazy init 폴백 (init() 시점에 서비스가 아직 준비되지 않았을 경우)
        ensureServices();

        if (userManager == null) {
            log.error("UserManager를 조회할 수 없습니다.");
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "내부 오류: 사용자 관리 서비스를 사용할 수 없습니다.");
            return;
        }

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

        // Render admin page using HTML template
        resp.setContentType("text/html;charset=UTF-8");
        renderAdminHtml(req, resp);
    }

    private void redirectToLogin(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        if (loginUriProvider != null) {
            URI currentUri = URI.create(req.getRequestURL().toString());
            resp.sendRedirect(loginUriProvider.getLoginUri(currentUri).toASCIIString());
        } else {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    private void renderAdminHtml(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String html = loadAdminHtml();
        // 컨텍스트 경로를 HTML에 동적 주입 (REST API 호출 시 올바른 경로 사용)
        String contextPath = req.getContextPath();
        String contextScript = "<script>window.CONTEXT_PATH='" + contextPath + "';</script>";
        html = html.replace("<body>", "<body>" + contextScript);
        resp.getWriter().write(html);
    }

    private String loadAdminHtml() {
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
