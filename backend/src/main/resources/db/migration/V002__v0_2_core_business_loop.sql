CREATE TABLE course (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_code VARCHAR(64) NOT NULL,
    course_name VARCHAR(128) NOT NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_code (course_code),
    KEY idx_course_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_area (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    area_code VARCHAR(96) NOT NULL,
    area_name VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'PLATFORM',
    external_id VARCHAR(128) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_area_course_code (course_id, area_code),
    KEY idx_knowledge_area_source_external (source_type, external_id),
    CONSTRAINT fk_knowledge_area_course FOREIGN KEY (course_id) REFERENCES course(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_point (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    area_id BIGINT NULL,
    knowledge_code VARCHAR(96) NOT NULL,
    knowledge_name VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'PLATFORM',
    external_id VARCHAR(128) NULL,
    mapping_status VARCHAR(20) NOT NULL DEFAULT 'MAPPED',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_point_course_code (course_id, knowledge_code),
    KEY idx_knowledge_point_course_area (course_id, area_id),
    KEY idx_knowledge_point_source_external (source_type, external_id),
    CONSTRAINT fk_knowledge_point_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_knowledge_point_area FOREIGN KEY (area_id) REFERENCES knowledge_area(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE exercise_unit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    exercise_code VARCHAR(96) NOT NULL,
    exercise_name VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'PLATFORM',
    external_id VARCHAR(128) NULL,
    identity_status VARCHAR(32) NOT NULL DEFAULT 'RESOLVED',
    mapping_status VARCHAR(20) NOT NULL DEFAULT 'UNMAPPED',
    difficulty DECIMAL(5,2) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_exercise_unit_course_code (course_id, exercise_code),
    KEY idx_exercise_unit_source_external (source_type, external_id),
    KEY idx_exercise_unit_course_status (course_id, status),
    CONSTRAINT fk_exercise_unit_course FOREIGN KEY (course_id) REFERENCES course(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE exercise_knowledge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exercise_unit_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    mapping_source VARCHAR(32) NOT NULL,
    confidence DECIMAL(5,4) NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_exercise_knowledge_pair (exercise_unit_id, knowledge_point_id),
    KEY idx_exercise_knowledge_point (knowledge_point_id),
    CONSTRAINT fk_exercise_knowledge_exercise FOREIGN KEY (exercise_unit_id) REFERENCES exercise_unit(id),
    CONSTRAINT fk_exercise_knowledge_point FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exercise_unit_id BIGINT NOT NULL,
    question_type VARCHAR(32) NOT NULL,
    stem TEXT NOT NULL,
    answer_json JSON NOT NULL,
    explanation TEXT NULL,
    difficulty DECIMAL(5,2) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_question_exercise_status (exercise_unit_id, status, id),
    CONSTRAINT fk_question_exercise_unit FOREIGN KEY (exercise_unit_id) REFERENCES exercise_unit(id),
    CONSTRAINT fk_question_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE question_option (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    option_key VARCHAR(16) NOT NULL,
    option_text TEXT NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_question_option_key (question_id, option_key),
    UNIQUE KEY uk_question_option_order (question_id, sort_order),
    CONSTRAINT fk_question_option_question FOREIGN KEY (question_id) REFERENCES question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE course_enrollment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    enroll_source VARCHAR(32) NOT NULL DEFAULT 'SELF_SERVICE',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    enrolled_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_enrollment_student_course (student_id, course_id),
    KEY idx_course_enrollment_course (course_id, status),
    CONSTRAINT fk_course_enrollment_student FOREIGN KEY (student_id) REFERENCES sys_user(id),
    CONSTRAINT fk_course_enrollment_course FOREIGN KEY (course_id) REFERENCES course(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE answer_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    exercise_unit_id BIGINT NOT NULL,
    submitted_answer_json JSON NOT NULL,
    is_correct BOOLEAN NOT NULL,
    attempt_no INT NOT NULL,
    duration_ms BIGINT NULL,
    client_request_id VARCHAR(128) NOT NULL,
    answered_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_answer_record_student_request (student_id, client_request_id),
    KEY idx_answer_record_student_answered (student_id, answered_at),
    KEY idx_answer_record_question (question_id),
    CONSTRAINT fk_answer_record_student FOREIGN KEY (student_id) REFERENCES sys_user(id),
    CONSTRAINT fk_answer_record_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_answer_record_question FOREIGN KEY (question_id) REFERENCES question(id),
    CONSTRAINT fk_answer_record_exercise_unit FOREIGN KEY (exercise_unit_id) REFERENCES exercise_unit(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE seed_import_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requested_by BIGINT NULL,
    mode VARCHAR(20) NOT NULL,
    source_name VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    summary_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_seed_import_run_created (created_at),
    CONSTRAINT fk_seed_import_run_requested_by FOREIGN KEY (requested_by) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE seed_import_conflict (
    id BIGINT NOT NULL AUTO_INCREMENT,
    import_run_id BIGINT NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    external_id VARCHAR(128) NULL,
    conflict_type VARCHAR(64) NOT NULL,
    detail_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_seed_import_conflict_run (import_run_id, id),
    CONSTRAINT fk_seed_import_conflict_run FOREIGN KEY (import_run_id) REFERENCES seed_import_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
