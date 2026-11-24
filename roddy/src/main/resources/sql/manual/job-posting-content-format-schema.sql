-- 본문 형식 표시를 추가한다. prod(ddl-auto=validate) 배포 전에 실행한다.
-- 컬럼이 이미 추가된 환경에서는 ALTER 문을 생략하고 UPDATE 문만 실행한다.
ALTER TABLE job_postings ADD COLUMN content_html BOOLEAN NULL;

-- 기존 수집 명세의 description 원본 형식을 기준으로 채운다.
-- HTML 상세 DOM에서 텍스트로 추출한 공고(ably, bucketplace, kakaobank, naver)와 NHN의 구조화 텍스트는 false.
UPDATE job_postings
SET content_html = CASE WHEN company_code IN (
    'coupang', 'daangn', 'gccompany', 'doodlin', 'kurly', 'musinsa', 'upstage', 'wrtn',
    'socar', 'zigbang', 'kakaopay', 'woowahan', 'lineplus', 'kakao', 'toss'
) THEN TRUE ELSE FALSE END
WHERE content_html IS NULL;
