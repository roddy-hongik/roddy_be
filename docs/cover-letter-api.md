# 자기소개서 문서·문항 관리

## 범위
- 로그인한 사용자의 자기소개서 작성, 목록, 상세, 전체 수정, 삭제.
- 공고 연결은 선택 사항이며 수정 시 변경하거나 해제할 수 있다.
- 제목은 1~255자, 문항은 1~20개이며 문항 내용은 1~1,000자다.
- 답변은 빈 문자열도 허용하며 최대 10,000자다. 답변의 공백·줄바꿈은 보존한다.
- 문항은 독립 리소스가 아니라 문서에 종속된 값으로 저장한다. 배열 순서가 문항 순서다.
- 관리자도 이 API에서는 본인의 문서만 접근할 수 있다.

## API
모든 요청은 Authorization: Bearer <accessToken>이 필요하다. 공통 ApiResponse 형식을 사용한다.

| Method | 경로 | 설명 |
|---|---|---|
| GET | /api/cover-letters?page=0&size=20 | 본인 문서만 수정일·ID 내림차순, size 1~100 |
| POST | /api/cover-letters | 작성, 초기 version=0 반환 |
| GET | /api/cover-letters/{id} | 상세와 문항 배열 조회 |
| PUT | /api/cover-letters/{id} | 조회한 version 및 문항 배열 전체로 교체 |
| DELETE | /api/cover-letters/{id}?version=0 | 해당 버전 문서와 문항 삭제 |

### 작성·수정 요청
```json
{
  "title": "백엔드 개발자 지원",
  "jobPostingId": null,
  "version": 0,
  "answers": [
    {"question": "지원 동기", "answer": ""},
    {"question": "문제 해결 경험", "answer": "프로젝트에서..."}
  ]
}
```
작성 시 version은 사용하지 않는다. 수정 시 반드시 마지막 조회/저장 응답의 version을 보낸다.
PUT에서 jobPostingId를 null로 보내거나 생략하면 연결을 해제한다.
answers에서 빠진 문항은 삭제되고 새 배열 순서대로 저장된다.

상세 result: id, title, job({id,title,company} 또는 null), version, answers, createdAt, updatedAt.
목록 result: coverLetters([{id,title,job,updatedAt}]), page, size, totalElements, totalPages.

### 오류
- 401: 인증 필요
- 404 COVER_LETTER_4041: 없는 문서 또는 타인 문서
- 404 JOB_4041: 연결 대상 공고 없음
- 409 COVER_LETTER_4091: 다른 창에서 수정/삭제되어 버전이 맞지 않음
- 400 REQ_4002: 입력 검증 실패 또는 수정 version 누락

409 응답에서는 편집 중인 입력을 유지한다. 사용자는 작성 내용을 복사한 뒤 최신 문서를 다시 열어 비교해야 한다.

## 저장 구조와 배포
- cover_letters: 소유 사용자, 선택 공고, 제목, 낙관적 잠금 version, 생성/수정 시각.
- cover_letter_answers: 문서 ID와 순서로 구분한 문항·답변.
- dev는 기존 ddl-auto=update를 따른다.
- prod는 배포 전에 roddy/src/main/resources/sql/manual/cover-letter-schema.sql을 적용한다.
- 기존 Question Entity 및 데이터는 변경하지 않는다.

## 프론트 연결
- /cover-letters: 본인 목록과 페이지 이동.
- /cover-letters/new: 새 문서 작성.
- /cover-letters/:letterId: 문서 조회·편집·삭제.
- /cover-letters/new?jobPostingId=ID: 공고가 선택된 새 문서.
- 진입점: 마이페이지의 내 자기소개서 관리, 공고 상세의 이 공고의 자기소개서 작성.
- 명시적 저장 방식이다. 목록으로 돌아가기와 브라우저 새로고침/종료 시 미저장 변경을 안내한다.
- 기존 mock 로그인은 이번 기능에서 수정하지 않았다. 실제 API 사용에는 유효한 서버 토큰과 VITE_API_BASE_URL 설정이 필요하다.

## 검증
CoverLetterControllerTest에서 소유자 분리, 인증, 빈 답변 저장, 문항 순서·교체, 줄바꿈 보존,
공고 연결/해제, 잘못된 입력, 페이지 조회, 버전 충돌, 문항 연쇄 삭제를 확인한다.
