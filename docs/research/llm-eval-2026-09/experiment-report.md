# 실험 리포트 — LLM 제공자 비교 실험 (로컬 LLM · 오픈 가중치 모델)

| 항목 | 내용 |
|---|---|
| 실험일 | 2026-09-17 |
| 목적 | 2026-09-21 팀 회의 안건. Gemini(학교 게이트웨이) 의존을 줄일 대안으로 로컬 LLM(Qwen 등)과 오픈 가중치 모델(Kimi 등)을 검토 |
| 결정용 요약 | [`../llm-provider-evaluation-2026-09.md`](../llm-provider-evaluation-2026-09.md) |
| 실험 도구 | [`ai/scripts/llm_eval/`](../../../ai/scripts/llm_eval/README.md) |
| 원시 데이터 | 이 디렉터리의 `raw/*.jsonl`, `auto-metrics-summary.json`, `eval-judge.jsonl`, `eval-judge-table.json`, `gpu-placement.log` |

---

## 0. 한 줄 결론

**운영은 현재 게이트웨이 Gemini(3.5 Flash-Lite + 3.1 Pro)를 유지한다.** ana-server(GTX 1660 Ti 6GB)의 로컬 LLM은 품질이 절반 수준(블라인드 채점 1.6~2.7 vs 4.0)이고, GPU 1장의 직렬 처리 때문에 동시 사용 시 꼬리질문이 33~111초로 무너져 운영 대체가 불가능하다. 게이트웨이 대비책으로는 Gemma 4 31B(품질 동급, 느림)와 Solar Pro 4(빠름, 프롬프트 보정 필요)가 유력하다.

---

## 1. 배경과 질문

StackUp 의 AI 서버는 두 티어로 LLM 을 쓴다.

| 티어 | 현재 모델 | 쓰이는 곳 | 요구사항 |
|---|---|---|---|
| Pro | gemini-3.1-pro-preview | 질문 풀 생성, 피드백 패널·종합, PDF 비전 | 품질, JSON 스키마 준수, 멀티모달(PDF) |
| Flash | gemini-3.5-flash-lite | 스트리밍 꼬리질문(+답변 의도 분류·채점), 자기소개 평가, 답변 코칭(세션당 ~15건, 동시 5) | 저지연(< 3s), 태그 출력 준수 |

두 티어 모두 학교 발급 키로 OpenAI 호환 게이트웨이(Mindlogic)를 경유한다. 이번 실험은 다음 질문에 답한다.

1. 이 서버의 GPU 로 Qwen 같은 로컬 모델을 돌려 Gemini 를 대체할 수 있는가?
2. Kimi·Qwen 같은 오픈 가중치 대형 모델은 현실적인 대안인가?
3. 게이트웨이를 못 쓰게 되면 무엇으로, 얼마에 갈 수 있는가?

---

## 2. 실험 환경

### 2.1 하드웨어·소프트웨어

| 항목 | 값 |
|---|---|
| 서버 | ana-server — Intel i7-10750H (12 스레드), RAM 16GB (가용 ~11GB) |
| GPU | NVIDIA GeForce GTX 1660 Ti 6GB (Turing), 드라이버 575.57.08 / CUDA 12.9 |
| 로컬 서빙 | Ollama 0.20.7 (Docker, 다른 프로젝트 devlog·devtalk·mcp 와 공유), OpenAI 호환 `/v1/chat/completions` |
| 로컬 모델 설정 | 전 모델 `PARAMETER num_ctx 8192` 파생 모델 생성, 측정 전 워밍업 |
| 실행 위치 | 운영 `stackup-ai` 컨테이너 안 (운영과 같은 의존성·네트워크). Ollama 컨테이너를 `stackup_default` 네트워크에 연결 |
| 게이트웨이 | factchat-cloud.mindlogic.ai (학교 키). 지연 수치에 게이트웨이 오버헤드 포함 |

### 2.2 운영 부하 기준값 (운영 DB 집계, 개인 데이터 미열람)

실험 입력 크기와 동시성 조건을 정하기 위해 `ai_request_logs`·`interview_messages` 를 집계만 했다.

| 지표 | 값 |
|---|---|
| 질문 풀 생성 입력 토큰 | p50 3,141 / p90 4,722 / max 4,869 |
| 질문 풀 생성 지연 (Gemini 3.1 Pro) | p50 14.4s / p90 16.9s |
| 꼬리질문 스트림 지연 (Gemini Flash-Lite) | p50 1.6s |
| 피드백 코칭 입력/출력 토큰 | 평균 ~2,000 / ~420 |
| 피드백 패널(Pro) 지연 | p50 12.2s / p90 19.4s |
| 지원자 답변 길이 | p50 162자 / p90 425자 / max 2,583자 |
| 누적 사용량 | 세션 95건, 사용자 4명 (2026-05~09) |

---

## 3. 후보 모델 선정

### 3.1 사전 조사 요약 (웹 리서치, 2026-09 기준)

| 구분 | 모델 | 요점 |
|---|---|---|
| 6GB 에 들어가는 소형 | Qwen3 4B Instruct-2507 | 기존 1차 후보. Apache-2.0 |
| | Qwen3.5 4B | 최신 소형 Qwen, 비전 포함, thinking 기본 on |
| | Kanana-2-3B (카카오) | 3B급 한국어 벤치 최상위(KMMLU 43.3, KoMT 6.92). 자사 서비스 사용 가능 라이선스 |
| | Mi:dm 2.0 Mini (KT) | 2.3B, MIT, Ko-IFEval 73.3 |
| | A.X 4.0 Light (SKT) | 7B, Apache-2.0, KMMLU 64.2. Q4 ~4.7GB 로 빠듯 |
| | Gemma 4 E2B | 대조군. thinking 기본 on |
| | EXAONE 3.5/4.0 (LG) | **비상업 라이선스** → 운영 후보에서 제외 (1차 측정만) |
| Kimi | K2.6 (1T/32B active), K3 (2.8T/104B active), Kimi-Linear 48B | **로컬 불가** (최소 Kimi-Linear Q2 도 16GB+). API 는 K2.6 $0.95/$4.00, K3 $3/$15 (per 1M) |
| 대형 오픈 (API) | Qwen3.8-Flash, DeepSeek V4.1 Flash, GLM-5.3-Flash, gpt-oss-120b | 가격은 Gemini 대비 수 분의 1. 한국어 공개 벤치 부족 |
| 게이트웨이에 이미 있음 | Gemma 4 31B, Solar Pro 4, gpt-oss-120b, Llama 4 Maverick | 학교 키로 바로 실측 가능 → 포함 |

게이트웨이 `/models` 에는 75개 모델이 있었으나 Qwen·Kimi·DeepSeek 는 없었다. 외부 API 키가 없어 이 셋은 **실측하지 못했다**.

### 3.2 최종 실측 대상

| 경로 | 모델 | 설정 |
|---|---|---|
| 게이트웨이 (기준) | gemini-3.5-flash-lite | 운영값 |
| 게이트웨이 (기준) | gemini-3.1-pro-preview | 질문 풀만 |
| 게이트웨이 | google/gemma-4-31B-it | 운영 프롬프트 그대로 |
| 게이트웨이 | solar-pro4 | 〃 |
| 게이트웨이 | gpt-oss-120b (Fireworks) | 운영값 512 토큰 + 재측정 2048 토큰 |
| 게이트웨이 | Llama-4-Maverick-17B-128E-Instruct-FP8 | 〃 |
| 로컬 | qwen3:4b-instruct | num_ctx 8192 |
| 로컬 | qwen3.5:4b | num_ctx 8192, `reasoning_effort: none` |
| 로컬 | kanana-2-3b-instruct Q4_K_M (GGUF) | num_ctx 8192 |
| 로컬 | Midm-2.0-Mini-Instruct Q4_K_M (GGUF) | num_ctx 8192 |
| 로컬 | A.X-4.0-Light Q4_K_M (GGUF) | num_ctx 8192 |
| 로컬 (실패) | gemma4:e2b-it-qat | Ollama 0.20.7 에서 pull 불가(업그레이드 요구). 공유 컨테이너라 미업그레이드 |

---

## 4. 실험 방법

### 4.1 원칙

- **운영 코드를 그대로 쓴다.** 프롬프트·파서·체인 빌더를 수정하지 않고 모델·엔드포인트만 바꿨다.
  - 질문 풀: `build_question_generation_chain` (PydanticOutputParser, JSON)
  - 꼬리질문: `build_streaming_followup_generator().stream()` (운영 경로, `<intent>/<question>/<meta>` 태그, 첫 질문 토큰 시점 측정)
  - 코칭: `build_answer_coaching_chain` (JSON)
- **합성 데이터만 쓴다.** 실제 사용자 이력서·답변은 쓰지 않았다.

### 4.2 테스트 케이스 (`cases.py`)

**질문 풀 5건** (각 2회 반복)

| ID | 직군 / 모드 | 자료 | 확인하려는 것 |
|---|---|---|---|
| q-backend-tech | BACKEND / TECHNICAL | 이력서 + 자기소개 | 자기소개 앵커링, 근거 인용 |
| q-frontend-repo | FRONTEND / TECHNICAL | GitHub 레포 분석 | 레포 기반 질문 |
| q-infra-dba-multi | INFRA+DBA / INTEGRATED | 이력서 | 복수 직군 분배, 카테고리 균형 |
| q-personality-coverletter | BACKEND / PERSONALITY | 자소서 | BEHAVIORAL 중심, STAR 유도 |
| q-long-context-p90 | BACKEND / TECHNICAL | 문서 4개(운영 p90 크기) + 최근 질문 2개 | 긴 문맥 절단, 최근 질문 중복 회피 |

**꼬리질문 14건** (각 3회 반복, 정답 라벨 포함)

| ID | 상황 | 정답 의도 | 점수 기대 | correctness 기대 |
|---|---|---|---|---|
| f-strong-backend | 구체 수치·원인 포함 좋은 답 | NORMAL | 높음(≥3) | null (컨텍스트 없음) |
| f-weak-vague | "잘 처리했습니다" 식 부실한 답 | NORMAL | 낮음(≤2) | null |
| f-dont-know-explicit | "잘 모르겠습니다, 넘어가도 될까요" | DONT_KNOW | — | null |
| f-dont-know-stt | STT 간투어 섞인 "모르겠어요" | DONT_KNOW | — | null |
| f-clarification | "질문을 쉽게 다시 말씀해 주세요" | CLARIFICATION | — | null |
| f-confirm-short | 확인형 질문에 "네, 맞습니다" | NORMAL | null (감점 금지) | null |
| f-infra-fact-error | 이력서(CPU 70%)와 다른 답(메모리 50%) | NORMAL | 낮음 | 낮음(≤2) |
| f-dba-correct-with-context | 이력서와 일치하는 좋은 답 | NORMAL | 높음 | 높음(≥3) |
| f-frontend-strong | 레포와 일치하는 좋은 답 | NORMAL | 높음 | 높음 |
| f-personality-star | STAR 구조 경험 답변 | NORMAL | 높음 | null |
| f-personality-rambling | 두루뭉술한 인성 답변 | NORMAL | 낮음 | null |
| f-stt-messy-normal | 간투어 많지만 내용 좋은 STT 답 | NORMAL | 높음 | null |
| f-long-answer-history | 1,000자 답변 + 대화 이력 2턴 | NORMAL | 높음 | null |
| f-english-mixed | 영문 기술용어 섞인 답 | NORMAL | 높음 | null |

**코칭 3건** (각 3회 반복): 부실한 기술 답변, "모름" 답변, 두루뭉술한 인성 답변.

### 4.3 지연·동시성 시나리오 (`--latency`)

1. **콜드스타트**: Ollama 에서 모델 언로드(`keep_alive: 0`) 후 첫 꼬리질문, 그다음 호출.
2. **피드백 fan-out**: 코칭 15건을 동시성 5 로 실행 (운영 `FEEDBACK_COACHING_CONCURRENCY=5`, 세션당 ~15건과 동일).
3. **경합**: fan-out 시작 3초 뒤 "다른 사용자"의 꼬리질문 1건 → 그 지연.

### 4.4 자동 지표 (`analyze.py`, 규칙 기반)

| 지표 | 정의 |
|---|---|
| 태그 준수 | 꼬리질문에 태그 잔해가 없고, NORMAL 답변이면 `<meta>` 평가가 파싱됨 (확인형 단답은 meta 없어도 준수) |
| 의도 분류 정확도 | `answer_intent` 가 라벨과 일치한 비율 |
| 점수 라벨 정확도 | NORMAL 케이스에서 specificity 가 기대(높음 ≥3 / 낮음 ≤2 / null)와 일치 |
| 사실대조 규칙 | correctness 가 기대(컨텍스트 없으면 null / 불일치면 ≤2 / 일치면 ≥3)와 일치 |
| 판별력 | 같은 질문의 강한 답 − 약한 답 specificity 중간값 차 |
| 질문 풀 성공 | JSON 파싱 성공 비율 |
| 근거 인용 실재 | `target_evidence` 의 4-gram 60% 이상이 자료 원문에 존재 |
| 중복 | 같은 풀 안 질문쌍 문자 bigram Jaccard ≥ 0.5 |
| 외국 문자 | 한자·가나·키릴·태국·아랍 문자 혼입 |
| 코칭 수치 날조 | 모범답안·리라이트에 자료·답변에 없는 2자리 이상 수치 |
| 지연 | 총 지연 중간값·p90, 첫 질문 토큰(TTFT), 3초·10초(운영 타임아웃) 초과 비율 |

### 4.5 블라인드 품질 채점 (`judge.py`)

- 같은 케이스에 대한 후보들의 출력(반복 0회차)을 **익명 A, B, C…** 로 묶고 판정 호출마다 **순서를 무작위**로 섞었다.
- 판정 모델: **gemini-3.1-pro-preview**, **claude-opus-5** (서로 다른 계열, temperature 0). 판정 호출 44건, 오류 0.
- 채점 축 (1~5):
  - 꼬리질문: relevance(답변 특정 대목·의도 대응), depth(기대 신호 겨냥·변별력), language(한국어·간결), overall
  - 질문 풀: grounding(자료 근거·날조 없음), coverage(주제·직군·모드 분배), depth, language, overall
  - 코칭: usefulness, faithfulness(날조 없음), rewrite(원 답변 기반), language, overall
- 제외: Kanana-2-3B (출력 자체가 깨져 채점 의미 없음).

---

## 5. 결과

### 5.1 블라인드 품질 — overall

| 모델 | 꼬리질문 Claude / Gemini / 평균 | 질문 풀 Claude / Gemini / 평균 | 코칭 Claude / Gemini / 평균 |
|---|---|---|---|
| Gemini 3.1 Pro | — | 4.4 / 4.6 / **4.5** | — |
| Gemini 3.5 Flash-Lite | 4.0 / 4.4 / **4.2** | 4.2 / 4.2 / 4.2 | 4.3 / 4.0 / 4.2 |
| Gemma 4 31B | 3.9 / 4.9 / **4.4** | 4.0 / 4.6 / 4.3 | 4.0 / 4.7 / 4.3 |
| Solar Pro 4 | 3.8 / 3.4 / 3.6 | 3.8 / 3.0 / 3.4 | 4.7 / 4.0 / **4.3** |
| gpt-oss-120b | 3.3 / 3.0 / 3.1 | 2.4 / 3.0 / 2.7 | 2.7 / 2.0 / 2.3 |
| Llama 4 Maverick | 3.3 / 2.7 / 3.0 | 2.8 / 1.8 / 2.3 | 3.0 / 2.3 / 2.7 |
| Qwen3 4B Instruct (로컬) | 2.6 / 2.4 / 2.5 | 3.0 / 3.2 / 3.1 | 2.3 / 2.0 / 2.2 |
| Qwen3.5 4B (로컬) | 2.7 / 2.2 / 2.5 | 2.7 / 2.3 / 2.5 | 2.0 / 1.7 / 1.8 |
| A.X 4.0 Light (로컬) | 2.1 / 1.7 / 1.9 | 2.7 / 2.3 / 2.5 | 2.3 / 2.0 / 2.2 |
| Mi:dm 2.0 Mini (로컬) | 1.6 / 1.4 / 1.5 | 2.0 / 2.0 / 2.0 | 전부 파싱 실패 |

**판정자 신뢰도**
- 같은 (케이스, 모델) 192쌍에서 두 판정자 overall 완전 일치 38.5%, **±1점 이내 90.1%**, 평균 절대차 0.71.
- **Gemini 판정자는 Google 계열에 후했다**: 꼬리질문 Gemma 4 31B +0.93, Gemini Flash-Lite +0.43, 질문 풀 Gemma +0.6 (Claude 대비). 반대로 Solar·Llama 는 Gemini 판정자가 더 낮게 줬다. → **순위 판단은 Claude 점수를 우선**했다.

### 5.2 블라인드 품질 — 세부 축 (Claude 판정)

**꼬리질문**

| 모델 | relevance | depth | language | overall |
|---|---|---|---|---|
| Gemini 3.5 Flash-Lite | 4.57 | 3.71 | 4.50 | 4.00 |
| Gemma 4 31B | 4.36 | 3.71 | 4.71 | 3.93 |
| Solar Pro 4 | 4.21 | **4.00** | 3.36 | 3.79 |
| gpt-oss-120b | 3.43 | 3.07 | 4.43 | 3.29 |
| Llama 4 Maverick | 3.64 | 2.93 | 4.64 | 3.29 |
| Qwen3.5 4B | 3.36 | 2.71 | 3.93 | 2.71 |
| Qwen3 4B Instruct | 3.14 | 2.21 | 3.43 | 2.57 |
| A.X 4.0 Light | 2.36 | 1.79 | 3.57 | 2.14 |
| Mi:dm 2.0 Mini | 1.79 | 2.07 | 3.29 | 1.64 |

**질문 풀**

| 모델 | grounding | coverage | depth | language | overall |
|---|---|---|---|---|---|
| Gemini 3.1 Pro | **5.00** | **4.60** | **4.60** | 4.40 | **4.40** |
| Gemini 3.5 Flash-Lite | 4.80 | 4.20 | 4.00 | 4.60 | 4.20 |
| Gemma 4 31B | 4.80 | 3.60 | 4.00 | **5.00** | 4.00 |
| Solar Pro 4 | 4.60 | 3.80 | **4.60** | 3.00 | 3.80 |
| Qwen3 4B Instruct | 4.20 | 3.40 | 3.00 | 3.40 | 3.00 |
| Llama 4 Maverick | 3.80 | 3.20 | 2.20 | 4.00 | 2.80 |
| Qwen3.5 4B | 3.67 | 3.00 | 3.33 | 2.67 | 2.67 |
| A.X 4.0 Light | 3.00 | 2.33 | 2.67 | 3.33 | 2.67 |
| gpt-oss-120b | 3.40 | 3.20 | 2.40 | 3.80 | 2.40 |
| Mi:dm 2.0 Mini | 2.00 | 2.00 | 2.00 | 4.00 | 2.00 |

**코칭**

| 모델 | usefulness | faithfulness | rewrite | language | overall |
|---|---|---|---|---|---|
| Solar Pro 4 | **4.67** | **4.67** | **4.67** | 5.00 | **4.67** |
| Gemini 3.5 Flash-Lite | 4.33 | **4.67** | 4.33 | 5.00 | 4.33 |
| Gemma 4 31B | 4.00 | 4.33 | 4.33 | 4.67 | 4.00 |
| Llama 4 Maverick | 3.00 | 4.00 | 2.67 | 4.00 | 3.00 |
| gpt-oss-120b | 3.67 | 3.00 | 3.00 | 3.33 | 2.67 |
| Qwen3 4B Instruct | 2.67 | 3.33 | 2.33 | 3.33 | 2.33 |
| A.X 4.0 Light | 2.33 | 3.33 | 2.33 | 3.33 | 2.33 |
| Qwen3.5 4B | 2.33 | 3.33 | 2.33 | 2.33 | 2.00 |

### 5.3 꼬리질문 케이스별 품질 (두 판정자 평균 overall)

| 케이스 | Gemini FL | Gemma 31B | Solar Pro 4 | gpt-oss | Llama 4 | Qwen3 4B | Qwen3.5 4B | A.X Light | Mi:dm Mini |
|---|---|---|---|---|---|---|---|---|---|
| f-strong-backend | 3.5 | 4.5 | 4.5 | 2.0 | 4.0 | 1.5 | 2.0 | 1.5 | 1.0 |
| f-weak-vague | 4.0 | 4.5 | 2.0 | 3.0 | 2.5 | 4.0 | 3.0 | 2.0 | 2.0 |
| f-dont-know-explicit | 5.0 | 4.5 | 2.0 | 1.0 | 3.0 | 1.0 | 1.5 | 1.5 | 1.0 |
| f-dont-know-stt | 4.0 | 4.5 | 1.5 | 1.5 | 3.5 | 1.0 | 2.0 | 1.5 | 1.5 |
| f-clarification | 4.5 | 4.5 | 2.0 | 4.5 | 3.0 | 2.5 | 2.0 | 2.5 | 1.5 |
| f-confirm-short | 4.5 | 3.5 | 4.5 | 3.5 | 2.5 | 1.0 | 2.0 | 2.5 | 2.0 |
| f-infra-fact-error | 5.0 | 5.0 | 3.0 | 3.0 | 3.0 | 2.5 | 4.0 | 1.5 | 1.5 |
| f-dba-correct-with-context | 3.5 | 4.5 | 2.5 | 4.0 | 4.0 | 3.0 | 1.5 | 1.5 | 1.5 |
| f-frontend-strong | 3.5 | 4.0 | 4.5 | 5.0 | 3.0 | 3.5 | 2.5 | 2.0 | 1.5 |
| f-personality-star | 4.5 | 4.5 | 5.0 | 3.5 | 2.5 | 2.0 | 2.5 | 2.0 | 1.5 |
| f-personality-rambling | 4.5 | 5.0 | 4.0 | 3.0 | 3.0 | 5.0 | 4.0 | 3.5 | 2.0 |
| f-stt-messy-normal | 4.5 | 4.5 | 4.5 | 3.5 | 3.0 | 3.5 | 2.5 | 2.0 | 1.0 |
| f-long-answer-history | 4.5 | 4.0 | 5.0 | 2.5 | 1.5 | 1.5 | 2.0 | 1.5 | 1.0 |
| f-english-mixed | 3.5 | 4.0 | 5.0 | 4.0 | 3.5 | 3.0 | 3.0 | 1.5 | 2.0 |

gpt-oss-120b 는 max_tokens 512 의 반복 0회차 출력으로 채점되지 않도록 2048 재측정 결과로 채점했다.

관찰:
- **Solar Pro 4 는 정상 답변에서 최상급**(긴 답변·STAR·영문 혼용 5.0)이지만 **"모름"·재설명 요청 대응이 약하다**(1.5~2.0). 재설명 대신 정답 요소를 나열하거나, 부실한 답을 DONT_KNOW 로 분류했다.
- **Gemini Flash-Lite 와 Gemma 31B 는 모든 유형에서 고르게 3.5 이상.**
- **로컬 소형 모델은 "모름"·재설명·확인형 단답 대응이 1.0~2.5** 로 가장 약하다. 운영에서 가장 흔한 흐름(짧은 답·모름)에서 면접 경험이 망가진다.

### 5.4 형식 준수·채점 신뢰도 (자동 지표)

| 모델 | 호출 성공 | 태그 준수 | 의도 분류 | 점수 라벨 | 사실대조 규칙 | 판별력(백엔드/인성) | 질문 풀 성공 | 긴 문맥 풀 | 근거 인용 실재 | 코칭 성공 |
|---|---|---|---|---|---|---|---|---|---|---|
| Gemini 3.1 Pro | — | — | — | — | — | — | 100% | 2/2 | 100% | — |
| Gemini 3.5 Flash-Lite | 100% | 100% | 100% | 100% | 100% | 3.0 / 2.0 | 90% | 1/2 | 100% | 100% |
| Gemma 4 31B | 100% | 100% | 100% | 100% | 100% | 3.0 / 3.0 | 100% | 2/2 | 100% | 100% |
| Solar Pro 4 | 100% | 100% | 95.2% | 87.9% | 100% | 2.0 / 2.0 | 100% | 2/2 | 100% | 100% |
| gpt-oss-120b (512) | 100% | 54.8% | 100% | 42.4% | 72.7% | 4.0 / — | 100% | 2/2 | 100% | 100% |
| gpt-oss-120b (2048) | 100% | 100% | 100% | 100% | 100% | — | — | — | — | — |
| Llama 4 Maverick | 100% | 100% | 92.9% | 90.9% | 81.8% | 3.0 / 2.0 | 100% | 2/2 | 100% | 100% |
| Qwen3 4B Instruct | 100% | 100% | 100% | 81.8% | 57.6% | 2.0 / 2.0 | 90% | 2/2 | 100% | 100% |
| Qwen3.5 4B | 100% | 33.3% | 92.9% | 18.2% | 69.7% | 2.0 / — | 50% | 0/2 | 100% | 100% |
| A.X 4.0 Light | 100% | 100% | 90.5% | 75.8% | 54.5% | 3.0 / 1.0 | 70% | 1/2 | 91.4% | 88.9% |
| Mi:dm 2.0 Mini | 100% | 28.6% | 14.3% | 9.1% | 72.7% | — | 30% | 1/2 | 100% | **0%** |
| Kanana-2-3B | 출력 깨짐 | 28.6% | 78.6% | 9.1% | 72.7% | — | **0%** | 0/2 | — | **0%** |

추가 지표:
- **외국 문자 혼입**: 모든 모델 0% (Qwen 계열의 중국어 혼입 우려는 이번 케이스에서 관찰되지 않음).
- **마크다운 기호 혼입(질문)**: 모든 모델 0%.
- **질문 풀 내 중복 질문쌍**: 모든 모델 0%. 최근 받은 질문 반복은 Mi:dm 1건.
- **코칭 수치 날조**: gpt-oss-120b 11.1%, 나머지 0%.
- **질문 길이** (질문 풀, 80자 이내 비율): Llama 4·gpt-oss 96%, Gemma 85%, Qwen3 4B 53%, Gemini Flash-Lite 45%, Gemini Pro 17%, **Solar Pro 4 0%** (중간값 117자, 최대 203자).

의도 분류 오답 패턴:
- Mi:dm 2.0 Mini: **42건 전부 DONT_KNOW**. 운영에서 DONT_KNOW 꼬리질문은 Core 가 폐기하므로 면접이 진행되지 않는다.
- Kanana-2-3B: 42건 전부 NORMAL (모름·재설명 인식 불가) + 출력 문자열 깨짐.
- Solar Pro 4 / Llama 4 / A.X: 부실한 답(f-weak-vague)을 DONT_KNOW 로 분류하는 경향.
- Qwen3.5 4B: 두루뭉술한 인성 답변을 CLARIFICATION 으로 1회 분류.

질문 풀 실패 원인:
- Gemini Flash-Lite 1건: 긴 문맥 케이스에서 JSON 코드펜스 출력 파싱 실패.
- Qwen3.5 4B: `{"questions": [...]}` 대신 **배열만 출력**해 스키마 불일치 (5건).
- A.X·Mi:dm: JSON 은 나왔으나 스키마 검증 실패.
- Kanana-2-3B: "관 관 관…" 같은 깨진 토큰 반복.

### 5.5 지연

| 모델 | 꼬리질문 중간값 / p90 | 첫 질문 토큰 중간값 | 3초 초과 | 10초 초과 | 질문 풀 중간값 / p90 | 코칭 중간값 |
|---|---|---|---|---|---|---|
| Gemini 3.5 Flash-Lite | **2.04 / 5.82s** | 1.54s | 16.7% | 0% | 4.19 / 4.68s | 3.03s |
| Gemini 3.1 Pro | — | — | — | — | 24.74 / 26.52s | — |
| Gemma 4 31B | 9.16 / 54.28s | 3.18s | 100% | 47.6% | 43.68 / 50.06s | 22.56s |
| Solar Pro 4 | 2.32 / 4.43s | 1.30s | 23.8% | 0% | 16.23 / 21.14s | 5.32s |
| gpt-oss-120b (2048) | 4.25 / 5.54s | 3.91s | 97.6% | 0% | 15.38 / 17.68s | 8.88s |
| Llama 4 Maverick | 1.85 / 2.75s | 1.32s | 0% | 0% | 7.27 / 8.21s | 3.82s |
| Qwen3 4B Instruct | 3.30 / 5.47s | 1.73s | 54.8% | 0% | 31.43 / 39.30s | 12.83s |
| Qwen3.5 4B | 6.37 / 7.56s | 3.97s | 100% | 0% | 41.02 / 51.08s | 18.87s |
| A.X 4.0 Light | 4.26 / 6.41s | 2.08s | 83.3% | 2.4% | 35.08 / 38.43s | 14.86s |
| Mi:dm 2.0 Mini | 1.90 / 3.08s * | — | 14.3% | 0% | 17.24 / 20.80s | 실패 |

\* 전부 DONT_KNOW 로 분류해 질문 스트리밍을 생략한 값이라 실제 속도로 볼 수 없다.
Gemma 4 31B 는 한 호출이 192초 걸린 이상치가 있었다(게이트웨이 경유).

**동시성·콜드스타트 (로컬 + 기준)**

| 모델 | 코칭 15건×동시 5 총시간 | 호출당 p90 | fan-out 중 타 사용자 꼬리질문 | 언로드 후 첫 꼬리질문 | 그다음 호출 |
|---|---|---|---|---|---|
| Gemini 3.5 Flash-Lite | **10.8s** (15/15) | 4.6s | **2.1s** | — | — |
| Mi:dm 2.0 Mini | 99.2s (**0/15**) | — | 33.1s | 7.7s | 1.2s |
| Qwen3 4B Instruct | 224.1s (15/15) | 81.1s | **78.8s** | 10.7s | 2.7s |
| A.X 4.0 Light | 231.4s (15/15) | 84.0s | 81.2s | 12.2s | 2.7s |
| Qwen3.5 4B | 304.7s (15/15) | 110.8s | 111.5s | 15.5s | 6.6s |

### 5.6 GPU 적재 상태

| 모델 | 메모리 (num_ctx 8192) | 적재 |
|---|---|---|
| Qwen3 4B Instruct | 4.2GB | 100% GPU |
| Kanana-2-3B | 3.4GB | 100% GPU |
| Mi:dm 2.0 Mini | 3.6GB | 100% GPU |
| A.X 4.0 Light 7B | 5.3GB | 6% CPU / 94% GPU |
| Qwen3.5 4B (비전 포함) | 6.4GB | 29% CPU / 71% GPU |
| (1차) Qwen2.5 7B | 5.6GB | 8% CPU / 92% GPU |
| (1차) EXAONE 3.5 7.8B | 6.3GB | 19% CPU / 81% GPU |

모델별 실행 전후 적재 상태는 `gpu-placement.log` 에 기록했다. 본 실험(하네스) 구간의 모든 측정은 위 적재 상태에서 이뤄졌다.

---

## 6. 실험 중 발견한 사항

### 6.1 GPU 간헐 인식 실패 → 무경고 CPU 폴백

- Ollama 로그에 `ggml_cuda_init: failed to initialize CUDA: initialization error` 가 기록되며 **0/33 레이어만 GPU 에 올라가고 오류 없이 CPU 로 실행**됐다 (qwen3:4b 계열 약 10배 느려짐).
- 호스트 `nvidia-smi` 는 정상, 컨테이너 내 `/dev/nvidia*` 도 정상이었다. 수 분 뒤 재시도하면 다시 100% GPU 로 올라갔다.
- 발생 시각: 06:52Z, 07:32~07:34Z. 각각 배포(PR #233 06:48Z, PR #234 07:22Z) 후 4분·10분 안. **인과는 확정하지 못했다.**
- 영향: 같은 Ollama 를 쓰는 devlog·devtalk·mcp 도 동일하게 느려질 수 있다. 운영에 로컬 LLM 을 쓰면 `ollama ps` 의 PROCESSOR 감시가 필수다.

### 6.2 기본 문맥 길이 4096 에서 조용한 절단

- 긴 문맥 케이스의 Qwen 토크나이저 기준 프롬프트가 **정확히 4,096 토큰으로 보고**됐다 = 뒷부분이 잘렸다. 오류는 없다.
- 다른 케이스(2,956~3,227 토큰)도 출력까지 합치면 4096 에 근접한다.
- OpenAI 호환 엔드포인트로는 `num_ctx` 를 넘길 수 없어 **`PARAMETER num_ctx 8192` 파생 모델**을 만들어 해결했다 (또는 서버 전역 `OLLAMA_CONTEXT_LENGTH`).

### 6.3 thinking 제어

| 모델 | `think: false` | `reasoning_effort: "none"` | 비고 |
|---|---|---|---|
| qwen3:4b (하이브리드) | 무시됨 (reasoning 에 사고 출력) | **사고 내용이 content 로 새어 나옴** | 사용 불가 → `qwen3:4b-instruct` 사용 |
| qwen3.5:4b | — | 정상 (reasoning 0) | 기본값은 thinking on: 300토큰을 사고에 쓰고 content 빈 값 (이 측정은 CPU 폴백 상태에서 88초) |
| gpt-oss-120b | — | 끌 수 없음 | 운영 `LLM_FLASH_MAX_TOKENS=512` 에서 추론이 토큰을 다 써 **꼬리질문이 빈 문자열**. 2048 에서 정상 |

### 6.4 서빙 호환성

- **Kanana-2-3B GGUF**: GPU 에 정상 적재됐지만 출력이 "관 관 관…", "n n n…" 로 깨졌다. Ollama 0.20.7 의 아키텍처 지원 문제로 추정. 벤치마크 점수는 좋지만 현 서빙 환경에서 사용 불가.
- **Gemma 4 E2B**: pull 시 Ollama 업그레이드를 요구. 공유 컨테이너라 업그레이드하지 않았다.

### 6.5 1차 예비 측정 (하네스 이전, 케이스 1개)

| 모델 | 꼬리질문 | 질문 풀 3개 | 비고 |
|---|---|---|---|
| Gemini 3.5 Flash-Lite | 2.5s | 3.2s | |
| Gemini 3.1 Pro | — | 22s | |
| qwen3:4b-instruct (8K) | 2.0s | 19s | 같은 저가치 질문(결제 키 형식·길이) 반복 |
| qwen2.5:7b (8K) | 4.4s | 34s | |
| exaone3.5:7.8b (8K) | 14.5s | 39s | 복합 질문, 비상업 라이선스 |

예비 측정은 케이스가 1개라 결론에 쓰지 않았고, 본 실험 설계의 근거로만 썼다.

---

## 7. 비용 추정 (게이트웨이를 못 쓸 경우)

세션 1회 모델: 질문 풀 1 + 답변 10(꼬리질문 10) + 피드백(패널 3·종합 1·자기소개 1·코칭 10).
토큰은 운영 로그 평균 — Flash 입력 37.9k / 출력 7.5k, Pro 입력 9.8k / 출력 1.6k. 정가 기준. Pro 의 thinking 토큰은 로그에 없어 **현재 구성 비용은 과소추정**이다.

| 옵션 | 가격 (입력/출력, per 1M) | 세션당 | 1천 세션 | 월 300세션 |
|---|---|---|---|---|
| 현재 구성 Gemini 3.5 Flash-Lite + 3.1 Pro | $0.30/$2.50 + $2.00/$12.00 | $0.069 | $69.0 | $20.7 |
| Gemini 3.5 Flash-Lite 단독 | $0.30/$2.50 | $0.037 | $37.0 | $11.1 |
| Solar Pro 4 | $0.30/$1.20 | $0.025 | $25.2 | $7.6 |
| DeepSeek V4.1 Flash (OpenRouter) | $0.30/$1.20 | $0.025 | $25.2 | $7.6 |
| gpt-oss-120b (Groq) | $0.15/$0.60 | $0.013 | $12.6 | $3.8 |
| Qwen3.8-Flash (Alibaba) | $0.15/$0.47 | $0.011 | $11.4 | $3.4 |
| gpt-oss-120b (DeepInfra) | $0.037/$0.17 | $0.003 | $3.3 | $1.0 |
| Kimi K2.6 (Moonshot) | $0.95/$4.00 | $0.082 | $81.6 | $24.5 |
| Kimi K3 (Moonshot) | $3.00/$15.00 | $0.279 | $279.3 | $83.8 |
| 로컬 (ana-server) | 전기료 | ≈ $0 | ≈ $0 | ≈ $0 — 단, §5 품질·동시성 한계 |

가격 출처는 OpenRouter models API·각 제공사 가격 페이지 (2026-09-17 조회). Gemma 4 31B 의 외부 제공자 가격은 조사하지 않았다.

---

## 8. 종합 평가

| 모델 | 품질 | 형식·채점 | 속도 | 동시성 | 라이선스/경로 | 판정 |
|---|---|---|---|---|---|---|
| **Gemini 3.5 Flash-Lite + 3.1 Pro** | ◎ | ◎ | ◎ | ◎ | 게이트웨이 (학교 키) | **운영 유지** |
| **Gemma 4 31B** | ◎ | ◎ | △ (꼬리질문 9s, p90 54s) | 미측정 | 오픈 가중치 | **Pro 티어 Plan B** |
| **Solar Pro 4** | ○ (정상 답변 ◎, 모름·재설명 △) | ○ | ○ (2.3s) | 미측정 | 비공개 가중치 API | **Flash 티어 Plan B** (프롬프트 보정 전제) |
| gpt-oss-120b | △ | ○ (토큰 상향 시) | △ (4.3s) | 미측정 | 오픈 가중치, 최저가 | 비용 우선 시 후보 |
| Llama 4 Maverick | △ | ○ | ◎ (1.9s) | 미측정 | 오픈 가중치 | 비권장 (깊이 부족) |
| Qwen3 4B Instruct | ✕ | △ | ○ | ✕ | 로컬 | 비권장 |
| Qwen3.5 4B | ✕ | ✕ | △ | ✕ | 로컬 | 비권장 |
| A.X 4.0 Light | ✕ | △ | △ | ✕ | 로컬 | 비권장 |
| Mi:dm 2.0 Mini | ✕ | ✕ (전부 DONT_KNOW) | — | ✕ | 로컬 | 사용 불가 |
| Kanana-2-3B | — | ✕ (출력 깨짐) | — | — | 로컬 | 현 Ollama 에서 사용 불가 |
| Kimi K2.6 / K3 | 미측정 | 미측정 | 미측정 | — | 로컬 불가, API 고가 | 우선순위 낮음 |
| Qwen3.8-Flash, DeepSeek V4.1 Flash | 미측정 | 미측정 | 미측정 | — | API 저가 | 키 확보 시 측정 권장 |

(◎ 우수 · ○ 양호 · △ 조건부 · ✕ 부적합)

### 로컬 LLM 이 이 서버에서 운영 대체가 안 되는 이유

1. **품질**: 로컬 최고가 Claude 판정 2.7 (Gemini 4.0). 모름·재설명·확인형 단답 대응이 특히 약하다.
2. **동시성**: GPU 1장 직렬 처리 → 한 사용자의 피드백 생성 중 다른 사용자의 꼬리질문 79~111초.
3. **채점 신뢰도**: 사실대조 규칙 55~70%, 점수 라벨 9~82%. 리포트 점수가 흔들린다.
4. **운영 리스크**: 무경고 CPU 폴백, 기본 문맥 절단, 모델 언로드 후 콜드스타트(7.7~15.5초, Flash 타임아웃 10초 초과), 공유 GPU 경합.

로컬을 계속 검토하려면 31B급 모델을 올릴 수 있는 VRAM 24GB 이상 GPU 와 배치 서빙(vLLM 등)이 필요하다.

---

## 9. 권고 및 후속 작업

1. **운영 유지**: 현재 게이트웨이 Gemini 구성 그대로.
2. **게이트웨이 지속성 확인**: 학교 키의 유효 기간·사용 한도 확인 (Plan B 시급성 결정).
3. **Plan B 사전 검증**: PR #234 의 티어 오버라이드로 스테이징에서
   - Pro → `google/gemma-4-31B-it` (피드백·질문 풀 지연 허용 범위 확인)
   - Flash → `solar-pro4` + 프롬프트 보정(질문 80자 제한 강화, 기대 신호 누설 금지, 재설명 규칙 강화) 후 하네스 재측정
4. **외부 API 키 확보 시** Qwen3.8-Flash·DeepSeek V4.1 Flash·Kimi K2.6 을 같은 하네스로 측정.
5. **공유 Ollama GPU 폴백 원인 조사** (다른 프로젝트 영향): 배포 시점과의 인과 확인.
6. **운영 코드 개선 후보**(이번 실험에서 드러남):
   - `AnswerEvaluation.specificity/logic` 이 `float` 필수라 확인형 단답에서 프롬프트가 지시한 null 을 주면 평가 전체가 버려진다 → Optional 검토.
   - 질문 풀 JSON 파싱 실패 시 재시도 없음(Gemini Flash-Lite 도 긴 문맥에서 1/2 실패) → 질문 풀 체인에 `with_retry` 검토.

---

## 10. 한계

- 케이스는 합성 22개. 통계적 유의성보다 **뚜렷한 격차** 확인용이다. 상위권(Gemini·Gemma·Solar) 간 0.1~0.3점 차는 오차 범위로 본다.
- LLM 판정은 사람 평가를 대체하지 않는다. 판정자 계열 편향이 확인됐다(§5.1).
- 게이트웨이 모델 지연에는 게이트웨이 오버헤드가 포함되어, 같은 모델을 다른 제공자로 쓰면 다를 수 있다.
- 게이트웨이 모델의 동시성(fan-out)은 Gemini Flash-Lite 만 측정했다.
- 스트리밍 호출의 토큰 사용량 집계는 청크 중복 합산으로 부정확해 분석에서 제외했다.
- 로컬 결과는 GTX 1660 Ti · Ollama 0.20.7 · Q4 양자화 기준이다.

---

## 11. 실험 진행 기록

시각은 git·PR 메타데이터, Ollama 로그, API 응답 타임스탬프, `gpu-placement.log` 로 확인한 값만 적었다.

| 시각 (KST) | 내용 | 근거 |
|---|---|---|
| 15:48 | PR #233 머지 → 배포 시작 | PR 메타데이터 |
| 15:50 | 예비 측정 첫 로컬 호출 (qwen2.5:3b 응답 확인) | API 응답 created |
| 15:52 | 첫 CUDA 초기화 실패 → CPU 폴백 | Ollama 로그 06:52Z |
| 15:54 | 배포가 AI 컨테이너를 재생성 → 진행 중이던 첫 벤치 실행 실패, 재실행 | 컨테이너 created |
| 16:09 | 예비 측정 결과로 PR #234(티어별 엔드포인트) 작성 | git 커밋 |
| 16:22 | PR #234 머지·배포 (기본값 빈 값, 동작 변화 없음) | PR 메타데이터 |
| 16:31 | 판정 모델(claude-opus-5) 게이트웨이 응답 확인 — 이 무렵 게이트웨이 6개 모델 실행 중 | API 응답 created |
| 16:32~16:34 | CUDA 초기화 실패 재발 → 재시도로 복구 확인, 모델별 GPU 적재 확인 로직 추가 | Ollama 로그 07:32Z, 07:34Z |
| 16:43 | 로컬 본 실험 시작 (Qwen3 4B Instruct, 100% GPU) | gpu-placement.log |
| 16:57 | Qwen3.5 4B 시작 (29% CPU) | gpu-placement.log |
| 17:22 | Kanana-2-3B 시작 → 출력 깨짐 확인 | gpu-placement.log |
| 18:00 | Kanana-2-3B 실행 중단, Mi:dm 2.0 Mini 시작 | gpu-placement.log |
| 18:07 | Gemma 4 E2B 파생 모델 생성 실패(Ollama 업그레이드 필요), A.X 4.0 Light 시작 | gpu-placement.log |
| 18:23 | 로컬 실행 완료 | gpu-placement.log |
| 18:23~18:35 | 자동 지표 집계, 확인형 단답 태그 준수 지표 보정, gpt-oss-120b max_tokens 2048 재측정, 블라인드 판정 44건 | 보고서 커밋 이전 |
| 18:35 | 결정용 보고서·하네스 커밋 (PR #235, 18:47 머지) | git 커밋 |

---

## 12. 재현 방법

`stackup-ai` 컨테이너에 `ai/scripts/llm_eval/` 을 복사한 뒤 컨테이너 안에서 실행한다.

```bash
# 1) 로컬 모델은 8K 파생 모델 생성 (호스트)
printf "FROM qwen3:4b-instruct\nPARAMETER num_ctx 8192\n" \
  | docker exec -i ollama sh -c "cat > /tmp/Mf && ollama create stackup-eval-qwen3-4b-instruct-8k -f /tmp/Mf"

# 2) 실행 (컨테이너 안, 작업 디렉터리 llm_eval)
python run_eval.py --label gw-gemini-3.5-flash-lite --model gemini-3.5-flash-lite \
  --reps 3 --latency --out /tmp/eval/gw-gemini-3.5-flash-lite.jsonl
python run_eval.py --label local-qwen3-4b-instruct --model stackup-eval-qwen3-4b-instruct-8k \
  --base-url http://ollama:11434/v1 --api-key ollama --reps 3 --latency \
  --out /tmp/eval/local-qwen3-4b-instruct.jsonl
python run_eval.py --label local-qwen3.5-4b --model stackup-eval-qwen3.5-4b-8k \
  --base-url http://ollama:11434/v1 --api-key ollama --reps 3 --latency \
  --extra-body '{"reasoning_effort": "none"}' --out /tmp/eval/local-qwen3.5-4b.jsonl
python run_eval.py --label gw-gpt-oss-120b-mt2048 --model accounts/fireworks/models/gpt-oss-120b \
  --suites followup --reps 3 --flash-max-tokens 2048 --out /tmp/eval/gw-gpt-oss-120b-mt2048.jsonl

# 3) 분석·판정
python analyze.py /tmp/eval/*.jsonl > auto-metrics-summary.json
python judge.py --exclude local-kanana2-3b --out eval-judge.jsonl /tmp/eval/*.jsonl > eval-judge-table.json
```

---

## 부록 A. 출력 예시

### A.1 사실 불일치 답변 (f-infra-fact-error)

지원자: "HPA 는 메모리 50% 를 기준으로 잡았습니다… 피크 대응은 따로 한 건 없고…" (이력서: CPU 70%, CronJob 사전 스케일아웃)

| 모델 | 꼬리질문 |
|---|---|
| Gemini Flash-Lite | 이력서에는 CPU 70%와 사전 스케일아웃 CronJob을 운영하셨다고 되어 있는데, 답변에서 메모리 50%와 HPA 단독 대응이라고 하신 이유는 무엇인가요? |
| Gemma 4 31B | 이력서에는 CPU 70% 기준과 CronJob을 통한 사전 스케일아웃을 운영하셨다고 적혀 있는데, 답변하신 내용과 차이가 있는 이유가 무엇인가요? |
| Solar Pro 4 | 이력서에 적힌 CPU 70% 기준과 월급날 피크 대비 CronJob 사전 스케일아웃은 어떤 근거로 선택하셨고, 그 사전 스케일아웃이 없었다면 피크 때 어떤 문제가 생겼을 거라고 보시나요? |
| Llama 4 Maverick | 서비스 특성상 메모리를 많이 쓴다고 했는데, 어떤 부분에서 메모리를 많이 사용하나요? (불일치 놓침) |
| Qwen3 4B (로컬) | 메모리 50% 기준을 선택한 이유가 무엇인가요? CPU 70% 기준이 더 일반적이지 않나요? (유도형) |
| A.X 4.0 Light (로컬) | HPA 기준 설정에 대해 구체적으로 설명해 주세요. 메모리 50%를 선택하신 이유가 무엇인지, 그리고 사전 스케일아웃을 CronJob으로 운영한 이유도 함께 말씀해 주시겠어요? (답변과 모순되는 전제) |

### A.2 좋은 답변 (f-strong-backend) — 약한 축 파고들기

| 모델 | 꼬리질문 |
|---|---|
| Gemini Flash-Lite | Outbox 테이블을 폴링하는 릴레이에서 발행 실패가 발생하면 데이터 정합성을 어떻게 보장하시나요? |
| Gemma 4 31B | Outbox 테이블의 데이터를 릴레이가 폴링해 발행할 때, 메시지가 중복 발행될 가능성은 어떻게 해결하셨나요? |
| Solar Pro 4 | Outbox 릴레이가 폴링 중 실패하거나 중복 발행했을 때, 멱등 키만으로는 막을 수 없는 케이스가 생기는데 그 경계는 어떻게 처리하셨나요? |
| Qwen3 4B (로컬) | 결제 키에 유니크 제약을 걸어 두 번째 요청을 돌려주는 부분에서, 그 키의 구체적인 형식은 무엇이었나요? (저가치) |
| Qwen3.5 4B (로컬) | 동일한 트랜잭션 내에서 outbox 테이블에 주문 정보를 기록하신 구체적 SQL 또는 애플리케이션 로직은 무엇인가요? |

### A.3 "모르겠습니다" (f-dont-know-explicit)

| 모델 | 의도 | 후속 문장 |
|---|---|---|
| Gemini Flash-Lite | DONT_KNOW | 그럼 PostgreSQL의 VACUUM이 어떤 역할을 하는지 알고 계신 범위에서 간략히 말씀해 주시겠어요? |
| Gemma 4 31B | DONT_KNOW | 괜찮습니다. 그럼 MVCC의 기본 개념이나… 아는 범위 내에서 설명해 주시겠어요? |
| Solar Pro 4 | DONT_KNOW | …xmin horizon 과 dead tuple 회수 지연, 그리고 테이블 bloat 으로 이어지는 흐름을 다시 설명해 드릴까요? (정답 누설) |
| Qwen3 4B (로컬) | DONT_KNOW | …영향에 대해 잘 모르시는 이유가 무엇인가요? (부적절) |

### A.4 재설명 요청 (f-clarification)

| 모델 | 의도 | 재설명 |
|---|---|---|
| Gemini Flash-Lite | CLARIFICATION | CPU 사용량이 갑자기 높을 때 자동으로 늘려주는 HPA의 기준을 70%로 정한 이유와, 트래픽이 몰리기 전에 미리 서버를 늘려둔 이유를 각각 쉽게 설명해 주시겠어요? |
| Solar Pro 4 | CLARIFICATION | HPA 가 CPU 70% 에 반응하기까지 메트릭 수집·파드 기동 시간이 걸리는데… (기대 신호를 그대로 노출) |
| Qwen3 4B (로컬) | CLARIFICATION | HPA 기준을 CPU 70%로 설정하신 이유와…, 좀 더 쉽게 설명해 주실 수 있나요? (원 질문 반복) |
| Mi:dm 2.0 Mini (로컬) | DONT_KNOW | (오분류 — 운영에서 폐기됨) |

### A.5 코칭 리라이트 (c-weak-backend)

원 답변: "그냥 중복이 안 생기게 잘 처리했고요, 트랜잭션도 잘 관리해서 문제없이 해결됐습니다."

- **Solar Pro 4** — 한 줄 코칭: "두 기법이 각각 어떤 실패 케이스를 막는지 구체적으로 구분해서 말하면, 멱등성과 트랜잭션 경계에 대한 이해를 훨씬 설득력 있게 전달할 수 있습니다."
- **Qwen3 4B (로컬)** — 판정자 지적: "지원자의 실제 답변을 전혀 반영하지 않고 모범 답안으로 완전히 대체함", "모범 답안과 재작성 답안이 거의 동일".
