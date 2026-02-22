package com.jask.bitbucket.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * 변경 승인 대기 Active Objects entity.
 * 중요 설정 변경 시 승인 워크플로우를 지원합니다.
 *
 * 요건 15: 변경 승인 워크플로우
 * 요건 16: 안전 적용 (Dry-run)
 */
@Table("JASK_PENDING_CHG")
@Preload
public interface PendingChangeEntity extends Entity {

    /**
     * 변경 대상 섹션: GLOBAL_SETTINGS, ENGINE_CONNECTION, SECURITY_MASKING 등
     */
    @Indexed
    String getTargetSection();
    void setTargetSection(String targetSection);

    /**
     * 변경 유형: CREATE, UPDATE, DELETE
     */
    String getChangeType();
    void setChangeType(String changeType);

    /**
     * 변경 전 값 (JSON)
     */
    @StringLength(StringLength.UNLIMITED)
    String getBeforeJson();
    void setBeforeJson(String beforeJson);

    /**
     * 변경 후 값 (JSON)
     */
    @StringLength(StringLength.UNLIMITED)
    String getAfterJson();
    void setAfterJson(String afterJson);

    /**
     * 변경 요약 (사람이 읽을 수 있는 설명)
     */
    @StringLength(StringLength.UNLIMITED)
    String getChangeSummary();
    void setChangeSummary(String changeSummary);

    /**
     * Dry-run 결과 (JSON: 영향 범위 분석)
     */
    @StringLength(StringLength.UNLIMITED)
    String getDryRunResult();
    void setDryRunResult(String dryRunResult);

    /**
     * 상태: PENDING, APPROVED, REJECTED, APPLIED, ROLLED_BACK
     */
    @Indexed
    String getStatus();
    void setStatus(String status);

    /**
     * 요청자
     */
    @Indexed
    String getRequestedBy();
    void setRequestedBy(String requestedBy);

    /**
     * 승인/거부자
     */
    String getReviewedBy();
    void setReviewedBy(String reviewedBy);

    /**
     * 승인/거부 사유
     */
    @StringLength(StringLength.UNLIMITED)
    String getReviewComment();
    void setReviewComment(String reviewComment);

    /**
     * 요청 시각 (epoch ms)
     */
    @Indexed
    long getRequestedAt();
    void setRequestedAt(long requestedAt);

    /**
     * 승인/거부 시각 (epoch ms)
     */
    long getReviewedAt();
    void setReviewedAt(long reviewedAt);

    /**
     * 적용 시각 (epoch ms)
     */
    long getAppliedAt();
    void setAppliedAt(long appliedAt);
}
