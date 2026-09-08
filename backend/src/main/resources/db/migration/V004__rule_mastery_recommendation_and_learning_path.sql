CREATE TABLE student_knowledge_mastery (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    mastery_score DECIMAL(5,4) NULL,
    source_type VARCHAR(16) NOT NULL,
    algorithm_version VARCHAR(64) NOT NULL,
    last_answered_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_knowledge_mastery_student_point (student_id, knowledge_point_id),
    KEY idx_student_knowledge_mastery_course_student (course_id, student_id, knowledge_point_id),
    KEY idx_student_knowledge_mastery_weak (student_id, course_id, mastery_score),
    CONSTRAINT chk_student_knowledge_mastery_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_student_knowledge_mastery_correct_count CHECK (correct_count >= 0 AND correct_count <= attempt_count),
    CONSTRAINT chk_student_knowledge_mastery_score CHECK (mastery_score IS NULL OR (mastery_score >= 0 AND mastery_score <= 1)),
    CONSTRAINT fk_student_knowledge_mastery_student FOREIGN KEY (student_id) REFERENCES sys_user(id),
    CONSTRAINT fk_student_knowledge_mastery_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_student_knowledge_mastery_point FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mastery_processed_answer (
    answer_record_id BIGINT NOT NULL,
    algorithm_version VARCHAR(64) NOT NULL,
    processed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (answer_record_id),
    CONSTRAINT fk_mastery_processed_answer_record FOREIGN KEY (answer_record_id) REFERENCES answer_record(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE student_knowledge_mastery_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    answer_record_id BIGINT NOT NULL,
    previous_attempt_count INT NOT NULL,
    previous_correct_count INT NOT NULL,
    previous_score DECIMAL(5,4) NULL,
    new_attempt_count INT NOT NULL,
    new_correct_count INT NOT NULL,
    new_score DECIMAL(5,4) NOT NULL,
    algorithm_version VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mastery_history_answer_point (answer_record_id, knowledge_point_id),
    KEY idx_mastery_history_student_course_time (student_id, course_id, created_at),
    KEY idx_mastery_history_point_time (knowledge_point_id, created_at),
    CONSTRAINT fk_mastery_history_student FOREIGN KEY (student_id) REFERENCES sys_user(id),
    CONSTRAINT fk_mastery_history_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_mastery_history_point FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id),
    CONSTRAINT fk_mastery_history_answer FOREIGN KEY (answer_record_id) REFERENCES answer_record(id),
    CONSTRAINT chk_mastery_history_counts CHECK (
        previous_attempt_count >= 0 AND previous_correct_count >= 0
        AND previous_correct_count <= previous_attempt_count
        AND new_attempt_count >= 1 AND new_correct_count >= 0
        AND new_correct_count <= new_attempt_count
    ),
    CONSTRAINT chk_mastery_history_score CHECK (
        (previous_score IS NULL OR (previous_score >= 0 AND previous_score <= 1))
        AND new_score >= 0 AND new_score <= 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recommendation_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    mastery_algorithm_version VARCHAR(64) NOT NULL,
    recommendation_rule_version VARCHAR(64) NOT NULL,
    generated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_recommendation_snapshot_student_course_time (student_id, course_id, generated_at),
    CONSTRAINT fk_recommendation_snapshot_student FOREIGN KEY (student_id) REFERENCES sys_user(id),
    CONSTRAINT fk_recommendation_snapshot_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_recommendation_snapshot_graph_version FOREIGN KEY (graph_version_id) REFERENCES graph_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recommendation_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recommendation_snapshot_id BIGINT NOT NULL,
    rank_no INT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    exercise_unit_id BIGINT NULL,
    mastery_score DECIMAL(5,4) NULL,
    reason_code VARCHAR(32) NOT NULL,
    explanation_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_recommendation_item_snapshot_rank (recommendation_snapshot_id, rank_no),
    UNIQUE KEY uk_recommendation_item_snapshot_pair (recommendation_snapshot_id, knowledge_point_id, exercise_unit_id),
    KEY idx_recommendation_item_point (knowledge_point_id),
    CONSTRAINT fk_recommendation_item_snapshot FOREIGN KEY (recommendation_snapshot_id)
        REFERENCES recommendation_snapshot(id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_item_point FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id),
    CONSTRAINT fk_recommendation_item_exercise FOREIGN KEY (exercise_unit_id) REFERENCES exercise_unit(id),
    CONSTRAINT chk_recommendation_item_rank CHECK (rank_no > 0),
    CONSTRAINT chk_recommendation_item_score CHECK (mastery_score IS NULL OR (mastery_score >= 0 AND mastery_score <= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
