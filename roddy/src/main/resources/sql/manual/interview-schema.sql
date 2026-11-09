-- prod에서는 애플리케이션 배포 전에 적용한다. dev는 ddl-auto=update를 사용한다.
CREATE TABLE interview_sessions (
    interview_session_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (interview_session_id),
    INDEX idx_interview_session_user_created (user_id, created_at),
    CONSTRAINT fk_interview_session_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
CREATE TABLE interview_session_answers (
    interview_session_id BIGINT NOT NULL,
    answer_order INT NOT NULL,
    question_key VARCHAR(50) NOT NULL,
    question VARCHAR(1000) NOT NULL,
    intent VARCHAR(500) NOT NULL,
    key_points VARCHAR(1000) NOT NULL,
    answer TEXT NOT NULL,
    feedback TEXT NOT NULL,
    score INT NOT NULL,
    PRIMARY KEY (interview_session_id, answer_order),
    CONSTRAINT fk_interview_session_answer FOREIGN KEY (interview_session_id)
        REFERENCES interview_sessions (interview_session_id) ON DELETE CASCADE
);
