### commit convention
- **`Feat`**: 새로운 기능 추가 (예: 로그인 기능 구현)
- **`Fix`**: 버그 수정
- **`Design`**: CSS 등 사용자 UI 디자인 변경
- **`Style`**: 코드 포맷팅, 세미콜론 누락 등 (로직 변경 없음)
- **`Refactor`**: 코드 리팩토링 (기능 변경 없이 구조만 개선)
- **`Docs`**: 문서 수정 (README.md 등)
- **`Chore`**: 빌드 업무, 패키지 매니저 설정, 폴더 구조 변경
- **`Rename`**: 파일 혹은 폴더명 수정/이동
ex) [Feat] 로그인 기능 구현

### branch convention
- **`main`** : 언제든 배포 가능한 제품 상태.
- **`dev`**: 다음 출시 버전을 개발하는 통합 브랜치.
- **`feat/기능명`**: 새로운 기능 개발 (예: `feat/login`, `feat/roadmap-graph`).
- **`fix/버그명`**: 급한 버그 수정.
- **`refactor/수정부분`**: 기능 변경 없는 코드 개선.

### code naming convention
- **Class / Interface**: PascalCase 사용함. (예: UserRepository, RoadmapService)
- **Method**: camelCase 사용함. 동사로 시작하는 것을 권장함. (예: getUserInfo(), saveRoadmap())
- **Constant (상수)**: SCREAMING_SNAKE_CASE 사용함. (예: MAX_RETRY_COUNT, DEFAULT_PAGE_SIZE)
- **Enum**: 클래스와 동일하게 PascalCase를 사용하되, 내부 값은 SCREAMING_SNAKE_CASE로 작성함.
- **Boolean 변수**: is, has, can 등의 접두사를 붙여 질문 형태의 이름을 가짐. (예: isDeleted, hasRole)

### REST API 설계 컨벤션 (RESTful API)

프론트엔드와 백엔드 간의 효율적인 통신을 위한 설계 규칙임.

URL 구조
* **URL Path** : **kebab-case** 사용함. 소문자만 사용하며 단어 사이는 하이픈으로 구분함. (예: `/api/v1/user-profiles`)
* **Resource 명사화** : URL에는 행위(동사)가 아닌 자원(명사)을 표시하며, **복수형** 사용을 지향함.
* **Good**: `GET /roadmaps`, `POST /users`
* **Bad**: `GET /getRoadmap`, `POST /create-user`

HTTP Method 활용
* **GET** : 리소스를 조회함.
* **POST** : 새로운 리소스를 생성함.
* **PUT** : 리소스를 전체적으로 수정(덮어쓰기)함.
* **PATCH** : 리소스의 일부를 수정함.
* **DELETE** : 리소스를 삭제함.

### 데이터베이스 네이밍 (DB Naming)

RDB와 Graph DB의 특성을 반영한 명명 규칙임.

* **Table / Column (MySQL)** : **snake_case** 사용함. 테이블명은 복수형을 사용함. (예: `user_roles`, `created_at`)
* **Node Label (Neo4j)** : **PascalCase** 사용함. (예: `:TechnologyStack`)
* **Relationship Type (Neo4j)** : **SCREAMING_SNAKE_CASE** 사용함. (예: `[:REQUIRED_FOR]`)
