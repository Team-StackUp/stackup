# LLM 후보 평가 하네스

운영 체인(질문 풀 생성 · 스트리밍 꼬리질문 · 답변 코칭)을 그대로 태워 후보 모델을 비교한다.
케이스는 합성 데이터이며, 입력 크기는 운영 `ai_request_logs` 분포(질문 풀 입력 p90 4.7k 토큰 등)에 맞췄다.

## 구성

| 파일 | 역할 |
|---|---|
| `cases.py` | 라벨이 붙은 케이스: 질문 풀 5개(긴 문맥 p90 포함), 꼬리질문 14개(강한·약한·모름·재설명·확인형·사실 불일치 등), 코칭 3개 |
| `run_eval.py` | 후보 1개를 돌려 원시 결과 JSONL 저장. `--latency` 는 콜드스타트, 코칭 15건×동시 5, 그 도중 꼬리질문 지연 측정 |
| `analyze.py` | 규칙 기반 자동 지표 (태그 준수, 의도 정확도, 점수 라벨·사실대조 규칙, 근거 인용 검증, 중복, 외국 문자, 지연) |
| `judge.py` | 블라인드 비교 채점. 판정 모델 2개(gemini-3.1-pro-preview, claude-opus-5)로 자기 계열 선호 편향 상쇄 |

## 실행 (stackup-ai 컨테이너 안)

```bash
# 게이트웨이 모델 (컨테이너 env 의 LLM_BASE_URL/LLM_API_KEY 사용)
python run_eval.py --label gw-flash-lite --model gemini-3.5-flash-lite --latency --out /tmp/eval/gw-flash-lite.jsonl

# 로컬 Ollama (num_ctx 8192 파생 모델 필수 — 기본 4096 은 질문 풀 프롬프트를 조용히 자른다)
python run_eval.py --label local-x --base-url http://ollama:11434/v1 --api-key ollama \
  --model <8k 파생 모델> --latency --out /tmp/eval/local-x.jsonl
# thinking 기본 on 모델(qwen3.5, gemma4)은 --extra-body '{"reasoning_effort": "none"}'

python analyze.py /tmp/eval/*.jsonl > summary.json
python judge.py --out judge.jsonl /tmp/eval/*.jsonl > judge-table.json
```

주의: reasoning 을 끌 수 없는 모델(gpt-oss)은 운영 `LLM_FLASH_MAX_TOKENS=512` 에서 추론이 토큰을 다 써
꼬리질문이 비어 나온다. 공정 비교가 필요하면 `--flash-max-tokens 2048`.
