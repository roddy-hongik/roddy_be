"""백엔드와 주고받는 모양. 이 파일이 두 서비스 사이의 계약이다."""
from typing import List, Literal, Optional

from pydantic import BaseModel, Field

StackLevel = Literal["BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"]


class AnalysisRequest(BaseModel):
    user_id: int

    # 깃허브를 연결하지 않은 사용자도 있다. 포트폴리오만으로도 분석한다.
    github_url: Optional[str] = None
    # 백엔드가 만든 S3 presigned URL. AI 서버는 S3 자격증명을 갖지 않는다.
    portfolio_url: Optional[str] = None
    portfolio_file_name: Optional[str] = None

    desired_job: Optional[str] = None
    experience_years: Optional[str] = None


class AnalyzedStack(BaseModel):
    """분석이 찾아낸 기술 하나. 이름 표준화는 백엔드의 기술스택 사전이 맡는다."""

    name: str
    score: int = Field(ge=0, le=100)
    level: StackLevel
    description: str


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
