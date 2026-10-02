package com.evops.regcond.common;

/**
 * 旺季报名条件规则引擎业务常量。
 */
public final class RegCondConstants {

    private RegCondConstants() {
    }

    /** 默认项目编码（批次未指定项目时）。 */
    public static final String PROJECT_DEFAULT = "DEFAULT";

    // 条件规则版本状态
    /** 草稿：可改核验项，未参与核验。 */
    public static final String VERSION_DRAFT = "DRAFT";
    /** 生效：同一规则同时刻仅一版生效；提交时点取当时 ACTIVE 版本。 */
    public static final String VERSION_ACTIVE = "ACTIVE";
    /** 停用：被新版本发布顶替或人工停用；历史提交仍保留其版本号。 */
    public static final String VERSION_DISABLED = "DISABLED";

    // 核验项类型
    public static final String ITEM_EDUCATION = "EDUCATION";
    public static final String ITEM_YEARS = "YEARS";
    public static final String ITEM_PRIOR_CERT = "PRIOR_CERT";

    // 单项核验结果
    public static final String CHECK_PASS = "PASS";
    public static final String CHECK_FAIL = "FAIL";
    /** 存疑：机器判不准（材料/日期/证书状态存疑），转人工。 */
    public static final String CHECK_DOUBT = "DOUBT";

    // 提交单状态
    public static final String SUB_PASS = "PASS";
    public static final String SUB_FAIL = "FAIL";
    public static final String SUB_MANUAL_PENDING = "MANUAL_PENDING";
    /** 申诉成立，改判通过（原机器判罚保留在逐项结果里，可追溯）。 */
    public static final String SUB_APPEAL_PASS = "APPEAL_PASS";
    /** 申诉驳回，维持不通过。 */
    public static final String SUB_REJECTED_FAIL = "REJECTED_FAIL";

    // 人工复核结论
    public static final String MANUAL_OUTCOME_PASS = "PASS";
    public static final String MANUAL_OUTCOME_FAIL = "FAIL";

    // 年限起算口径
    /** 工作起始日起算。 */
    public static final String BASIS_WORK_START = "WORK_START_DATE";
    /** 前一证书取得日起算。 */
    public static final String BASIS_LAST_CERT = "LAST_CERT_DATE";

    // 申诉状态
    public static final String APPEAL_PENDING = "PENDING";
    public static final String APPEAL_APPROVED = "APPROVED";
    public static final String APPEAL_REJECTED = "REJECTED";
}
