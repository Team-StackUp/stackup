# 논문용 실증 연구 자료

> 가제: **제약된 로컬 하드웨어에서 한국어 LLM 면접관의 품질·지연·동시성 트레이드오프 — 클라우드 LLM 대체 가능성에 대한 실증 연구**
> 대상 시스템은 StackUp 이며, 운영 프롬프트와 실제 부하 특성을 그대로 사용한다.

## 문서

| 문서 | 내용 |
|---|---|
| [`outline.md`](./outline.md) | 논문 목차 초안과 장별 자료 대응표 (가장 먼저 볼 것) |
| [`research-design.md`](./research-design.md) | 연구 질문 RQ1~RQ7, 가설, 분석 계획, 대체 가능성 판정 기준 |
| [`related-work.md`](./related-work.md) | 선행연구 정리와 연구 공백 |
| [`quantization-study.md`](./quantization-study.md) | RQ2 — 비트 폭·한국어 보정 행렬·재양자화 |
| [`prefill-scaling.md`](./prefill-scaling.md) | RQ3·RQ5 — 입력 길이와 첫 응답 시간, 토크나이저 효율의 지연 환산 |
| [`latency-experiment.md`](./latency-experiment.md) | RQ3 — 개방형 Poisson 부하, 프리픽스 캐시, 처리 용량 경계, 에너지 |
| [`tokenizer-efficiency.md`](./tokenizer-efficiency.md) | RQ5 — 한국어 토크나이저 효율 |
| [`judge-panel-analysis.md`](./judge-panel-analysis.md) | RQ6 — 3계열 판정자 패널, 일치도, 같은 계열 편향 |
| [`label-verification.md`](./label-verification.md) | 정답 라벨 독립 검증 (LLM 2차 주석자) |
| [`stats/round1-stats.md`](./stats/round1-stats.md), [`stats/round2-stats.md`](./stats/round2-stats.md) | 1·2라운드 데이터 통계 재분석 |
| [`human-eval/README.md`](./human-eval/README.md) | 인간 평가 키트 (라벨 시트·루브릭·절차) |
| [`model-provenance.md`](./model-provenance.md) | 모델 파일 출처·직접 만든 양자화본 명령·삭제 기록 |

## 원자료

| 경로 | 내용 |
|---|---|
| `quant/kld/` | 양자화본과 원본의 분포 차이 측정 원문 |
| `quant/raw/`, `quant/analysis/` | 양자화 조건별 모델 출력과 자동 지표 |
| `judges/` | 판정자 원 응답과 패널 점수 |
| `latency/` | 개방형 부하 요청별 기록, GPU 전력 원격 측정 |
| `prefill/` | 입력 길이별 첫 토큰 지연 원자료 (모델 3종 × 6단계 × 5회) |
| `human-eval/` | 라벨 시트와 2차 주석자 응답 |

## 재현

평가 하네스는 [`ai/scripts/llm_eval/`](../../../ai/scripts/llm_eval/README.md) 에 있다. 각 문서 머리말에 사용한 명령과 원자료 경로를 적어 두었다.
