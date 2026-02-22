# Jask Code Suggestion - Bitbucket Server/Data Center Plugin

AI 기반 코드 제안 플러그인으로, Pull Request에서 자동으로 코드 리뷰 및 개선 제안을 제공합니다.
Bitbucket Server 및 Data Center 환경을 모두 지원합니다.

## 주요 기능

### 코드 분석
- **자동 코드 분석**: PR 생성/업데이트 시 자동으로 AI 코드 리뷰 실행
- **인라인 코드 제안**: Diff 뷰에서 라인별 개선 제안 표시
- **머지 체크**: 심각한 이슈 미해결 시 머지 차단
- **카테고리별 분석**: 보안, 성능, 버그 위험, 코드 스타일, 모범 사례, 복잡도, 에러 처리
- **분석 버전 관리**: PR 분석 이력 추적 및 버전 간 비교
- **PR 댓글 삽입**: 제안을 PR 댓글로 직접 삽입

### 다중 LLM 엔진 지원
- **Ollama**: 로컬 LLM 서버 지원
- **vLLM**: GPU 가속 추론 서버 지원
- **OpenAI Compatible**: OpenAI API 호환 엔드포인트 지원
- **다중 프로파일**: 여러 엔진을 등록하고 우선순위/폴백 관리
- **연결 테스트**: DNS → TLS → 인증 → 모델 가용성 단계별 검증

### 관리자 기능
- **좌측 네비게이션 SPA**: 7개 섹션 + 백업/역할 관리 (IA 구조)
- **역할 기반 접근 제어 (RBAC)**: SYS_ADMIN, PROJECT_ADMIN, AUDIT_VIEWER, READ_ONLY
- **정책 템플릿**: Standard, Strict, Light, Security-focused 내장 템플릿
- **마스킹 규칙 편집**: 8개 내장 규칙 + 커스텀 정규식 패턴 + 실시간 테스트
- **사용량/비용 관리**: 일/주/월 단위 호출 및 토큰 한도
- **모니터링 대시보드**: LLM 호출/성공률/캐시 히트율/P95 응답 시간
- **감사 로그**: 모든 설정 변경/분석 실행/보안 이벤트 기록 + CSV 내보내기
- **운영 알림**: 한도 초과, 연결 실패, 에러율 상승, 서킷 브레이커 등
- **변경 승인 워크플로우**: PENDING → APPROVED → APPLIED
- **Dry-run (안전 적용)**: 변경 영향 분석 후 적용
- **백업/복원**: JSON 스냅샷 기반 설정 백업 및 복원
- **시스템 진단**: JVM 메모리, AO DB, 엔진 상태, 작업 큐, 설정 무결성

### 보안
- **AES-256-GCM 자격증명 암호화**: API 키 등 민감 정보 암호화 저장
- **SSRF 방지**: 내부 네트워크/메타데이터 서비스 차단
- **PII 감지/마스킹**: 이메일, 전화번호, 주민등록번호, 신용카드 등 자동 마스킹
- **시크릿 마스킹**: API 키, 비밀번호, JWT, 개인 키 자동 감지
- **CSRF 보호**: ATL-Token/X-Atlassian-Token 검증
- **보안 헤더**: X-Content-Type-Options, X-Frame-Options, CSP 등
- **데이터 최소화**: LLM에 전송 전 코드 내 민감 정보 제거

### DC 클러스터 지원
- **비동기 작업 큐**: 202 Accepted + 적응형 지수 백오프 폴링
- **클러스터 잡 관리**: 공유 DB 기반 노드 간 작업 조율
- **스톨 잡 복구**: 10분 타임아웃 후 자동 재큐 (최대 3회 재시도)
- **서킷 브레이커**: CLOSED → OPEN → HALF_OPEN 상태 관리
- **인메모리 LRU 캐시**: SHA-256 키 기반, TTL 만료

## 분석 카테고리

| 카테고리 | 설명 |
|----------|------|
| SECURITY | SQL 인젝션, XSS, 하드코딩된 비밀번호 등 |
| PERFORMANCE | N+1 쿼리, 메모리 누수, 비효율적 알고리즘 |
| BUG_RISK | NPE 가능성, 경쟁 조건, 리소스 누수 |
| CODE_STYLE | 네이밍 컨벤션, 포맷팅, 일관성 |
| BEST_PRACTICE | SOLID 원칙, 디자인 패턴 |
| COMPLEXITY | 순환 복잡도, 중첩 깊이 |
| ERROR_HANDLING | 누락된 예외 처리, 에러 무시 |

## 빌드 및 설치

### 사전 요구사항

- JDK 11+
- Maven 3.6+
- Atlassian Plugin SDK

### 빌드

```bash
cd bitbucket-code-suggestion-addon

# 플러그인 빌드
mvn clean package

# 로컬 Bitbucket에서 테스트
mvn bitbucket:run

# 테스트 실행
mvn test
```

### 설치

1. `target/code-suggestion-addon-1.0.0.jar` 파일을 생성합니다
2. Bitbucket Server 관리자 > Manage apps > Upload app에서 JAR를 업로드합니다
3. 플러그인 활성화 후 "AI 코드 제안 설정" 메뉴에서 LLM을 설정합니다

## 관리자 화면 구조 (IA)

관리자 페이지는 좌측 네비게이션 + 우측 상세 패널 구조입니다.

| 섹션 | 설명 | 필요 권한 |
|------|------|-----------|
| 전역 설정 | LLM 기본 설정, 분석 옵션, 파일 필터, 정책 템플릿 | SYS_ADMIN |
| 엔진 연결 | 다중 엔진 프로파일 CRUD, 연결 테스트, 기본 설정 | SYS_ADMIN |
| 보안·마스킹 | 마스킹 규칙 관리, 정규식 패턴 테스트, PII 정책 | SYS_ADMIN |
| 프로젝트 정책 | 프로젝트별 분석 설정 재정의, 템플릿 적용 | PROJECT_ADMIN |
| 사용량·비용 | 한도 설정, 사용량 대시보드, 메트릭, 알림 조회 | READ_ONLY+ |
| 로그·감사 | 감사 로그 조회/필터/CSV 내보내기, 변경 이력 | AUDIT_VIEWER+ |
| 진단 | JVM, DB, 엔진, 큐, 설정 무결성 진단 | READ_ONLY+ |
| 백업·복원 | JSON 스냅샷 생성/복원/삭제, 자동 백업 | SYS_ADMIN |
| 역할 관리 | 사용자별 관리 역할 할당/제거 | SYS_ADMIN |

### RBAC 역할

| 역할 | 접근 가능 섹션 |
|------|---------------|
| SYS_ADMIN | 모든 섹션 (Bitbucket 시스템 관리자 자동 매핑) |
| PROJECT_ADMIN | 프로젝트 정책, 감사 로그 |
| AUDIT_VIEWER | 감사 로그 |
| READ_ONLY | 사용량·비용, 진단 |

## REST API

### 코드 분석 API

| Method | Path | 설명 |
|--------|------|------|
| POST | `/rest/code-suggestion/1.0/analyze` | 코드 분석 실행 (비동기, 202 반환) |
| GET | `/rest/code-suggestion/1.0/suggestions/{repoId}/{prId}` | 제안 목록 조회 |
| GET | `/rest/code-suggestion/1.0/suggestions/{repoId}/{prId}/file?path=...` | 파일별 제안 조회 |
| PUT | `/rest/code-suggestion/1.0/suggestions/{id}/status` | 제안 상태 변경 |
| DELETE | `/rest/code-suggestion/1.0/suggestions/{repoId}/{prId}` | 제안 삭제 |
| GET | `/rest/code-suggestion/1.0/stats/{repoId}/{prId}` | 통계 조회 |
| GET | `/rest/code-suggestion/1.0/versions/{repoId}/{prId}` | 분석 버전 이력 |
| GET | `/rest/code-suggestion/1.0/versions/{repoId}/{prId}/compare` | 버전 비교 |
| POST | `/rest/code-suggestion/1.0/suggestions/{id}/comment` | PR 댓글 삽입 |

### 관리자 API

| Method | Path | 설명 |
|--------|------|------|
| GET | `/admin/navigation` | 접근 가능 섹션 목록 (RBAC) |
| GET/PUT | `/admin/settings` | 전역 설정 조회/저장 |
| GET/POST | `/admin/engines` | 엔진 프로파일 목록/생성 |
| PUT/DELETE | `/admin/engines/{id}` | 엔진 수정/삭제 |
| POST | `/admin/engines/{id}/test` | 엔진 연결 테스트 |
| PUT | `/admin/engines/{id}/default` | 기본 엔진 설정 |
| GET/POST | `/admin/masking-rules` | 마스킹 규칙 목록/추가 |
| PUT/DELETE | `/admin/masking-rules/{id}` | 마스킹 규칙 수정/삭제 |
| POST | `/admin/masking-rules/test` | 패턴 테스트 |
| GET | `/admin/policy-templates` | 정책 템플릿 목록 |
| GET/POST | `/admin/quotas` | 한도 설정 목록/생성 |
| PUT/DELETE | `/admin/quotas/{id}` | 한도 수정/삭제 |
| GET | `/admin/usage-summary` | 사용량 요약 |
| GET | `/admin/usage-stats` | 일별 사용량 통계 |
| GET | `/admin/metrics` | 플러그인 메트릭 |
| DELETE | `/admin/metrics` | 메트릭 초기화 |
| GET | `/admin/audit-log` | 감사 로그 (필터, 페이지네이션) |
| GET | `/admin/alerts` | 운영 알림 목록 |
| POST | `/admin/alerts/{id}/acknowledge` | 알림 확인 |
| GET | `/admin/changes/pending` | 대기 중 변경 요청 |
| POST | `/admin/changes/{id}/approve` | 변경 승인 |
| POST | `/admin/changes/{id}/reject` | 변경 거부 |
| POST | `/admin/dry-run` | Dry-run 영향 분석 |
| GET/POST | `/admin/backups` | 스냅샷 목록/생성 |
| POST | `/admin/backups/{id}/restore` | 스냅샷 복원 |
| DELETE | `/admin/backups/{id}` | 스냅샷 삭제 |
| GET | `/admin/diagnostics` | 시스템 진단 보고서 |
| GET | `/admin/diagnostics/tables` | AO 테이블 통계 |
| GET | `/admin/roles` | 역할 할당 목록 |
| PUT | `/admin/roles/{username}` | 역할 할당 |
| DELETE | `/admin/roles/{username}` | 역할 제거 |
| POST | `/admin/test-connection` | LLM 연결 테스트 (레거시) |
| GET | `/admin/cluster-stats` | 클러스터 잡 통계 |
| GET | `/health` | 헬스체크 |
| GET | `/info` | 플러그인 정보 |

> 모든 관리자 API 경로 앞에 `/rest/code-suggestion/1.0` 기본 경로가 붙습니다.

## 프로젝트 구조

```
bitbucket-code-suggestion-addon/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/jask/bitbucket/
│   │   │   ├── ao/                        # Active Objects (10개 DB 엔티티)
│   │   │   │   ├── SuggestionEntity        # 코드 제안
│   │   │   │   ├── AnalysisJobEntity       # 비동기 분석 잡
│   │   │   │   ├── AnalysisVersionEntity   # 분석 버전 이력
│   │   │   │   ├── AuditLogEntity          # 감사 로그
│   │   │   │   ├── EngineProfileEntity     # 엔진 프로파일
│   │   │   │   ├── UsageQuotaEntity        # 사용량 한도
│   │   │   │   ├── UsageRecordEntity       # 사용량 기록
│   │   │   │   ├── SettingsSnapshotEntity  # 설정 스냅샷
│   │   │   │   ├── PendingChangeEntity     # 변경 승인 대기
│   │   │   │   └── PluginSettingsEntity    # 플러그인 설정
│   │   │   ├── config/                     # 플러그인/프로젝트 설정 서비스
│   │   │   ├── hook/                       # PR 이벤트 리스너, 머지 체크
│   │   │   ├── model/                      # 데이터 모델 (Request/Response/Suggestion)
│   │   │   ├── rest/                       # REST API 엔드포인트
│   │   │   │   ├── AdminConfigResource     # 관리자 API (40+ 엔드포인트)
│   │   │   │   ├── CodeSuggestionResource  # 코드 분석 API
│   │   │   │   ├── ProjectConfigResource   # 프로젝트 설정 API
│   │   │   │   └── HealthCheckResource     # 헬스체크/정보 API
│   │   │   ├── security/                   # 보안 모듈
│   │   │   │   ├── PermissionCheckService  # RBAC 인증/인가
│   │   │   │   ├── CredentialEncryptor     # AES-256-GCM 암호화
│   │   │   │   ├── EndpointValidator       # SSRF 방지 검증
│   │   │   │   ├── SecretMasker            # 시크릿 마스킹
│   │   │   │   ├── DataPrivacyService      # PII 감지/마스킹
│   │   │   │   └── ApiSecurityFilter       # CSRF/보안헤더/입력검증
│   │   │   ├── service/                    # 핵심 비즈니스 로직
│   │   │   │   ├── CodeAnalysisService     # 코드 분석 오케스트레이션
│   │   │   │   ├── LlmClientService        # LLM API 클라이언트
│   │   │   │   ├── EngineProfileService    # 엔진 프로파일 관리
│   │   │   │   ├── AnalysisJobService      # 비동기 잡 관리
│   │   │   │   ├── AnalysisVersionService  # 분석 버전 관리
│   │   │   │   ├── AnalysisCacheService    # LRU 캐시
│   │   │   │   ├── ClusterJobManager       # DC 클러스터 잡 조율
│   │   │   │   ├── SuggestionService       # 제안 CRUD
│   │   │   │   ├── AuditLogService         # 감사 로그
│   │   │   │   ├── MetricsService          # 메트릭 수집 (P95)
│   │   │   │   ├── UsageQuotaService       # 사용량 한도 관리
│   │   │   │   ├── AlertService            # 운영 알림
│   │   │   │   ├── PolicyTemplateService   # 정책 템플릿
│   │   │   │   ├── MaskingRuleService      # 마스킹 규칙 편집
│   │   │   │   ├── SettingsBackupService   # 설정 백업/복원
│   │   │   │   ├── ChangeApprovalService   # 변경 승인 워크플로우
│   │   │   │   ├── DryRunService           # 안전 적용 분석
│   │   │   │   ├── DiagnosticsService      # 시스템 진단
│   │   │   │   ├── BitbucketCommentService # PR 댓글 삽입
│   │   │   │   └── llm/                    # LLM 엔진 어댑터
│   │   │   │       ├── LlmEngineAdapter    # 어댑터 인터페이스
│   │   │   │       ├── OllamaAdapter       # Ollama 어댑터
│   │   │   │       ├── OpenAiCompatibleAdapter # OpenAI 어댑터
│   │   │   │       ├── CircuitBreaker      # 서킷 브레이커
│   │   │   │       └── RetryPolicy         # 지수 백오프 재시도
│   │   │   └── servlet/                    # 관리자 설정 서블릿
│   │   └── resources/
│   │       ├── atlassian-plugin.xml        # 플러그인 디스크립터
│   │       ├── css/
│   │       │   ├── code-suggestion.css     # PR 뷰 스타일
│   │       │   └── admin-config.css        # 관리자 페이지 스타일
│   │       ├── js/
│   │       │   ├── code-suggestion.js      # PR 뷰 스크립트
│   │       │   └── admin-config.js         # 관리자 SPA 스크립트
│   │       └── templates/
│   │           ├── pr-suggestion-panel.soy # PR 제안 패널
│   │           └── admin-config.soy        # 관리자 페이지 (좌측 네비)
│   └── test/
│       └── java/com/jask/bitbucket/        # 단위 테스트
│           ├── rest/                       # REST API 테스트
│           ├── security/                   # 보안 모듈 테스트
│           └── service/                    # 서비스 테스트
```

## 설정

### LLM 기본 설정

| 항목 | 기본값 | 설명 |
|------|--------|------|
| API 엔드포인트 | `http://localhost:11434/api/chat` | LLM API URL |
| 모델명 | `codellama:13b` | 사용할 모델 |
| Temperature | `0.1` | 낮을수록 일관성 있는 결과 |
| 최대 토큰 | `4096` | 응답 최대 길이 |

### 분석 설정

| 항목 | 기본값 | 설명 |
|------|--------|------|
| 자동 분석 | `true` | PR 이벤트 시 자동 실행 |
| 머지 체크 | `false` | CRITICAL 이슈 시 머지 차단 |
| 최소 확신도 | `0.7` | 이 값 미만 제안 필터링 |
| 최대 파일 수 | `20` | 분석당 최대 파일 수 |
| 최대 파일 크기 | `500KB` | 단일 파일 크기 제한 |

### 내장 정책 템플릿

| 템플릿 | 자동 분석 | 머지 체크 | 최소 확신도 | 용도 |
|--------|-----------|-----------|-------------|------|
| Standard | ✓ | ✗ | 0.6 | 일반 프로젝트 |
| Strict | ✓ | ✓ (0건) | 0.8 | 높은 품질 기준 |
| Light | ✗ | ✗ | 0.7 | 리소스 최소화 |
| Security | ✓ | ✓ (0건) | 0.85 | 보안 중심 |

### 내장 마스킹 규칙

| 규칙 | 카테고리 | 대상 |
|------|----------|------|
| 이메일 주소 | PII | `user@example.com` → `[EMAIL_MASKED]` |
| 한국 전화번호 | PII | `010-1234-5678` → `[PHONE_MASKED]` |
| API 키 | SECRET | `api_key=abc123...` → `[API_KEY_MASKED]` |
| 비밀번호 | SECRET | `password=...` → `[PASSWORD_MASKED]` |
| JWT 토큰 | SECRET | `eyJ...` → `[JWT_MASKED]` |
| 개인 키 | SECRET | `-----BEGIN PRIVATE KEY-----` → `[PRIVATE_KEY_MASKED]` |
| IP 주소 | PII | `192.168.1.1` → `[IP_MASKED]` |
| 주민등록번호 | PII | `900101-1234567` → `[SSN_MASKED]` |

## Active Objects 테이블

| 테이블명 | 엔티티 | 용도 |
|----------|--------|------|
| JASK_SUGGESTION | SuggestionEntity | 코드 제안 저장 |
| JASK_ANALYSIS_JOB | AnalysisJobEntity | 비동기 분석 잡 |
| JASK_ANALYSIS_VER | AnalysisVersionEntity | 분석 버전 이력 |
| JASK_AUDIT_LOG | AuditLogEntity | 감사 로그 |
| JASK_ENGINE_PROF | EngineProfileEntity | 엔진 프로파일 |
| JASK_USAGE_QUOTA | UsageQuotaEntity | 사용량 한도 설정 |
| JASK_USAGE_REC | UsageRecordEntity | 사용량 기록 |
| JASK_SETTINGS_SNAP | SettingsSnapshotEntity | 설정 스냅샷 |
| JASK_PENDING_CHG | PendingChangeEntity | 변경 승인 대기 |
| JASK_PLUGIN_SET | PluginSettingsEntity | 플러그인 설정 |

## 지원 언어

Java, JavaScript, TypeScript, Python, Go, Kotlin, Scala, Ruby, PHP, C#, C++, C, Rust, Swift

## 기술 스택

| 기술 | 용도 |
|------|------|
| Atlassian Plugin SDK (AMPS) | 플러그인 프레임워크 |
| Active Objects | DC 클러스터 호환 ORM |
| SAL (Shared Application Layer) | 플러그인 설정/사용자 관리 |
| Spring Scanner | DI 컨테이너 |
| JAX-RS (Jersey) | REST API |
| OkHttp | LLM HTTP 클라이언트 |
| Gson | JSON 직렬화 |
| Soy Templates | 서버 사이드 렌더링 |
| AUI (Atlassian UI) | 프론트엔드 UI 프레임워크 |
| jQuery | DOM 조작 |
| UPM License API | 마켓플레이스 라이선스 |

## 라이선스

Proprietary - Jask
