# Codenose API 테스트 가이드

## 개요
GitHub 파일을 저장하고 AI로 코드 분석을 수행하는 API 테스트 가이드입니다.

---

## 1단계: GitHub 파일 저장

### 엔드포인트
```
POST /api/github/save-file
```

### 설명
GitHub 레포지토리의 파일 내용을 가져와서 DB에 저장합니다.

### 요청 헤더
```
Content-Type: application/json
```

### 요청 바디
```json
{
  "repositoryUrl": "https://github.com/octocat/Hello-World",
  "owner": "octocat",
  "repo": "Hello-World",
  "filePath": "README",
  "userId": 1
}
```

### 요청 파라미터 설명
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| repositoryUrl | String | O | GitHub 레포지토리 전체 URL |
| owner | String | O | 레포지토리 소유자 (username 또는 organization) |
| repo | String | O | 레포지토리 이름 |
| filePath | String | O | 저장할 파일 경로 (예: src/main/java/Example.java) |
| userId | Long | O | 사용자 ID |

### 응답 성공 (200 OK)
```json
{
  "code": "0000",
  "message": "success",
  "Data": {
    "analysisId": "550e8400-e29b-41d4-a716-446655440000",
    "message": "파일 내용이 성공적으로 저장되었습니다."
  }
}
```

### Postman 테스트 예시
```
Method: POST
URL: http://localhost:9443/api/github/save-file

Headers:
  Content-Type: application/json

Body (raw - JSON):
{
  "repositoryUrl": "https://github.com/octocat/Hello-World",
  "owner": "octocat",
  "repo": "Hello-World",
  "filePath": "README",
  "userId": 1
}
```

### cURL 예시
```bash
curl -X POST "https://localhost:9443/api/github/save-file" \
  -H "Content-Type: application/json" \
  -d '{
    "repositoryUrl": "https://github.com/octocat/Hello-World",
    "owner": "octocat",
    "repo": "Hello-World",
    "filePath": "README",
    "userId": 1
  }'
```

---

## 2단계: AI 코드 분석 (저장된 파일)

### 엔드포인트
```
POST /api/analysis/analyze-stored
```

### 설명
1단계에서 저장한 파일을 DB에서 조회하여 AI로 코드 분석을 수행합니다.
사용자가 선택한 피드백 강도(toneLevel)에 따라 다른 톤의 분석 결과를 받습니다.

### 요청 헤더
```
Content-Type: application/json
```

### 요청 바디
```json
{
  "repositoryUrl": "https://github.com/octocat/Hello-World",
  "filePath": "README",
  "code": "public class Example {\n    public static void main(String[] args) {\n        int x = 10;\n        System.out.println(x);\n    }\n}",
  "analysisTypes": ["code_smell", "design_pattern", "best_practices"],
  "toneLevel": 3,
  "customRequirements": "변수명 컨벤션과 매직 넘버를 중점적으로 검토해주세요",
  "userId": 1
}
```

### 요청 파라미터 설명
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| repositoryUrl | String | O | GitHub 레포지토리 URL (1단계에서 사용한 것과 동일) |
| filePath | String | O | 파일 경로 (1단계에서 사용한 것과 동일) |
| code | String | O | 분석할 실제 코드 내용 |
| analysisTypes | String[] | O | 분석 타입 (code_smell, design_pattern, best_practices 등) |
| toneLevel | Integer | O | 피드백 강도 (1~5) |
| customRequirements | String | X | 사용자 커스텀 요구사항 |
| userId | Long | O | 사용자 ID |

### toneLevel 설명
| 레벨 | 설명 |
|------|------|
| 1 | 매우 부드럽고 격려하는 톤 (유머 많음) |
| 2 | 친근하고 도움되는 톤 (가벼운 농담) |
| 3 | 중립적이고 전문적인 톤 (위트 있음) - **권장** |
| 4 | 엄격하고 직설적인 톤 (풍자적 유머) |
| 5 | 매우 엄격하고 까다로운 톤 (심술궂은 고양이 같은) |

### 응답 성공 (200 OK)
```json
{
  "code": "0000",
  "message": "success",
  "Data": "{\"aiScore\":85,\"codeSmells\":[{\"name\":\"Magic Number\",\"description\":\"변수 x에 10이라는 매직 넘버가 사용되었습니다.\"},{\"name\":\"Poor Variable Naming\",\"description\":\"변수명 'x'가 너무 일반적입니다.\"}],\"suggestions\":[{\"problematicSnippet\":\"int x = 10;\",\"proposedReplacement\":\"private static final int DEFAULT_VALUE = 10;\\nint meaningfulVariableName = DEFAULT_VALUE;\"}]}"
}
```

### 응답 데이터 구조 (JSON 파싱 후)
```json
{
  "aiScore": 85,
  "codeSmells": [
    {
      "name": "Magic Number",
      "description": "변수 x에 10이라는 매직 넘버가 사용되었습니다."
    },
    {
      "name": "Poor Variable Naming",
      "description": "변수명 'x'가 너무 일반적입니다."
    }
  ],
  "suggestions": [
    {
      "problematicSnippet": "int x = 10;",
      "proposedReplacement": "private static final int DEFAULT_VALUE = 10;\nint meaningfulVariableName = DEFAULT_VALUE;"
    }
  ]
}
```

### Postman 테스트 예시
```
Method: POST
URL: http://localhost:9443/api/analysis/analyze-stored

Headers:
  Content-Type: application/json

Body (raw - JSON):
{
  "repositoryUrl": "https://github.com/octocat/Hello-World",
  "filePath": "README",
  "code": "public class Example {\n    public static void main(String[] args) {\n        int x = 10;\n        System.out.println(x);\n    }\n}",
  "analysisTypes": ["code_smell", "design_pattern"],
  "toneLevel": 3,
  "customRequirements": "변수명과 매직 넘버 검토",
  "userId": 1
}
```

### cURL 예시
```bash
curl -X POST "https://localhost:9443/api/analysis/analyze-stored" \
  -H "Content-Type: application/json" \
  -d '{
    "repositoryUrl": "https://github.com/octocat/Hello-World",
    "filePath": "README",
    "code": "public class Example {\n    public static void main(String[] args) {\n        int x = 10;\n        System.out.println(x);\n    }\n}",
    "analysisTypes": ["code_smell", "design_pattern"],
    "toneLevel": 3,
    "customRequirements": "변수명과 매직 넘버 검토",
    "userId": 1
  }'
```

---

## 전체 플로우 테스트 시나리오

### 시나리오 1: 기본 플로우
1. **파일 저장**: POST `/api/github/save-file`로 GitHub 파일 저장
2. **응답 확인**: `analysisId` 확인
3. **AI 분석**: POST `/api/analysis/analyze-stored`로 저장된 파일 분석
4. **결과 확인**: AI 분석 결과 (점수, 코드 스멜, 제안사항) 확인

### 시나리오 2: 다양한 톤 레벨 테스트
1. 동일한 파일로 `toneLevel`을 1~5까지 변경하며 테스트
2. 각 톤 레벨별 AI 응답 톤 차이 확인

### 시나리오 3: 여러 분석 타입 테스트
```json
{
  "analysisTypes": ["code_smell"],
  "toneLevel": 3
}
```
```json
{
  "analysisTypes": ["code_smell", "design_pattern", "best_practices"],
  "toneLevel": 3
}
```

---

## 기타 GitHub API 엔드포인트

### 1. 레포지토리 목록 조회
```
GET /api/github/repos
```

**응답:**
```json
[
  {
    "name": "Hello-World",
    "fullName": "octocat/Hello-World",
    "repoUrl": "https://github.com/octocat/Hello-World",
    "owner": "octocat"
  }
]
```

### 2. 브랜치 목록 조회
```
GET /api/github/repos/{owner}/{repo}/branches
```

**예시:**
```
GET /api/github/repos/octocat/Hello-World/branches
```

**응답:**
```json
[
  {
    "name": "main"
  },
  {
    "name": "develop"
  }
]
```

### 3. 파일 트리 조회
```
GET /api/github/repos/{owner}/{repo}/tree/{branch}
```

**예시:**
```
GET /api/github/repos/octocat/Hello-World/tree/main
```

**응답:**
```json
[
  {
    "path": "README",
    "type": "blob"
  },
  {
    "path": "src",
    "type": "tree"
  },
  {
    "path": "src/main.java",
    "type": "blob"
  }
]
```

### 4. 파일 내용 직접 조회 (저장 없이)
```
GET /api/github/repos/{owner}/{repo}/content?path={filePath}
```

**예시:**
```
GET /api/github/repos/octocat/Hello-World/content?path=README
```

**응답:**
```json
{
  "name": "README",
  "path": "README",
  "content": "Hello World!\nThis is a sample repository.",
  "encoding": "base64",
  "size": 1024
}
```

---

## 에러 응답 예시

### 400 Bad Request (잘못된 요청)
```json
{
  "code": "4000",
  "message": "repositoryUrl is required",
  "Data": null
}
```

### 404 Not Found (파일을 찾을 수 없음)
```json
{
  "code": "4004",
  "message": "저장된 파일을 찾을 수 없습니다",
  "Data": null
}
```

### 500 Internal Server Error
```json
{
  "code": "5000",
  "message": "파일 저장에 실패했습니다: [상세 에러 메시지]",
  "Data": null
}
```

---

## 주의사항

1. **GitHub Token**: `.env` 파일에 `GITHUB_TOKEN`이 설정되어 있어야 private 레포지토리 접근 가능
2. **HTTPS**: 로컬 테스트 시 `https://localhost:9443` 사용 (자체 서명 인증서)
3. **인증**: 현재는 인증이 비활성화되어 있으나, 프로덕션에서는 JWT 토큰 필요
4. **Rate Limit**: GitHub API는 시간당 요청 제한이 있음 (인증 시 5000회/시간)
5. **파일 크기**: 너무 큰 파일은 GitHub API에서 제한될 수 있음 (일반적으로 1MB 이하 권장)

---

## 테스트 체크리스트

- [ ] 1단계: GitHub 파일 저장 성공
- [ ] 1단계: analysisId 반환 확인
- [ ] 2단계: AI 분석 요청 성공
- [ ] 2단계: toneLevel=1로 테스트 (부드러운 톤)
- [ ] 2단계: toneLevel=3로 테스트 (중립적 톤)
- [ ] 2단계: toneLevel=5로 테스트 (엄격한 톤)
- [ ] AI 응답의 aiScore 필드 확인
- [ ] AI 응답의 codeSmells 배열 확인
- [ ] AI 응답의 suggestions 배열 확인
- [ ] DB에 분석 결과 저장 확인 (CODE_RESULT 테이블)
- [ ] 에러 케이스 테스트 (존재하지 않는 파일)

---

## 문제 해결

### 문제: "저장된 파일을 찾을 수 없습니다"
**해결**: 1단계에서 사용한 `repositoryUrl`과 `filePath`를 2단계에서도 동일하게 사용해야 함

### 문제: GitHub API 403 Forbidden
**해결**: `.env` 파일에 유효한 `GITHUB_TOKEN` 설정 필요

### 문제: SSL Certificate Error
**해결**: Postman에서 "Settings > SSL certificate verification" 비활성화 또는 cURL에서 `-k` 옵션 사용

---

**작성일**: 2025-11-21
**버전**: 1.0
