# 선행 연구 정리 — 로컬 LLM 기반 한국어 AI 모의면접 (논문용)

> 2026-09-17 문헌 조사(arXiv·ACL Anthology·USENIX·ACM DL·KCI·DBpia·Crossref 검색). 모든 문헌은 랜딩 페이지·메타데이터로 존재를 확인했고, arXiv 전용은 preprint 로 표기했다. 인용 전 DBLP 로 게재처 최신화와 "and others" 저자 목록 보완이 필요하다.
> 참고문헌 BibTeX: 각 절 끝의 키를 기준으로 원 조사 기록에서 옮겨 적는다(조사 원문은 세션 기록에 있음).

---

## 1. 연구 위치 한 줄

**LLM 모의면접 시스템(질문 생성·꼬리질문·답변 평가)을 한국어로, 제약된 로컬 하드웨어에서, 품질·지연·동시성·비용을 함께 측정한 연구는 찾지 못했다.** 가장 가까운 선행연구는 Ryu & Jung (2025, 사물인터넷융복합논문지)로, 온프레미스 Llama 3.1 8B 가 한국어 면접 *평가*에서 70B 와 비열등함을 보였으나 실시간 꼬리질문·의도 분류·동시성은 다루지 않았다.

---

## 2. LLM 모의면접·면접 평가 시스템

- **LLM 이전**: MACH(Hoque et al., UbiComp 2013) 가상 에이전트 면접 코칭, 자동 영상면접 채점의 심리측정 타당도(Hickman et al., J. Appl. Psych. 2022), 이력서 기반 질문 생성(EZInterviewer, WSDM 2023).
- **LLM 에이전트**: MockLLM(KDD 2025), Conversate(PACM HCI 2025, 적응형 꼬리질문+대화형 피드백), PolyInterview(preprint 2026, JD+CV 질문·음성 꼬리질문·STAR/KSA 리포트 — StackUp 과 기능이 거의 같지만 로컬 모델·지연 연구 없음). 현장 실험에서 AI 면접 리포트가 최종 면접 통과율을 17.5~20%p 높였다(Aka et al., preprint 2025).
- **국내**: ChatGPT+STT CS 모의면접(전재성 외, 실천공학교육논문지 2024), GPT-4 메타버스 면접(윤채원 외, 한국정보기술학회논문지 2024) — 모두 클라우드 LLM·설문 평가. **Ryu & Jung (2025)** 만 온프레미스 소형 LLM.

## 3. 꼬리질문(Probing) 생성

- 과제 정의: FollowupQG(IJCNLP-AACL 2023), SocratiQ(EACL 2023) — 모델 질문이 사람보다 얕다. 지식그래프로 깊이 보강(COLING 2025).
- LLM: GPT-4o 꼬리질문이 사람 이상, 면접관 실수 유형 가이드 시 더 좋음(IEEE RE 2025). AI 설문 인터뷰어 데이터 품질이 사람과 비슷(LaTeCH-CLfL 2025).
- **Panfilova et al. (Sci. Rep. 2026)**: 오픈 모델 포함 6개 LLM 의 꼬리질문 1,658건을 이진 기준 5개로 전문가 평가(κ 0.67~0.93)하고 **지연을 함께 보고**(GPT-5 9.8초 vs Qwen3 2분 40초). 품질×지연 설계가 가장 가까우나 API·러시아어·동시성 없음.
- 명확화(clarification) 요청은 LLM 평가 맥락(LLM-as-an-Interviewer, Findings ACL 2025)에서만 다뤄졌고, **면접 답변의 "모름/재설명 요청" 의도 분류 연구는 찾지 못했다.**

## 4. 답변 채점·피드백

- LLM-as-a-judge 인간 일치 ~80%, 위치·장황함·자기선호 편향(Zheng et al., NeurIPS D&B 2023).
- 에세이 채점 LLM–인간 일치는 맥락 의존적(65편 체계적 문헌고찰, preprint 2025). 근거 생성 후 소형 채점기로 다중 특성 신뢰도 향상(Findings NAACL 2025).
- **면접 채점**: 더 크고 최신인 LLM 앙상블이 BARS 기준 단일 인간 평가자 이상(Stockdale, Hickman & Liu, J. Appl. Psych. 2026) — **모델 크기 효과가 로컬 소형 모델에서 유지되는지는 미검증.** 다중 에이전트 MMI 채점 QWK 0.62(preprint 2026).
- STAR 구조·이력서 일치 채점을 인간 평가와 대조한 연구는 찾지 못했다.

## 5. 근거 기반 생성·환각

- RAG(NeurIPS 2020), RAGAS(EACL Demos 2024), FActScore(EMNLP 2023, 오픈 모델 사실성 낮음).
- HR 과제에서 GPT-4 증류로 소형 모델이 교사와 비슷(preprint 2023).
- **채용 파이프라인 날조**: 이력서 편집→질문→피드백 다단계에서 96.7% 가 근거 없는 주장 포함, 가드레일 후에도 50%(Takano, preprint 2026).
- KMMLU(NAACL 2025): 한국어 전문 지식에서 최상위 모델도 60% 미만(발표 당시).

## 6. 대화 지연 기준

- 대화 턴 간격은 한국어 포함 10개 언어에서 ~200ms 로 수렴(Stivers et al., PNAS 2009; Levinson & Torreira 2015). 음성 대화 시스템 턴테이킹 리뷰(Skantze 2021).
- LLM 에이전트 허용 범위: VR 에서 4초 초과 시 경험 저하, 필러가 완화(CUI 2025). 지식 과제에서는 2초 TTFT 가 9~20초보다 덜 사려깊게 평가됨(CHI 2026) → **면접관의 "생각하는 시간"은 과제 의존적으로 허용될 수 있음.**
- 실서비스 AI 면접 30만 건에서 STT×LLM×TTS 조합 비교, 객관 품질과 사용자 만족 상관 약함(preprint 2025).
- 소비자 GPU 의 양자화 Qwen3-30B: 저부하는 클라우드급, 동시 사용자 증가 시 저하(Khalil et al., preprint 2025).

## 7. 서빙·하드웨어 병목 (우리 측정의 설명)

| 우리 관측 | 문헌 설명 |
|---|---|
| 프롬프트 처리 ~240 tok/s 가 설정 무관 | 프리필은 연산(FLOPs) 제약, 이미 포화(Sarathi preprint 2023; Sarathi-Serve OSDI 2024; Splitwise ISCA 2024) |
| 생성 ~60 tok/s | 디코드는 메모리 대역폭 제약: 288 GB/s ÷ 2.5GB ≈ 115 tok/s 상한의 절반(roofline, preprint 2024) |
| 병렬 4슬롯 처리량 +16% | 연속 배칭(Orca OSDI 2022, vLLM SOSP 2023)의 이득은 디코드 위주·연산 여유 가정. 소형 모델·단일 가속기에서 조기 포화(Recasens et al., IEEE CLOUD 2025) |
| 26B-A4B MoE CPU 오프로드 사용 가능 | 전문가를 CPU 에서 실행(Fiddler ICLR 2025). 엣지에서는 총 파라미터가 비용을 좌우(preprint 2026) |
| 공유 시스템 프롬프트 캐시로 TTFT 단축 | 프리픽스 캐시 RadixAttention(SGLang NeurIPS 2024) |

## 8. 양자화

- 4비트 GPTQ(ICLR 2023)·AWQ(MLSys 2024), 2~3비트는 벡터/격자 코드북(QuIP# ICML 2024, AQLM ICML 2024) 필요.
- **양자화된 큰 모델 > FP16 작은 모델**, 단 지시 따르기 손상·소형 모델 취약(Lee et al., IJCAI 2025). 한국어 모델에서도 같은 경향(노연수·김철진, 한국산학기술학회논문지 2024 — 객관식 과제만).
- **비영어 손상이 더 큼**: 비라틴 문자·인간 평가에서 최대(Marchisio et al., Findings EMNLP 2024), 원거리 언어 2비트 붕괴(preprint 2025), INT3 소형 모델 비영어 perplexity 손상 2~4배(preprint 2026), 다국어 보정 데이터가 개선(EACL 2026).
- MoE 는 전문가별 민감도가 달라 균일 저비트가 비최적(QuantMoE-Bench, preprint 2024).
- **재양자화(Q4→Q2) 연구는 찾지 못했다** — Kanana-2-30B 붕괴가 새 관찰.

## 9. 구조화 출력(JSON 강제)

- 문법 강제 디코딩(Outlines preprint 2023, XGrammar MLSys 2025, JSONSchemaBench preprint 2025).
- 형식 제약이 추론 저하(Let Me Speak Freely?, preprint 2024). 손실 대부분은 디코더 마스크가 아니라 **프롬프트의 형식 지시**이며 자유 추론 후 포맷으로 회복(The Format Tax, preprint 2026). **여유 용량이 적은 소형 모델이 더 큰 비용**(preprint 2026). 7~9B 모델 구조화 출력 신뢰도(preprint 2026).

## 10. 소형 vs 대형·라우팅·증류

- 근거 증류로 소형 모델이 대형 모델 능가(Findings ACL 2023), QLoRA(NeurIPS 2023).
- 캐스케이드·라우팅: FrugalGPT(preprint 2023), Hybrid LLM(ICLR 2024), RouteLLM(preprint 2024) → "로컬 기본 + 어려운 요청만 클라우드" 설계 근거.

## 11. 한국어 평가

KoBEST, KMMLU(NAACL 2025), HAE-RAE(LREC-COLING 2024), CLIcK, Open Ko-LLM Leaderboard 1·2(ACL 2024, NAACL 2025 Industry — Ko-IFEval 포함), KoMT-Bench·LogicKor(EXAONE 3.0 보고서). 한국어 판정 LLM 은 사실 오류·문화 오류·**잘못된 언어 답변**을 놓친다(KUDGE, preprint 2024). 토크나이저 불공정(NeurIPS 2023), A.X K1 은 한국어 토큰 수가 Qwen3 대비 ~42% 적음(preprint 2026) — **프리필이 병목인 환경에서 토크나이저 효율이 곧 지연.**

## 12. 평가 방법론 (판정·통계·지연 측정)

- **판정 편향**: 자기 인식 → 자기 선호(Panickssery et al., NeurIPS 2024), 저 perplexity 친숙성(preprint 2024), 일부는 실제 품질 차이(preprint 2025). 위치 편향(ACL 2024; IJCNLP-AACL 2025), 길이 편향 보정(Length-Controlled AlpacaEval, preprint 2024), 권위(가짜 인용) 편향(EMNLP 2024), 12종 편향 목록(CALM, ICLR 2025).
- **완화**: 다른 계열 판정자 패널(PoLL, preprint 2024), 루브릭 기반 판정(Prometheus 2, EMNLP 2024), 근거 먼저 생성(G-Eval, EMNLP 2023), 순서 회전.
- **인간 평가**: 보고 지침(van der Lee et al., CSL 2021), Krippendorff α ≥ .80(.667 잠정; Hayes & Krippendorff 2007), ICC 절대 일치(Koo & Li 2016), LLM 판정의 인간 대체 검정 alt-test(ACL 2025: 평가자 3+, 문항 50~100), Bradley–Terry(Chatbot Arena, ICML 2024).
- **통계**: 유의성 검정 지침(Dror et al., ACL 2018), 소규모 평가의 낮은 검정력(Card et al., EMNLP 2020), 평가 오차 막대·클러스터 표준오차(Miller, preprint 2024), 수백 개 미만에서 CLT 금지(Bowyer et al., ICML 2025), 다중 비교 Holm(1979)·BH(1995), Cliff's δ(1993), 최대 무작위효과 구조(Barr et al., JML 2013), temperature 0 비결정성(preprint 2024).
- **지연 측정**: MLPerf Inference(ISCA 2020: 최소 60초 실행, 서버 시나리오 5회, Poisson 도착), 개방형 vs 폐쇄형 부하(NSDI 2006), 벤치마크 반복 설계(ISMM 2013), TTFT/TPOT/goodput(DistServe OSDI 2024), 토큰 간 지연 분포(Etalon, preprint 2024).
- **구성 타당도**: LLM 벤치마크 445개 검토에서 구성-측정 연결 약함(NeurIPS D&B 2025), 합성 데이터 위험(preprint 2024).

---

## 13. 논문이 채울 수 있는 연구 공백

1. **한국어 LLM 면접관의 품질 × 지연 × 동시성 × 비용 결합 측정** — 제약된 로컬 하드웨어에서 스트리밍 꼬리질문의 TTFT 를 N 동시 세션 조건에서 대화 지연 기준(~0.2~4초)과 대조한 연구 없음.
2. **면접 답변 의도(모름·재설명 요청) 분류** — STT 전사 답변에서 소형 모델의 의도 인식 신뢰도와 오분류가 꼬리질문에 미치는 영향.
3. **소형·로컬 모델의 한국어 면접 채점 타당도** — 구체성·논리·STAR·이력서 일치 축에서 인간 평가 대조.
4. **한국어 생성 품질의 GGUF 저비트(2~3비트)·imatrix·재양자화 영향** — 기존 한국어 양자화 연구는 BitsAndBytes 4/8비트·객관식뿐.
5. **Turing 세대·전력 제한 노트북 GPU** 의 프리필 한계와 배칭 무효 영역 실측.
6. **형식 강제(JSON)의 한국어 소형 모델 비용**과 2단계(자유 생성→포맷) 회복 여부.
7. **LLM 판정자의 계열 편향이 한국어 면접 과제에서 얼마나 되는지**, 품질과 분리한 추정.
8. **저사용량 교육 서비스의 TCO** (한국어 토크나이저 비용·에너지 포함).
