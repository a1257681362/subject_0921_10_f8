package com.evops.absence.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 合格名单行：成绩有效者进名单，带等级（合不合格由等级规则表达）。
 */
@Data
public class PassListItem {
    private String regNo;
    private String name;
    private BigDecimal paperScore;
    private String grade;
    private String ruleVersionNo;

    public PassListItem() {
    }

    public PassListItem(String regNo, String name, BigDecimal paperScore, String grade, String ruleVersionNo) {
        this.regNo = regNo;
        this.name = name;
        this.paperScore = paperScore;
        this.grade = grade;
        this.ruleVersionNo = ruleVersionNo;
    }
}
