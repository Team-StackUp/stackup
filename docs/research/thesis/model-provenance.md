# 모델 파일 출처와 보관 상태

> 실험에 쓴 GGUF 파일의 정확한 출처. 서버 디스크 용량 때문에 일부 파일은 실험 종료 후 삭제했으므로, 재현하려면 아래 명령으로 같은 파일을 다시 받는다.
> 측정 결과와 모델 출력 원자료는 모두 레포에 남아 있어, 파일이 없어도 분석은 재현된다.

## 1. 내려받기

```bash
dl() { curl -sSL --retry 5 -C - -o "$2" "https://huggingface.co/$1/resolve/main/$2"; }
```

| 파일 | 저장소 | 용도 | 서버 보관 |
|---|---|---|---|
| `Qwen3-4B-Instruct-2507-{F16,Q8_0,Q6_K,Q5_K_M,Q4_K_M,Q3_K_M,UD-Q2_K_XL}.gguf` | `unsloth/Qwen3-4B-Instruct-2507-GGUF` | 비트 곡선·KL 기준 | 보관 |
| `gemma-4-E4B-it-{Q8_0,Q6_K,Q5_K_M,Q4_K_M,Q3_K_M}.gguf` | `unsloth/gemma-4-E4B-it-GGUF` | 비트 곡선 교차 확인 | 보관 |
| `gemma-4-E2B-it-Q4_K_M.gguf` | `unsloth/gemma-4-E2B-it-GGUF` | 지연·동시성 실험 | 보관 |
| `gemma-4-26B-A4B-it-{UD-IQ3_S,UD-IQ2_M,UD-Q3_K_M}.gguf` | `unsloth/gemma-4-26B-A4B-it-GGUF` | 중형 MoE 품질 하한선 | **삭제** |
| `Qwen3.6-35B-A3B-UD-Q2_K_XL.gguf` | `unsloth/Qwen3.6-35B-A3B-GGUF` | 중형 MoE 품질 하한선 | **삭제** |
| `gpt-oss-20b-MXFP4.gguf` | `ggml-org/gpt-oss-20b-GGUF` | 중형 MoE 품질 하한선 | **삭제** |
| `kanana-2-30b-a3b-instruct-2601-Q4_K_M.gguf` | `deul/Kanana-2-30b-a3b-instruct-2601-GGUF` | 한국어 특화 30B (재양자화 실패) | **삭제** |

## 2. 직접 만든 파일

원본 F16 에서 만들었다. 보정 텍스트(`ko_calib.txt`)는 이 레포 문서에서 뽑았고 평가 텍스트(`ko_eval.txt`)와 분리했다.

```bash
llama-imatrix   -m Qwen3-4B-Instruct-2507-F16.gguf -f ko_calib.txt -o ko.imatrix -c 512 --chunks 40 -t 5
llama-quantize                          F16.gguf Qwen3-4B-direct-Q2_K.gguf      Q2_K     5
llama-quantize --allow-requantize   Q4_K_M.gguf Qwen3-4B-requant-Q4toQ2_K.gguf Q2_K     5
llama-quantize --imatrix ko.imatrix     F16.gguf Qwen3-4B-koimat-Q2_K.gguf      Q2_K     5
llama-quantize --imatrix ko.imatrix     F16.gguf Qwen3-4B-koimat-IQ2_XXS.gguf   IQ2_XXS  5
```

이미지: `ghcr.io/ggml-org/llama.cpp:full`(양자화·KL), `:server-cuda`(서빙). 2026-09-17 빌드.

## 3. 삭제한 파일에 대한 판단

| 파일 | 왜 지웠나 | 잃은 것 |
|---|---|---|
| 26B-A4B 3종 (32.4 GB) | 전문가 계층을 CPU 로 내보내야 해 꼬리질문 지연이 8초대로 고정. 대화형 면접 기준 미달이 확정됐다 | 없음. 품질·지연 측정치와 출력 원자료는 `quant/` 에 보관 |
| Qwen3.6-35B-A3B (11.7 GB) | 위와 같은 이유로 서비스 불가 판정 | 없음. 1라운드 결과 보관 |
| gpt-oss-20b (11.5 GB) | 위와 같음 | 없음 |
| kanana-2-30b Q4_K_M (17.7 GB) | 6 GB VRAM 에서 실행 불가. 2비트 재양자화본은 출력이 깨져 측정 중단(`*-INVALID-broken-requant.jsonl`) | 없음. 재양자화 실패 사례는 기록됨 |

삭제 시점 2026-09-18. 서버 `/home` 이 98% 로 차 있었고, 같은 디스크에 배포용 GitHub Actions 러너가 있어 배포 실패 위험이 있었다.
