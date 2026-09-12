"""포트폴리오 파일에서 글을 뽑는다."""
import io
import logging
from typing import List, Optional, Tuple

import httpx

from app.config import settings

logger = logging.getLogger(__name__)

PDF_SUFFIX = ".pdf"
TEXT_SUFFIXES = (".txt", ".md", ".markdown")


def extract_text(portfolio_url: Optional[str], file_name: Optional[str]) -> Tuple[str, List[str]]:
    """
    presigned URL 로 내려받아 글만 남긴다.

    AI 서버는 S3 자격증명을 갖지 않는다. 어떤 파일을 읽을지는 백엔드가 정하고, 여기서는 건네받은
    주소만 연다. 실패해도 예외를 올리지 않는다. 깃허브만으로도 분석할 수 있기 때문이다.
    """
    if not portfolio_url:
        return "", []

    try:
        with httpx.Client(timeout=settings.portfolio_timeout_seconds, follow_redirects=True) as client:
            response = client.get(portfolio_url)
            response.raise_for_status()
            content = response.content
    except httpx.HTTPError as error:
        logger.warning("포트폴리오를 내려받지 못했습니다. %s", error)
        return "", ["포트폴리오 파일을 내려받지 못했습니다."]

    if len(content) > settings.portfolio_max_bytes:
        return "", ["포트폴리오 파일이 너무 큽니다."]

    text, warnings = _to_text(content, file_name or "")
    if len(text) > settings.portfolio_max_chars:
        text = text[: settings.portfolio_max_chars]
        warnings.append("포트폴리오가 길어 앞부분만 분석했습니다.")

    return text, warnings


def _to_text(content: bytes, file_name: str) -> Tuple[str, List[str]]:
    lowered = file_name.lower()

    if lowered.endswith(PDF_SUFFIX):
        return _from_pdf(content)
    if lowered.endswith(TEXT_SUFFIXES):
        return content.decode("utf-8", errors="replace"), []

    # 확장자를 모르면 일단 글로 읽어 본다. 깨지면 아래에서 걸러진다.
    decoded = content.decode("utf-8", errors="replace")
    if decoded.count("�") > len(decoded) * 0.1:
        return "", ["글을 읽을 수 없는 형식입니다. PDF 나 텍스트 파일을 올려주세요."]
    return decoded, []


def _from_pdf(content: bytes) -> Tuple[str, List[str]]:
    try:
        from pypdf import PdfReader
    except ImportError:
        return "", ["PDF 를 읽을 수 없습니다."]

    try:
        reader = PdfReader(io.BytesIO(content))
        pages = [page.extract_text() or "" for page in reader.pages]
    except Exception as error:  # pypdf 는 깨진 파일마다 다른 예외를 낸다
        logger.warning("PDF 를 읽지 못했습니다. %s", error)
        return "", ["PDF 를 읽지 못했습니다."]

    text = "\n".join(page.strip() for page in pages if page.strip())
    if not text:
        # 스캔한 이미지 PDF 는 글이 없다. OCR 은 넣지 않았다.
        return "", ["PDF 에서 글을 찾지 못했습니다. 이미지로 된 파일일 수 있습니다."]
    return text, []
