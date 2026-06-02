import base64
from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest

from ai_server.analyzer.sources.github_repo import (
    GitHubRepoSourceExtractor,
    RepositoryFetchError,
    _select_files,
)


def _make_client(routes: dict[str, dict | int]) -> tuple[MagicMock, AsyncMock]:
    """`routes`: path → response dict (JSON) or int (HTTP status)."""
    client = MagicMock()

    async def _get(path: str) -> MagicMock:
        body = routes.get(path)
        resp = MagicMock(spec=httpx.Response)
        if isinstance(body, int):
            resp.status_code = body
            resp.text = ""
            resp.raise_for_status = MagicMock()
            return resp
        resp.status_code = 200
        resp.json = MagicMock(return_value=body)
        resp.raise_for_status = MagicMock()
        return resp

    client.get = AsyncMock(side_effect=_get)
    return client, client.get


def _b64(text: str) -> str:
    return base64.b64encode(text.encode("utf-8")).decode("ascii")


@pytest.mark.asyncio
async def test_extract_assembles_readme_tree_and_snippets() -> None:
    routes = {
        "/repos/user/repo": {
            "default_branch": "dev",
            "description": "demo project",
        },
        "/repos/user/repo/readme": {
            "encoding": "base64",
            "content": _b64("# Hello\nReadme body"),
        },
        "/repos/user/repo/git/trees/dev?recursive=1": {
            "tree": [
                {"path": "pyproject.toml", "type": "blob"},
                {"path": "src/app.py", "type": "blob"},
                {"path": "tests/test_app.py", "type": "blob"},
                {"path": "docs", "type": "tree"},
            ],
            "truncated": False,
        },
        "/repos/user/repo/contents/pyproject.toml?ref=dev": {
            "encoding": "base64",
            "content": _b64('[project]\nname = "app"'),
        },
        "/repos/user/repo/contents/src/app.py?ref=dev": {
            "encoding": "base64",
            "content": _b64("def main():\n    return 1"),
        },
        "/repos/user/repo/contents/tests/test_app.py?ref=dev": {
            "encoding": "base64",
            "content": _b64("def test(): pass"),
        },
    }
    client, _ = _make_client(routes)
    extractor = GitHubRepoSourceExtractor(
        api_base_url="https://api.github.com", client=client
    )

    result = await extractor.extract("user/repo")

    assert result.source_type == "REPOSITORY"
    assert "user/repo" in result.text
    assert "Readme body" in result.text
    assert "pyproject.toml" in result.text
    assert "def main()" in result.text
    assert result.metadata["default_branch"] == "dev"
    assert result.metadata["tree_size"] == 3
    assert "pyproject.toml" in result.metadata["sampled_files"]


@pytest.mark.asyncio
async def test_extract_handles_missing_readme_gracefully() -> None:
    routes = {
        "/repos/user/repo": {"default_branch": "main"},
        "/repos/user/repo/readme": 404,
        "/repos/user/repo/git/trees/main?recursive=1": {"tree": [], "truncated": False},
    }
    client, _ = _make_client(routes)
    extractor = GitHubRepoSourceExtractor(
        api_base_url="https://api.github.com", client=client
    )

    result = await extractor.extract("user/repo")
    assert "user/repo" in result.text
    assert result.metadata["tree_size"] == 0


@pytest.mark.asyncio
async def test_extract_raises_on_repo_not_found() -> None:
    routes = {"/repos/user/repo": 404}
    client, _ = _make_client(routes)
    extractor = GitHubRepoSourceExtractor(
        api_base_url="https://api.github.com", client=client
    )

    with pytest.raises(RepositoryFetchError) as exc_info:
        await extractor.extract("user/repo")
    assert exc_info.value.code == "REPO_NOT_FOUND"
    assert exc_info.value.retriable is False


@pytest.mark.asyncio
async def test_extract_raises_on_rate_limit_as_retriable() -> None:
    routes = {"/repos/user/repo": 403}
    client, _ = _make_client(routes)
    extractor = GitHubRepoSourceExtractor(
        api_base_url="https://api.github.com", client=client
    )

    with pytest.raises(RepositoryFetchError) as exc_info:
        await extractor.extract("user/repo")
    assert exc_info.value.code == "REPO_AUTH_OR_RATE"
    assert exc_info.value.retriable is True


@pytest.mark.asyncio
async def test_extract_rejects_malformed_locator() -> None:
    client, _ = _make_client({})
    extractor = GitHubRepoSourceExtractor(
        api_base_url="https://api.github.com", client=client
    )
    with pytest.raises(RepositoryFetchError) as exc_info:
        await extractor.extract("not-a-full-name")
    assert exc_info.value.code == "INVALID_REPO_LOCATOR"


@pytest.mark.asyncio
async def test_extract_includes_contributor_analysis_with_user_token() -> None:
    routes = {
        "/repos/user/repo": {"default_branch": "dev", "description": "demo"},
        "/user": {"login": "alice"},
        "/repos/user/repo/commits?author=alice&sha=dev&per_page=100": [
            {"sha": "c1"},
            {"sha": "c2"},
        ],
        "/repos/user/repo/commits/c1": {"files": [{"filename": "src/pay.py"}]},
        "/repos/user/repo/commits/c2": {
            "files": [{"filename": "src/pay.py"}, {"filename": "src/util.py"}]
        },
        "/repos/user/repo/readme": 404,
        "/repos/user/repo/git/trees/dev?recursive=1": {
            "tree": [
                {"path": "src/pay.py", "type": "blob"},
                {"path": "src/util.py", "type": "blob"},
                {"path": "src/other.py", "type": "blob"},
            ],
            "truncated": False,
        },
        "/repos/user/repo/contents/src/pay.py?ref=dev": {
            "encoding": "base64",
            "content": _b64("def pay(): ..."),
        },
        "/repos/user/repo/contents/src/util.py?ref=dev": {
            "encoding": "base64",
            "content": _b64("def util(): ..."),
        },
        "/repos/user/repo/contents/src/other.py?ref=dev": {
            "encoding": "base64",
            "content": _b64("def other(): ..."),
        },
    }
    client, _ = _make_client(routes)
    extractor = GitHubRepoSourceExtractor(
        api_base_url="https://api.github.com", client=client
    )

    result = await extractor.extract("user/repo", access_token="alice-token")

    assert "## 지원자 기여" in result.text
    assert "@alice" in result.text
    assert "커밋 2개" in result.text
    assert result.metadata["contributor_login"] == "alice"
    assert result.metadata["contrib_commit_count"] == 2
    # 기여 파일(src/pay.py)이 샘플링 우선순위에 들어감
    assert "src/pay.py" in result.metadata["sampled_files"]


@pytest.mark.asyncio
async def test_extract_skips_contribution_without_user_token() -> None:
    routes = {
        "/repos/user/repo": {"default_branch": "main"},
        "/repos/user/repo/readme": 404,
        "/repos/user/repo/git/trees/main?recursive=1": {
            "tree": [],
            "truncated": False,
        },
    }
    client, getter = _make_client(routes)
    extractor = GitHubRepoSourceExtractor(
        api_base_url="https://api.github.com", client=client
    )
    result = await extractor.extract("user/repo")  # access_token 없음
    # /user 를 호출하지 않음 (기여도 분석 스킵)
    called_paths = [c.args[0] for c in getter.call_args_list]
    assert "/user" not in called_paths
    assert result.metadata["contrib_commit_count"] == 0


def test_select_files_prioritizes_contributed_files_first() -> None:
    paths = ["package.json", "src/index.ts", "src/feature/pay.ts"]
    picked = _select_files(paths, cap=2, prioritized=["src/feature/pay.ts"])
    assert picked[0] == "src/feature/pay.ts"  # 기여 파일이 최우선


def test_select_files_prioritizes_manifests_then_one_per_dir() -> None:
    paths = [
        "package.json",
        "src/index.ts",
        "src/util/format.ts",
        "tests/index.test.ts",
        "README.md",
    ]
    picked = _select_files(paths, cap=4)
    # manifest 우선
    assert "package.json" in picked
    # 디렉토리당 1개씩 sampling (src/, tests/, root)
    assert any(p.startswith("src/") for p in picked)
    assert any(p.startswith("tests/") for p in picked)
    assert len(picked) <= 4


def test_select_files_respects_cap() -> None:
    paths = [f"src/a{i}.py" for i in range(20)]
    picked = _select_files(paths, cap=3)
    assert len(picked) <= 3


def test_build_client_includes_authorization_with_user_token() -> None:
    extractor = GitHubRepoSourceExtractor(api_base_url="https://api.github.com")
    client = extractor._build_client("user-token-xyz")
    try:
        assert client.headers.get("Authorization") == "Bearer user-token-xyz"
    finally:
        # 비동기 cleanup 없이 닫기 OK — 실제 연결은 안 함
        pass


def test_build_client_omits_authorization_when_token_empty() -> None:
    extractor = GitHubRepoSourceExtractor(api_base_url="https://api.github.com")
    client = extractor._build_client("")
    assert "Authorization" not in client.headers


def test_fallback_token_used_when_no_per_call_token() -> None:
    extractor = GitHubRepoSourceExtractor(
        api_base_url="https://api.github.com",
        fallback_token="fallback-tok",
    )
    # access_token=None → fallback 사용
    client = extractor._build_client(extractor._cfg.fallback_token)
    assert client.headers.get("Authorization") == "Bearer fallback-tok"
