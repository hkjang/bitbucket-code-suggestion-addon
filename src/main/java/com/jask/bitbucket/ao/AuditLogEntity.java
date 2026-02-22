package com.jask.bitbucket.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * 감사 로그 Active Objects entity.
 * 설정 변경, 분석 실행, 보안 이벤트 등을 기록합니다.
 */
@Table("JASK_AUDIT_LOG")
@Preload
public interface AuditLogEntity extends Entity {

    /**
     * 이벤트 유형: SETTINGS_CHANGED, ANALYSIS_REQUESTED, ANALYSIS_COMPLETED,
     * SUGGESTION_ACCEPTED, SUGGESTION_REJECTED, API_KEY_CHANGED,
     * ENDPOINT_CHANGED, SECURITY_VIOLATION, etc.
     */
    @Indexed
    String getEventType();
    void setEventType(String eventType);

    /**
     * 이벤트를 발생시킨 사용자
     */
    @Indexed
    String getUsername();
    void setUsername(String username);

    /**
     * 이벤트 대상 (레포지토리, PR 등)
     */
    String getTargetType();
    void setTargetType(String targetType);

    /**
     * 대상 식별자 (repoId, prId 등)
     */
    String getTargetId();
    void setTargetId(String targetId);

    /**
     * 이벤트 상세 내용 (JSON 또는 텍스트)
     */
    @StringLength(StringLength.UNLIMITED)
    String getDetails();
    void setDetails(String details);

    /**
     * 요청 IP 주소
     */
    String getIpAddress();
    void setIpAddress(String ipAddress);

    /**
     * 이벤트 발생 시간 (epoch ms)
     */
    @Indexed
    long getTimestamp();
    void setTimestamp(long timestamp);
}
