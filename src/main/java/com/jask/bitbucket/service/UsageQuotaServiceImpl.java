package com.jask.bitbucket.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.jask.bitbucket.ao.UsageQuotaEntity;
import com.jask.bitbucket.ao.UsageRecordEntity;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 사용량/비용 한도 관리 서비스 구현.
 *
 * 요건 11: 일/주/월 단위 호출 및 토큰 한도 관리
 * 요건 14: 임계값 기반 경고 알림 연계
 */
@ExportAsService({UsageQuotaService.class})
@Named("usageQuotaService")
public class UsageQuotaServiceImpl implements UsageQuotaService {

    private static final Logger log = LoggerFactory.getLogger(UsageQuotaServiceImpl.class);

    private final ActiveObjects ao;

    @Inject
    public UsageQuotaServiceImpl(@ComponentImport ActiveObjects ao) {
        this.ao = ao;
    }

    @Override
    public QuotaInfo createQuota(QuotaCreateRequest request) {
        UsageQuotaEntity entity = ao.create(UsageQuotaEntity.class);
        applyRequest(entity, request);
        entity.setCreatedAt(System.currentTimeMillis());
        entity.setUpdatedAt(System.currentTimeMillis());
        entity.save();

        log.info("사용량 한도 생성: scope={}/{}, period={}, maxCalls={}",
                request.getScope(), request.getScopeKey(), request.getPeriod(), request.getMaxCalls());
        return toInfo(entity);
    }

    @Override
    public QuotaInfo updateQuota(int quotaId, QuotaCreateRequest request) {
        UsageQuotaEntity entity = ao.get(UsageQuotaEntity.class, quotaId);
        if (entity == null) {
            throw new IllegalArgumentException("한도 설정을 찾을 수 없습니다: ID=" + quotaId);
        }
        applyRequest(entity, request);
        entity.setUpdatedAt(System.currentTimeMillis());
        entity.save();

        log.info("사용량 한도 수정: id={}", quotaId);
        return toInfo(entity);
    }

    @Override
    public void deleteQuota(int quotaId) {
        UsageQuotaEntity entity = ao.get(UsageQuotaEntity.class, quotaId);
        if (entity == null) {
            throw new IllegalArgumentException("한도 설정을 찾을 수 없습니다: ID=" + quotaId);
        }
        ao.delete(entity);
        log.info("사용량 한도 삭제: id={}", quotaId);
    }

    @Override
    public List<QuotaInfo> getAllQuotas() {
        UsageQuotaEntity[] entities = ao.find(UsageQuotaEntity.class,
                Query.select().order("SCOPE ASC, SCOPE_KEY ASC"));
        List<QuotaInfo> list = new ArrayList<>();
        for (UsageQuotaEntity e : entities) {
            list.add(toInfo(e));
        }
        return list;
    }

    @Override
    public QuotaInfo getQuotaByScope(String scope, String scopeKey) {
        UsageQuotaEntity[] entities = ao.find(UsageQuotaEntity.class,
                Query.select().where("SCOPE = ? AND SCOPE_KEY = ?", scope, scopeKey));
        return entities.length > 0 ? toInfo(entities[0]) : null;
    }

    @Override
    public void recordUsage(UsageRecord record) {
        try {
            UsageRecordEntity entity = ao.create(UsageRecordEntity.class);
            entity.setProjectKey(record.getProjectKey() != null ? record.getProjectKey() : "global");
            entity.setUsername(record.getUsername());
            entity.setEngineProfile(record.getEngineProfile());
            entity.setInputTokens(record.getInputTokens());
            entity.setOutputTokens(record.getOutputTokens());
            entity.setEstimatedCostMicro(record.getEstimatedCostMicro());
            entity.setLatencyMs(record.getLatencyMs());
            entity.setSuccess(record.isSuccess());
            entity.setTimestamp(System.currentTimeMillis());
            entity.save();
        } catch (Exception e) {
            log.error("사용량 기록 실패: {}", e.getMessage(), e);
        }
    }

    @Override
    public UsageSummary getUsageSummary(String scope, String scopeKey, String period) {
        long periodStart = calculatePeriodStart(period);

        String projectFilter = "GLOBAL".equals(scope) ? null : scopeKey;

        // 현재 기간 사용량 집계
        UsageRecordEntity[] records;
        if (projectFilter != null) {
            records = ao.find(UsageRecordEntity.class,
                    Query.select().where("PROJECT_KEY = ? AND TIMESTAMP >= ?", projectFilter, periodStart));
        } else {
            records = ao.find(UsageRecordEntity.class,
                    Query.select().where("TIMESTAMP >= ?", periodStart));
        }

        int totalCalls = records.length;
        long totalInputTokens = 0;
        long totalOutputTokens = 0;
        long totalCost = 0;
        for (UsageRecordEntity r : records) {
            totalInputTokens += r.getInputTokens();
            totalOutputTokens += r.getOutputTokens();
            totalCost += r.getEstimatedCostMicro();
        }

        // 한도 조회
        QuotaInfo quota = getQuotaByScope(scope, scopeKey);

        UsageSummary summary = new UsageSummary();
        summary.setScope(scope);
        summary.setScopeKey(scopeKey);
        summary.setPeriod(period);
        summary.setTotalCalls(totalCalls);
        summary.setTotalInputTokens(totalInputTokens);
        summary.setTotalOutputTokens(totalOutputTokens);
        summary.setTotalCostMicro(totalCost);

        if (quota != null) {
            summary.setMaxCalls(quota.getMaxCalls());
            summary.setMaxTokens(quota.getMaxTokens());
            double percent = quota.getMaxCalls() > 0 ?
                    (double) totalCalls / quota.getMaxCalls() * 100.0 : 0;
            summary.setUsagePercent(Math.min(percent, 100.0));

            if (percent >= 100) {
                summary.setStatus("EXCEEDED");
            } else if (percent >= quota.getWarningThresholdPercent()) {
                summary.setStatus("WARNING");
            } else {
                summary.setStatus("NORMAL");
            }
        } else {
            summary.setStatus("NORMAL");
            summary.setUsagePercent(0);
        }

        return summary;
    }

    @Override
    public QuotaCheckResult checkQuota(String projectKey, String username) {
        // 프로젝트 레벨 한도 확인
        QuotaInfo projectQuota = getQuotaByScope("PROJECT", projectKey);
        if (projectQuota != null && projectQuota.isEnabled()) {
            QuotaCheckResult result = evaluateQuota(projectQuota, projectKey);
            if (!result.isAllowed()) return result;
        }

        // 전역 한도 확인
        QuotaInfo globalQuota = getQuotaByScope("GLOBAL", "global");
        if (globalQuota != null && globalQuota.isEnabled()) {
            return evaluateQuota(globalQuota, "global");
        }

        return QuotaCheckResult.allow();
    }

    @Override
    public List<DailyUsageStat> getDailyUsageStats(String scope, String scopeKey, int days) {
        long startTime = System.currentTimeMillis() - ((long) days * 24 * 60 * 60 * 1000);

        UsageRecordEntity[] records;
        if ("GLOBAL".equals(scope)) {
            records = ao.find(UsageRecordEntity.class,
                    Query.select().where("TIMESTAMP >= ?", startTime).order("TIMESTAMP ASC"));
        } else {
            records = ao.find(UsageRecordEntity.class,
                    Query.select().where("PROJECT_KEY = ? AND TIMESTAMP >= ?", scopeKey, startTime)
                            .order("TIMESTAMP ASC"));
        }

        // 일별 집계
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Map<String, DailyUsageStat> dailyMap = new LinkedHashMap<>();

        // 빈 날짜도 포함
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(startTime);
        for (int i = 0; i < days; i++) {
            String dateStr = sdf.format(cal.getTime());
            DailyUsageStat stat = new DailyUsageStat();
            stat.setDate(dateStr);
            dailyMap.put(dateStr, stat);
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        for (UsageRecordEntity r : records) {
            String dateStr = sdf.format(new Date(r.getTimestamp()));
            DailyUsageStat stat = dailyMap.get(dateStr);
            if (stat != null) {
                stat.setCalls(stat.getCalls() + 1);
                stat.setInputTokens(stat.getInputTokens() + r.getInputTokens());
                stat.setOutputTokens(stat.getOutputTokens() + r.getOutputTokens());
                stat.setCostMicro(stat.getCostMicro() + r.getEstimatedCostMicro());
            }
        }

        return new ArrayList<>(dailyMap.values());
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    private QuotaCheckResult evaluateQuota(QuotaInfo quota, String scopeKey) {
        long periodStart = calculatePeriodStart(quota.getPeriod());

        UsageRecordEntity[] records;
        if ("global".equals(scopeKey)) {
            records = ao.find(UsageRecordEntity.class,
                    Query.select().where("TIMESTAMP >= ?", periodStart));
        } else {
            records = ao.find(UsageRecordEntity.class,
                    Query.select().where("PROJECT_KEY = ? AND TIMESTAMP >= ?", scopeKey, periodStart));
        }

        int currentCalls = records.length;
        double percent = quota.getMaxCalls() > 0 ?
                (double) currentCalls / quota.getMaxCalls() * 100.0 : 0;

        QuotaCheckResult result = new QuotaCheckResult();
        result.setUsagePercent(percent);

        if (percent >= 100) {
            String action = quota.getExceedAction();
            if ("BLOCK".equals(action)) {
                result.setAllowed(false);
                result.setAction("BLOCK");
                result.setMessage("사용량 한도 초과 (호출 차단됨)");
            } else if ("THROTTLE".equals(action)) {
                result.setAllowed(true);
                result.setAction("THROTTLE");
                result.setMessage("사용량 한도 초과 (속도 제한 적용)");
            } else {
                result.setAllowed(true);
                result.setAction("WARN");
                result.setMessage("사용량 한도 초과 (경고)");
            }
        } else if (percent >= quota.getWarningThresholdPercent()) {
            result.setAllowed(true);
            result.setAction("WARN");
            result.setMessage(String.format("사용량 경고: %.0f%% 사용됨", percent));
        } else {
            result.setAllowed(true);
            result.setAction("ALLOW");
        }

        return result;
    }

    private long calculatePeriodStart(String period) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if ("WEEKLY".equalsIgnoreCase(period)) {
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        } else if ("MONTHLY".equalsIgnoreCase(period)) {
            cal.set(Calendar.DAY_OF_MONTH, 1);
        }
        // DAILY는 이미 오늘 시작

        return cal.getTimeInMillis();
    }

    private void applyRequest(UsageQuotaEntity entity, QuotaCreateRequest request) {
        entity.setScope(request.getScope());
        entity.setScopeKey(request.getScopeKey());
        entity.setPeriod(request.getPeriod());
        entity.setMaxCalls(request.getMaxCalls());
        entity.setMaxTokens(request.getMaxTokens());
        entity.setWarningThresholdPercent(request.getWarningThresholdPercent());
        entity.setExceedAction(request.getExceedAction());
        entity.setEnabled(request.isEnabled());
    }

    private QuotaInfo toInfo(UsageQuotaEntity entity) {
        QuotaInfo info = new QuotaInfo();
        info.setId(entity.getID());
        info.setScope(entity.getScope());
        info.setScopeKey(entity.getScopeKey());
        info.setPeriod(entity.getPeriod());
        info.setMaxCalls(entity.getMaxCalls());
        info.setMaxTokens(entity.getMaxTokens());
        info.setWarningThresholdPercent(entity.getWarningThresholdPercent());
        info.setExceedAction(entity.getExceedAction());
        info.setEnabled(entity.isEnabled());
        info.setCreatedAt(entity.getCreatedAt());
        info.setUpdatedAt(entity.getUpdatedAt());
        return info;
    }
}
