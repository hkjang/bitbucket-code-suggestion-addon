package com.jask.bitbucket.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.jask.bitbucket.ao.AuditLogEntity;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;

/**
 * 감사 로그 서비스 구현.
 */
@ExportAsService({AuditLogService.class})
@Named("auditLogService")
public class AuditLogServiceImpl implements AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    private final ActiveObjects ao;

    @Inject
    public AuditLogServiceImpl(@ComponentImport ActiveObjects ao) {
        this.ao = ao;
    }

    @Override
    public void log(String eventType, String username, String targetType,
                    String targetId, String details, String ipAddress) {
        try {
            AuditLogEntity entry = ao.create(AuditLogEntity.class);
            entry.setEventType(eventType);
            entry.setUsername(username != null ? username : "system");
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setDetails(details);
            entry.setIpAddress(ipAddress);
            entry.setTimestamp(System.currentTimeMillis());
            entry.save();

            log.debug("감사 로그 기록: type={}, user={}, target={}/{}",
                    eventType, username, targetType, targetId);
        } catch (Exception e) {
            // 감사 로그 기록 실패가 주요 기능에 영향을 주면 안 됨
            log.error("감사 로그 기록 실패: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<AuditEntry> getAuditLog(int page, int pageSize) {
        AuditLogEntity[] entities = ao.find(AuditLogEntity.class,
                Query.select()
                        .order("TIMESTAMP DESC")
                        .limit(pageSize)
                        .offset(page * pageSize));
        return toEntries(entities);
    }

    @Override
    public List<AuditEntry> getAuditLogByUser(String username, int page, int pageSize) {
        AuditLogEntity[] entities = ao.find(AuditLogEntity.class,
                Query.select()
                        .where("USERNAME = ?", username)
                        .order("TIMESTAMP DESC")
                        .limit(pageSize)
                        .offset(page * pageSize));
        return toEntries(entities);
    }

    @Override
    public List<AuditEntry> getAuditLogByEventType(String eventType, int page, int pageSize) {
        AuditLogEntity[] entities = ao.find(AuditLogEntity.class,
                Query.select()
                        .where("EVENT_TYPE = ?", eventType)
                        .order("TIMESTAMP DESC")
                        .limit(pageSize)
                        .offset(page * pageSize));
        return toEntries(entities);
    }

    @Override
    public int getTotalCount() {
        return ao.count(AuditLogEntity.class);
    }

    private List<AuditEntry> toEntries(AuditLogEntity[] entities) {
        List<AuditEntry> entries = new ArrayList<>();
        for (AuditLogEntity entity : entities) {
            AuditEntry entry = new AuditEntry();
            entry.setId(entity.getID());
            entry.setEventType(entity.getEventType());
            entry.setUsername(entity.getUsername());
            entry.setTargetType(entity.getTargetType());
            entry.setTargetId(entity.getTargetId());
            entry.setDetails(entity.getDetails());
            entry.setIpAddress(entity.getIpAddress());
            entry.setTimestamp(entity.getTimestamp());
            entries.add(entry);
        }
        return entries;
    }
}
