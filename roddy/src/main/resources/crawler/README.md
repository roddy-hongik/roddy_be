# 채용공고 수집 명세

회사별 채용 사이트에서 공고를 어떻게 받아올지 적어둔 YAML 명세다.
회사별 지식은 전부 여기 있고, `com.roddy.global.crawler` 의 엔진에는 회사 이름이 하나도 없다.
사이트가 개편되면 자바 코드가 아니라 이 명세를 고친다.

```
crawler/
├── specs/      회사별 명세. 파일명이 곧 회사 코드다 (kakao.yaml → company: kakao)
└── templates/  ATS 템플릿. __SUBDOMAIN__ 을 회사값으로 치환해 새 명세를 만든다
```

## 명세는 어디서 오나

[jobai-project/jobai-crawler-agents](https://github.com/jobai-project/jobai-crawler-agents) 의
분석 에이전트(LangGraph + Claude)가 회사 채용 URL 하나를 받아 자동 생성한다.
에이전트는 개발 시점에만 쓰는 도구이고, 운영에는 필요 없다.

```bash
python -m agent.verify_agent kakao https://careers.kakao.com/jobs --save
```

회사를 추가하거나 사이트 개편으로 수집이 깨졌을 때만 돌려서, 나온 YAML 을 `specs/` 에 커밋한다.
그래서 런타임에는 파이썬도 LLM API 키도 필요 없다.

## 명세 형태

```yaml
company: kakao                 # 회사 코드 (파일명과 같아야 한다)
company_name_ko: 카카오          # 표시용 회사명
entry_url: https://...         # 사람이 보는 채용 페이지 (수집에는 쓰지 않는다)
source_type: json              # json | embedded_json | html(미지원)

list:
  url: https://careers.kakao.com/public/api/job-list
  method: GET                  # 본문이 있으면 기본값이 POST
  params: {}                   # 쿼리 파라미터
  headers: {}                  # 요청 헤더
  body: {}                     # POST 본문
  response_path: jobList       # 응답에서 공고 배열의 위치
  record_path: primary_job     # 각 항목에서 한 단계 더 들어가야 할 때
  script_id: __NEXT_DATA__     # embedded_json 에서 JSON 이 박힌 script 태그
  select:                      # 배열에서 조건에 맞는 항목 하나를 고를 때
    array: props.pageProps.dehydratedState.queries
    match_field: queryKey
    match_value: ["openings"]  # 또는 match_prefix: [career, getOpeningById]
    take: state.data

pagination:
  type: none                   # none | page_number | offset
  param: page
  start: 1
  total_pages_path: totalPage

required: [job_id, title]      # 비어 있으면 안 되는 필드 (수집 후 점검)

fields:                        # 결과 컬럼 → 응답 경로
  job_id: realId
  title: jobOfferTitle
  description:                 # 본문이 쪼개져 있으면 여러 경로를 이어붙인다
    - introduction
    - workContentDesc
  location: jobLocations[].name    # 배열의 각 항목에서 꺼내기

apply_url:
  template: "https://careers.kakao.com/jobs/{job_id}"   # 중괄호에 필드 값을 끼운다

filter:                        # 공고가 아닌 항목 제외
  field: title
  exclude_contains: [인재풀, Talent Pool]

null_values:                   # 값에 이 문자열이 있으면 null 로 본다
  deadline: "2999"

metadata_extraction:           # [{name, value}] 배열에서 이름으로 찾아 필드로 올린다
  source_field: metadata
  match_by: name
  value_from: value
  mappings:
    employment_type: "Employment_Type"

extra:                         # 정규화 대상은 아니지만 원본으로 남길 값
  d_day: deadlineDDay

detail:                        # 공고마다 상세 페이지를 한 번 더 받아 본문 채우기
  enabled: true
  source_type: html            # json | embedded_json | html
  url_template: https://career.woowahan.com/w1/recruits/{recruit_number}
  url_from: apply_url          # url_template 이 없을 때 쓸 레코드 필드 (기본값 apply_url)
  script_id: __NEXT_DATA__     # embedded_json 일 때
  select: {...}                # 목록과 같은 형태
  fields:                      # json / embedded_json 일 때
    description: data.recruitContents
  body_selector: .desc_cont    # html 일 때 본문 요소
  section_box: div.detail_box  # 제목이 붙은 섹션이 반복되는 요소
  section_title: h4.detail_title
```

## 주의

- 대부분의 회사는 마감 여부를 알려주지 않는다. 목록에서 공고가 사라지는 것으로 마감을 판정해야 한다.
- 목록 응답에 본문을 주는 회사는 얼마 없다. 본문은 `detail` 수집으로 채워진다.
- `detail` 은 공고 수만큼 요청이 나간다. 요청 사이를 띄우고, 공고 하나가 실패해도 나머지는 계속 받는다.
- 회사 사이트에 부담을 주지 않도록, 수집은 회사 단위로 순차 실행한다.
- 목록의 `source_type: html` 은 아직 지원하지 않는다. 쓰는 회사가 없어서다
  (상세의 `source_type: html` 은 지원한다).
