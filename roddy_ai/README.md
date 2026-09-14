# roddy-ai

사용자의 깃허브와 포트폴리오를 읽어 **역량 분석 리포트**를 만들고, 부족한 기술을 채우는 **학습 로드맵**과 그 기술을 확인하는 **모의면접 질문**을 만드는 내부 서비스.

백엔드만 호출한다. 외부로 포트를 열지 않고, 같은 도커 네트워크 안에서 `http://ai:8000` 으로 불린다.

```
roddy_ai/
├── app/
│   ├── main.py        FastAPI 앱과 라우트
│   ├── schemas.py     백엔드와의 계약 (요청/응답 모양)
│   ├── security.py    내부 호출 확인
│   ├── github.py      공개 저장소 수집
│   ├── portfolio.py   포트폴리오 글 추출
│   ├── analyzer.py    OpenAI 호출과 리포트 조립
│   ├── roadmap.py     OpenAI 호출과 학습 로드맵 조립
│   ├── interview.py   OpenAI 호출과 모의면접 질문 조립
│   └── config.py      환경변수
├── Dockerfile
└── requirements.txt
```

## API

### `POST /internal/analyses`

헤더에 `X-Internal-Secret` 이 있어야 한다. 값이 다르면 401.

```json
{
  "user_id": 1,
  "github_url": "https://github.com/octocat",
  "portfolio_url": "https://s3.../resume.pdf?X-Amz-Signature=...",
  "portfolio_file_name": "resume.pdf",
  "desired_job": "BACKEND",
  "experience_years": "JUNIOR",
  "categories": [
    {
      "code": "DATA_MODELING",
      "name": "효율적인 데이터 설계 및 최적화",
      "description": "기업은 단순히 DB를 사용하는 것을 넘어, 성능을 고려한 설계를 할 수 있는지를 봅니다."
    }
  ]
}
```

```json
{
  "title": "백엔드 기초를 갖춘 주니어",
  "total_score": 64,
  "summary": "...",
  "github_analysis": "...",
  "portfolio_analysis": "...",
  "stacks": [
    {
      "name": "Java",
      "score": 72,
      "level": "INTERMEDIATE",
      "description": "...",
      "category": "DATA_MODELING",
      "found_in": ["GITHUB", "PORTFOLIO"]
    }
  ],
  "categories": [
    { "code": "DATA_MODELING", "score": 58, "interpretation": "..." }
  ],
  "sources": {
    "repository_count": 12,
    "portfolio_included": true,
    "warnings": ["저장소가 많아 최근 20개만 분석했습니다."]
  }
}
```

`stacks[].name` 은 표준화하지 않은 이름이다. **표준 이름으로 맞추는 일은 백엔드의 기술스택 사전**
(`resources/jobposting/tech-stacks.yaml`)이 맡는다. 공고에서 뽑은 기술과 같은 사전을 거쳐야 서로
이어지기 때문이다.

`level` 은 백엔드의 `StackLevel` 과 같은 값을 쓴다: `BEGINNER` / `INTERMEDIATE` / `ADVANCED` / `EXPERT`.

`categories` 는 **백엔드가 직무별로 정한 평가 축**이다. 축을 정하지 않은 직무는 빈 목록으로 오고,
그러면 응답에도 축별 점수(`categories`)와 기술별 축(`stacks[].category`)이 비어 있다.

`stacks[].found_in` 은 그 기술의 근거를 찾은 곳이다: `GITHUB` / `PORTFOLIO`.

### `POST /internal/roadmaps`

헤더에 `X-Internal-Secret` 이 있어야 한다. 값이 다르면 401. `gap_skills` 가 비어 있으면 422.

```json
{
  "current_skills": ["Java", "Spring Boot"],
  "gap_skills": ["Redis", "Kafka"],
  "target_job": "백엔드 개발자",
  "target_company": "토스"
}
```

```json
{
  "title": "백엔드 성장 로드맵",
  "steps": [
    { "stage": "기초", "goal": "...", "topics": ["..."], "outputs": ["..."] },
    { "stage": "심화", "goal": "...", "topics": ["..."], "outputs": ["..."] },
    { "stage": "실전 프로젝트", "goal": "...", "topics": ["..."], "outputs": ["..."] }
  ]
}
```

`steps` 는 늘 `기초` / `심화` / `실전 프로젝트` 순서의 세 단계다. 백엔드는 이 순서가 아니면 응답을 버린다.
그래서 LLM 에는 단계를 배열이 아니라 이름 붙은 속성으로 받고, 단계 이름과 순서는 서버가 붙인다.

### `POST /internal/interview-questions`

헤더에 `X-Internal-Secret` 이 있어야 한다. 값이 다르면 401. 요청은 `/internal/roadmaps` 와 같은 모양이고, `gap_skills` 가 비어 있으면 422.

```json
{
  "questions": [
    { "id": "q1", "question": "...", "intent": "...", "key_points": ["...", "..."] },
    { "id": "q2", "question": "...", "intent": "...", "key_points": ["..."] },
    { "id": "q3", "question": "...", "intent": "...", "key_points": ["..."] }
  ]
}
```

`questions` 는 늘 `q1`~`q3` 세 개다. 백엔드는 개수가 다르거나 id·질문이 겹치면 응답을 버린다.
그래서 LLM 에는 질문을 배열이 아니라 `q1`~`q3` 속성으로 받고, 개수와 id 는 서버가 정한다.

### `GET /health`

컨테이너가 떠 있는지만 본다. 외부 의존성을 건드리지 않는다.

## 설계에서 정한 것

**자료가 없으면 LLM 을 부르지 않는다.** 깃허브와 포트폴리오를 모두 읽지 못하면 그 사실을 담은
리포트를 돌려준다. 근거 없이 지어낸 진단을 주지 않기 위해서다.

**한쪽만 있어도 분석한다.** 깃허브를 연결하지 않았거나 포트폴리오를 올리지 않은 사용자가 많다.

**부분 실패를 삼키지 않는다.** 깃허브 호출 한도를 넘었거나 PDF 에서 글을 못 찾은 경우를
`sources.warnings` 에 담아 올린다. 리포트가 빈약한 이유를 사용자가 알 수 있어야 한다.

**S3 자격증명을 갖지 않는다.** 포트폴리오는 백엔드가 만든 presigned URL 로만 읽는다. 어떤 파일을
읽을지 정하는 권한은 백엔드에 둔다.

**평가 축은 백엔드가 정한다.** LLM 이 축을 매번 새로 지으면 리포트끼리 점수를 견줄 수 없다. 축 code 를
응답 스키마의 enum 으로 묶어 모르는 축을 지어내지 못하게 하고, 그래도 받은 축에 없는 값이 오면 버린다.

**빠뜨린 축을 0점으로 채우지 않는다.** 0점은 "못한다"로 읽히는데, 실제로는 판단하지 않은 것이다.
응답의 `categories` 에서 빼고 로그를 남긴다.

## 환경변수

| 이름 | 필수 | 설명 |
| :--- | :---: | :--- |
| `INTERNAL_API_SECRET` | ✅ | 백엔드와 나눠 갖는 비밀값 |
| `OPENAI_API_KEY` | ✅ | 리포트 생성 |
| `OPENAI_MODEL` | | 기본 `gpt-4o-mini` |
| `GITHUB_TOKEN` | | 없으면 공개 API 가 시간당 60회로 묶인다. 넣으면 5000회 |
| `GITHUB_MAX_REPOSITORIES` | | 기본 20. 저장소마다 언어 구성을 따로 물어봐야 해서 제한한다 |
| `PORTFOLIO_MAX_CHARS` | | 기본 20000. 이력서 전체를 넣어도 정확도가 나아지지 않는다 |

## 로컬에서 혼자 띄우기

레포 루트의 `docker compose up` 이 보통이지만, 이 서비스만 띄울 수도 있다.

```bash
cd roddy_ai
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

export INTERNAL_API_SECRET=local-secret
export OPENAI_API_KEY=sk-...
uvicorn app.main:app --reload --port 8000
```

```bash
curl -X POST http://localhost:8000/internal/analyses \
  -H 'Content-Type: application/json' \
  -H 'X-Internal-Secret: local-secret' \
  -d '{"user_id": 1, "github_url": "https://github.com/octocat"}'
```

## 테스트

LLM 은 부르지 않는다. OpenAI 호출을 가짜로 바꿔 스키마와 응답 조립만 확인한다.

```bash
cd roddy_ai
pip install -r requirements-dev.txt
python -m unittest discover -s tests -t .
```

## 아직 안 한 것

- **깃허브 코드 내용은 읽지 않는다.** 저장소 이름·설명·언어 구성·토픽까지만 본다. 코드를 읽으려면
  토큰과 호출 한도가 더 필요하다.
- **이미지로 된 PDF 는 읽지 못한다.** OCR 을 넣지 않았다.
- **분석 결과를 캐시하지 않는다.** 같은 사용자를 다시 분석하면 LLM 을 다시 부른다.
