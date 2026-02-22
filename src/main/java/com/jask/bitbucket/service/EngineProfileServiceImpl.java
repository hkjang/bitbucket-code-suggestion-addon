package com.jask.bitbucket.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.jask.bitbucket.ao.EngineProfileEntity;
import com.jask.bitbucket.security.CredentialEncryptor;
import com.jask.bitbucket.security.EndpointValidator;
import net.java.ao.Query;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.net.InetAddress;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 엔진 프로파일 관리 서비스 구현.
 *
 * 요건 5: 엔진 프로파일 CRUD + 다중 프로파일 관리
 * 요건 6: 자격증명 AES-256-GCM 암호화 저장 (CredentialEncryptor)
 * 요건 7: 연결 테스트 (DNS → TLS → 인증 → 모델 가용성 단계별)
 */
@ExportAsService({EngineProfileService.class})
@Named("engineProfileService")
public class EngineProfileServiceImpl implements EngineProfileService {

    private static final Logger log = LoggerFactory.getLogger(EngineProfileServiceImpl.class);

    private final ActiveObjects ao;
    private final OkHttpClient httpClient;

    @Inject
    public EngineProfileServiceImpl(@ComponentImport ActiveObjects ao) {
        this.ao = ao;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public EngineProfileInfo createProfile(CreateProfileRequest request, String createdBy) {
        // SSRF 검증 (Ollama 로컬 허용)
        EndpointValidator.ValidationResult validation =
                EndpointValidator.validate(request.getEndpointUrl(), true);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("엔드포인트 검증 실패: " + validation.getMessage());
        }

        EngineProfileEntity entity = ao.create(EngineProfileEntity.class);
        entity.setProfileName(request.getProfileName());
        entity.setEngineType(request.getEngineType());
        entity.setEndpointUrl(request.getEndpointUrl());

        // API 키 암호화 저장
        if (request.getApiKey() != null && !request.getApiKey().isEmpty()) {
            entity.setApiKey(CredentialEncryptor.encrypt(request.getApiKey()));
        }

        entity.setDefaultModel(request.getDefaultModel());
        entity.setTemperature(request.getTemperature());
        entity.setMaxTokens(request.getMaxTokens());
        entity.setTimeoutSeconds(request.getTimeoutSeconds());
        entity.setEnabled(true);
        entity.setDefaultProfile(false);
        entity.setPriority(request.getPriority());
        entity.setLastTestResult("NEVER_TESTED");
        entity.setCustomHeaders(request.getCustomHeaders());
        entity.setDescription(request.getDescription());
        entity.setCreatedAt(System.currentTimeMillis());
        entity.setUpdatedAt(System.currentTimeMillis());
        entity.setCreatedBy(createdBy);
        entity.save();

        log.info("엔진 프로파일 생성: name={}, type={}, by={}",
                request.getProfileName(), request.getEngineType(), createdBy);
        return toInfo(entity);
    }

    @Override
    public EngineProfileInfo updateProfile(int profileId, UpdateProfileRequest request) {
        EngineProfileEntity entity = ao.get(EngineProfileEntity.class, profileId);
        if (entity == null) {
            throw new IllegalArgumentException("프로파일을 찾을 수 없습니다: ID=" + profileId);
        }

        if (request.getEndpointUrl() != null) {
            EndpointValidator.ValidationResult validation =
                    EndpointValidator.validate(request.getEndpointUrl(), true);
            if (!validation.isValid()) {
                throw new IllegalArgumentException("엔드포인트 검증 실패: " + validation.getMessage());
            }
            entity.setEndpointUrl(request.getEndpointUrl());
        }

        if (request.getProfileName() != null) entity.setProfileName(request.getProfileName());
        if (request.getEngineType() != null) entity.setEngineType(request.getEngineType());
        if (request.getApiKey() != null && !request.getApiKey().isEmpty()) {
            entity.setApiKey(CredentialEncryptor.encrypt(request.getApiKey()));
        }
        if (request.getDefaultModel() != null) entity.setDefaultModel(request.getDefaultModel());
        if (request.getTemperature() > 0) entity.setTemperature(request.getTemperature());
        if (request.getMaxTokens() > 0) entity.setMaxTokens(request.getMaxTokens());
        if (request.getTimeoutSeconds() > 0) entity.setTimeoutSeconds(request.getTimeoutSeconds());
        if (request.getEnabled() != null) entity.setEnabled(request.getEnabled());
        if (request.getCustomHeaders() != null) entity.setCustomHeaders(request.getCustomHeaders());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());

        entity.setUpdatedAt(System.currentTimeMillis());
        entity.save();

        log.info("엔진 프로파일 수정: id={}, name={}", profileId, entity.getProfileName());
        return toInfo(entity);
    }

    @Override
    public void deleteProfile(int profileId) {
        EngineProfileEntity entity = ao.get(EngineProfileEntity.class, profileId);
        if (entity == null) {
            throw new IllegalArgumentException("프로파일을 찾을 수 없습니다: ID=" + profileId);
        }
        if (entity.isDefaultProfile()) {
            throw new IllegalStateException("기본 프로파일은 삭제할 수 없습니다. 다른 프로파일을 기본으로 설정한 후 삭제하세요.");
        }
        String name = entity.getProfileName();
        ao.delete(entity);
        log.info("엔진 프로파일 삭제: id={}, name={}", profileId, name);
    }

    @Override
    public List<EngineProfileInfo> getAllProfiles() {
        EngineProfileEntity[] entities = ao.find(EngineProfileEntity.class,
                Query.select().order("PRIORITY ASC, PROFILE_NAME ASC"));
        return toInfoList(entities);
    }

    @Override
    public List<EngineProfileInfo> getEnabledProfiles() {
        EngineProfileEntity[] entities = ao.find(EngineProfileEntity.class,
                Query.select().where("ENABLED = ?", true).order("PRIORITY ASC"));
        return toInfoList(entities);
    }

    @Override
    public EngineProfileInfo getProfile(int profileId) {
        EngineProfileEntity entity = ao.get(EngineProfileEntity.class, profileId);
        if (entity == null) {
            throw new IllegalArgumentException("프로파일을 찾을 수 없습니다: ID=" + profileId);
        }
        return toInfo(entity);
    }

    @Override
    public EngineProfileInfo getDefaultProfile() {
        EngineProfileEntity[] entities = ao.find(EngineProfileEntity.class,
                Query.select().where("DEFAULT_PROFILE = ? AND ENABLED = ?", true, true));
        if (entities.length > 0) {
            return toInfo(entities[0]);
        }
        // 기본이 없으면 가장 높은 우선순위의 활성 프로파일
        EngineProfileEntity[] fallback = ao.find(EngineProfileEntity.class,
                Query.select().where("ENABLED = ?", true).order("PRIORITY ASC").limit(1));
        return fallback.length > 0 ? toInfo(fallback[0]) : null;
    }

    @Override
    public void setDefaultProfile(int profileId) {
        // 기존 기본 해제
        EngineProfileEntity[] current = ao.find(EngineProfileEntity.class,
                Query.select().where("DEFAULT_PROFILE = ?", true));
        for (EngineProfileEntity e : current) {
            e.setDefaultProfile(false);
            e.save();
        }

        // 새 기본 설정
        EngineProfileEntity entity = ao.get(EngineProfileEntity.class, profileId);
        if (entity == null) {
            throw new IllegalArgumentException("프로파일을 찾을 수 없습니다: ID=" + profileId);
        }
        entity.setDefaultProfile(true);
        entity.setUpdatedAt(System.currentTimeMillis());
        entity.save();
        log.info("기본 프로파일 변경: id={}, name={}", profileId, entity.getProfileName());
    }

    @Override
    public ConnectionTestResult testConnection(int profileId) {
        EngineProfileEntity entity = ao.get(EngineProfileEntity.class, profileId);
        if (entity == null) {
            throw new IllegalArgumentException("프로파일을 찾을 수 없습니다: ID=" + profileId);
        }

        ConnectionTestResult result = new ConnectionTestResult();
        List<TestStep> steps = new ArrayList<>();
        long totalStart = System.currentTimeMillis();
        boolean allPassed = true;

        // Step 1: DNS 해석
        long stepStart = System.currentTimeMillis();
        try {
            URL url = new URL(entity.getEndpointUrl());
            InetAddress.getByName(url.getHost());
            steps.add(new TestStep("DNS 해석", true,
                    "호스트 해석 성공: " + url.getHost(),
                    System.currentTimeMillis() - stepStart));
        } catch (Exception e) {
            steps.add(new TestStep("DNS 해석", false,
                    "DNS 해석 실패: " + e.getMessage(),
                    System.currentTimeMillis() - stepStart));
            allPassed = false;
        }

        // Step 2: TLS 검증
        stepStart = System.currentTimeMillis();
        boolean isTls = EndpointValidator.isTlsEndpoint(entity.getEndpointUrl());
        if (isTls) {
            steps.add(new TestStep("TLS 검증", true,
                    "HTTPS 연결 사용 중",
                    System.currentTimeMillis() - stepStart));
        } else {
            steps.add(new TestStep("TLS 검증", true,
                    "경고: HTTP 연결 (로컬 LLM 환경에서는 허용)",
                    System.currentTimeMillis() - stepStart));
        }

        // Step 3: 인증 확인 (헬스체크 엔드포인트 호출)
        if (allPassed) {
            stepStart = System.currentTimeMillis();
            try {
                String healthUrl = buildHealthUrl(entity);
                Request.Builder reqBuilder = new Request.Builder().url(healthUrl).get();

                // API 키 헤더 추가
                String apiKey = entity.getApiKey();
                if (apiKey != null && !apiKey.isEmpty()) {
                    String decrypted = CredentialEncryptor.decrypt(apiKey);
                    reqBuilder.addHeader("Authorization", "Bearer " + decrypted);
                }

                try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
                    if (response.isSuccessful()) {
                        steps.add(new TestStep("인증 확인", true,
                                "서버 응답: " + response.code(),
                                System.currentTimeMillis() - stepStart));
                    } else if (response.code() == 401 || response.code() == 403) {
                        steps.add(new TestStep("인증 확인", false,
                                "인증 실패: HTTP " + response.code() + " - API 키를 확인하세요",
                                System.currentTimeMillis() - stepStart));
                        allPassed = false;
                    } else {
                        steps.add(new TestStep("인증 확인", true,
                                "서버 응답: HTTP " + response.code() + " (연결은 가능)",
                                System.currentTimeMillis() - stepStart));
                    }
                }
            } catch (Exception e) {
                steps.add(new TestStep("인증 확인", false,
                        "연결 실패: " + e.getMessage(),
                        System.currentTimeMillis() - stepStart));
                allPassed = false;
            }
        }

        // Step 4: 모델 가용성 확인
        if (allPassed) {
            stepStart = System.currentTimeMillis();
            try {
                String modelsUrl = buildModelsUrl(entity);
                if (modelsUrl != null) {
                    Request.Builder reqBuilder = new Request.Builder().url(modelsUrl).get();

                    String apiKey = entity.getApiKey();
                    if (apiKey != null && !apiKey.isEmpty()) {
                        String decrypted = CredentialEncryptor.decrypt(apiKey);
                        reqBuilder.addHeader("Authorization", "Bearer " + decrypted);
                    }

                    try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
                        String body = response.body() != null ? response.body().string() : "";
                        if (response.isSuccessful() && body.contains(entity.getDefaultModel())) {
                            steps.add(new TestStep("모델 확인", true,
                                    "모델 가용: " + entity.getDefaultModel(),
                                    System.currentTimeMillis() - stepStart));
                        } else if (response.isSuccessful()) {
                            steps.add(new TestStep("모델 확인", true,
                                    "경고: 모델 목록에서 '" + entity.getDefaultModel() + "' 확인 불가 (수동 검증 권장)",
                                    System.currentTimeMillis() - stepStart));
                        } else {
                            steps.add(new TestStep("모델 확인", false,
                                    "모델 목록 조회 실패: HTTP " + response.code(),
                                    System.currentTimeMillis() - stepStart));
                        }
                    }
                } else {
                    steps.add(new TestStep("모델 확인", true,
                            "모델 목록 API 미지원 엔진 (수동 검증 필요)",
                            System.currentTimeMillis() - stepStart));
                }
            } catch (Exception e) {
                steps.add(new TestStep("모델 확인", false,
                        "모델 확인 실패: " + e.getMessage(),
                        System.currentTimeMillis() - stepStart));
            }
        }

        long totalLatency = System.currentTimeMillis() - totalStart;
        result.setSuccess(allPassed);
        result.setSteps(steps);
        result.setTotalLatencyMs(totalLatency);

        // 테스트 결과를 엔티티에 저장
        entity.setLastTestResult(allPassed ? "SUCCESS" : "FAILURE");
        entity.setLastTestTime(System.currentTimeMillis());
        entity.setLastTestLatencyMs(totalLatency);
        entity.setLastTestDetails(buildTestSummary(steps));
        entity.save();

        return result;
    }

    @Override
    public void updatePriority(int profileId, int newPriority) {
        EngineProfileEntity entity = ao.get(EngineProfileEntity.class, profileId);
        if (entity == null) {
            throw new IllegalArgumentException("프로파일을 찾을 수 없습니다: ID=" + profileId);
        }
        entity.setPriority(newPriority);
        entity.setUpdatedAt(System.currentTimeMillis());
        entity.save();
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    private String buildHealthUrl(EngineProfileEntity entity) {
        String base = entity.getEndpointUrl().replaceAll("/+$", "");
        String type = entity.getEngineType();

        if ("OLLAMA".equalsIgnoreCase(type)) {
            return base; // Ollama root returns "Ollama is running"
        } else if ("VLLM".equalsIgnoreCase(type)) {
            return base + "/health";
        } else {
            // OpenAI compatible
            return base + "/v1/models";
        }
    }

    private String buildModelsUrl(EngineProfileEntity entity) {
        String base = entity.getEndpointUrl().replaceAll("/+$", "");
        String type = entity.getEngineType();

        if ("OLLAMA".equalsIgnoreCase(type)) {
            return base + "/api/tags";
        } else if ("VLLM".equalsIgnoreCase(type) || "OPENAI_COMPATIBLE".equalsIgnoreCase(type)) {
            return base + "/v1/models";
        }
        return null;
    }

    private String buildTestSummary(List<TestStep> steps) {
        StringBuilder sb = new StringBuilder();
        for (TestStep step : steps) {
            sb.append(step.isPassed() ? "✓" : "✗").append(" ")
              .append(step.getName()).append(": ")
              .append(step.getMessage())
              .append(" (").append(step.getDurationMs()).append("ms)\n");
        }
        return sb.toString();
    }

    private EngineProfileInfo toInfo(EngineProfileEntity entity) {
        EngineProfileInfo info = new EngineProfileInfo();
        info.setId(entity.getID());
        info.setProfileName(entity.getProfileName());
        info.setEngineType(entity.getEngineType());
        info.setEndpointUrl(entity.getEndpointUrl());
        info.setHasApiKey(entity.getApiKey() != null && !entity.getApiKey().isEmpty());
        info.setDefaultModel(entity.getDefaultModel());
        info.setTemperature(entity.getTemperature());
        info.setMaxTokens(entity.getMaxTokens());
        info.setTimeoutSeconds(entity.getTimeoutSeconds());
        info.setEnabled(entity.isEnabled());
        info.setDefaultProfile(entity.isDefaultProfile());
        info.setPriority(entity.getPriority());
        info.setLastTestResult(entity.getLastTestResult());
        info.setLastTestTime(entity.getLastTestTime());
        info.setLastTestDetails(entity.getLastTestDetails());
        info.setLastTestLatencyMs(entity.getLastTestLatencyMs());
        info.setCustomHeaders(entity.getCustomHeaders());
        info.setDescription(entity.getDescription());
        info.setCreatedAt(entity.getCreatedAt());
        info.setUpdatedAt(entity.getUpdatedAt());
        info.setCreatedBy(entity.getCreatedBy());
        return info;
    }

    private List<EngineProfileInfo> toInfoList(EngineProfileEntity[] entities) {
        List<EngineProfileInfo> list = new ArrayList<>();
        for (EngineProfileEntity entity : entities) {
            list.add(toInfo(entity));
        }
        return list;
    }
}
