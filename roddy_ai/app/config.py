"""환경변수. 컨테이너 밖에서 주입한다."""
import os


class Settings:
    """실행에 필요한 값들. 없으면 기동 시점에 바로 알 수 있게 모아 둔다."""

    def __init__(self) -> None:
        # 백엔드만 호출하는 내부 API 다. 같은 도커 네트워크 안에 있지만 한 겹 더 막는다.
        self.internal_api_secret = os.getenv("INTERNAL_API_SECRET", "")

        self.openai_api_key = os.getenv("OPENAI_API_KEY", "")
        self.openai_model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
        self.openai_timeout_seconds = float(os.getenv("OPENAI_TIMEOUT_SECONDS", "90"))

        # 토큰이 없으면 깃허브 공개 API 는 시간당 60회로 묶인다. 토큰을 주면 5000회로 늘어난다.
        self.github_token = os.getenv("GITHUB_TOKEN", "")
        self.github_timeout_seconds = float(os.getenv("GITHUB_TIMEOUT_SECONDS", "10"))
        # 저장소마다 언어 구성을 따로 물어봐야 해서, 최근 저장소부터 이만큼만 본다.
        self.github_max_repositories = int(os.getenv("GITHUB_MAX_REPOSITORIES", "20"))

        self.portfolio_timeout_seconds = float(os.getenv("PORTFOLIO_TIMEOUT_SECONDS", "20"))
        self.portfolio_max_bytes = int(os.getenv("PORTFOLIO_MAX_BYTES", str(10 * 1024 * 1024)))
        # 이력서 전체를 넣으면 토큰이 커지고 정확도도 나아지지 않는다.
        self.portfolio_max_chars = int(os.getenv("PORTFOLIO_MAX_CHARS", "20000"))

    def require_internal_secret(self) -> str:
        if not self.internal_api_secret:
            raise RuntimeError("INTERNAL_API_SECRET 이 필요합니다.")
        return self.internal_api_secret

    def require_openai_api_key(self) -> str:
        if not self.openai_api_key:
            raise RuntimeError("OPENAI_API_KEY 가 필요합니다.")
        return self.openai_api_key


settings = Settings()
