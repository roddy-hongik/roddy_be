"""LLM 에 넘기는 면접 질문 응답 스키마가 빈 값을 막는지 확인한다.

LLM 을 부르지 않는다. OpenAI 호출을 가짜로 바꿔 실제로 넘긴 스키마를 꺼내고, 그 스키마로 경계 값을 검증한다.
"""
import json
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from jsonschema import Draft202012Validator

from app import interview
from app.config import settings
from app.schemas import InterviewQuestionRequest


def valid_content() -> dict:
    return {
        question_id: {"question": "질문 %s" % question_id, "intent": "의도", "key_points": ["포인트"]}
        for question_id in interview.QUESTION_IDS
    }


class InterviewResponseSchemaTest(unittest.TestCase):
    def setUp(self) -> None:
        settings.openai_api_key = "test-key"

        with patch("app.interview.OpenAI") as openai:
            completion = SimpleNamespace(
                choices=[SimpleNamespace(message=SimpleNamespace(content=json.dumps(valid_content())))]
            )
            create = openai.return_value.chat.completions.create
            create.return_value = completion

            response = interview.generate(
                InterviewQuestionRequest(current_skills=["Java"], gap_skills=["Redis"], target_job="백엔드 개발자")
            )

        self.response = response
        self.response_format = create.call_args.kwargs["response_format"]
        self.validator = Draft202012Validator(self.response_format["json_schema"]["schema"])

    def with_q1(self, **fields) -> dict:
        content = valid_content()
        content["q1"].update(fields)
        return content

    def test_strict_모드로_스키마를_넘기고_유효한_응답은_통과한다(self) -> None:
        self.assertTrue(self.response_format["json_schema"]["strict"])
        Draft202012Validator.check_schema(self.response_format["json_schema"]["schema"])
        self.assertTrue(self.validator.is_valid(valid_content()))
        self.assertEqual(["q1", "q2", "q3"], [question.id for question in self.response.questions])

    def test_공백만_있는_질문은_거부한다(self) -> None:
        self.assertFalse(self.validator.is_valid(self.with_q1(question="   ")))
        self.assertFalse(self.validator.is_valid(self.with_q1(question="")))

    def test_공백만_있는_의도는_거부한다(self) -> None:
        self.assertFalse(self.validator.is_valid(self.with_q1(intent=" \n\t ")))

    def test_핵심_포인트가_비어_있으면_거부한다(self) -> None:
        self.assertFalse(self.validator.is_valid(self.with_q1(key_points=[])))

    def test_공백만_있는_핵심_포인트_항목은_거부한다(self) -> None:
        self.assertFalse(self.validator.is_valid(self.with_q1(key_points=["포인트", "  "])))

    def test_질문이_하나라도_빠지면_거부한다(self) -> None:
        content = valid_content()
        del content["q3"]
        self.assertFalse(self.validator.is_valid(content))


if __name__ == "__main__":
    unittest.main()
