package com.smartlearning.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_option")
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "option_key", nullable = false, length = 16)
    private String optionKey;

    @Column(name = "option_text", nullable = false, columnDefinition = "TEXT")
    private String optionText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected QuestionOption() {
    }

    public QuestionOption(Long questionId, String optionKey, String optionText, int sortOrder) {
        this.questionId = questionId;
        this.optionKey = optionKey;
        this.optionText = optionText;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public String getOptionKey() {
        return optionKey;
    }

    public String getOptionText() {
        return optionText;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
