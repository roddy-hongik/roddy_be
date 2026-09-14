"""역량 격차를 확인하는 실전형 면접 질문을 LLM 으로 만든다."""
import json
from typing import List

from openai import OpenAI

from app.config import settings
from app.schemas import InterviewQuestion, InterviewQuestionRequest, InterviewQuestionResponse

# 질문 id 이자 LLM 응답의 속성 이름. 질문 개수와 id 는 LLM 에 맡기지 않고 여기서 정한다.
QUESTION_IDS = ["q1", "q2", "q3"]

SYSTEM_PROMPT = """\
너는 주니어 개발자 기술 면접을 진행하는 시니어 면접관이다.
지원자가 이미 할 줄 아는 기술과, 목표 직무의 채용공고가 요구하지만 아직 없는 기술을 보고 실전형 면접 질문 세 개를 만든다.

지켜야 할 것:
- 단순 정의 암기보다 선택 이유, 트레이드오프, 장애 대응을 묻는다.
- 부족한 기술을 중심으로 묻되, 이미 할 줄 아는 기술과 이어서 답할 수 있게 한다.
- 세 질문은 서로 다른 내용을 묻는다.
- 질문마다 평가 의도와, 좋은 답변에 들어가야 할 핵심 포인트를 하나 이상 적는다.
- 특정 기업의 비공개 면접 질문이나 내부 사정을 지어내지 않는다.
- 한국어로 간결하게 쓴다."""


def generate(request: InterviewQuestionRequest) -> InterviewQuestionResponse:
    content = _request_questions(request)
    return InterviewQuestionResponse(
        questions=[_to_question(question_id, content.get(question_id) or {}) for question_id in QUESTION_IDS]
    )


def _request_questions(request: InterviewQuestionRequest) -> dict:
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
                "name": "interview_questions",
                "strict": True,
                "schema": _response_schema(),
            },
        },
    )

    return json.loads(response.choices[0].message.content)


def _response_schema() -> dict:
    """
    LLM 이 돌려줄 모양. 질문을 배열이 아니라 q1~q3 속성으로 받는다.

    배열로 받으면 질문이 모자라거나 넘쳐도 막을 수 없다. 속성으로 받으면 strict 모드가 세 질문을 모두 요구한다.

    빈 질문·빈 의도·빈 핵심 포인트도 스키마에서 막는다. 백엔드는 빈 값이 섞인 응답을 통째로 버리므로,
    LLM 이 빈 값을 내면 호출은 성공하고도 사용자는 실패를 보게 된다.
    """
    non_blank = {"type": "string", "pattern": r"\S"}
    question = {
        "type": "object",
        "additionalProperties": False,
        "required": ["question", "intent", "key_points"],
        "properties": {
            "question": dict(non_blank, description="면접 질문. 한 문장"),
            "intent": dict(non_blank, description="이 질문으로 확인하려는 것"),
            "key_points": {
                "type": "array",
                "description": "좋은 답변에 들어가야 할 핵심 포인트",
                "minItems": 1,
                "items": non_blank,
            },
        },
    }

    return {
        "type": "object",
        "additionalProperties": False,
        "required": list(QUESTION_IDS),
        "properties": {question_id: question for question_id in QUESTION_IDS},
    }


def _build_prompt(request: InterviewQuestionRequest) -> str:
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


def _to_question(question_id: str, raw: dict) -> InterviewQuestion:
    return InterviewQuestion(
        id=question_id,
        question=str(raw.get("question", "")).strip(),
        intent=str(raw.get("intent", "")).strip(),
        key_points=_texts(raw.get("key_points")),
    )


def _texts(values: object) -> List[str]:
    """빈 항목은 버린다. 백엔드는 빈 항목이 섞인 응답을 통째로 거절한다."""
    if not isinstance(values, list):
        return []
    return [value.strip() for value in values if isinstance(value, str) and value.strip()]
