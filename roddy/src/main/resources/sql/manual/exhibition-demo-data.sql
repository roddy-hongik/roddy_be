-- Roddy 전시용 예시 데이터. 운영 DB에서 전용 시연 계정으로만 실행한다.
-- 지정된 시연용 일반 사용자 계정으로 전체 스크립트를 한 세션에서 실행한다.
-- 모든 공개 글에는 [시연용] 표시를 붙이고, 실제 합격/인터뷰 또는 모집을 주장하지 않는다.
-- 기존 데이터 수정/삭제 없음. 동일 작성자와 제목 기준으로 재실행 시 중복 삽입하지 않는다.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @demo_email = 'jobdri.official@gmail.com';
SET @demo_author = NULL;
SET @demo_author = (SELECT user_id FROM users WHERE email = CONVERT(@demo_email USING utf8mb4) COLLATE utf8mb4_unicode_ci
                    AND role = 'USER' AND deleted_at IS NULL AND suspended_at IS NULL);
SELECT @demo_author AS demo_author_id; -- NULL이면 아무 데이터도 삽입하지 않는다.

START TRANSACTION;

INSERT INTO community_posts (author_id, post_category, job_category, title, content,
                             view_count, like_count, report_count, created_at, updated_at)
SELECT @demo_author, s.category, s.track, s.title, s.content, 0, 0, 0, NOW(), NOW()
FROM (
    SELECT 'FREE' AS category, 'B2C' AS track, '[시연용] Spring Boot 프로젝트에서 테스트 범위 정하기' AS title,
           '전시용 토론 예시입니다. 서비스 계층 단위 테스트와 API 통합 테스트를 어떤 기준으로 나누면 좋을까요?' AS content
    UNION ALL SELECT 'FREE', 'FINTECH', '[시연용] 결제 동시성 테스트 방법', '전시용 토론 예시입니다. 중복 주문을 방지하는 동시성 제어와 트랜잭션 테스트 경험을 나눠 봅시다.'
    UNION ALL SELECT 'FREE', 'INFRA_DEVOPS', '[시연용] Docker 배포 체크리스트', '전시용 토론 예시입니다. 헬스 체크, 로그, 재시작 정책부터 확인하는 배포 체크리스트를 공유합니다.'
    UNION ALL SELECT 'FREE', 'B2B', '[시연용] API 문서와 협업 규칙', '전시용 토론 예시입니다. 요청·응답 계약과 오류 코드의 일관성을 팀에서 어떻게 유지할까요?'
    UNION ALL SELECT 'FREE', 'GENERALIST', '[시연용] 주니어 개발자 기술 학습 순서', '전시용 토론 예시입니다. 작은 프로젝트를 완성한 뒤 부족한 기술을 우선순위로 정하는 방법을 이야기합니다.'
    UNION ALL SELECT 'ROADMAP', 'B2C', '[시연용] Spring Boot 백엔드 학습 로드맵', '전시용 학습 로드맵 예시입니다. REST API부터 데이터 저장 및 배포까지 순서대로 학습합니다.'
    UNION ALL SELECT 'ROADMAP', 'INFRA_DEVOPS', '[시연용] Docker와 CI/CD 학습 로드맵', '전시용 학습 로드맵 예시입니다. 컨테이너부터 자동 빌드와 운영 점검을 학습합니다.'
    UNION ALL SELECT 'ROADMAP', 'FINTECH', '[시연용] 데이터 정합성 학습 로드맵', '전시용 학습 로드맵 예시입니다. 트랜잭션, 잠금, 동시성 테스트를 학습합니다.'
    UNION ALL SELECT 'PASS_REVIEW_INTERVIEW', 'B2C', '[시연용] 백엔드 면접 준비 사례', '가상의 면접 준비 사례입니다. 실제 합격 후기 또는 기업 면접 정보가 아닙니다.'
    UNION ALL SELECT 'PASS_REVIEW_INTERVIEW', 'INFRA_DEVOPS', '[시연용] 운영 장애 대응 면접 사례', '가상의 면접 준비 사례입니다. 장애 분석과 운영 경험을 어떻게 설명하는지 보여주는 예시입니다.'
    UNION ALL SELECT 'PASS_REVIEW_INTERVIEW', 'B2B', '[시연용] API 설계 면접 준비 사례', '가상의 면접 준비 사례입니다. 실제 기업이나 현직자의 인터뷰가 아닙니다.'
) s
WHERE @demo_author IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM community_posts p WHERE p.author_id = @demo_author AND p.title = s.title
);

INSERT INTO community_post_tags (community_post_id, tag)
SELECT p.community_post_id, CASE p.job_category WHEN 'FINTECH' THEN 'MySQL'
    WHEN 'INFRA_DEVOPS' THEN 'Docker' WHEN 'B2B' THEN 'REST API' ELSE 'Spring Boot' END
FROM community_posts p WHERE p.author_id = @demo_author AND p.title LIKE '[시연용] %'
AND NOT EXISTS (SELECT 1 FROM community_post_tags t WHERE t.community_post_id = p.community_post_id
 AND t.tag = CASE p.job_category WHEN 'FINTECH' THEN 'MySQL' WHEN 'INFRA_DEVOPS' THEN 'Docker'
                 WHEN 'B2B' THEN 'REST API' ELSE 'Spring Boot' END);

INSERT INTO community_roadmap_post_details (community_post_id, roadmap_id, roadmap_title, summary, description,
                                            target_job, target_company)
SELECT p.community_post_id, NULL, p.title, '기초부터 실전까지 3단계 학습 계획', p.content,
       '백엔드 개발자', NULL
FROM community_posts p WHERE p.author_id = @demo_author AND p.post_category = 'ROADMAP'
AND p.title IN ('[시연용] Spring Boot 백엔드 학습 로드맵', '[시연용] Docker와 CI/CD 학습 로드맵', '[시연용] 데이터 정합성 학습 로드맵')
AND NOT EXISTS (SELECT 1 FROM community_roadmap_post_details d WHERE d.community_post_id = p.community_post_id);

INSERT INTO community_roadmap_post_steps (community_roadmap_post_detail_id, display_order, stage, goal)
SELECT d.community_roadmap_post_detail_id, s.n, s.stage,
       CASE p.job_category
         WHEN 'INFRA_DEVOPS' THEN CASE s.n WHEN 1 THEN 'Docker 이미지와 컨테이너 이해' WHEN 2 THEN 'CI/CD 및 로그 관리 실습' ELSE '서비스 배포 및 장애 복구' END
         WHEN 'FINTECH' THEN CASE s.n WHEN 1 THEN '트랜잭션과 격리 수준 이해' WHEN 2 THEN '잠금과 동시성 제어 실습' ELSE '중복 주문 방지 테스트' END
         ELSE CASE s.n WHEN 1 THEN 'REST API와 JPA 기초' WHEN 2 THEN '서비스 계층 테스트와 예외 처리' ELSE 'API 서비스 배포' END
       END
FROM community_roadmap_post_details d JOIN community_posts p ON p.community_post_id = d.community_post_id
CROSS JOIN (SELECT 1 AS n, '기초' AS stage UNION ALL SELECT 2, '심화' UNION ALL SELECT 3, '실전 프로젝트') s
WHERE p.author_id = @demo_author AND p.title IN ('[시연용] Spring Boot 백엔드 학습 로드맵', '[시연용] Docker와 CI/CD 학습 로드맵', '[시연용] 데이터 정합성 학습 로드맵')
AND NOT EXISTS (SELECT 1 FROM community_roadmap_post_steps x WHERE x.community_roadmap_post_detail_id = d.community_roadmap_post_detail_id AND x.display_order = s.n);

INSERT INTO community_roadmap_post_recommended_skills (community_roadmap_post_detail_id, recommended_skill)
SELECT d.community_roadmap_post_detail_id, t.tag FROM community_roadmap_post_details d
JOIN community_posts p ON p.community_post_id = d.community_post_id JOIN community_post_tags t ON t.community_post_id = p.community_post_id
WHERE p.author_id = @demo_author AND p.title IN ('[시연용] Spring Boot 백엔드 학습 로드맵', '[시연용] Docker와 CI/CD 학습 로드맵', '[시연용] 데이터 정합성 학습 로드맵')
AND NOT EXISTS (SELECT 1 FROM community_roadmap_post_recommended_skills x WHERE x.community_roadmap_post_detail_id = d.community_roadmap_post_detail_id AND x.recommended_skill = t.tag);

INSERT INTO community_roadmap_post_step_topics (community_roadmap_post_step_id, topic)
SELECT s.community_roadmap_post_step_id, s.goal FROM community_roadmap_post_steps s
JOIN community_roadmap_post_details d ON d.community_roadmap_post_detail_id = s.community_roadmap_post_detail_id
JOIN community_posts p ON p.community_post_id = d.community_post_id
WHERE p.author_id = @demo_author AND p.title IN ('[시연용] Spring Boot 백엔드 학습 로드맵', '[시연용] Docker와 CI/CD 학습 로드맵', '[시연용] 데이터 정합성 학습 로드맵')
AND NOT EXISTS (SELECT 1 FROM community_roadmap_post_step_topics x WHERE x.community_roadmap_post_step_id = s.community_roadmap_post_step_id AND x.topic = s.goal);

INSERT INTO community_roadmap_post_step_outputs (community_roadmap_post_step_id, output)
SELECT s.community_roadmap_post_step_id, CONCAT(s.stage, ' 학습 결과 및 실습 기록') FROM community_roadmap_post_steps s
JOIN community_roadmap_post_details d ON d.community_roadmap_post_detail_id = s.community_roadmap_post_detail_id
JOIN community_posts p ON p.community_post_id = d.community_post_id
WHERE p.author_id = @demo_author AND p.title IN ('[시연용] Spring Boot 백엔드 학습 로드맵', '[시연용] Docker와 CI/CD 학습 로드맵', '[시연용] 데이터 정합성 학습 로드맵')
AND NOT EXISTS (SELECT 1 FROM community_roadmap_post_step_outputs x WHERE x.community_roadmap_post_step_id = s.community_roadmap_post_step_id AND x.output = CONCAT(s.stage, ' 학습 결과 및 실습 기록'));

INSERT INTO community_interview_post_details (community_post_id, subtype, company, job_role, preparation_period,
    process_summary, background, preparation_process, experience_detail, advice)
SELECT p.community_post_id, 'INCUMBENT', '가상 기업(시연용)', '백엔드 개발자', '4주(예시)',
       '서류와 기술 면접 준비 예시', '개인 프로젝트 경험을 정리하는 가상 사례',
       '기술 개념 정리, 프로젝트 회고, 예상 질문 연습', p.content, '구현 이유와 선택의 근거를 설명해 보세요.'
FROM community_posts p WHERE p.author_id = @demo_author AND p.post_category = 'PASS_REVIEW_INTERVIEW'
AND p.title IN ('[시연용] 백엔드 면접 준비 사례', '[시연용] 운영 장애 대응 면접 사례', '[시연용] API 설계 면접 준비 사례')
AND NOT EXISTS (SELECT 1 FROM community_interview_post_details d WHERE d.community_post_id = p.community_post_id);

INSERT INTO community_interview_post_tech_stacks (community_interview_post_detail_id, tech_stack)
SELECT d.community_interview_post_detail_id, t.tag FROM community_interview_post_details d
JOIN community_posts p ON p.community_post_id = d.community_post_id JOIN community_post_tags t ON t.community_post_id = p.community_post_id
WHERE p.author_id = @demo_author AND p.title IN ('[시연용] 백엔드 면접 준비 사례', '[시연용] 운영 장애 대응 면접 사례', '[시연용] API 설계 면접 준비 사례')
AND NOT EXISTS (SELECT 1 FROM community_interview_post_tech_stacks x WHERE x.community_interview_post_detail_id = d.community_interview_post_detail_id AND x.tech_stack = t.tag);

INSERT INTO community_comments (community_post_id, author_id, content, parent_comment_id, created_at, updated_at)
SELECT p.community_post_id, @demo_author, '[시연용 댓글] 학습 목표와 실습 결과를 함께 기록하면 좋겠습니다.', NULL, NOW(), NOW()
FROM community_posts p WHERE p.author_id = @demo_author AND p.title IN ('[시연용] Spring Boot 프로젝트에서 테스트 범위 정하기', '[시연용] Docker 배포 체크리스트', '[시연용] Spring Boot 백엔드 학습 로드맵')
AND NOT EXISTS (SELECT 1 FROM community_comments c WHERE c.community_post_id = p.community_post_id AND c.author_id = @demo_author AND c.content = '[시연용 댓글] 학습 목표와 실습 결과를 함께 기록하면 좋겠습니다.');

INSERT INTO study_posts (author_id, title, content, mode, location, scheduled_at, capacity, applicant_count, status, created_at, updated_at)
SELECT @demo_author, s.title, s.content, s.mode, s.location, DATE_ADD(NOW(), INTERVAL s.days DAY), s.capacity, 0, 'RECRUITING', NOW(), NOW()
FROM (SELECT '[시연용] Spring Boot API 스터디' AS title, '전시용 모집 예시이며 실제 모집이 아닙니다. REST API 구현과 코드 리뷰를 연습합니다.' AS content,
             'ONLINE' AS mode, '온라인(예시)' AS location, 7 AS days, 5 AS capacity
      UNION ALL SELECT '[시연용] SQL 및 인덱스 스터디', '전시용 모집 예시이며 실제 모집이 아닙니다. 실행 계획과 인덱스를 학습합니다.', 'OFFLINE', '교내 스터디룸(예시)', 8, 4
      UNION ALL SELECT '[시연용] 코딩 테스트 스터디', '전시용 모집 예시이며 실제 모집이 아닙니다. 매주 알고리즘 문제를 풉니다.', 'ONLINE', '온라인(예시)', 9, 6
      UNION ALL SELECT '[시연용] Docker 배포 스터디', '전시용 모집 예시이며 실제 모집이 아닙니다. 배포 및 모니터링을 실습합니다.', 'ONLINE', '온라인(예시)', 10, 4
      UNION ALL SELECT '[시연용] 기술 면접 스터디', '전시용 모집 예시이며 실제 모집이 아닙니다. 기술 질문에 답변하는 연습을 진행합니다.', 'OFFLINE', '교내 스터디룸(예시)', 11, 5) s
WHERE @demo_author IS NOT NULL AND NOT EXISTS (SELECT 1 FROM study_posts p WHERE p.author_id = @demo_author AND p.title = s.title);

-- 실행 성공을 확인한 뒤 COMMIT을 실행한다. 오류가 있으면 ROLLBACK을 실행한다.
SELECT post_category, COUNT(*) AS demo_posts FROM community_posts WHERE author_id = @demo_author AND title LIKE '[시연용] %' GROUP BY post_category;
SELECT COUNT(*) AS demo_study_posts FROM study_posts WHERE author_id = @demo_author AND title LIKE '[시연용] %';
-- COMMIT;
-- ROLLBACK;
