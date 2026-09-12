"""깃허브 공개 저장소를 모은다."""
import logging
import re
from typing import List, Optional, Tuple

import httpx

from app.config import settings
from app.schemas import Repository

logger = logging.getLogger(__name__)

GITHUB_API_BASE = "https://api.github.com"
# https://github.com/<login> 또는 https://github.com/<login>/ 형태에서 로그인만 뽑는다.
GITHUB_LOGIN_PATTERN = re.compile(r"github\.com/([A-Za-z0-9-]+)/?$")


def extract_login(github_url: Optional[str]) -> Optional[str]:
    if not github_url:
        return None

    matched = GITHUB_LOGIN_PATTERN.search(github_url.strip())
    return matched.group(1) if matched else None


def collect_repositories(github_url: Optional[str]) -> Tuple[List[Repository], List[str]]:
    """
    공개 저장소를 최근 갱신순으로 모은다.

    토큰 없이 부르면 시간당 60회로 묶이므로 저장소 수를 제한한다. 실패해도 예외를 올리지 않고
    경고만 남긴다. 깃허브가 막혔다고 분석 전체를 포기할 이유는 없다.
    """
    login = extract_login(github_url)
    if not login:
        return [], ["깃허브 주소에서 계정을 읽지 못했습니다."]

    warnings: List[str] = []
    try:
        with httpx.Client(timeout=settings.github_timeout_seconds, headers=_headers()) as client:
            raw_repositories = _fetch_repositories(client, login)
            repositories = [
                _to_repository(client, login, raw)
                for raw in raw_repositories[: settings.github_max_repositories]
            ]

            if len(raw_repositories) > settings.github_max_repositories:
                warnings.append(
                    "저장소가 많아 최근 %d개만 분석했습니다." % settings.github_max_repositories
                )
            return repositories, warnings
    except httpx.HTTPStatusError as error:
        logger.warning("깃허브 응답이 실패했습니다. status=%s", error.response.status_code)
        if error.response.status_code == 403:
            return [], ["깃허브 호출 한도를 넘었습니다. 잠시 뒤에 다시 분석해주세요."]
        return [], ["깃허브 저장소를 불러오지 못했습니다."]
    except httpx.HTTPError as error:
        logger.warning("깃허브 호출에 실패했습니다. %s", error)
        return [], ["깃허브 저장소를 불러오지 못했습니다."]


def _headers() -> dict:
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "roddy-ai",
    }
    if settings.github_token:
        headers["Authorization"] = "Bearer %s" % settings.github_token
    return headers


def _fetch_repositories(client: httpx.Client, login: str) -> List[dict]:
    response = client.get(
        "%s/users/%s/repos" % (GITHUB_API_BASE, login),
        params={"per_page": 100, "sort": "updated", "type": "owner"},
    )
    response.raise_for_status()

    # 포크는 본인이 쓴 코드가 아니라 역량 판단에 방해가 된다.
    return [repo for repo in response.json() if not repo.get("fork")]


def _to_repository(client: httpx.Client, login: str, raw: dict) -> Repository:
    return Repository(
        name=raw.get("name", ""),
        description=raw.get("description"),
        primary_language=raw.get("language"),
        languages=_fetch_languages(client, login, raw.get("name", "")),
        topics=raw.get("topics", []) or [],
        stars=raw.get("stargazers_count", 0) or 0,
        updated_at=raw.get("updated_at"),
    )


def _fetch_languages(client: httpx.Client, login: str, name: str) -> List[str]:
    """언어 구성은 저장소마다 따로 물어봐야 한다. 실패하면 주 언어만으로도 충분하므로 넘어간다."""
    if not name:
        return []

    try:
        response = client.get("%s/repos/%s/%s/languages" % (GITHUB_API_BASE, login, name))
        response.raise_for_status()

        languages = response.json()
        # 사용량이 많은 언어부터
        return sorted(languages, key=languages.get, reverse=True)
    except httpx.HTTPError:
        return []
