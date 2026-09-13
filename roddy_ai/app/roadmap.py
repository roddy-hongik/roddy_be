"""부족한 기술을 채우는 학습 로드맵을 LLM 으로 만든다."""
import json
import logging
from typing import List

from openai import OpenAI

from app.config import settings
from app.schemas import RoadmapRequest, RoadmapResponse, RoadmapStep

logger = logging.getLogger(__name__)

# (LLM 응답의 속성 이름, 백엔드와 약속한 단계 이름). 단계 이름과 순서는 LLM 에 맡기지 않고 여기서 붙인다.
STAGES = [("basic", "기초"), ("advanced", "심화"), ("project", "실전 프로젝트")]

SYSTEM_PROMPT = """\
너는 주니어 개발자의 성장 계획을 짜 주는 시니어 개발자다.
사용자가 이미 할 줄 아는 기술과, 목표 직무의 채용공고가 요구하지만 아직 없는 기술을 보고 학습 로드맵을 만든다.

지켜야 할 것:
- 로드맵은 기초, 심화, 실전 프로젝트 세 단계다.
- 부족한 기술을 채우는 데 집중한다. 이미 할 줄 아는 기술은 다시 배울 거리로 넣지 않고 발판으로 쓴다.
- 단계마다 목표 한 문장, 공부할 주제, 만들어 볼 결과물을 적는다. 주제와 결과물은 하나 이상 적는다.
- 결과물은 눈으로 확인할 수 있는 것으로 적는다(예: 캐시를 붙인 조회 API 와 부하 테스트 결과). "이해하기" 같은 말은 쓰지 않는다.
- 기술 이름은 널리 쓰이는 표기를 쓴다(Java, Spring Boot, Redis). 한글로 적지 않는다.
- 설명은 한국어로, 평어체로 쓴다."""


def generate(request: RoadmapRequest) -> RoadmapResponse:
    content = _request_roadmap(request)
    return RoadmapResponse(
        title=str(content.get("title", "")).strip(),
        steps=[_to_step(stage, content.get(key) or {}) for key, stage in STAGES],
    )


def _request_roadmap(request: RoadmapRequest) -> dict:
    client = OpenAI(api_key=settings.require_openai_api_key(), timeout=settings.openai_timeout_seconds)

    response = client.chat.completions.create(
        model=settings.openai_model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": _build_prompt(request)},
        ],
        response_format={
            "type": "json_schema",
            "json_schema": {
                "name": "learning_roadmap",
                "strict": True,
                "schema": _response_schema(),
            },
        },
    )

    return json.loads(response.choices[0].message.content)


def _response_schema() -> dict:
    """
    LLM 이 돌려줄 모양. 단계를 배열이 아니라 이름 붙은 속성으로 받는다.

    배열로 받으면 단계를 빠뜨리거나 순서를 바꿔도 막을 수 없다. 속성으로 받으면 strict 모드가 세 단계를 모두 요구한다.
    """
    step = {
        "type": "object",
        "additionalProperties": False,
        "required": ["goal", "topics", "outputs"],
        "properties": {
            "goal": {"type": "string", "description": "이 단계를 마치면 할 수 있게 되는 것. 한 문장"},
            "topics": {"type": "array", "description": "공부할 주제", "items": {"type": "string"}},
            "outputs": {"type": "array", "description": "만들어 볼 결과물", "items": {"type": "string"}},
        },
    }
    properties = {"title": {"type": "string", "description": "로드맵 제목. 한 줄로"}}
    for key, stage in STAGES:
        properties[key] = dict(step, description="%s 단계" % stage)

    return {
        "type": "object",
        "additionalProperties": False,
        "required": list(properties),
        "properties": properties,
    }


def _build_prompt(request: RoadmapRequest) -> str:
    return "\n".join(
        [
            "## 목표",
            "직무: %s" % request.target_job,
            "회사: %s" % (request.target_company or "미입력"),
            "",
            "## 이미 할 줄 아는 기술",
            ", ".join(request.current_skills) or "없음",
            "",
            "## 채용공고가 요구하지만 아직 없는 기술 (자주 요구하는 순)",
            ", ".join(request.gap_skills),
        ]
    )


def _to_step(stage: str, raw: dict) -> RoadmapStep:
    return RoadmapStep(
        stage=stage,
        goal=str(raw.get("goal", "")).strip(),
        topics=_texts(raw.get("topics")),
        outputs=_texts(raw.get("outputs")),
    )


def _texts(values: object) -> List[str]:
    """빈 항목은 버린다. 백엔드는 빈 항목이 섞인 응답을 통째로 거절한다."""
    if not isinstance(values, list):
        return []
    return [value.strip() for value in values if isinstance(value, str) and value.strip()]
