"""로디 AI 서버.

백엔드만 호출하는 내부 서비스다. 깃허브와 포트폴리오를 읽어 역량 리포트를 만들고, 부족한 기술을 채우는
학습 로드맵을 만든다.
"""
import logging

from fastapi import Depends, FastAPI

from app import github, portfolio, roadmap
from app.analyzer import analyze
from app.schemas import AnalysisRequest, AnalysisResponse, RoadmapRequest, RoadmapResponse
from app.security import verify_internal_caller

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger(__name__)

app = FastAPI(title="roddy-ai", description="로디 역량 분석 서버", docs_url=None, redoc_url=None)


@app.get("/health")
def health() -> dict:
    """컨테이너가 떠 있는지만 본다. 외부 의존성을 건드리지 않는다."""
    return {"status": "ok"}


@app.post("/internal/analyses", response_model=AnalysisResponse)
def create_analysis(
    request: AnalysisRequest,
    _: None = Depends(verify_internal_caller),
) -> AnalysisResponse:
    """
    사용자 한 명의 역량을 분석한다.

    깃허브와 포트폴리오 중 하나만 있어도 분석한다. 둘 다 읽지 못하면 LLM 을 부르지 않고 그 사실을
    담아 돌려준다. 자료 없이 지어낸 리포트를 주지 않기 위함이다.
    """
    logger.info("분석을 시작합니다. userId=%s", request.user_id)

    repositories, github_warnings = github.collect_repositories(request.github_url, request.github_token)
    portfolio_text, portfolio_warnings = portfolio.extract_text(
        request.portfolio_url, request.portfolio_file_name
    )

    response = analyze(
        request, repositories, portfolio_text, github_warnings + portfolio_warnings
    )

    logger.info(
        "분석을 마쳤습니다. userId=%s 저장소=%d 포트폴리오=%s 기술=%d",
        request.user_id,
        response.sources.repository_count,
        response.sources.portfolio_included,
        len(response.stacks),
    )
    return response


@app.post("/internal/roadmaps", response_model=RoadmapResponse)
def create_roadmap(
    request: RoadmapRequest,
    _: None = Depends(verify_internal_caller),
) -> RoadmapResponse:
    """
    부족한 기술을 채우는 세 단계 학습 로드맵을 만든다.

    단계 이름과 순서는 LLM 에 맡기지 않고 붙인다. 백엔드는 기초, 심화, 실전 프로젝트 순서가 아니면 응답을 버린다.
    """
    logger.info(
        "로드맵 생성을 시작합니다. 직무=%s 현재 기술=%d 부족 기술=%d",
        request.target_job,
        len(request.current_skills),
        len(request.gap_skills),
    )

    response = roadmap.generate(request)

    logger.info("로드맵 생성을 마쳤습니다. 단계=%d", len(response.steps))
    return response
