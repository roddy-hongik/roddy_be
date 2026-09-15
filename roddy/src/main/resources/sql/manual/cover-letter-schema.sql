-- prod에서는 애플리케이션 배포 전에 적용한다. dev는 ddl-auto=update를 사용한다.
CREATE TABLE cover_letters (
    cover_letter_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    job_posting_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (cover_letter_id),
    INDEX idx_cover_letter_user_updated (user_id, updated_at),
    CONSTRAINT fk_cover_letter_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_cover_letter_job FOREIGN KEY (job_posting_id) REFERENCES job_postings (job_posting_id)
);
CREATE TABLE cover_letter_answers (
    cover_letter_id BIGINT NOT NULL,
    answer_order INT NOT NULL,
    question VARCHAR(1000) NOT NULL,
    answer TEXT NOT NULL,
    PRIMARY KEY (cover_letter_id, answer_order),
    CONSTRAINT fk_cover_letter_answer FOREIGN KEY (cover_letter_id)
        REFERENCES cover_letters (cover_letter_id) ON DELETE CASCADE
);
