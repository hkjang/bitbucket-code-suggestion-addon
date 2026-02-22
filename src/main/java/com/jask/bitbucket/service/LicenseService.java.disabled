package com.jask.bitbucket.service;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.upm.api.license.PluginLicenseManager;
import com.atlassian.upm.api.license.entity.PluginLicense;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Atlassian 마켓플레이스 라이선스 관리 서비스.
 *
 * UPM (Universal Plugin Manager)를 통해 플러그인 라이선스를 검증합니다.
 * 라이선스 상태에 따라 기능 제한을 적용합니다.
 */
@ExportAsService({LicenseService.class})
@Named("licenseService")
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);

    private final PluginLicenseManager licenseManager;

    @Inject
    public LicenseService(@ComponentImport PluginLicenseManager licenseManager) {
        this.licenseManager = licenseManager;
    }

    /**
     * 현재 라이선스가 유효한지 확인합니다.
     */
    public boolean isLicenseValid() {
        try {
            if (licenseManager == null) {
                log.debug("라이선스 매니저를 사용할 수 없음 (개발 모드)");
                return true; // 개발 환경에서는 허용
            }

            com.atlassian.fugue.Option<PluginLicense> license = licenseManager.getLicense();
            if (license.isEmpty()) {
                return false;
            }

            PluginLicense pluginLicense = license.get();
            return pluginLicense.isValid();
        } catch (Exception e) {
            log.warn("라이선스 확인 실패: {}", e.getMessage());
            return true; // 라이선스 서비스 오류 시 허용 (관대 정책)
        }
    }

    /**
     * 라이선스 상세 정보를 반환합니다.
     */
    public LicenseInfo getLicenseInfo() {
        LicenseInfo info = new LicenseInfo();

        try {
            if (licenseManager == null) {
                info.setStatus("DEV_MODE");
                info.setMessage("개발 모드: 라이선스 검증 비활성화");
                info.setValid(true);
                return info;
            }

            com.atlassian.fugue.Option<PluginLicense> license = licenseManager.getLicense();
            if (license.isEmpty()) {
                info.setStatus("NO_LICENSE");
                info.setMessage("라이선스가 설정되지 않았습니다.");
                info.setValid(false);
                return info;
            }

            PluginLicense pluginLicense = license.get();
            info.setValid(pluginLicense.isValid());

            if (pluginLicense.isValid()) {
                info.setStatus("ACTIVE");
                info.setMessage("라이선스가 유효합니다.");
            } else if (pluginLicense.getError().isDefined()) {
                String error = pluginLicense.getError().get().name();
                info.setStatus("INVALID");
                info.setMessage("라이선스 오류: " + error);
            } else {
                info.setStatus("EXPIRED");
                info.setMessage("라이선스가 만료되었습니다.");
            }

        } catch (Exception e) {
            info.setStatus("ERROR");
            info.setMessage("라이선스 확인 실패: " + e.getMessage());
            info.setValid(true); // 관대 정책
        }

        return info;
    }

    /**
     * 기능 접근 제한 확인.
     * 라이선스가 없어도 기본 기능은 제공하되, 고급 기능은 제한합니다.
     *
     * @param feature 기능 이름
     * @return 사용 가능 여부
     */
    public boolean isFeatureAllowed(String feature) {
        if (isLicenseValid()) {
            return true; // 유효 라이선스: 모든 기능 허용
        }

        // 무료/평가판 기능 (라이선스 없어도 사용 가능)
        switch (feature) {
            case "BASIC_ANALYSIS":      // 기본 분석
            case "VIEW_SUGGESTIONS":    // 제안 조회
                return true;
            case "AUTO_ANALYSIS":       // 자동 분석
            case "MERGE_CHECK":         // 머지 체크
            case "BATCH_COMMENT":       // 일괄 코멘트
            case "VERSION_HISTORY":     // 버전 히스토리
            case "PROJECT_SETTINGS":    // 프로젝트별 설정
            case "METRICS":             // 메트릭 대시보드
                return false;
            default:
                return false;
        }
    }

    // --- DTO ---

    public static class LicenseInfo {
        private String status;
        private String message;
        private boolean valid;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
    }
}
