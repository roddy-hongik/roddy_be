"""내부 호출만 받도록 막는다."""
import hmac

from fastapi import Header, HTTPException, status

from app.config import settings

INTERNAL_SECRET_HEADER = "X-Internal-Secret"


def verify_internal_caller(x_internal_secret: str = Header(default="")) -> None:
    """
    백엔드가 보낸 호출인지 확인한다.

    같은 도커 네트워크 안에서만 열리는 포트지만, 네트워크 설정이 한 번 잘못되면 그대로 열리므로
    공유 비밀값을 한 겹 더 둔다. 값 비교는 길이에 따라 시간이 달라지지 않는 방식으로 한다.
    """
    expected = settings.require_internal_secret()

    if not hmac.compare_digest(x_internal_secret, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="내부 호출만 허용합니다.",
        )
