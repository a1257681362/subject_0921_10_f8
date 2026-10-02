package com.evops.mixalgo.common;

/**
 * 同校混编算法业务常量。
 */
public final class MixConstants {

    private MixConstants() {
    }

    /** 默认项目编码（批次未指定项目时）。 */
    public static final String PROJECT_DEFAULT = "DEFAULT";

    // 混编算法版本状态
    /** 草稿：参数可改，未参与编排。 */
    public static final String VERSION_DRAFT = "DRAFT";
    /** 生效：同一算法同时刻仅一版生效，编排只认生效版。 */
    public static final String VERSION_ACTIVE = "ACTIVE";
    /** 停用：被新版本发布顶替或人工停用；历史编排记录仍保留其版本号。 */
    public static final String VERSION_DISABLED = "DISABLED";

    // 编排策略
    /** 同校错开：同校按容量比例摊到多个考场，考场内交错入座最小化相邻。 */
    public static final String STRATEGY_SCHOOL_SPREAD = "SCHOOL_SPREAD";

    // 内置默认参数（项目尚无生效算法版本时的兜底口径）
    public static final int DEFAULT_MIN_SCHOOL_GAP = 1;
    public static final int DEFAULT_TRIES = 40;
    public static final boolean DEFAULT_SPREAD_ROOMS = true;

    public static final int MIN_TRIES = 1;
    public static final int MAX_TRIES = 1000;
}
