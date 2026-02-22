package com.jask.bitbucket.service;

import java.util.List;

/**
 * 감사 로그 서비스 인터페이스.
 */
public interface AuditLogService {

    /**
     * 감사 로그를 기록합니다.
     */
    void log(String eventType, String username, String targetType,
             String targetId, String details, String ipAddress);

    /**
     * 감사 로그를 조회합니다.
     */
    List<AuditEntry> getAuditLog(int page, int pageSize);

    /**
     * 특정 사용자의 감사 로그를 조회합니다.
     */
    List<AuditEntry> getAuditLogByUser(String username, int page, int pageSize);

    /**
     * 특정 이벤트 유형의 감사 로그를 조회합니다.
     */
    List<AuditEntry> getAuditLogByEventType(String eventType, int page, int pageSize);

    /**
     * 총 로그 수를 반환합니다.
     */
    int getTotalCount();

    /**
     * 감사 로그 DTO.
     */
    class AuditEntry {
        private long id;
        private String eventType;
        private String username;
        private String targetType;
        private String targetId;
        private String details;
        private String ipAddress;
        private long timestamp;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }

        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }

        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }

        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
}
