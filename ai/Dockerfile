FROM python:3.13-slim

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /app

COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev

# 웹 이력서(JS 렌더링 SPA) 폴백용 헤드리스 chromium + 시스템 의존성.
# 빌드 호스트에 IPv6 경로가 없어 cdn.playwright.dev 로의 IPv6 연결이 간헐적으로
# 실패(ENETUNREACH)해 배포가 죽던 문제 → Node DNS 를 IPv4 우선으로 두고 재시도한다.
ENV NODE_OPTIONS=--dns-result-order=ipv4first
RUN for i in 1 2 3 4 5; do \
      uv run playwright install --with-deps chromium && exit 0; \
      echo "playwright install attempt $i failed; retrying in 10s" >&2; \
      sleep 10; \
    done; \
    echo "playwright install failed after 5 attempts" >&2; exit 1

COPY src/ src/

EXPOSE 38030

CMD ["uv", "run", "uvicorn", "ai_server.main:app", "--host", "0.0.0.0", "--port", "38030"]
