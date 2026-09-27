# nginx 설정

2026-09-27 이전까지 이 설정은 **서버에만** 있었다. 레포에 없으니 누가 언제 무엇을
바꿨는지 추적할 수 없었고, 서버가 날아가면 복원할 근거도 없었다. 이 디렉토리가 원본이다.

| 파일 | 설치 위치 |
|---|---|
| `stack-up.shop.conf` | `/etc/nginx/sites-available/stack-up.shop` (→ `sites-enabled` 심볼릭 링크) |
| `conf.d-stackup-ratelimit.conf` | `/etc/nginx/conf.d/stackup-ratelimit.conf` |
| `stack-up.shop.conf.orig` | 2026-09-27 하드닝 이전 원본 (참고용, 설치 안 함) |

## 배포는 자동이 아니다

`deploy-app.yml` 은 레포를 `~/stackup` 으로 rsync 할 뿐 `/etc/nginx` 는 건드리지 않는다
(sudo 가 필요하고, nginx 는 다른 사이트와 공유되기 때문). **수동으로 적용한다:**

```bash
sudo cp ~/stackup/infra/nginx/conf.d-stackup-ratelimit.conf /etc/nginx/conf.d/stackup-ratelimit.conf
sudo cp ~/stackup/infra/nginx/stack-up.shop.conf /etc/nginx/sites-available/stack-up.shop
sudo nginx -t            # 반드시 먼저. 실패하면 reload 하지 않는다
sudo systemctl reload nginx
```

바꾸기 전 항상 백업하고, `nginx -t` 통과 후에만 reload 한다.

## 이 호스트는 nginx 를 공유한다

`/etc/nginx/nginx.conf` 는 `beontteuk.com`, `udangtang.site`, `udangtangames.cloud`,
`warurulab.site` 와 공유된다. **전역(http 컨텍스트) 설정을 고치면 남의 사이트가 같이 바뀐다.**
그래서 하드닝을 전부 `server` 블록 안으로 한정했다 — `ssl_protocols`, `server_tokens`,
보안 헤더는 server 컨텍스트에서도 재정의된다. rate limit zone 만 http 컨텍스트가 필요해
`conf.d` 로 뺐다(zone 선언은 메모리만 잡고 제한은 `limit_req` 를 붙인 곳에만 걸린다).

## 2026-09-27 하드닝에서 바꾼 것

| 항목 | 이전 | 이후 |
|---|---|---|
| `/ai/` 프록시 | AI 서버가 인터넷에 공개(`/ai/docs`·`/ai/redoc`·`/ai/openapi.json` 포함) | **제거** — 쓰는 곳이 없었다 |
| TLS | 전역 설정이 TLSv1·1.1 허용, 서버가 실제로 수락 | 이 사이트만 **TLSv1.2/1.3** |
| 보안 헤더 | 전무 | HSTS·nosniff·X-Frame-Options·Referrer-Policy·Permissions-Policy |
| rate limit | 없음 | API 20r/s(burst 60), 인증 2r/s(burst 20) |
| 버전 노출 | `nginx/1.18.0 (Ubuntu)` | `server_tokens off` |

바꾸지 않은 것(이미 올바름): `client_max_body_size 25m`(백엔드 25MB 상한과 일치),
`/realtime/` 의 `proxy_buffering off` + 1h 타임아웃(SSE·WS 가 이게 없으면 깨진다).

### `/ai/` 를 지운 근거

- 프론트 운영 번들에 `/ai/` 참조 **0건**
- RealTime 은 도커 네트워크로 직접 연결(`REALTIME_AI_WS_URL=ws://ai:8000/internal/voice/stream`)
- `ai/CLAUDE.md` 가 이 서버를 "내부 통신만, 사용자 인증 없음"으로 설계했다고 명시

즉 순수한 잉여 노출이었다. 나중에 외부에서 AI 서버에 직접 붙어야 한다면 인증을 먼저 설계한다.
