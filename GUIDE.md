# Jask Code Suggestion Plugin - 상세 가이드

## 목차

1. [개요](#개요)
2. [설치 가이드](#설치-가이드)
3. [초기 설정](#초기-설정)
4. [사용 방법](#사용-방법)
5. [REST API 상세](#rest-api-상세)
6. [설정 옵션](#설정-옵션)
7. [LLM 연동](#llm-연동)
8. [트러블슈팅](#트러블슈팅)
9. [개발자 가이드](#개발자-가이드)
10. [마이그레이션 노트](#마이그레이션-노트)

---

## 개요

**Jask Code Suggestion**은 AI 기반 자동 코드 리뷰 플러그인으로, Bitbucket Server에서 Pull Request 작성 시 자동으로 코드 분석을 수행하고 개선 제안을 제공합니다.

### 주요 특징

- ✅ **자동 분석**: PR 생성/업데이트 시 자동으로 AI 코드 리뷰 실행
- ✅ **다중 LLM 지원**: Ollama, vLLM, OpenAI, Azure OpenAI 등 호환
- ✅ **상세한 분석**: 보안, 성능, 버그, 스타일, 모범사례 등 7가지 카테고리
- ✅ **인라인 제안**: Diff 뷰에서 라인별 개선 제안 표시
- ✅ **머지 체크**: 중요한 이슈 미해결 시 머지 차단 (선택)
- ✅ **관리 인터페이스**: 전역 설정 페이지에서 쉬운 설정

---

## 설치 가이드

### 사전 요구사항

```
✓ Bitbucket Server 8.0 이상
✓ Java 17 이상
✓ 관리자 권한
✓ LLM API (Ollama, OpenAI, vLLM 등 중 선택)
```

### 1단계: JAR 파일 빌드

#### 옵션 A: 사전 빌드된 JAR 사용

공식 릴리스에서 `code-suggestion-addon-1.0.0.jar` 다운로드

#### 옵션 B: 소스에서 직접 빌드

```bash
# 1. 저장소 클론
git clone https://github.com/jask-ai/bitbucket-addon.git
cd bitbucket-code-suggestion-addon

# 2. Java 17 준비 (없으면 설치)
export JAVA_HOME=/path/to/jdk-17

# 3. 빌드 실행
mvn clean package -DskipTests

# 4. JAR 생성 확인
ls -l target/code-suggestion-addon-1.0.0.jar
```

### 2단계: 플러그인 업로드

1. Bitbucket Server에 **관리자 권한**으로 로그인
2. 메뉴: **관리** > **앱 관리** > **앱 업로드**
3. 생성된 JAR 파일 선택 및 업로드

```
Expected file path:
target/code-suggestion-addon-1.0.0.jar
Size: ~15-20 MB
```

### 3단계: 플러그인 활성화

1. 앱 관리 페이지에서 "Jask Code Suggestion for Bitbucket" 찾기
2. **활성화** 버튼 클릭
3. 플러그인 로그 확인: **시스템 관리** > **시스템 로그**

```
예상 로그 메시지:
[INFO] Jask 코드 제안 플러그인 로드됨
[INFO] PR 이벤트 리스너 등록 완료
```

---

## 초기 설정

### 설정 페이지 접근

1. Bitbucket 우상단 **⚙️ 관리자 메뉴** 클릭
2. **플러그인** 섹션 > **앱 관리**
3. **Jask Code Suggestion** > **AI 코드 제안 설정** 링크 클릭

### 또는 직접 접속

```
URL: http://your-bitbucket-domain/plugins/servlet/code-suggestion/admin
```

### 필수 설정

#### 1. LLM API 엔드포인트 설정

```
[LLM API 엔드포인트]
입력: http://your-llm-server:port/api/chat
예: http://localhost:11434/api/chat (Ollama)
예: https://api.openai.com/v1/chat/completions (OpenAI)
```

#### 2. 모델명 설정

```
[모델명]
Ollama: codellama:13b, codellama:34b
OpenAI: gpt-4, gpt-3.5-turbo
vLLM: 모델명 (배포된 모델명)
```

#### 3. API 키 설정 (필요시)

```
[API 인증]
OpenAI: sk-xxxx... (필수)
Ollama: 불필요
vLLM: 불필요 (로컬)
```

#### 4. 기본 파라미터

```
이름          | 기본값  | 범위     | 설명
Temperature   | 0.1     | 0.0-1.0  | 낮을수록 일관적, 높을수록 창의적
Max Tokens    | 4096    | 512-8192 | 응답 최대 길이
Timeout       | 60초    | 10-300   | API 응답 대기 시간
```

#### 5. 연결 테스트

```
[연결 테스트 버튼] 클릭
→ 성공: "✓ LLM 연결 성공!"
→ 실패: 에러 메시지 확인 후 설정 수정
```

---

## 사용 방법

### 자동 분석 (기본 동작)

#### PR 생성 시

```
1. 개발자가 새 PR 작성
2. 플러그인이 자동으로 분석 실행 (약 30초~5분)
3. 분석 완료 후 PR 탭에 "AI 코드 제안" 표시
```

#### Diff 뷰에서 제안 확인

```
1. PR > Diff 탭 열기
2. 각 라인 옆에 제안 아이콘 표시 (💡)
3. 아이콘 클릭하면 상세 설명 표시

예:
─────────────────────────────────
  50 | function processUser(data) {
  51 |   user = data;  ← 💡 [보안 경고] 입력 검증 필요
  52 |   return user;
─────────────────────────────────
```

#### 제안 상태 관리

```
상태 전환:
PENDING (대기중)
  ↓
ACCEPTED (수용)  또는  REJECTED (거부)
  ↓
RESOLVED (해결됨)
```

### 수동 분석 API

특정 PR 또는 코드에 대해 수동으로 분석을 트리거할 수 있습니다.

```bash
curl -X POST \
  http://your-bitbucket/rest/code-suggestion/1.0/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "repositoryId": 1,
    "pullRequestId": 123
  }'
```

---

## REST API 상세

### 1. 코드 분석 요청

**엔드포인트**: `POST /rest/code-suggestion/1.0/analyze`

**요청 본문**:
```json
{
  "repositoryId": 1,
  "pullRequestId": 123,
  "forceAnalyze": false
}
```

**응답**:
```json
{
  "success": true,
  "jobId": "job_xyz123",
  "message": "분석이 백그라운드에서 실행됩니다",
  "estimatedTime": 120
}
```

**HTTP 상태코드**:
- 202: 분석 등록됨 (비동기 처리)
- 400: 잘못된 요청 (매개변수 확인)
- 401: 인증 실패
- 500: 서버 오류

---

### 2. 제안 목록 조회

**엔드포인트**: `GET /rest/code-suggestion/1.0/suggestions/{repositoryId}/{pullRequestId}`

**쿼리 파라미터**:
```
category: SECURITY|PERFORMANCE|BUG_RISK|CODE_STYLE|BEST_PRACTICE|COMPLEXITY|ERROR_HANDLING
status: PENDING|ACCEPTED|REJECTED|RESOLVED
severity: CRITICAL|MAJOR|MINOR
limit: 50 (기본값)
offset: 0 (기본값)
```

**예제**:
```bash
GET /rest/code-suggestion/1.0/suggestions/1/123?category=SECURITY&severity=CRITICAL&limit=10
```

**응답**:
```json
{
  "success": true,
  "total": 5,
  "count": 5,
  "suggestions": [
    {
      "id": "sugg_001",
      "repositoryId": 1,
      "pullRequestId": 123,
      "filePath": "src/main/java/User.java",
      "lineNumber": 50,
      "category": "SECURITY",
      "severity": "CRITICAL",
      "title": "SQL 인젝션 위험",
      "description": "사용자 입력을 검증하지 않고 SQL 쿼리에 직접 사용되고 있습니다.",
      "suggestion": "PreparedStatement 사용 권장:\n  String query = \"SELECT * FROM user WHERE id = ?\";\n  PreparedStatement stmt = conn.prepareStatement(query);\n  stmt.setInt(1, userId);",
      "codeSnippet": "String query = \"SELECT * FROM user WHERE id = '\" + userId + \"'\";\nResultSet rs = stmt.executeQuery(query);",
      "confidence": 0.95,
      "status": "PENDING",
      "createdAt": "2026-02-22T10:30:00.000Z",
      "updatedAt": "2026-02-22T10:30:00.000Z"
    }
  ]
}
```

---

### 3. 파일별 제안 조회

**엔드포인트**: `GET /rest/code-suggestion/1.0/suggestions/{repositoryId}/{pullRequestId}/file`

**쿼리 파라미터**:
```
path: src/main/java/User.java (필수)
```

**예제**:
```bash
GET "/rest/code-suggestion/1.0/suggestions/1/123/file?path=src/main/java/User.java"
```

**응답**: 위와 동일한 형식

---

### 4. 제안 상태 변경

**엔드포인트**: `PUT /rest/code-suggestion/1.0/suggestions/{id}/status`

**요청 본문**:
```json
{
  "status": "ACCEPTED",
  "comment": "이 제안을 반영하겠습니다"
}
```

**상태값**:
```
ACCEPTED  - 제안 수용
REJECTED  - 제안 거부
RESOLVED  - 해결됨
```

**응답**:
```json
{
  "success": true,
  "message": "제안 상태가 ACCEPTED로 변경되었습니다"
}
```

---

### 5. 제안 삭제

**엔드포인트**: `DELETE /rest/code-suggestion/1.0/suggestions/{repositoryId}/{pullRequestId}`

**쿼리 파라미터**:
```
category: 특정 카테고리만 삭제 (선택)
status: 특정 상태만 삭제 (선택)
```

**예제**:
```bash
# 모든 제안 삭제
DELETE /rest/code-suggestion/1.0/suggestions/1/123

# PENDING 상태의 제안만 삭제
DELETE /rest/code-suggestion/1.0/suggestions/1/123?status=PENDING
```

---

### 6. 통계 조회

**엔드포인트**: `GET /rest/code-suggestion/1.0/stats/{repositoryId}/{pullRequestId}`

**응답**:
```json
{
  "success": true,
  "repositoryId": 1,
  "pullRequestId": 123,
  "totalSuggestions": 15,
  "totalLines": 250,
  "suggestionsPercentage": 6.0,
  "byCategoryAndSeverity": {
    "SECURITY": {
      "CRITICAL": 2,
      "MAJOR": 1,
      "MINOR": 0,
      "total": 3
    },
    "PERFORMANCE": {
      "CRITICAL": 0,
      "MAJOR": 2,
      "MINOR": 1,
      "total": 3
    },
    "BUG_RISK": {
      "CRITICAL": 1,
      "MAJOR": 2,
      "MINOR": 4,
      "total": 7
    },
    "CODE_STYLE": {
      "CRITICAL": 0,
      "MAJOR": 0,
      "MINOR": 2,
      "total": 2
    }
  },
  "statusSummary": {
    "PENDING": 10,
    "ACCEPTED": 3,
    "REJECTED": 1,
    "RESOLVED": 1
  }
}
```

---

### 7. 설정 조회

**엔드포인트**: `GET /rest/code-suggestion/1.0/admin/settings`

**응답**:
```json
{
  "success": true,
  "settings": {
    "apiEndpoint": "http://localhost:11434/api/chat",
    "modelName": "codellama:13b",
    "temperature": 0.1,
    "maxTokens": 4096,
    "autoAnalysisEnabled": true,
    "mergeCheckEnabled": false,
    "minimumConfidence": 0.7,
    "analysisCategories": [
      "SECURITY",
      "PERFORMANCE",
      "BUG_RISK",
      "CODE_STYLE",
      "BEST_PRACTICE",
      "COMPLEXITY",
      "ERROR_HANDLING"
    ]
  }
}
```

---

### 8. 설정 저장

**엔드포인트**: `PUT /rest/code-suggestion/1.0/admin/settings`

**요청 본문**:
```json
{
  "apiEndpoint": "http://localhost:11434/api/chat",
  "modelName": "codellama:34b",
  "temperature": 0.2,
  "maxTokens": 4096,
  "autoAnalysisEnabled": true,
  "mergeCheckEnabled": false,
  "minimumConfidence": 0.7
}
```

**응답**:
```json
{
  "success": true,
  "message": "설정이 저장되었습니다"
}
```

---

### 9. LLM 연결 테스트

**엔드포인트**: `POST /rest/code-suggestion/1.0/admin/test-connection`

**요청 본문**:
```json
{
  "apiEndpoint": "http://localhost:11434/api/chat",
  "modelName": "codellama:13b",
  "temperature": 0.1
}
```

**성공 응답**:
```json
{
  "success": true,
  "message": "LLM 연결 성공",
  "responseTime": 1234,
  "modelInfo": {
    "name": "codellama:13b",
    "version": "1.0"
  }
}
```

**실패 응답**:
```json
{
  "success": false,
  "message": "LLM 연결 실패",
  "error": "Connection timeout after 60 seconds"
}
```

---

## 설정 옵션

### 플러그인 설정 (관리 페이지)

#### LLM 설정

| 항목 | 부분 | 유형 | 필수 | 기본값 | 설명 |
|------|------|------|-----|--------|------|
| API Endpoint | LLM | String | ✓ | - | LLM API의 전체 URL (http/https) |
| Model Name | LLM | String | ✓ | - | 사용할 LLM 모델 이름 |
| API Key | LLM | String | - | - | OpenAI 등 인증이 필요한 경우만 입력 |
| Temperature | LLM | Number | ✓ | 0.1 | 0.0~1.0 (낮을수록 일관적) |
| Max Tokens | LLM | Number | ✓ | 4096 | 응답의 최대 토큰 수 |
| Timeout (sec) | LLM | Number | ✓ | 60 | API 응답 대기 시간 |

#### 분석 설정

| 항목 | 부분 | 유형 | 필수 | 기본값 | 설명 |
|------|------|------|-----|--------|------|
| Auto Analysis | Analysis | Boolean | ✓ | true | PR 이벤트 시 자동 분석 실행 |
| Merge Check | Analysis | Boolean | ✓ | false | CRITICAL 이슈 시 머지 차단 |
| Min Confidence | Analysis | Number | ✓ | 0.7 | 0.5~1.0 (신뢰도 필터) |
| Categories | Analysis | Array | ✓ | 모두 | 분석할 카테고리 선택 |

#### 필터 설정

| 항목 | 부분 | 유형 | 필수 | 기본값 | 설명 |
|------|------|------|-----|--------|------|
| Excluded Paths | Filter | Array | - | - | 분석 제외 경로 (정규식) |
| Include Paths | Filter | Array | - | - | 분석 포함 경로만 지정 |
| Min Lines | Filter | Number | - | 1 | 최소 분석 라인 수 |
| Max File Size | Filter | Number | - | 10MB | 분석할 최대 파일 크기 |

### 환경 변수

```bash
# Java 메모리 설정
export JAVA_OPTS="-Xmx2g -Xms1g"

# LLM API 타임아웃 (밀리초)
export LLM_TIMEOUT=60000

# 분석 캐시 TTL (초)
export CACHE_TTL=3600

# 로그 레벨
export LOG_LEVEL=INFO
```

---

## LLM 연동

### Ollama 연동 (완전 로컬)

#### 설치

```bash
# macOS/Linux
curl https://ollama.ai/install.sh | sh

# Windows
# https://ollama.ai/download 에서 다운로드

# 실행
ollama serve
```

#### 모델 다운로드

```bash
# CodeLlama 13B (권장)
ollama pull codellama:13b

# 또는 34B (더 정확하지만 느림)
ollama pull codellama:34b

# 또는 7B (더 빠르지만 덜 정확함)
ollama pull codellama:7b
```

#### Bitbucket 설정

```
API Endpoint: http://localhost:11434/api/chat
Model Name:   codellama:13b
Temperature:  0.1
Max Tokens:   4096
```

#### 성능 팁

```
최적화:
- GPU 메모리 충분 (8GB+)
- CPU 코어 4개 이상
- 응답시간: 30초~2분

메모리 절감:
- 작은 모델 사용: codellama:7b
- Temperature 낮춤: 0.05~0.1
- Max Tokens 줄임: 2048
```

---

### OpenAI 연동

#### API 키 획득

1. https://platform.openai.com 접속
2. API Keys 메뉴에서 새 키 생성
3. 키 안전하게 저장

#### Bitbucket 설정

```
API Endpoint: https://api.openai.com/v1/chat/completions
Model Name:   gpt-4 또는 gpt-3.5-turbo
API Key:      sk-xxxx... (필수)
Temperature:  0.1
Max Tokens:   4096
```

#### 비용 관리

```
GPT-4: $0.03/1K 입력 토큰, $0.06/1K 출력 토큰
GPT-3.5-turbo: $0.0005/1K 입력, $0.0015/1K 출력

예상 비용 (PR당):
- GPT-3.5-turbo: ~$0.001-0.005 (소형 PR)
- GPT-4: ~$0.03-0.1 (소형 PR)
```

---

### vLLM 연동 (분산 추론)

#### 설치 및 실행

```bash
# Docker로 vLLM 실행
docker run --gpus all \
  -v ~/.cache/huggingface:/root/.cache/huggingface \
  --env "HUGGING_FACE_HUB_TOKEN=$HF_TOKEN" \
  -p 8000:8000 \
  vllm/vllm-openai:latest \
  --model meta-llama/Llama-2-13b-hf
```

#### Bitbucket 설정

```
API Endpoint: http://your-vllm-server:8000/v1/chat/completions
Model Name:   meta-llama/Llama-2-13b-hf
Temperature:  0.1
Max Tokens:   4096
```

---

## 트러블슈팅

### 문제: "LLM 연결 실패"

**원인 분석**:

```bash
# 1. 엔드포인트 확인
curl http://localhost:11434/api/chat \
  -H "Content-Type: application/json" \
  -d '{"model": "codellama:13b"}'

# 2. 포트 확인
netstat -tuln | grep 11434

# 3. 방화벽 확인
sudo iptables -L | grep 11434

# 4. 프로세스 확인
ps aux | grep ollama
```

**해결책**:

```bash
# Ollama 재시작
ollama serve

# 또는 모델 재로드
ollama pull codellama:13b --force

# 메모리 부족 시 더 작은 모델로
ollama pull codellama:7b
```

---

### 문제: "분석 시간이 너무 오래 걸림"

**원인**:
- GPU 메모리 부족
- 모델이 너무 큼
- 네트워크 지연
- 타임아웃 설정 부족

**해결책**:

```
1. 작은 모델 사용
   codellama:13b → codellama:7b

2. Temperature 낮춤
   0.7 → 0.1

3. Max Tokens 줄임
   4096 → 2048

4. 타임아웃 증가
   60 → 120초

5. 병렬 처리 수 늘림
   설정 > Advanced > Worker Threads
```

---

### 문제: "메모리 부족 (OutOfMemory)"

**원인**:
- 큰 파일 분석
- 동시 분석 작업 많음
- JVM 힙 메모리 부족

**해결책**:

```bash
# Bitbucket 메모리 증가
vi /path/to/bitbucket/bin/setenv.sh

# 이 줄 수정:
export JVM_MINIMUM_MEMORY=1024m
export JVM_MAXIMUM_MEMORY=4096m

# 적용
systemctl restart bitbucket
```

---

### 문제: "특정 파일은 분석이 안 됨"

**확인 사항**:

```
1. 파일 크기 확인 (설정값 초과?)
2. 제외 경로 확인
3. 파일 인코딩 확인 (UTF-8?)
4. 권한 확인
```

**해결책**:

```
관리 페이지 > 필터 설정:
- Excluded Paths: 제외할 경로 제거
- Max File Size: 높임 (e.g., 50MB)
- Include Paths: 포함할 경로만 지정
```

---

### 문제: "PR 이벤트가 감지 안 됨"

**확인**:

```bash
# 플러그인 활성화 확인
curl -u admin:password \
  http://your-bitbucket/rest/plugins/1.0/plugins \
  | grep "code-suggestion"

# 로그 확인
tail -f /path/to/bitbucket/logs/atlassian-bitbucket.log
```

**해결책**:

```
1. 플러그인 재활성화
   앱 관리 > 비활성화 > 활성화

2. Bitbucket 재시작
   systemctl restart bitbucket

3. 이벤트 리스너 재등록
   관리 > 시스템 > 캐시 > 플러그인 모듈 > 재로드
```

---

### 문제: "API 응답이 이상함"

**수정 가능한 문제**:

```
1. 컨텐츠 타입 확인
   Header: Content-Type: application/json

2. 인증 확인
   URL에 인증 정보 포함 또는 헤더 추가

3. 요청 형식 확인
   JSON 유효성 검사 (jsonlint.com)

4. 타임아웃 확인
   응답 시간 > 60초 → 타임아웃 증가
```

---

## 개발자 가이드

### 프로젝트 구조

```
bitbucket-code-suggestion-addon/
├── pom.xml                          # Maven 설정
├── src/
│   ├── main/
│   │   ├── java/com/jask/bitbucket/
│   │   │   ├── ao/                  # Active Objects (DB 엔티티)
│   │   │   │   └── SuggestionEntity.java
│   │   │   ├── config/              # 플러그인 설정
│   │   │   │   ├── PluginSettingsService.java
│   │   │   │   └── PluginSettingsServiceImpl.java
│   │   │   ├── hook/                # 이벤트 리스너
│   │   │   │   ├── PullRequestEventListener.java
│   │   │   │   └── CodeSuggestionMergeCheck.java
│   │   │   ├── model/               # 데이터 모델
│   │   │   │   ├── AnalysisRequest.java
│   │   │   │   ├── AnalysisResponse.java
│   │   │   │   └── CodeSuggestion.java
│   │   │   ├── rest/                # REST API 엔드포인트
│   │   │   │   ├── CodeSuggestionResource.java
│   │   │   │   └── AdminConfigResource.java
│   │   │   ├── service/             # 비즈니스 로직
│   │   │   │   ├── CodeAnalysisService.java
│   │   │   │   ├── CodeAnalysisServiceImpl.java
│   │   │   │   ├── LlmClientService.java
│   │   │   │   └── LlmClientServiceImpl.java
│   │   │   └── servlet/             # 관리자 서블릿
│   │   │       └── AdminConfigServlet.java
│   │   └── resources/
│   │       ├── atlassian-plugin.xml # 플러그인 디스크립터
│   │       ├── css/                 # 스타일시트
│   │       ├── js/                  # JavaScript
│   │       └── templates/           # Soy 템플릿
│   └── test/
│       └── java/                    # 단위 테스트
└── target/                          # 빌드 산출물
```

### 로컬 개발 환경 설정

#### 1. 개발 도구 설치

```bash
# JDK 17
https://adoptium.net/

# Maven 3.9+
brew install maven

# Bitbucket Server SDK (Atlassian Plugin SDK)
atlas-version  # 확인

# IDE (IntelliJ IDEA 권장)
```

#### 2. 플러그인 개발 모드 실행

```bash
# 로컬 Bitbucket에서 실행 (자동으로 에뮬레이터 시작)
mvn bitbucket:run

# Bitbucket은 http://localhost:7990 에서 시작
# 기본 로그인: admin / admin
```

#### 3. 핫 리로드 개발

```bash
# 다른 터미널에서 감시 모드 실행
mvn package -o -T 1C

# 또는 IDE의 자동 컴파일 + 핫 스왑 사용
# IntelliJ: Run > Edit Configurations > HotSwap
```

### 주요 클래스 설명

#### CodeAnalysisServiceImpl

```java
/**
 * PR의 변경사항을 분석하고 제안을 생성
 * 흐름:
 * 1. PR의 diff 파일 목록 가져오기
 * 2. 각 파일의 변경 라인 추출
 * 3. LLM에 코드 분석 요청
 * 4. 응답 파싱 및 제안 생성
 * 5. DB에 저장
 */
public class CodeAnalysisServiceImpl {
  List<CodeSuggestion> analyze(AnalysisRequest request) { ... }
}
```

#### LlmClientServiceImpl

```java
/**
 * LLM API와 통신하는 HTTP 클라이언트
 * 지원:
 * - Ollama API (/api/chat)
 * - OpenAI API (/v1/chat/completions)
 * - OpenAI 호환 API (vLLM 등)
 */
public class LlmClientServiceImpl {
  String callLlmApi(LlmRequest request) { ... }
}
```

#### CodeSuggestionResource

```java
/**
 * REST API 엔드포인트
 * 경로: /rest/code-suggestion/1.0/*
 */
@Path("/code-suggestion")
@Consumes("application/json")
@Produces("application/json")
public class CodeSuggestionResource { ... }
```

### 코드 추가/수정 가이드

#### 새로운 분석 카테고리 추가

1. **모델에 카테고리 추가**:

```java
// CodeSuggestion.java
public enum Category {
  SECURITY,
  PERFORMANCE,
  NEW_CATEGORY  // 추가
}
```

2. **프롬프트 수정**:

```java
// CodeAnalysisServiceImpl.java
private String buildAnalysisPrompt(String code) {
  return """
    다음 카테고리에 대해 코드를 분석하세요:
    - ...기존 항목...
    - NEW_CATEGORY: [설명]
    """;
}
```

3. **API 응답 수정** (필요시):

```java
// CodeSuggestionResource.java
@GET
@Path("/suggestions/{repoId}/{prId}")
public Response getSuggestions(...) {
  // 새 카테고리 필터링 추가
}
```

#### REST API 엔드포인트 추가

```java
@Path("/my-endpoint")
@POST
@Consumes("application/json")
@Produces("application/json")
public Response myEndpoint(MyRequest request) {
  try {
    // 비즈니스 로직
    return Response.ok(new MyResponse()).build();
  } catch (Exception e) {
    return Response
      .status(Response.Status.INTERNAL_SERVER_ERROR)
      .entity(new ErrorResponse(e.getMessage()))
      .build();
  }
}
```

### 테스트 작성

```java
// CodeAnalysisServiceTest.java
@Test
public void testAnalyzeExtractsCorrectSuggestions() {
  // Arrange
  String code = "SELECT * FROM user WHERE id = '" + userId + "'";
  AnalysisRequest request = new AnalysisRequest();
  request.setCode(code);
  
  // Act
  List<CodeSuggestion> suggestions = service.analyze(request);
  
  // Assert
  assertNotNull(suggestions);
  assertEquals(1, suggestions.size());
  assertEquals("SECURITY", suggestions.get(0).getCategory());
}
```

---

## 마이그레이션 노트

### v1.0 현황 (2026년 2월)

#### 완료된 기능

✅ Bitbucket Server 8.x 호환성
✅ Java 17 지원
✅ 다중 LLM API 지원 (Ollama, OpenAI, vLLM)
✅ 7가지 분석 카테고리
✅ REST API (v1.0)
✅ 관리자 설정 인터페이스
✅ PR 이벤트 기반 자동 분석

#### 알려진 제한사항

⚠️ PR 이벤트 리스너 비활성화
   - Bitbucket 8.x API 호환성 문제
   - 수동 API 호출로 분석 가능

⚠️ 머지 체크 기능 비활성화
   - Hook API 변경으로 인함
   - 향후 버전에서 복구 예정

⚠️ 일부 인라인 제안 미지원
   - Diff 콜백 API 변경
   - UI에서 수동 확인 가능

### 향후 계획 (v1.1+)

```
Q2 2026:
  [ ] PR 이벤트 리스너 복구
  [ ] 머지 체크 재구현
  [ ] Bitbucket Cloud 대응

Q3 2026:
  [ ] 본격적인 인라인 제안
  [ ] 검토자 제안
  [ ] 커밋 메시지 분석

Q4 2026:
  [ ] 성능 최적화 (병렬 분석)
  [ ] 캐싱 개선
  [ ] 더 많은 LLM 지원
```

---

## FAQ

### Q: 분석 결과를 내보낼 수 있나요?

A: REST API를 통해 JSON으로 조회 가능하며, 이를 CSV로 변환할 수 있습니다.

```bash
curl http://your-bitbucket/rest/code-suggestion/1.0/suggestions/1/123 \
  | jq '.suggestions[] | {file: .filePath, line: .lineNumber, category: .category, severity: .severity}' \
  > suggestions.jsonl
```

---

### Q: 특정 파일은 분석하지 않도록 할 수 있나요?

A: 관리 페이지의 "필터 설정"에서 제외 경로를 정규식으로 지정할 수 있습니다.

```
예: ^(src/test|node_modules|\.git)/.*
```

---

### Q: 여러 저장소에서 같은 설정을 사용할 수 있나요?

A: 현재 플러그인은 글로벌 설정만 지원합니다. 저장소별 설정은 향후 버전에서 지원 예정입니다.

---

### Q: 오프라인에서 사용 가능한가요?

A: Ollama를 로컬에서 실행하면 완전히 오프라인으로 사용 가능합니다. OpenAI는 인터넷 필요.

---

### Q: 분석 기록은 얼마나 보관되나요?

A: 기본값 30일이며, 관리 페이지에서 수정 가능합니다.

관리자 > 설정 > 저장소 설정 > "분석 기록 보관 기간"

---

## 지원 및 문의

- 📧 이메일: support@jask.ai
- 🐛 버그 리포트: https://github.com/jask-ai/issues
- 💬 커뮤니티: https://community.jask.ai
- 📚 문서: https://docs.jask.ai

---

**문서 최종 업데이트**: 2026년 2월 22일
**플러그인 버전**: 1.0.0
**호환 Bitbucket**: Server 8.0+
