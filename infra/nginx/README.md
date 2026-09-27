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
| TLS | TLSv1·1.1 수락 | **TLSv1.2/1.3** (전역 `nginx.conf` 도 변경) |
| 보안 헤더 | 전무 | HSTS·nosniff·X-Frame-Options·Referrer-Policy·Permissions-Policy |
| rate limit | 없음 | API 20r/s(burst 60), 인증 2r/s(burst 20) |
| 버전 노출 | `nginx/1.18.0 (Ubuntu)` | `server_tokens off` |

바꾸지 않은 것(이미 올바름): `client_max_body_size 25m`(백엔드 25MB 상한과 일치),
`/realtime/` 의 `proxy_buffering off` + 1h 타임아웃(SSE·WS 가 이게 없으면 깨진다).

### TLS — server 블록만으로는 안 됐다

처음엔 이 사이트의 `server` 블록에만 `ssl_protocols TLSv1.2 TLSv1.3;` 을 넣었다. **효과가 없었다.**

`ssl_protocols` 는 **SNI 이전**에 결정되므로, 같은 `443` 소켓을 여러 `server` 가 공유하면
**default server 의 값이 전체에 적용**된다. 이 호스트는 443 에 4개 사이트가 물려 있어
전역 `/etc/nginx/nginx.conf` 를 바꿔야 했다:

```diff
-	ssl_protocols TLSv1 TLSv1.1 TLSv1.2 TLSv1.3;   # Dropping SSLv3, ref: POODLE
+	ssl_protocols TLSv1.2 TLSv1.3;
```

백업: `/etc/nginx/nginx.conf.bak-20260927-132844`. **이 변경은 5개 사이트 전부에 적용된다.**
사이트 블록의 선언도 그대로 남겨 뒀다 — 전역이 되돌아가도 의도가 설정에 남는다.

### TLS 버전 지원 여부를 확인하는 법 (틀리기 쉽다)

`openssl s_client` 출력의 `Protocol : TLSv1` 줄은 **클라이언트가 시도한** 버전이라
**실패해도 찍힌다.** 이걸 "수락"으로 읽어서 두 번 오판했다. 올바른 신호는 협상된 암호다:

```bash
out=$(echo | openssl s_client -connect stack-up.shop:443 -servername stack-up.shop         -tls1 -cipher 'DEFAULT@SECLEVEL=0' 2>&1)
echo "$out" | grep -q "Cipher is (NONE)" && echo "거절" || echo "수락"
```

`Cipher is (NONE)` + `alert number 70`(protocol_version) = 서버가 거절한 것이다.
또한 최신 OpenSSL(3.x)은 기본적으로 TLS 1.0/1.1 을 **제안조차 하지 않으므로**
`-cipher 'DEFAULT@SECLEVEL=0'` 없이 테스트하면 서버 설정과 무관하게 항상 실패한다.

### `/ai/` 를 지운 근거

- 프론트 운영 번들에 `/ai/` 참조 **0건**
- RealTime 은 도커 네트워크로 직접 연결(`REALTIME_AI_WS_URL=ws://ai:8000/internal/voice/stream`)
- `ai/CLAUDE.md` 가 이 서버를 "내부 통신만, 사용자 인증 없음"으로 설계했다고 명시

즉 순수한 잉여 노출이었다. 나중에 외부에서 AI 서버에 직접 붙어야 한다면 인증을 먼저 설계한다.
