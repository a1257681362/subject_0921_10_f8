package com.evops.cert.common;

/**
 * 证书编号与验真业务常量。
 */
public final class CertConstants {

    private CertConstants() {
    }

    /** 默认项目编码（规则未指定项目时）。 */
    public static final String PROJECT_DEFAULT = "DEFAULT";

    // 规则版本状态：与等级规则 / 混编算法同一套三段式
    /** 草稿：可改发号参数，未参与发号。 */
    public static final String VERSION_DRAFT = "DRAFT";
    /** 生效：同一规则同时刻仅一版生效，发号只认生效版。 */
    public static final String VERSION_ACTIVE = "ACTIVE";
    /** 停用：被新版本发布顶替或人工停用；已发证书仍保留其版本号与冻结参数。 */
    public static final String VERSION_DISABLED = "DISABLED";

    // 证书来源
    /** 系统按规则发号。 */
    public static final String SOURCE_SYSTEM = "SYSTEM";
    /** 手工旧号补登（不重发，抬序列防撞）。 */
    public static final String SOURCE_LEGACY = "LEGACY";

    // 证书状态
    /** 有效（发证后信息只读）。 */
    public static final String CERT_ISSUED = "ISSUED";
    /** 已作废（留记录，不删除；验真给明确提示）。 */
    public static final String CERT_REVOKED = "REVOKED";

    // 验真配置键
    public static final String CFG_VERIFY_ENABLED = "VERIFY_ENABLED";
    public static final String CFG_VERIFY_RATE = "VERIFY_RATE_PER_MINUTE";
}
