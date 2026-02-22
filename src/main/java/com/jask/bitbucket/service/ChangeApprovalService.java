package com.jask.bitbucket.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.jask.bitbucket.ao.PendingChangeEntity;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

/**
 * 변경 승인 워크플로우 서비스.
 *
 * 요건 15: 변경 승인 워크플로우 (PENDING → APPROVED → APPLIED)
 *
 * 중요 설정 변경(엔진 연결, 마스킹 규칙, 전역 정책)은
 * 승인 프로세스를 거쳐야 적용됩니다.
 */
@ExportAsService({ChangeApprovalService.class})
@Named("changeApprovalService")
public class ChangeApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ChangeApprovalService.class);

    private final ActiveObjects ao;
    private final AuditLogService auditLogService;
    private final AlertService alertService;

    @Inject
    public ChangeApprovalService(@ComponentImport ActiveObjects ao,
                                  AuditLogService auditLogService,
                                  AlertService alertService) {
        this.ao = ao;
        this.auditLogService = auditLogService;
        this.alertService = alertService;
    }

    /**
     * 변경 요청을 생성합니다.
     */
    public ChangeRequestInfo createChangeRequest(ChangeRequest request) {
        PendingChangeEntity entity = ao.create(PendingChangeEntity.class);
        entity.setTargetSection(request.getTargetSection());
        entity.setChangeType(request.getChangeType());
        entity.setBeforeJson(request.getBeforeJson());
        entity.setAfterJson(request.getAfterJson());
        entity.setChangeSummary(request.getChangeSummary());
        entity.setStatus("PENDING");
        entity.setRequestedBy(request.getRequestedBy());
        entity.setRequestedAt(System.currentTimeMillis());
        entity.save();

        // 알림 발생
        alertService.fireChangeApprovalRequest(request.getTargetSection(), request.getRequestedBy());

        log.info("변경 승인 요청 생성: section={}, type={}, by={}",
                request.getTargetSection(), request.getChangeType(), request.getRequestedBy());
        return toInfo(entity);
    }

    /**
     * 대기 중인 변경 요청 목록을 조회합니다.
     */
    public List<ChangeRequestInfo> getPendingChanges() {
        PendingChangeEntity[] entities = ao.find(PendingChangeEntity.class,
                Query.select().where("STATUS = ?", "PENDING").order("REQUESTED_AT DESC"));
        return toInfoList(entities);
    }

    /**
     * 모든 변경 요청을 조회합니다.
     */
    public List<ChangeRequestInfo> getAllChanges(int page, int pageSize) {
        PendingChangeEntity[] entities = ao.find(PendingChangeEntity.class,
                Query.select().order("REQUESTED_AT DESC")
                        .limit(pageSize).offset(page * pageSize));
        return toInfoList(entities);
    }

    /**
     * 특정 변경 요청의 상세를 조회합니다.
     */
    public ChangeRequestDetail getChangeDetail(int changeId) {
        PendingChangeEntity entity = ao.get(PendingChangeEntity.class, changeId);
        if (entity == null) {
            throw new IllegalArgumentException("변경 요청을 찾을 수 없습니다: ID=" + changeId);
        }
        ChangeRequestDetail detail = new ChangeRequestDetail();
        detail.setInfo(toInfo(entity));
        detail.setBeforeJson(entity.getBeforeJson());
        detail.setAfterJson(entity.getAfterJson());
        detail.setDryRunResult(entity.getDryRunResult());
        return detail;
    }

    /**
     * 변경을 승인합니다.
     */
    public ChangeRequestInfo approveChange(int changeId, String reviewedBy, String comment) {
        PendingChangeEntity entity = ao.get(PendingChangeEntity.class, changeId);
        if (entity == null) {
            throw new IllegalArgumentException("변경 요청을 찾을 수 없습니다: ID=" + changeId);
        }
        if (!"PENDING".equals(entity.getStatus())) {
            throw new IllegalStateException("이미 처리된 변경 요청입니다: status=" + entity.getStatus());
        }
        if (entity.getRequestedBy().equals(reviewedBy)) {
            throw new IllegalArgumentException("자신의 변경 요청은 승인할 수 없습니다");
        }

        entity.setStatus("APPROVED");
        entity.setReviewedBy(reviewedBy);
        entity.setReviewComment(comment);
        entity.setReviewedAt(System.currentTimeMillis());
        entity.save();

        auditLogService.log("CHANGE_APPROVED", reviewedBy, entity.getTargetSection(),
                String.valueOf(changeId), "변경 승인: " + entity.getChangeSummary(), "internal");

        log.info("변경 승인: id={}, section={}, by={}", changeId, entity.getTargetSection(), reviewedBy);
        return toInfo(entity);
    }

    /**
     * 변경을 거부합니다.
     */
    public ChangeRequestInfo rejectChange(int changeId, String reviewedBy, String comment) {
        PendingChangeEntity entity = ao.get(PendingChangeEntity.class, changeId);
        if (entity == null) {
            throw new IllegalArgumentException("변경 요청을 찾을 수 없습니다: ID=" + changeId);
        }
        if (!"PENDING".equals(entity.getStatus())) {
            throw new IllegalStateException("이미 처리된 변경 요청입니다: status=" + entity.getStatus());
        }

        entity.setStatus("REJECTED");
        entity.setReviewedBy(reviewedBy);
        entity.setReviewComment(comment);
        entity.setReviewedAt(System.currentTimeMillis());
        entity.save();

        auditLogService.log("CHANGE_REJECTED", reviewedBy, entity.getTargetSection(),
                String.valueOf(changeId), "변경 거부: " + comment, "internal");

        log.info("변경 거부: id={}, section={}, by={}", changeId, entity.getTargetSection(), reviewedBy);
        return toInfo(entity);
    }

    /**
     * 승인된 변경을 적용 완료로 표시합니다.
     */
    public void markApplied(int changeId) {
        PendingChangeEntity entity = ao.get(PendingChangeEntity.class, changeId);
        if (entity != null && "APPROVED".equals(entity.getStatus())) {
            entity.setStatus("APPLIED");
            entity.setAppliedAt(System.currentTimeMillis());
            entity.save();
        }
    }

    /**
     * Dry-run 결과를 저장합니다.
     */
    public void saveDryRunResult(int changeId, String dryRunJson) {
        PendingChangeEntity entity = ao.get(PendingChangeEntity.class, changeId);
        if (entity != null) {
            entity.setDryRunResult(dryRunJson);
            entity.save();
        }
    }

    /**
     * 대기 중인 변경 수를 반환합니다.
     */
    public int getPendingCount() {
        return ao.count(PendingChangeEntity.class, Query.select().where("STATUS = ?", "PENDING"));
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    private ChangeRequestInfo toInfo(PendingChangeEntity entity) {
        ChangeRequestInfo info = new ChangeRequestInfo();
        info.setId(entity.getID());
        info.setTargetSection(entity.getTargetSection());
        info.setChangeType(entity.getChangeType());
        info.setChangeSummary(entity.getChangeSummary());
        info.setStatus(entity.getStatus());
        info.setRequestedBy(entity.getRequestedBy());
        info.setReviewedBy(entity.getReviewedBy());
        info.setReviewComment(entity.getReviewComment());
        info.setRequestedAt(entity.getRequestedAt());
        info.setReviewedAt(entity.getReviewedAt());
        info.setAppliedAt(entity.getAppliedAt());
        info.setHasDryRun(entity.getDryRunResult() != null && !entity.getDryRunResult().isEmpty());
        return info;
    }

    private List<ChangeRequestInfo> toInfoList(PendingChangeEntity[] entities) {
        List<ChangeRequestInfo> list = new ArrayList<>();
        for (PendingChangeEntity e : entities) {
            list.add(toInfo(e));
        }
        return list;
    }

    // =========================================================================
    // DTOs
    // =========================================================================

    public static class ChangeRequest {
        private String targetSection;
        private String changeType;
        private String beforeJson;
        private String afterJson;
        private String changeSummary;
        private String requestedBy;

        public String getTargetSection() { return targetSection; }
        public void setTargetSection(String targetSection) { this.targetSection = targetSection; }
        public String getChangeType() { return changeType; }
        public void setChangeType(String changeType) { this.changeType = changeType; }
        public String getBeforeJson() { return beforeJson; }
        public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
        public String getAfterJson() { return afterJson; }
        public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
        public String getChangeSummary() { return changeSummary; }
        public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
        public String getRequestedBy() { return requestedBy; }
        public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    }

    public static class ChangeRequestInfo {
        private int id;
        private String targetSection;
        private String changeType;
        private String changeSummary;
        private String status;
        private String requestedBy;
        private String reviewedBy;
        private String reviewComment;
        private long requestedAt;
        private long reviewedAt;
        private long appliedAt;
        private boolean hasDryRun;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getTargetSection() { return targetSection; }
        public void setTargetSection(String s) { this.targetSection = s; }
        public String getChangeType() { return changeType; }
        public void setChangeType(String changeType) { this.changeType = changeType; }
        public String getChangeSummary() { return changeSummary; }
        public void setChangeSummary(String s) { this.changeSummary = s; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getRequestedBy() { return requestedBy; }
        public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
        public String getReviewedBy() { return reviewedBy; }
        public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
        public String getReviewComment() { return reviewComment; }
        public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
        public long getRequestedAt() { return requestedAt; }
        public void setRequestedAt(long requestedAt) { this.requestedAt = requestedAt; }
        public long getReviewedAt() { return reviewedAt; }
        public void setReviewedAt(long reviewedAt) { this.reviewedAt = reviewedAt; }
        public long getAppliedAt() { return appliedAt; }
        public void setAppliedAt(long appliedAt) { this.appliedAt = appliedAt; }
        public boolean isHasDryRun() { return hasDryRun; }
        public void setHasDryRun(boolean hasDryRun) { this.hasDryRun = hasDryRun; }
    }

    public static class ChangeRequestDetail {
        private ChangeRequestInfo info;
        private String beforeJson;
        private String afterJson;
        private String dryRunResult;

        public ChangeRequestInfo getInfo() { return info; }
        public void setInfo(ChangeRequestInfo info) { this.info = info; }
        public String getBeforeJson() { return beforeJson; }
        public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
        public String getAfterJson() { return afterJson; }
        public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
        public String getDryRunResult() { return dryRunResult; }
        public void setDryRunResult(String dryRunResult) { this.dryRunResult = dryRunResult; }
    }
}
