package com.jask.bitbucket.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.jask.bitbucket.ao.*;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.*;

/**
 * 시스템 진단 서비스.
 *
 * 요건 18: 진단 페이지 — 시스템 상태, AO 테이블 통계,
 * JVM 메모리, 설정 무결성 등을 확인합니다.
 */
@ExportAsService({DiagnosticsService.class})
@Named("diagnosticsService")
public class DiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsService.class);

    private final ActiveObjects ao;
    private final EngineProfileService engineProfileService;
    private final MetricsService metricsService;

    @Inject
    public DiagnosticsService(@ComponentImport ActiveObjects ao,
                               EngineProfileService engineProfileService,
                               MetricsService metricsService) {
        this.ao = ao;
        this.engineProfileService = engineProfileService;
        this.metricsService = metricsService;
    }

    /**
     * 전체 시스템 진단을 수행합니다.
     */
    public DiagnosticsReport runFullDiagnostics() {
        DiagnosticsReport report = new DiagnosticsReport();
        report.setTimestamp(System.currentTimeMillis());

        List<DiagnosticCheck> checks = new ArrayList<>();

        // 1. JVM 메모리 상태
        checks.add(checkJvmMemory());

        // 2. AO 테이블 상태
        checks.add(checkAoTables());

        // 3. 엔진 프로파일 상태
        checks.add(checkEngineProfiles());

        // 4. 플러그인 설정 무결성
        checks.add(checkSettingsIntegrity());

        // 5. 작업 큐 상태
        checks.add(checkJobQueue());

        // 6. 메트릭 상태
        checks.add(checkMetrics());

        report.setChecks(checks);

        // 전체 상태 결정
        boolean hasCritical = checks.stream().anyMatch(c -> "CRITICAL".equals(c.getStatus()));
        boolean hasWarning = checks.stream().anyMatch(c -> "WARNING".equals(c.getStatus()));
        if (hasCritical) {
            report.setOverallStatus("CRITICAL");
        } else if (hasWarning) {
            report.setOverallStatus("WARNING");
        } else {
            report.setOverallStatus("HEALTHY");
        }

        // 시스템 정보
        report.setSystemInfo(collectSystemInfo());

        return report;
    }

    /**
     * AO 테이블별 레코드 수를 조회합니다.
     */
    public Map<String, Integer> getTableStats() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        try {
            stats.put("suggestions", ao.count(SuggestionEntity.class));
            stats.put("analysisJobs", ao.count(AnalysisJobEntity.class));
            stats.put("auditLogs", ao.count(AuditLogEntity.class));
            stats.put("analysisVersions", ao.count(AnalysisVersionEntity.class));
            stats.put("engineProfiles", ao.count(EngineProfileEntity.class));
            stats.put("usageQuotas", ao.count(UsageQuotaEntity.class));
            stats.put("usageRecords", ao.count(UsageRecordEntity.class));
            stats.put("settingsSnapshots", ao.count(SettingsSnapshotEntity.class));
            stats.put("pendingChanges", ao.count(PendingChangeEntity.class));
        } catch (Exception e) {
            log.error("AO 테이블 통계 조회 실패: {}", e.getMessage());
        }
        return stats;
    }

    // =========================================================================
    // 개별 진단 체크
    // =========================================================================

    private DiagnosticCheck checkJvmMemory() {
        DiagnosticCheck check = new DiagnosticCheck();
        check.setName("JVM 메모리");

        try {
            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            long used = memBean.getHeapMemoryUsage().getUsed();
            long max = memBean.getHeapMemoryUsage().getMax();
            double usagePercent = max > 0 ? (double) used / max * 100 : 0;

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("usedMb", used / (1024 * 1024));
            details.put("maxMb", max / (1024 * 1024));
            details.put("usagePercent", String.format("%.1f%%", usagePercent));
            check.setDetails(details);

            if (usagePercent > 90) {
                check.setStatus("CRITICAL");
                check.setMessage("힙 메모리 사용량이 90%를 초과했습니다.");
            } else if (usagePercent > 75) {
                check.setStatus("WARNING");
                check.setMessage("힙 메모리 사용량이 높습니다: " + String.format("%.1f%%", usagePercent));
            } else {
                check.setStatus("HEALTHY");
                check.setMessage("메모리 상태 정상: " + String.format("%.1f%%", usagePercent));
            }
        } catch (Exception e) {
            check.setStatus("WARNING");
            check.setMessage("메모리 정보 조회 실패: " + e.getMessage());
        }

        return check;
    }

    private DiagnosticCheck checkAoTables() {
        DiagnosticCheck check = new DiagnosticCheck();
        check.setName("Active Objects DB");

        try {
            Map<String, Integer> stats = getTableStats();
            check.setDetails(new LinkedHashMap<>(stats));

            int totalRecords = stats.values().stream().mapToInt(Integer::intValue).sum();
            if (totalRecords > 100000) {
                check.setStatus("WARNING");
                check.setMessage("총 레코드 수가 많습니다 (" + totalRecords + "). 정리가 필요할 수 있습니다.");
            } else {
                check.setStatus("HEALTHY");
                check.setMessage("DB 상태 정상 (총 " + totalRecords + " 레코드)");
            }
        } catch (Exception e) {
            check.setStatus("CRITICAL");
            check.setMessage("AO DB 접근 실패: " + e.getMessage());
        }

        return check;
    }

    private DiagnosticCheck checkEngineProfiles() {
        DiagnosticCheck check = new DiagnosticCheck();
        check.setName("엔진 프로파일");

        try {
            List<EngineProfileService.EngineProfileInfo> profiles = engineProfileService.getEnabledProfiles();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("enabledCount", profiles.size());
            details.put("totalCount", engineProfileService.getAllProfiles().size());

            if (profiles.isEmpty()) {
                check.setStatus("CRITICAL");
                check.setMessage("활성화된 엔진 프로파일이 없습니다. 코드 분석을 수행할 수 없습니다.");
            } else {
                // 마지막 테스트 결과 확인
                boolean anyFailed = profiles.stream()
                        .anyMatch(p -> "FAILURE".equals(p.getLastTestResult()));
                if (anyFailed) {
                    check.setStatus("WARNING");
                    check.setMessage("일부 엔진의 연결 테스트가 실패했습니다.");
                } else {
                    check.setStatus("HEALTHY");
                    check.setMessage(profiles.size() + "개 엔진 활성화됨");
                }

                List<String> engineNames = new ArrayList<>();
                for (EngineProfileService.EngineProfileInfo p : profiles) {
                    engineNames.add(p.getProfileName() + " (" + p.getLastTestResult() + ")");
                }
                details.put("engines", engineNames);
            }
            check.setDetails(details);
        } catch (Exception e) {
            check.setStatus("WARNING");
            check.setMessage("엔진 프로파일 조회 실패: " + e.getMessage());
        }

        return check;
    }

    private DiagnosticCheck checkSettingsIntegrity() {
        DiagnosticCheck check = new DiagnosticCheck();
        check.setName("설정 무결성");

        try {
            List<String> issues = new ArrayList<>();

            EngineProfileService.EngineProfileInfo defaultProfile = engineProfileService.getDefaultProfile();
            if (defaultProfile == null) {
                issues.add("기본 엔진 프로파일이 설정되지 않았습니다.");
            }

            if (issues.isEmpty()) {
                check.setStatus("HEALTHY");
                check.setMessage("설정 무결성 확인 완료");
            } else {
                check.setStatus("WARNING");
                check.setMessage(issues.size() + "개 이슈 발견");
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("issues", issues);
                check.setDetails(details);
            }
        } catch (Exception e) {
            check.setStatus("WARNING");
            check.setMessage("무결성 검사 실패: " + e.getMessage());
        }

        return check;
    }

    private DiagnosticCheck checkJobQueue() {
        DiagnosticCheck check = new DiagnosticCheck();
        check.setName("작업 큐");

        try {
            int queued = ao.count(AnalysisJobEntity.class,
                    Query.select().where("STATUS = ?", "QUEUED"));
            int running = ao.count(AnalysisJobEntity.class,
                    Query.select().where("STATUS = ?", "RUNNING"));

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("queued", queued);
            details.put("running", running);
            check.setDetails(details);

            if (queued > 50) {
                check.setStatus("WARNING");
                check.setMessage("큐에 대기 중인 작업이 많습니다: " + queued);
            } else {
                check.setStatus("HEALTHY");
                check.setMessage("작업 큐 정상 (대기: " + queued + ", 실행 중: " + running + ")");
            }
        } catch (Exception e) {
            check.setStatus("WARNING");
            check.setMessage("작업 큐 조회 실패: " + e.getMessage());
        }

        return check;
    }

    private DiagnosticCheck checkMetrics() {
        DiagnosticCheck check = new DiagnosticCheck();
        check.setName("메트릭 시스템");

        try {
            Map<String, Object> metrics = metricsService.getAllMetrics();
            check.setStatus("HEALTHY");
            check.setMessage("메트릭 수집 정상 작동 중");

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("uptimeMs", metrics.get("uptimeMs"));
            check.setDetails(details);
        } catch (Exception e) {
            check.setStatus("WARNING");
            check.setMessage("메트릭 조회 실패: " + e.getMessage());
        }

        return check;
    }

    private Map<String, Object> collectSystemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        try {
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            info.put("javaVersion", System.getProperty("java.version"));
            info.put("javaVendor", System.getProperty("java.vendor"));
            info.put("osName", System.getProperty("os.name"));
            info.put("osArch", System.getProperty("os.arch"));
            info.put("availableProcessors", Runtime.getRuntime().availableProcessors());
            info.put("uptimeMs", runtime.getUptime());
            info.put("pluginVersion", "1.0.0");
        } catch (Exception e) {
            info.put("error", "시스템 정보 수집 실패: " + e.getMessage());
        }
        return info;
    }

    // =========================================================================
    // DTOs
    // =========================================================================

    public static class DiagnosticsReport {
        private String overallStatus; // HEALTHY, WARNING, CRITICAL
        private long timestamp;
        private List<DiagnosticCheck> checks;
        private Map<String, Object> systemInfo;

        public String getOverallStatus() { return overallStatus; }
        public void setOverallStatus(String s) { this.overallStatus = s; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public List<DiagnosticCheck> getChecks() { return checks; }
        public void setChecks(List<DiagnosticCheck> checks) { this.checks = checks; }
        public Map<String, Object> getSystemInfo() { return systemInfo; }
        public void setSystemInfo(Map<String, Object> systemInfo) { this.systemInfo = systemInfo; }
    }

    public static class DiagnosticCheck {
        private String name;
        private String status; // HEALTHY, WARNING, CRITICAL
        private String message;
        private Map<String, Object> details;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Map<String, Object> getDetails() { return details; }
        public void setDetails(Map<String, Object> details) { this.details = details; }
    }
}
