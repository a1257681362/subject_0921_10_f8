package com.evops.grade.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 成绩录入预检项。
 */
@Data
public class ScorePreviewItem {
    private String regNo;
    private BigDecimal paperScore;
    private String grade;
    private boolean actionable;
    private String note;

    public ScorePreviewItem() {
    }

    public ScorePreviewItem(String regNo, BigDecimal paperScore, String grade,
                            boolean actionable, String note) {
        this.regNo = regNo;
        this.paperScore = paperScore;
        this.grade = grade;
        this.actionable = actionable;
        this.note = note;
    }
}
