FROM python:3.13-slim

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /app

COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev

# 웹 이력서(JS 렌더링 SPA) 폴백용 헤드리스 chromium + 시스템 의존성.
RUN uv run playwright install --with-deps chromium

COPY src/ src/

EXPOSE 38030

CMD ["uv", "run", "uvicorn", "ai_server.main:app", "--host", "0.0.0.0", "--port", "38030"]
