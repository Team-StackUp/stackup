from __future__ import annotations

import base64
from dataclasses import dataclass

import httpx
import structlog

from ai_server.analyzer.sources.base import ExtractedSource, SourceExtractor

log = structlog.get_logger(__name__)


# 프로젝트 설정 파일을 먼저 읽는다
_PRIORITY_FILES: tuple[str, ...] = (
    "package.json",
    "pyproject.toml",
    "requirements.txt",
    "go.mod",
    "build.gradle",
    "build.gradle.kts",
    "pom.xml",
    "Cargo.toml",
    "composer.json",
    "Gemfile",
    "Dockerfile",
)

_TEXT_EXTENSIONS: frozenset[str] = frozenset(
    {
        ".py",
        ".ts",
        ".tsx",
        ".js",
        ".jsx",
        ".go",
        ".java",
        ".kt",
        ".rs",
        ".rb",
        ".php",
        ".cs",
        ".swift",
        ".md",
        ".yaml",
        ".yml",
        ".toml",
        ".json",
        ".sh",
    }
)


class RepositoryFetchError(Exception):
    def __init__(self, *, code: str, message: str, retriable: bool) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retriable = retriable


@dataclass(frozen=True)
class ContributionInfo:
    login: str
    commit_count: int
    authored_files: list[str]


@dataclass(frozen=True)
class _RepoConfig:
    api_base_url: str
    fallback_token: str
    max_files: int
    max_file_bytes: int
    timeout_sec: float
    max_contrib_commits: int


# 리드미, 주요 소스를 읽는다
class GitHubRepoSourceExtractor(SourceExtractor):
    def __init__(
        self,
        *,
        api_base_url: str,
        fallback_token: str = "",
        max_files: int = 8,
        max_file_bytes: int = 50_000,
        timeout_sec: float = 30.0,
        max_contrib_commits: int = 15,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        self._cfg = _RepoConfig(
            api_base_url=api_base_url.rstrip("/"),
            fallback_token=fallback_token,
            max_files=max_files,
            max_file_bytes=max_file_bytes,
            timeout_sec=timeout_sec,
            max_contrib_commits=max_contrib_commits,
        )
        self._client = client

    async def extract(
        self,
        locator: str,
        *,
        access_token: str | None = None,
    ) -> ExtractedSource:
        """`locator`는 "owner/repo" 형태의 full name.

        `access_token`이 주어지면 그 token으로 호출(권장 — Core에서 받은 값).
        None이면 fallback_token(public repo용)으로 호출.
        """
        owner_repo = locator.strip().strip("/")
        if "/" not in owner_repo:
            raise RepositoryFetchError(
                code="INVALID_REPO_LOCATOR",
                message=f"locator must be 'owner/repo', got: {locator!r}",
                retriable=False,
            )

        effective_token = access_token or self._cfg.fallback_token
        # 기여도 분석은 지원자 본인 token 이 있을 때만 의미 있음(GET /user = 지원자).
        has_user_token = bool(access_token)

        if self._client is not None:
            return await self._extract_with_client(
                self._client, owner_repo, has_user_token=has_user_token
            )

        async with self._build_client(effective_token) as client:
            return await self._extract_with_client(
                client, owner_repo, has_user_token=has_user_token
            )

    def _build_client(self, token: str) -> httpx.AsyncClient:
        headers = {
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        if token:
            headers["Authorization"] = f"Bearer {token}"
        return httpx.AsyncClient(
            base_url=self._cfg.api_base_url,
            headers=headers,
            timeout=self._cfg.timeout_sec,
        )

    async def _extract_with_client(
        self,
        client: httpx.AsyncClient,
        owner_repo: str,
        *,
        has_user_token: bool = False,
    ) -> ExtractedSource:
        repo_info = await self._fetch_json(client, f"/repos/{owner_repo}")
        default_branch = repo_info.get("default_branch") or "main"

        contribution: ContributionInfo | None = None
        if has_user_token:
            contribution = await self._fetch_contribution(
                client, owner_repo, default_branch
            )

        readme_text = await self._fetch_readme(client, owner_repo)
        tree_paths, truncated = await self._fetch_tree(
            client, owner_repo, default_branch
        )
        authored = contribution.authored_files if contribution else []
        picked = _select_files(tree_paths, self._cfg.max_files, prioritized=authored)
        snippets = await self._fetch_snippets(
            client, owner_repo, default_branch, picked
        )

        parts: list[str] = []
        parts.append(f"# {owner_repo} (branch: {default_branch})")
        if desc := repo_info.get("description"):
            parts.append(f"\n> {desc}")

        # 다중 기여자 레포에서 "지원자가 실제 쓴 코드" 를 구분하기 위한 기여 요약.
        if contribution and contribution.commit_count:
            parts.append("\n## 지원자 기여\n")
            parts.append(
                f"- 지원자(@{contribution.login}) 커밋 {contribution.commit_count}개"
            )
            if contribution.authored_files:
                top = contribution.authored_files[:10]
                parts.append("- 주요 작성 파일:\n" + "\n".join(f"  - {p}" for p in top))

        if readme_text:
            parts.append("\n## README\n")
            parts.append(readme_text)

        parts.append("\n## File tree (truncated)\n")
        parts.append("\n".join(f"- {p}" for p in tree_paths[:200]))
        if truncated:
            parts.append("\n(tree truncated by GitHub API)")

        if snippets:
            parts.append("\n## Sampled sources\n")
            for path, body in snippets:
                parts.append(f"\n### `{path}`\n```\n{body}\n```")

        text = "\n".join(parts).strip()
        return ExtractedSource(
            text=text,
            source_type="REPOSITORY",
            metadata={
                "locator": owner_repo,
                "default_branch": default_branch,
                "tree_size": len(tree_paths),
                "tree_truncated": truncated,
                "sampled_files": [p for p, _ in snippets],
                "contributor_login": contribution.login if contribution else None,
                "contrib_commit_count": (
                    contribution.commit_count if contribution else 0
                ),
            },
        )

    # 지원자 token 으로 GET /user → login 확보 후, 그 login 이 author 인 커밋을
    # 집계해 기여 파일을 추린다. 실패(권한/rate limit/공개레포)는 None 으로 graceful.
    async def _fetch_contribution(
        self,
        client: httpx.AsyncClient,
        owner_repo: str,
        branch: str,
    ) -> ContributionInfo | None:
        try:
            me = await self._fetch_json(client, "/user")
        except RepositoryFetchError as err:
            log.warning("repo.contrib.user_skip", code=err.code)
            return None
        login = me.get("login") if isinstance(me, dict) else None
        if not login:
            return None

        try:
            commits = await self._fetch_json(
                client,
                f"/repos/{owner_repo}/commits"
                f"?author={login}&sha={branch}&per_page=100",
            )
        except RepositoryFetchError as err:
            log.warning("repo.contrib.commits_skip", code=err.code)
            return None
        if not isinstance(commits, list) or not commits:
            return ContributionInfo(login=login, commit_count=0, authored_files=[])

        file_counter: dict[str, int] = {}
        for commit in commits[: self._cfg.max_contrib_commits]:
            sha = commit.get("sha") if isinstance(commit, dict) else None
            if not sha:
                continue
            try:
                detail = await self._fetch_json(
                    client, f"/repos/{owner_repo}/commits/{sha}"
                )
            except RepositoryFetchError:
                continue
            files = detail.get("files") if isinstance(detail, dict) else None
            for f in files or []:
                path = f.get("filename") if isinstance(f, dict) else None
                if path:
                    file_counter[path] = file_counter.get(path, 0) + 1

        authored = sorted(file_counter, key=lambda p: file_counter[p], reverse=True)
        return ContributionInfo(
            login=login, commit_count=len(commits), authored_files=authored
        )

    async def _fetch_json(self, client: httpx.AsyncClient, path: str) -> dict:
        resp = await client.get(path)
        if resp.status_code == 404:
            raise RepositoryFetchError(
                code="REPO_NOT_FOUND",
                message=f"GitHub returned 404 for {path}",
                retriable=False,
            )
        if resp.status_code in (401, 403):
            raise RepositoryFetchError(
                code="REPO_AUTH_OR_RATE",
                message=f"GitHub auth/rate-limit: {resp.status_code} {resp.text[:200]}",
                retriable=True,
            )
        resp.raise_for_status()
        return resp.json()

    async def _fetch_readme(
        self,
        client: httpx.AsyncClient,
        owner_repo: str,
    ) -> str:
        try:
            data = await self._fetch_json(client, f"/repos/{owner_repo}/readme")
        except RepositoryFetchError as err:
            if err.code == "REPO_NOT_FOUND":
                return ""  # README 없는 레포는 정상
            raise
        if data.get("encoding") != "base64":
            return ""
        try:
            return base64.b64decode(data.get("content", "")).decode(
                "utf-8", errors="replace"
            )
        except Exception:
            return ""

    async def _fetch_tree(
        self,
        client: httpx.AsyncClient,
        owner_repo: str,
        branch: str,
    ) -> tuple[list[str], bool]:
        data = await self._fetch_json(
            client,
            f"/repos/{owner_repo}/git/trees/{branch}?recursive=1",
        )
        tree = data.get("tree", [])
        paths = [
            item["path"]
            for item in tree
            if item.get("type") == "blob" and item.get("path")
        ]
        return paths, bool(data.get("truncated"))

    async def _fetch_snippets(
        self,
        client: httpx.AsyncClient,
        owner_repo: str,
        branch: str,
        picked: list[str],
    ) -> list[tuple[str, str]]:
        out: list[tuple[str, str]] = []
        for path in picked:
            try:
                data = await self._fetch_json(
                    client, f"/repos/{owner_repo}/contents/{path}?ref={branch}"
                )
            except RepositoryFetchError as err:
                log.warning(
                    "repo.snippet.skip",
                    path=path,
                    code=err.code,
                )
                continue
            if isinstance(data, list) or data.get("encoding") != "base64":
                continue
            try:
                body = base64.b64decode(data.get("content", "")).decode(
                    "utf-8", errors="replace"
                )
            except Exception:  # noqa: BLE001
                continue
            if len(body.encode("utf-8")) > self._cfg.max_file_bytes:
                body = body[: self._cfg.max_file_bytes] + "\n... (truncated)"
            out.append((path, body))
        return out


def _select_files(
    paths: list[str], cap: int, *, prioritized: list[str] | None = None
) -> list[str]:
    """지원자 기여 파일 → 설정파일 우선순위 → 디렉토리당 대표 텍스트 파일, cap까지."""
    chosen: list[str] = []
    seen: set[str] = set()
    tree = set(paths)

    # 지원자가 실제 작성한 텍스트 파일을 가장 먼저 (트리에 존재 + 텍스트 확장자).
    for p in prioritized or []:
        if p in seen or p not in tree:
            continue
        if "." not in p.rsplit("/", 1)[-1]:
            continue
        if ("." + p.rsplit(".", 1)[-1].lower()) not in _TEXT_EXTENSIONS:
            continue
        chosen.append(p)
        seen.add(p)
        if len(chosen) >= cap:
            return chosen

    for needle in _PRIORITY_FILES:
        for p in paths:
            if p == needle or p.endswith("/" + needle):
                if p not in seen:
                    chosen.append(p)
                    seen.add(p)
                break
        if len(chosen) >= cap:
            return chosen

    by_dir: dict[str, str] = {}
    for p in paths:
        if p in seen:
            continue
        if "." not in p.rsplit("/", 1)[-1]:
            continue
        ext = "." + p.rsplit(".", 1)[-1].lower()
        if ext not in _TEXT_EXTENSIONS:
            continue
        d = p.rsplit("/", 1)[0] if "/" in p else ""
        if d not in by_dir:
            by_dir[d] = p
    for p in by_dir.values():
        if p not in seen:
            chosen.append(p)
            seen.add(p)
            if len(chosen) >= cap:
                break
    return chosen
