package com.jask.bitbucket.service;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

/**
 * Dry-run (안전 적용) 서비스.
 *
 * 요건 16: 안전 적용 — 변경 사항의 영향 범위를 사전 분석합니다.
 *
 * Dry-run은 실제 적용 없이 변경이 미칠 영향을 시뮬레이션하여
 * 관리자가 안전하게 설정을 변경할 수 있도록 합니다.
 */
@ExportAsService({DryRunService.class})
@Named("dryRunService")
public class DryRunService {

    private static final Logger log = LoggerFactory.getLogger(DryRunService.class);

    private final EngineProfileService engineProfileService;
    private final UsageQuotaService usageQuotaService;
    private final Gson gson;

    @Inject
    public DryRunService(EngineProfileService engineProfileService,
                          UsageQuotaService usageQuotaService) {
        this.engineProfileService = engineProfileService;
        this.usageQuotaService = usageQuotaService;
        this.gson = new Gson();
    }

    /**
     * 변경 사항의 영향 범위를 분석합니다 (실제 적용 없음).
     */
    @SuppressWarnings("unchecked")
    public DryRunResult analyze(String targetSection, String changeType,
                                 String beforeJson, String afterJson) {
        DryRunResult result = new DryRunResult();
        result.setTargetSection(targetSection);
        result.setChangeType(changeType);
        result.setTimestamp(System.currentTimeMillis());

        List<ImpactItem> impacts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        Map<String, Object> before = beforeJson != null ?
                gson.fromJson(beforeJson, Map.class) : Collections.emptyMap();
        Map<String, Object> after = afterJson != null ?
                gson.fromJson(afterJson, Map.class) : Collections.emptyMap();

        // 영향 분석 수행
        switch (targetSection) {
            case "ENGINE_CONNECTION":
                analyzeEngineChange(before, after, impacts, warnings, recommendations);
                break;
            case "GLOBAL_SETTINGS":
                analyzeGlobalSettingsChange(before, after, impacts, warnings, recommendations);
                break;
            case "SECURITY_MASKING":
                analyzeMaskingChange(before, after, impacts, warnings, recommendations);
                break;
            case "USAGE_COST":
                analyzeQuotaChange(before, after, impacts, warnings, recommendations);
                break;
            default:
                impacts.add(new ImpactItem("UNKNOWN", "INFO",
                        "알 수 없는 섹션의 변경: " + targetSection));
        }

        result.setImpacts(impacts);
        result.setWarnings(warnings);
        result.setRecommendations(recommendations);
        result.setRiskLevel(calculateRiskLevel(impacts, warnings));
        result.setAffectedProjects(estimateAffectedProjects(targetSection));

        return result;
    }

    // =========================================================================
    // 섹션별 영향 분석
    // =========================================================================

    @SuppressWarnings("unchecked")
    private void analyzeEngineChange(Map<String, Object> before, Map<String, Object> after,
                                      List<ImpactItem> impacts, List<String> warnings,
                                      List<String> recommendations) {
        // 엔드포인트 변경
        String beforeUrl = (String) before.get("endpointUrl");
        String afterUrl = (String) after.get("endpointUrl");
        if (afterUrl != null && !afterUrl.equals(beforeUrl)) {
            impacts.add(new ImpactItem("엔드포인트 변경", "WARNING",
                    "LLM 서버 주소가 변경됩니다. 진행 중인 분석 작업에 영향을 줄 수 있습니다."));
            warnings.add("엔드포인트 변경 시 기존 연결이 끊어질 수 있습니다.");
            recommendations.add("변경 전 진행 중인 분석 작업이 없는지 확인하세요.");
        }

        // 모델 변경
        String beforeModel = (String) before.get("defaultModel");
        String afterModel = (String) after.get("defaultModel");
        if (afterModel != null && !afterModel.equals(beforeModel)) {
            impacts.add(new ImpactItem("모델 변경", "INFO",
                    "사용 모델이 변경됩니다: " + beforeModel + " → " + afterModel));
            recommendations.add("새 모델에서 분석 품질을 검증하세요.");
        }

        // 비활성화
        Object enabledObj = after.get("enabled");
        if (enabledObj != null && Boolean.FALSE.equals(enabledObj)) {
            impacts.add(new ImpactItem("엔진 비활성화", "CRITICAL",
                    "엔진이 비활성화됩니다. 해당 엔진을 사용하는 모든 분석이 중단됩니다."));
            warnings.add("활성 엔진이 없으면 코드 분석 기능을 사용할 수 없습니다.");
        }
    }

    @SuppressWarnings("unchecked")
    private void analyzeGlobalSettingsChange(Map<String, Object> before, Map<String, Object> after,
                                              List<ImpactItem> impacts, List<String> warnings,
                                              List<String> recommendations) {
        // 자동 분석 토글
        Object autoEnabled = after.get("autoAnalysisEnabled");
        Object beforeAuto = before.get("autoAnalysisEnabled");
        if (autoEnabled != null && !autoEnabled.equals(beforeAuto)) {
            if (Boolean.TRUE.equals(autoEnabled)) {
                impacts.add(new ImpactItem("자동 분석 활성화", "INFO",
                        "PR 생성 시 자동으로 코드 분석이 실행됩니다."));
                warnings.add("자동 분석은 LLM 사용량을 증가시킵니다.");
            } else {
                impacts.add(new ImpactItem("자동 분석 비활성화", "WARNING",
                        "PR에서 수동으로만 코드 분석을 실행할 수 있습니다."));
            }
        }

        // 머지 체크 토글
        Object mergeCheck = after.get("mergeCheckEnabled");
        Object beforeMerge = before.get("mergeCheckEnabled");
        if (mergeCheck != null && !mergeCheck.equals(beforeMerge)) {
            if (Boolean.TRUE.equals(mergeCheck)) {
                impacts.add(new ImpactItem("머지 체크 활성화", "WARNING",
                        "심각한 코드 이슈가 있는 PR의 머지가 차단됩니다."));
            } else {
                impacts.add(new ImpactItem("머지 체크 비활성화", "INFO",
                        "코드 이슈와 관계없이 PR을 머지할 수 있습니다."));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void analyzeMaskingChange(Map<String, Object> before, Map<String, Object> after,
                                       List<ImpactItem> impacts, List<String> warnings,
                                       List<String> recommendations) {
        impacts.add(new ImpactItem("마스킹 규칙 변경", "WARNING",
                "코드 분석 시 적용되는 데이터 마스킹 규칙이 변경됩니다."));
        warnings.add("마스킹 규칙 변경은 보안에 직접적 영향을 줍니다.");
        recommendations.add("변경 후 테스트 분석을 실행하여 마스킹이 올바르게 적용되는지 확인하세요.");
    }

    @SuppressWarnings("unchecked")
    private void analyzeQuotaChange(Map<String, Object> before, Map<String, Object> after,
                                     List<ImpactItem> impacts, List<String> warnings,
                                     List<String> recommendations) {
        Object maxCalls = after.get("maxCalls");
        Object beforeMax = before.get("maxCalls");
        if (maxCalls != null && !maxCalls.equals(beforeMax)) {
            impacts.add(new ImpactItem("한도 변경", "INFO",
                    "호출 한도가 변경됩니다: " + beforeMax + " → " + maxCalls));
        }

        Object exceedAction = after.get("exceedAction");
        if ("BLOCK".equals(exceedAction)) {
            warnings.add("한도 초과 시 분석 요청이 차단됩니다.");
        }
    }

    // =========================================================================
    // 리스크 계산
    // =========================================================================

    private String calculateRiskLevel(List<ImpactItem> impacts, List<String> warnings) {
        boolean hasCritical = impacts.stream()
                .anyMatch(i -> "CRITICAL".equals(i.getSeverity()));
        if (hasCritical) return "HIGH";

        boolean hasWarning = impacts.stream()
                .anyMatch(i -> "WARNING".equals(i.getSeverity()));
        if (hasWarning || warnings.size() >= 2) return "MEDIUM";

        return "LOW";
    }

    private int estimateAffectedProjects(String section) {
        // 전역 설정 변경은 모든 프로젝트에 영향
        if ("GLOBAL_SETTINGS".equals(section) || "ENGINE_CONNECTION".equals(section)) {
            return -1; // -1 = 모든 프로젝트
        }
        return 0;
    }

    // =========================================================================
    // DTOs
    // =========================================================================

    public static class DryRunResult {
        private String targetSection;
        private String changeType;
        private String riskLevel; // LOW, MEDIUM, HIGH
        private int affectedProjects; // -1 = all
        private List<ImpactItem> impacts;
        private List<String> warnings;
        private List<String> recommendations;
        private long timestamp;

        public String getTargetSection() { return targetSection; }
        public void setTargetSection(String s) { this.targetSection = s; }
        public String getChangeType() { return changeType; }
        public void setChangeType(String changeType) { this.changeType = changeType; }
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        public int getAffectedProjects() { return affectedProjects; }
        public void setAffectedProjects(int n) { this.affectedProjects = n; }
        public List<ImpactItem> getImpacts() { return impacts; }
        public void setImpacts(List<ImpactItem> impacts) { this.impacts = impacts; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> r) { this.recommendations = r; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    public static class ImpactItem {
        private String title;
        private String severity; // INFO, WARNING, CRITICAL
        private String description;

        public ImpactItem() {}

        public ImpactItem(String title, String severity, String description) {
            this.title = title;
            this.severity = severity;
            this.description = description;
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
