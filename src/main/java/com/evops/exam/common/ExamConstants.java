package com.evops.exam.common;

/**
 * 考试建档业务状态与阶段常量。
 */
public final class ExamConstants {

    private ExamConstants() {
    }

    // 批次状态
    public static final String BATCH_CREATED = "CREATED";
    public static final String BATCH_ARRANGED = "ARRANGED";
    public static final String BATCH_FINALIZED = "FINALIZED";

    // 报名状态
    public static final String REG_REGISTERED = "REGISTERED";
    public static final String REG_ARRANGED = "ARRANGED";

    // 准考证状态
    public static final String TICKET_PENDING = "PENDING";
    public static final String TICKET_PRINTED = "PRINTED";
    public static final String TICKET_ISSUED = "ISSUED";

    // 阶段
    public static final String STAGE_IMPORT = "IMPORT";
    public static final String STAGE_ARRANGEMENT = "ARRANGEMENT";
    public static final String STAGE_REARRANGE = "REARRANGE";
    public static final String STAGE_PRINTING = "PRINTING";
    public static final String STAGE_ISSUANCE = "ISSUANCE";

    // 版本事件
    public static final String VERSION_KIND_SNAPSHOT = "SNAPSHOT";
    public static final String VERSION_KIND_ROLLBACK = "ROLLBACK";
}
