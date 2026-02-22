package com.jask.bitbucket.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jask.bitbucket.ao.EngineProfileEntity;
import com.jask.bitbucket.ao.SettingsSnapshotEntity;
import com.jask.bitbucket.ao.UsageQuotaEntity;
import com.jask.bitbucket.config.PluginSettingsService;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

/**
 * 설정 백업/복원 서비스.
 *
 * 요건 17: 백업/복원 (JSON 스냅샷)
 *
 * 주요 기능:
 * - 현재 설정을 JSON 스냅샷으로 저장
 * - 스냅샷에서 설정 복원 (선택적 섹션 복원 가능)
 * - 스냅샷 목록 조회, 비교, 삭제
 * - 자동 백업 (중요 변경 전)
 */
@ExportAsService({SettingsBackupService.class})
@Named("settingsBackupService")
public class SettingsBackupService {

    private static final Logger log = LoggerFactory.getLogger(SettingsBackupService.class);
    private static final int MAX_SNAPSHOTS = 50;

    private final ActiveObjects ao;
    private final PluginSettingsService settingsService;
    private final Gson gson;

    @Inject
    public SettingsBackupService(@ComponentImport ActiveObjects ao,
                                  PluginSettingsService settingsService) {
        this.ao = ao;
        this.settingsService = settingsService;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * 현재 설정의 스냅샷을 생성합니다.
     */
    public SnapshotInfo createSnapshot(String name, String type, String description, String createdBy) {
        Map<String, Object> allSettings = collectAllSettings();
        String json = gson.toJson(allSettings);

        SettingsSnapshotEntity entity = ao.create(SettingsSnapshotEntity.class);
        entity.setSnapshotName(name != null ? name : generateSnapshotName(type));
        entity.setSnapshotType(type != null ? type : "MANUAL");
        entity.setSettingsJson(json);
        entity.setIncludedSections("GLOBAL,ENGINE,QUOTA");
        entity.setDescription(description);
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(System.currentTimeMillis());
        entity.setSizeBytes(json.getBytes().length);
        entity.save();

        // 오래된 자동 백업 정리
        cleanupOldSnapshots();

        log.info("설정 스냅샷 생성: name={}, type={}, by={}, size={}bytes",
                entity.getSnapshotName(), type, createdBy, json.getBytes().length);
        return toInfo(entity);
    }

    /**
     * 변경 전 자동 백업을 생성합니다.
     */
    public SnapshotInfo createAutoBackup(String section, String changedBy) {
        return createSnapshot(
                "auto_" + section + "_" + System.currentTimeMillis(),
                "PRE_CHANGE",
                section + " 변경 전 자동 백업",
                changedBy
        );
    }

    /**
     * 스냅샷 목록을 조회합니다.
     */
    public List<SnapshotInfo> listSnapshots() {
        SettingsSnapshotEntity[] entities = ao.find(SettingsSnapshotEntity.class,
                Query.select().order("CREATED_AT DESC"));
        List<SnapshotInfo> list = new ArrayList<>();
        for (SettingsSnapshotEntity e : entities) {
            list.add(toInfo(e));
        }
        return list;
    }

    /**
     * 특정 스냅샷의 상세 내용을 조회합니다 (JSON 포함).
     */
    public SnapshotDetail getSnapshotDetail(int snapshotId) {
        SettingsSnapshotEntity entity = ao.get(SettingsSnapshotEntity.class, snapshotId);
        if (entity == null) {
            throw new IllegalArgumentException("스냅샷을 찾을 수 없습니다: ID=" + snapshotId);
        }
        SnapshotDetail detail = new SnapshotDetail();
        detail.setInfo(toInfo(entity));
        detail.setSettingsJson(entity.getSettingsJson());
        return detail;
    }

    /**
     * 스냅샷에서 설정을 복원합니다.
     */
    @SuppressWarnings("unchecked")
    public RestoreResult restoreFromSnapshot(int snapshotId, List<String> sections, String restoredBy) {
        SettingsSnapshotEntity entity = ao.get(SettingsSnapshotEntity.class, snapshotId);
        if (entity == null) {
            throw new IllegalArgumentException("스냅샷을 찾을 수 없습니다: ID=" + snapshotId);
        }

        // 복원 전 자동 백업
        createAutoBackup("RESTORE", restoredBy);

        Map<String, Object> settings = gson.fromJson(entity.getSettingsJson(), Map.class);
        List<String> restoredSections = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (sections == null || sections.isEmpty() || sections.contains("GLOBAL")) {
            try {
                restoreGlobalSettings(settings);
                restoredSections.add("GLOBAL");
            } catch (Exception e) {
                errors.add("전역 설정 복원 실패: " + e.getMessage());
            }
        }

        RestoreResult result = new RestoreResult();
        result.setSuccess(errors.isEmpty());
        result.setRestoredSections(restoredSections);
        result.setErrors(errors);
        result.setSnapshotName(entity.getSnapshotName());

        log.info("설정 복원 완료: snapshot={}, sections={}, by={}",
                entity.getSnapshotName(), restoredSections, restoredBy);
        return result;
    }

    /**
     * 두 스냅샷 간 차이를 비교합니다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> compareSnapshots(int snapshotId1, int snapshotId2) {
        SettingsSnapshotEntity e1 = ao.get(SettingsSnapshotEntity.class, snapshotId1);
        SettingsSnapshotEntity e2 = ao.get(SettingsSnapshotEntity.class, snapshotId2);

        if (e1 == null || e2 == null) {
            throw new IllegalArgumentException("스냅샷을 찾을 수 없습니다");
        }

        Map<String, Object> s1 = gson.fromJson(e1.getSettingsJson(), Map.class);
        Map<String, Object> s2 = gson.fromJson(e2.getSettingsJson(), Map.class);

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("snapshot1", Map.of("id", e1.getID(), "name", e1.getSnapshotName(), "createdAt", e1.getCreatedAt()));
        diff.put("snapshot2", Map.of("id", e2.getID(), "name", e2.getSnapshotName(), "createdAt", e2.getCreatedAt()));

        // 키별 차이 검출
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(s1.keySet());
        allKeys.addAll(s2.keySet());

        List<Map<String, Object>> changes = new ArrayList<>();
        for (String key : allKeys) {
            Object v1 = s1.get(key);
            Object v2 = s2.get(key);
            if (!Objects.equals(v1, v2)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("key", key);
                change.put("before", v1);
                change.put("after", v2);
                changes.add(change);
            }
        }
        diff.put("changes", changes);
        diff.put("totalChanges", changes.size());

        return diff;
    }

    /**
     * 스냅샷을 삭제합니다.
     */
    public void deleteSnapshot(int snapshotId) {
        SettingsSnapshotEntity entity = ao.get(SettingsSnapshotEntity.class, snapshotId);
        if (entity == null) {
            throw new IllegalArgumentException("스냅샷을 찾을 수 없습니다: ID=" + snapshotId);
        }
        ao.delete(entity);
        log.info("스냅샷 삭제: id={}, name={}", snapshotId, entity.getSnapshotName());
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    private Map<String, Object> collectAllSettings() {
        Map<String, Object> all = new LinkedHashMap<>();

        // 전역 설정
        Map<String, Object> global = new LinkedHashMap<>();
        global.put("llmEndpoint", settingsService.getLlmEndpoint());
        global.put("llmModel", settingsService.getLlmModel());
        global.put("llmTemperature", settingsService.getLlmTemperature());
        global.put("llmMaxTokens", settingsService.getLlmMaxTokens());
        global.put("autoAnalysisEnabled", settingsService.isAutoAnalysisEnabled());
        global.put("mergeCheckEnabled", settingsService.isMergeCheckEnabled());
        global.put("mergeCheckMaxCritical", settingsService.getMergeCheckMaxCritical());
        global.put("minConfidenceThreshold", settingsService.getMinConfidenceThreshold());
        global.put("excludedFilePatterns", settingsService.getExcludedFilePatterns());
        global.put("supportedLanguages", settingsService.getSupportedLanguages());
        global.put("maxFilesPerAnalysis", settingsService.getMaxFilesPerAnalysis());
        global.put("maxFileSizeKb", settingsService.getMaxFileSizeKb());
        all.put("global", global);

        // 엔진 프로파일 (API 키 제외)
        EngineProfileEntity[] profiles = ao.find(EngineProfileEntity.class, Query.select());
        List<Map<String, Object>> profileList = new ArrayList<>();
        for (EngineProfileEntity p : profiles) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("profileName", p.getProfileName());
            pm.put("engineType", p.getEngineType());
            pm.put("endpointUrl", p.getEndpointUrl());
            pm.put("defaultModel", p.getDefaultModel());
            pm.put("temperature", p.getTemperature());
            pm.put("maxTokens", p.getMaxTokens());
            pm.put("timeoutSeconds", p.getTimeoutSeconds());
            pm.put("enabled", p.isEnabled());
            pm.put("defaultProfile", p.isDefaultProfile());
            pm.put("priority", p.getPriority());
            // API 키는 백업에서 제외 (보안)
            profileList.add(pm);
        }
        all.put("engineProfiles", profileList);

        // 사용량 한도
        UsageQuotaEntity[] quotas = ao.find(UsageQuotaEntity.class, Query.select());
        List<Map<String, Object>> quotaList = new ArrayList<>();
        for (UsageQuotaEntity q : quotas) {
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("scope", q.getScope());
            qm.put("scopeKey", q.getScopeKey());
            qm.put("period", q.getPeriod());
            qm.put("maxCalls", q.getMaxCalls());
            qm.put("maxTokens", q.getMaxTokens());
            qm.put("warningThreshold", q.getWarningThresholdPercent());
            qm.put("exceedAction", q.getExceedAction());
            qm.put("enabled", q.isEnabled());
            quotaList.add(qm);
        }
        all.put("quotas", quotaList);

        all.put("exportedAt", System.currentTimeMillis());
        all.put("pluginVersion", "1.0.0");

        return all;
    }

    @SuppressWarnings("unchecked")
    private void restoreGlobalSettings(Map<String, Object> settings) {
        Map<String, Object> global = (Map<String, Object>) settings.get("global");
        if (global == null) return;

        if (global.containsKey("llmEndpoint"))
            settingsService.setLlmEndpoint((String) global.get("llmEndpoint"));
        if (global.containsKey("llmModel"))
            settingsService.setLlmModel((String) global.get("llmModel"));
        if (global.containsKey("llmTemperature"))
            settingsService.setLlmTemperature(((Number) global.get("llmTemperature")).doubleValue());
        if (global.containsKey("llmMaxTokens"))
            settingsService.setLlmMaxTokens(((Number) global.get("llmMaxTokens")).intValue());
        if (global.containsKey("autoAnalysisEnabled"))
            settingsService.setAutoAnalysisEnabled((Boolean) global.get("autoAnalysisEnabled"));
        if (global.containsKey("mergeCheckEnabled"))
            settingsService.setMergeCheckEnabled((Boolean) global.get("mergeCheckEnabled"));
        if (global.containsKey("excludedFilePatterns"))
            settingsService.setExcludedFilePatterns((String) global.get("excludedFilePatterns"));
        if (global.containsKey("supportedLanguages"))
            settingsService.setSupportedLanguages((String) global.get("supportedLanguages"));
    }

    private void cleanupOldSnapshots() {
        SettingsSnapshotEntity[] all = ao.find(SettingsSnapshotEntity.class,
                Query.select().where("SNAPSHOT_TYPE = ?", "PRE_CHANGE")
                        .order("CREATED_AT DESC"));
        if (all.length > MAX_SNAPSHOTS) {
            for (int i = MAX_SNAPSHOTS; i < all.length; i++) {
                ao.delete(all[i]);
            }
        }
    }

    private String generateSnapshotName(String type) {
        return type + "_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
                .format(new Date());
    }

    private SnapshotInfo toInfo(SettingsSnapshotEntity entity) {
        SnapshotInfo info = new SnapshotInfo();
        info.setId(entity.getID());
        info.setSnapshotName(entity.getSnapshotName());
        info.setSnapshotType(entity.getSnapshotType());
        info.setIncludedSections(entity.getIncludedSections());
        info.setDescription(entity.getDescription());
        info.setCreatedBy(entity.getCreatedBy());
        info.setCreatedAt(entity.getCreatedAt());
        info.setSizeBytes(entity.getSizeBytes());
        return info;
    }

    // =========================================================================
    // DTOs
    // =========================================================================

    public static class SnapshotInfo {
        private int id;
        private String snapshotName;
        private String snapshotType;
        private String includedSections;
        private String description;
        private String createdBy;
        private long createdAt;
        private long sizeBytes;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getSnapshotName() { return snapshotName; }
        public void setSnapshotName(String snapshotName) { this.snapshotName = snapshotName; }
        public String getSnapshotType() { return snapshotType; }
        public void setSnapshotType(String snapshotType) { this.snapshotType = snapshotType; }
        public String getIncludedSections() { return includedSections; }
        public void setIncludedSections(String s) { this.includedSections = s; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    }

    public static class SnapshotDetail {
        private SnapshotInfo info;
        private String settingsJson;

        public SnapshotInfo getInfo() { return info; }
        public void setInfo(SnapshotInfo info) { this.info = info; }
        public String getSettingsJson() { return settingsJson; }
        public void setSettingsJson(String settingsJson) { this.settingsJson = settingsJson; }
    }

    public static class RestoreResult {
        private boolean success;
        private String snapshotName;
        private List<String> restoredSections;
        private List<String> errors;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getSnapshotName() { return snapshotName; }
        public void setSnapshotName(String snapshotName) { this.snapshotName = snapshotName; }
        public List<String> getRestoredSections() { return restoredSections; }
        public void setRestoredSections(List<String> s) { this.restoredSections = s; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
    }
}
