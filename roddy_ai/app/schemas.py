"""백엔드와 주고받는 모양. 이 파일이 두 서비스 사이의 계약이다."""
from typing import List, Literal, Optional

from pydantic import BaseModel, Field

StackLevel = Literal["BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"]
# 기술의 근거를 어디서 찾았는지. 프론트는 깃허브에서 찾은 기술과 이력서에서 찾은 기술을 나눠 보여준다.
StackSource = Literal["GITHUB", "PORTFOLIO"]


class CompetencyCategory(BaseModel):
    """
    리포트의 평가 축 하나. 백엔드가 직무별로 정해 넘긴다.

    축을 LLM 이 매번 새로 지으면 리포트끼리 점수를 견줄 수 없다.
    """

    code: str
    name: str
    description: str


class AnalysisRequest(BaseModel):
    user_id: int

    # 깃허브를 연결하지 않은 사용자도 있다. 포트폴리오만으로도 분석한다.
    github_url: Optional[str] = None
    # 사용자 토큰. 없으면 공개 API 한도(IP 당 시간당 60회)에 묶인다. 로그에 남기지 않는다.
    github_token: Optional[str] = None
    # 백엔드가 만든 S3 presigned URL. AI 서버는 S3 자격증명을 갖지 않는다.
    portfolio_url: Optional[str] = None
    portfolio_file_name: Optional[str] = None

    desired_job: Optional[str] = None
    experience_years: Optional[str] = None

    # 평가 축. 축을 정하지 않은 직무는 비어 있고, 그러면 축별 점수 없이 분석한다.
    categories: List[CompetencyCategory] = Field(default_factory=list)


class AnalyzedStack(BaseModel):
    """분석이 찾아낸 기술 하나. 이름 표준화는 백엔드의 기술스택 사전이 맡는다."""

    name: str
    score: int = Field(ge=0, le=100)
    level: StackLevel
    description: str
    # 이 기술이 속한 평가 축의 code. 축을 받지 않았거나, 받은 축에 없는 값이면 비어 있다.
    category: Optional[str] = None
    found_in: List[StackSource] = Field(default_factory=list)


class CategoryScore(BaseModel):
    """평가 축 하나에 대한 점수와 해석."""

    code: str
    score: int = Field(ge=0, le=100)
    interpretation: str


class AnalysisSources(BaseModel):
    """무엇을 근거로 분석했는지. 결과가 빈약할 때 원인을 짚기 위해 남긴다."""

    repository_count: int = 0
    portfolio_included: bool = False
    warnings: List[str] = Field(default_factory=list)


class AnalysisResponse(BaseModel):
    title: str
    total_score: int = Field(ge=0, le=100)
    summary: str
    github_analysis: str
    portfolio_analysis: str
    stacks: List[AnalyzedStack] = Field(default_factory=list)
    # 요청한 축의 순서를 따른다. LLM 이 빠뜨린 축은 0점으로 채우지 않고 뺀다.
    categories: List[CategoryScore] = Field(default_factory=list)
    sources: AnalysisSources = Field(default_factory=AnalysisSources)


class Repository(BaseModel):
    """깃허브에서 모은 저장소 하나. LLM 에 넘길 최소한만 담는다."""

    name: str
    description: Optional[str] = None
    primary_language: Optional[str] = None
    languages: List[str] = Field(default_factory=list)
    topics: List[str] = Field(default_factory=list)
    stars: int = 0
    updated_at: Optional[str] = None


# 로드맵 단계. 이름과 순서가 백엔드와의 약속이다. 백엔드는 이 순서가 아니면 응답을 버린다.
RoadmapStage = Literal["기초", "심화", "실전 프로젝트"]


class RoadmapRequest(BaseModel):
    """로드맵 재료. 백엔드가 최신 분석 리포트와 모집 중인 공고에서 뽑아 넘긴다."""

    current_skills: List[str] = Field(default_factory=list)
    # 채용공고가 자주 요구하는 순이다. 비어 있으면 채울 것이 없으므로 로드맵을 지어내지 않는다.
    gap_skills: List[str] = Field(min_length=1)
    # 직무의 한글 이름. 예: 백엔드 개발자
    target_job: str
    target_company: Optional[str] = None


class RoadmapStep(BaseModel):
    stage: RoadmapStage
    goal: str
    topics: List[str] = Field(default_factory=list)
    outputs: List[str] = Field(default_factory=list)


class RoadmapResponse(BaseModel):
    title: str
    # 늘 기초, 심화, 실전 프로젝트 순서의 세 단계다.
    steps: List[RoadmapStep]
