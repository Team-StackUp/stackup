# 객체 스토리지 (S3 / MinIO)

> 로컬은 MinIO (`infra/minio`), 운영은 AWS S3. 동일한 SDK 인터페이스(S3 호환)로 구현한다.

---

## 1. 버킷 설계

| 환경 | 버킷명 | 비고 |
|------|--------|------|
| 로컬 | `stackup` (단일) | `infra/minio/init.sh` 자동 생성 |
| 스테이징 | `stackup-staging` | |
| 운영 | `stackup-prod` | |

> bucket은 **환경변수**로 주입(`S3_BUCKET`), DB에는 key만 저장. ([database.md §5](./database.md))

---

## 2. Key Convention

```
resumes/raw/{user_id}/{uuid}.pdf
analyzed/resume/{resume_id}/summary.md
analyzed/repository/{repository_id}/summary.md
session/{session_id}/audio/{message_id}.webm
session/{session_id}/audio/{message_id}.transcript.txt
interview/tts/{session_id}/{message_id}.{ext}                # 질문 whole-message TTS (영속, ttsAudioPath)
interview/tts/{session_id}/{message_id}/seg-{seq}.{ext}      # 꼬리질문 문장 단위 TTS 세그먼트 (휘발성, DB 미기록)
feedback/{session_id}/report.md                              # AI 학습 리포트 (GFM, reportS3Key — Core 프록시로 열람)
feedback/{session_id}/charts/{name}.png
```

> 세그먼트 키 규칙은 **AI(발행)·Core(프록시 재구성) 양측이 하드코딩으로 공유**하는 SSOT다 — 변경 시 양측 동시 수정. `{ext}` ∈ `wav|mp3|ogg|m4a`, `{seq}` 는 0부터의 오디오 세그먼트 순번. 세그먼트는 DB에 기록하지 않고 Core 프록시(`GET …/audio/segments/{seq}?ext=`)가 소유권 검증 후 규칙으로 키를 만든다.

규칙:
- 슬래시(`/`) 계층 구조로 탐색성 확보
- 사용자 식별자는 user_id (정수) 사용 — github_username은 변경 가능하므로 회피
- 확장자는 실제 컨텐츠 타입과 일치
- 파일명에 한글·공백 금지 (URL 인코딩 이슈)

---

## 3. 업로드 정책

| 항목 | 값 |
|------|----|
| Resume PDF 최대 크기 | 20 MB |
| 음성 오디오 최대 크기 (단일) | 50 MB |
| 분석 마크다운 평균 크기 | < 100 KB |

업로드 방식:
- **소형 (< 5MB)**: Core Server 경유 직접 업로드
- **대형 (≥ 5MB)**: Presigned URL 발급 후 클라이언트 직접 업로드 (Phase 2 도입 검토)

### Resume 업로드 시퀀스
```
[Frontend] POST /api/resumes (multipart/form-data)
  → [Core] MIME validation (application/pdf)
  → [Core] 파일 크기 검증 (≤ 20MB)
  → [Core] S3 PUT: resumes/raw/{userId}/{uuid}.pdf
  → [Core] resumes INSERT (file_path = S3 key)
  → [Core] 응답 201 + Location 헤더
```

---

## 4. 다운로드 / 조회

### 4.1 인증된 사용자에게 제공
- Core Server가 **presigned GET URL**을 생성해서 응답에 포함:
  ```json
  {
    "id": 42,
    "documentS3Key": "analyzed/resume/42/summary.md",
    "downloadUrl": "https://s3.../...?X-Amz-Signature=...",
    "downloadUrlExpiresAt": "2026-04-27T16:00:00Z"
  }
  ```
- TTL: 5분 (다운로드용), 1시간 (오디오 스트리밍용)

### 4.2 AI Server의 직접 접근
- AI Server는 자체 IAM 자격증명으로 S3 직접 GET/PUT
- presigned URL 미사용

---

## 5. 보안

- 모든 버킷은 **public access 차단** (`BlockPublicAcls = true`)
- 외부 접근은 presigned URL 또는 CloudFront 서명 URL을 통해서만
- 서버 측 암호화 (SSE-S3 또는 SSE-KMS) 활성화
- MinIO 로컬은 평문 OK, 운영은 KMS 키 사용

---

## 6. 라이프사이클

| 객체 | 보관 기간 | 처리 |
|------|-----------|------|
| `resumes/raw/*` | 영구 (사용자 삭제 시까지) | 사용자가 이력서 삭제 → soft delete + S3 객체 30일 후 영구 삭제 |
| `analyzed/*` | 분석 문서 archived 후 90일 | 운영 정책 |
| `session/*/audio/*` | 세션 종료 후 180일 | Glacier로 이동 후 365일 |
| `feedback/*` | 영구 | 사용자가 명시적 삭제 시 |

라이프사이클 룰은 S3 Bucket Lifecycle Configuration으로 관리 (운영 단계에서 적용).

---

## 7. MinIO 로컬 사용

`docker-compose.yml`의 `minio` + `minio-init` 서비스가 자동 부트스트랩.

- API: http://localhost:9000
- Console: http://localhost:9001 (default `minioadmin`/`minioadmin`)
- 버킷 자동 생성: `infra/minio/init.sh` 가 `${MINIO_BUCKET}` 생성

### Java SDK (Core Server)
```java
// AWS SDK v2 사용, endpoint만 환경별로 분기
S3Client s3 = S3Client.builder()
    .endpointOverride(URI.create(env.get("S3_ENDPOINT")))  // local: http://minio:9000
    .credentialsProvider(...)
    .region(Region.US_EAST_1)
    .forcePathStyle(true)  // MinIO 필수
    .build();
```

### Python SDK (AI Server)
```python
import boto3
s3 = boto3.client(
    "s3",
    endpoint_url=settings.S3_ENDPOINT,
    aws_access_key_id=settings.S3_ACCESS_KEY,
    aws_secret_access_key=settings.S3_SECRET_KEY,
    region_name="us-east-1",
)
```

---

## 8. 비용·성능 메모

- presigned URL은 발급 자체는 무료, 다운로드 시 데이터 전송 비용 발생
- 분석 마크다운은 평균 < 100KB → S3 비용 무시 가능
- 음성 오디오가 비용 주범 → 압축 코덱 (Opus, 32kbps) 사용
