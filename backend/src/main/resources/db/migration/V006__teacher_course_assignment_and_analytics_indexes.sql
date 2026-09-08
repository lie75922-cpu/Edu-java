CREATE TABLE course_teacher_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teacher_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    assignment_role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    assigned_by BIGINT NULL,
    assigned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_teacher_assignment_teacher_course (teacher_id, course_id),
    KEY idx_course_teacher_assignment_course_status (course_id, status),
    KEY idx_course_teacher_assignment_teacher_status (teacher_id, status),
    CONSTRAINT fk_course_teacher_assignment_teacher FOREIGN KEY (teacher_id) REFERENCES sys_user(id),
    CONSTRAINT fk_course_teacher_assignment_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_course_teacher_assignment_assigned_by FOREIGN KEY (assigned_by) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_answer_record_course_answered_id ON answer_record (course_id, answered_at, id);
CREATE INDEX idx_answer_record_course_question_answered ON answer_record (course_id, question_id, answered_at);
