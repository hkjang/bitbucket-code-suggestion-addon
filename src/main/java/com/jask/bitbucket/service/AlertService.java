package com.jask.bitbucket.service;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 운영 알림 서비스.
 *
 * 요건 14: 운영 알림 (한도 임계값, 연결 실패, 에러율 상승 등)
 *
 * 알림 이력을 인메모리에 보관하고 (최근 500건),
 * 관리자 대시보드와 REST API를 통해 조회합니다.
 * 향후 이메일/Webhook 확장 가능.
 */
@ExportAsService({AlertService.class})
@Named("alertService")
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private static final int MAX_ALERTS = 500;

    private final AuditLogService auditLogService;

    /** 알림 이력 (최근 500건, 최신순) */
    private final ConcurrentLinkedDeque<AlertEntry> alertHistory = new ConcurrentLinkedDeque<>();

    @Inject
    public AlertService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    // =========================================================================
    // 알림 발생
    // =========================================================================

    /**
     * 사용량 경고 알림을 발생시킵니다.
     */
    public void fireQuotaWarning(String scope, String scopeKey, double usagePercent) {
        String message = String.format("[사용량 경고] %s/%s: %.0f%% 사용됨", scope, scopeKey, usagePercent);
        addAlert(AlertLevel.WARNING, AlertCategory.QUOTA, message);
        auditLogService.log("ALERT_QUOTA_WARNING", "system", "QUOTA",
                scope + "/" + scopeKey, message, "internal");
    }

    /**
     * 사용량 한도 초과 알림을 발생시킵니다.
     */
    public void fireQuotaExceeded(String scope, String scopeKey, String action) {
        String message = String.format("[한도 초과] %s/%s: 조치=%s", scope, scopeKey, action);
        addAlert(AlertLevel.CRITICAL, AlertCategory.QUOTA, message);
        auditLogService.log("ALERT_QUOTA_EXCEEDED", "system", "QUOTA",
                scope + "/" + scopeKey, message, "internal");
    }

    /**
     * 엔진 연결 실패 알림을 발생시킵니다.
     */
    public void fireConnectionFailure(String profileName, String error) {
        String message = String.format("[연결 실패] 엔진: %s - %s", profileName, error);
        addAlert(AlertLevel.CRITICAL, AlertCategory.CONNECTION, message);
        auditLogService.log("ALERT_CONNECTION_FAILURE", "system", "ENGINE",
                profileName, message, "internal");
    }

    /**
     * 높은 에러율 알림을 발생시킵니다.
     */
    public void fireHighErrorRate(double errorRate, int windowMinutes) {
        String message = String.format("[에러율 상승] 최근 %d분간 에러율: %.1f%%", windowMinutes, errorRate);
        addAlert(AlertLevel.WARNING, AlertCategory.ERROR_RATE, message);
    }

    /**
     * 서킷 브레이커 오픈 알림을 발생시킵니다.
     */
    public void fireCircuitBreakerOpen(String engineName) {
        String message = String.format("[서킷 브레이커] %s 서킷 오픈됨 - 요청이 차단됩니다", engineName);
        addAlert(AlertLevel.CRITICAL, AlertCategory.CIRCUIT_BREAKER, message);
        auditLogService.log("ALERT_CIRCUIT_OPEN", "system", "ENGINE",
                engineName, message, "internal");
    }

    /**
     * 변경 승인 요청 알림을 발생시킵니다.
     */
    public void fireChangeApprovalRequest(String section, String requestedBy) {
        String message = String.format("[승인 요청] %s 섹션 변경 요청 (by: %s)", section, requestedBy);
        addAlert(AlertLevel.INFO, AlertCategory.CHANGE_APPROVAL, message);
    }

    // =========================================================================
    // 알림 조회
    // =========================================================================

    /**
     * 최근 알림 목록을 조회합니다.
     */
    public List<AlertEntry> getRecentAlerts(int limit) {
        List<AlertEntry> result = new ArrayList<>();
        Iterator<AlertEntry> it = alertHistory.descendingIterator();
        int count = 0;
        while (it.hasNext() && count < limit) {
            result.add(it.next());
            count++;
        }
        return result;
    }

    /**
     * 특정 카테고리의 알림을 조회합니다.
     */
    public List<AlertEntry> getAlertsByCategory(AlertCategory category, int limit) {
        List<AlertEntry> result = new ArrayList<>();
        Iterator<AlertEntry> it = alertHistory.descendingIterator();
        while (it.hasNext() && result.size() < limit) {
            AlertEntry entry = it.next();
            if (entry.getCategory() == category) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * 확인되지 않은 알림 수를 반환합니다.
     */
    public int getUnacknowledgedCount() {
        int count = 0;
        for (AlertEntry entry : alertHistory) {
            if (!entry.isAcknowledged()) count++;
        }
        return count;
    }

    /**
     * 알림을 확인 처리합니다.
     */
    public void acknowledgeAlert(String alertId) {
        for (AlertEntry entry : alertHistory) {
            if (entry.getId().equals(alertId)) {
                entry.setAcknowledged(true);
                break;
            }
        }
    }

    /**
     * 모든 알림을 확인 처리합니다.
     */
    public void acknowledgeAll() {
        for (AlertEntry entry : alertHistory) {
            entry.setAcknowledged(true);
        }
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    private void addAlert(AlertLevel level, AlertCategory category, String message) {
        AlertEntry entry = new AlertEntry();
        entry.setId(UUID.randomUUID().toString().substring(0, 8));
        entry.setLevel(level);
        entry.setCategory(category);
        entry.setMessage(message);
        entry.setTimestamp(System.currentTimeMillis());
        entry.setAcknowledged(false);

        alertHistory.addLast(entry);
        while (alertHistory.size() > MAX_ALERTS) {
            alertHistory.pollFirst();
        }

        if (level == AlertLevel.CRITICAL) {
            log.warn("운영 알림 (CRITICAL): {}", message);
        } else {
            log.info("운영 알림 ({}): {}", level, message);
        }
    }

    // =========================================================================
    // 열거형 & DTO
    // =========================================================================

    public enum AlertLevel {
        INFO, WARNING, CRITICAL
    }

    public enum AlertCategory {
        QUOTA, CONNECTION, ERROR_RATE, CIRCUIT_BREAKER, CHANGE_APPROVAL, SYSTEM
    }

    public static class AlertEntry {
        private String id;
        private AlertLevel level;
        private AlertCategory category;
        private String message;
        private long timestamp;
        private boolean acknowledged;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public AlertLevel getLevel() { return level; }
        public void setLevel(AlertLevel level) { this.level = level; }
        public AlertCategory getCategory() { return category; }
        public void setCategory(AlertCategory category) { this.category = category; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public boolean isAcknowledged() { return acknowledged; }
        public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
    }
}
