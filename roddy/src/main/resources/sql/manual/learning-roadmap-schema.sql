-- 기존 roadmaps 테이블은 사용되지 않는 초기 모델이라 그대로 둔다.
-- 새 계약은 생성 당시의 분석 요약과 단계별 배열을 보존해야 하므로 별도 테이블을 쓴다.

CREATE TABLE learning_roadmaps (
    roadmap_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    target_job VARCHAR(50) NOT NULL,
    target_company VARCHAR(255) NULL,
    fingerprint VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (roadmap_id),
    CONSTRAINT fk_learning_roadmap_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT uk_roadmap_user_fingerprint UNIQUE (user_id, fingerprint)
);

CREATE TABLE learning_roadmap_current_skills (
    roadmap_id BIGINT NOT NULL,
    skill_order INT NOT NULL,
    skill VARCHAR(100) NOT NULL,
    PRIMARY KEY (roadmap_id, skill_order),
    CONSTRAINT fk_learning_roadmap_current_skill FOREIGN KEY (roadmap_id)
        REFERENCES learning_roadmaps (roadmap_id) ON DELETE CASCADE
);

CREATE TABLE learning_roadmap_gap_skills (
    roadmap_id BIGINT NOT NULL,
    skill_order INT NOT NULL,
    skill VARCHAR(100) NOT NULL,
    PRIMARY KEY (roadmap_id, skill_order),
    CONSTRAINT fk_learning_roadmap_gap_skill FOREIGN KEY (roadmap_id)
        REFERENCES learning_roadmaps (roadmap_id) ON DELETE CASCADE
);

CREATE TABLE learning_roadmap_steps (
    roadmap_step_id BIGINT NOT NULL AUTO_INCREMENT,
    roadmap_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    stage VARCHAR(30) NOT NULL,
    goal TEXT NOT NULL,
    PRIMARY KEY (roadmap_step_id),
    CONSTRAINT uk_learning_roadmap_step_order UNIQUE (roadmap_id, step_order),
    CONSTRAINT fk_learning_roadmap_step FOREIGN KEY (roadmap_id)
        REFERENCES learning_roadmaps (roadmap_id) ON DELETE CASCADE
);

CREATE TABLE learning_roadmap_step_topics (
    roadmap_step_id BIGINT NOT NULL,
    topic_order INT NOT NULL,
    topic VARCHAR(500) NOT NULL,
    PRIMARY KEY (roadmap_step_id, topic_order),
    CONSTRAINT fk_learning_roadmap_step_topic FOREIGN KEY (roadmap_step_id)
        REFERENCES learning_roadmap_steps (roadmap_step_id) ON DELETE CASCADE
);

CREATE TABLE learning_roadmap_step_outputs (
    roadmap_step_id BIGINT NOT NULL,
    output_order INT NOT NULL,
    output_text VARCHAR(500) NOT NULL,
    PRIMARY KEY (roadmap_step_id, output_order),
    CONSTRAINT fk_learning_roadmap_step_output FOREIGN KEY (roadmap_step_id)
        REFERENCES learning_roadmap_steps (roadmap_step_id) ON DELETE CASCADE
);
