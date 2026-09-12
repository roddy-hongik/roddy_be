"""모아 온 자료를 LLM 에 넘겨 리포트를 만든다."""
import json
import logging
from typing import List, Optional

from openai import OpenAI

from app.config import settings
from app.schemas import (
    AnalysisRequest,
    AnalysisResponse,
    AnalysisSources,
    AnalyzedStack,
    Repository,
)

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """\
너는 주니어 개발자의 역량을 진단하는 시니어 개발자다.
사용자의 깃허브 저장소와 포트폴리오를 보고, 무엇을 할 줄 아는지 근거를 들어 정리한다.

지켜야 할 것:
- 자료에 없는 내용을 지어내지 않는다. 근거가 없으면 없다고 적는다.
- 점수는 주니어 채용 시장을 기준으로 매긴다. 저장소가 적으면 낮게 준다.
- 기술 이름은 널리 쓰이는 표기를 쓴다(Java, Spring Boot, React). 한글로 적지 않는다.
- 설명은 한국어로, 평어체로 쓴다. 칭찬만 늘어놓지 말고 부족한 점도 짚는다.
- 자료가 부족하면 점수를 낮추고 그 이유를 요약에 적는다."""

RESPONSE_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "required": ["title", "total_score", "summary", "github_analysis", "portfolio_analysis", "stacks"],
    "properties": {
        "title": {"type": "string", "description": "리포트 제목. 사용자의 현재 위치를 한 줄로"},
        "total_score": {"type": "integer", "minimum": 0, "maximum": 100},
        "summary": {"type": "string", "description": "전체 요약. 강점과 부족한 점을 함께"},
        "github_analysis": {"type": "string", "description": "저장소에서 읽어낸 내용"},
        "portfolio_analysis": {"type": "string", "description": "포트폴리오에서 읽어낸 내용. 없으면 그렇다고 적는다"},
        "stacks": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": ["name", "score", "level", "description"],
                "properties": {
                    "name": {"type": "string"},
                    "score": {"type": "integer", "minimum": 0, "maximum": 100},
                    "level": {
                        "type": "string",
                        "enum": ["BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"],
                    },
                    "description": {"type": "string", "description": "그렇게 본 근거"},
                },
            },
        },
    },
}


def analyze(
    request: AnalysisRequest,
    repositories: List[Repository],
    portfolio_text: str,
    warnings: List[str],
) -> AnalysisResponse:
    """자료가 하나도 없으면 LLM 을 부르지 않는다. 지어낸 리포트를 만들 이유가 없다."""
    sources = AnalysisSources(
        repository_count=len(repositories),
        portfolio_included=bool(portfolio_text),
        warnings=warnings,
    )

    if not repositories and not portfolio_text:
        return _empty_report(sources)

    content = _request_report(request, repositories, portfolio_text)
    return AnalysisResponse(
        title=content.get("title", "역량 분석 리포트"),
        total_score=_clamp(content.get("total_score", 0)),
        summary=content.get("summary", ""),
        github_analysis=content.get("github_analysis", ""),
        portfolio_analysis=content.get("portfolio_analysis", ""),
        stacks=_to_stacks(content.get("stacks", [])),
        sources=sources,
    )


def _request_report(
    request: AnalysisRequest, repositories: List[Repository], portfolio_text: str
) -> dict:
    client = OpenAI(api_key=settings.require_openai_api_key(), timeout=settings.openai_timeout_seconds)

    response = client.chat.completions.create(
        model=settings.openai_model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": _build_prompt(request, repositories, portfolio_text)},
        ],
        response_format={
            "type": "json_schema",
            "json_schema": {"name": "analysis_report", "strict": True, "schema": RESPONSE_SCHEMA},
        },
    )

    return json.loads(response.choices[0].message.content)


def _build_prompt(
    request: AnalysisRequest, repositories: List[Repository], portfolio_text: str
) -> str:
    parts = [
        "## 사용자",
        "희망 직무: %s" % (request.desired_job or "미입력"),
        "경력: %s" % (request.experience_years or "미입력"),
        "",
        "## 깃허브 저장소 (%d개)" % len(repositories),
    ]

    if repositories:
        parts.extend(_describe(repository) for repository in repositories)
    else:
        parts.append("없음")

    parts.extend(["", "## 포트폴리오"])
    parts.append(portfolio_text if portfolio_text else "없음")

    return "\n".join(parts)


def _describe(repository: Repository) -> str:
    languages = ", ".join(repository.languages) or repository.primary_language or "언어 정보 없음"
    topics = ", ".join(repository.topics)

    line = "- %s | 언어: %s | 스타: %d" % (repository.name, languages, repository.stars)
    if repository.description:
        line += " | 설명: %s" % repository.description
    if topics:
        line += " | 토픽: %s" % topics
    return line


def _to_stacks(raw_stacks: list) -> List[AnalyzedStack]:
    stacks: List[AnalyzedStack] = []
    for raw in raw_stacks:
        try:
            stacks.append(AnalyzedStack(**raw))
        except Exception as error:
            # 기술 하나가 형식에 안 맞는다고 리포트 전체를 버리지 않는다.
            logger.warning("기술스택 하나를 읽지 못해 건너뜁니다. %s", error)
    return stacks


def _empty_report(sources: AnalysisSources) -> AnalysisResponse:
    return AnalysisResponse(
        title="분석할 자료가 없습니다",
        total_score=0,
        summary="깃허브 저장소와 포트폴리오를 모두 읽지 못했습니다. 깃허브를 연결하거나 포트폴리오를 올린 뒤 다시 분석해주세요.",
        github_analysis="",
        portfolio_analysis="",
        stacks=[],
        sources=sources,
    )


def _clamp(score: Optional[int]) -> int:
    if score is None:
        return 0
    return max(0, min(100, int(score)))
