package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 单人报名条件提交材料。
 * 日期统一 yyyy-MM-dd；规则按提交时点生效（可显式传 submittedAt，缺省当前时刻）。
 */
@Data
public class RegSubmitItem {
    @NotBlank(message = "报名号不能为空")
    private String regNo;
    @NotBlank(message = "姓名不能为空")
    private String name;
    /** 可空：给批次则按批次项目取规则并关联名册。 */
    private Long batchId;
    /** 项目编码：给批次时可空（取批次项目）；不给批次缺省 DEFAULT。 */
    private String projectCode;
    @NotBlank(message = "报考级别不能为空")
    private String levelCode;
    /** 实际学历层次：HIGH_SCHOOL/JUNIOR_COLLEGE/BACHELOR/MASTER/DOCTOR。 */
    private String educationLevel;
    /** 工作起始日（年限按工作起始日起算时使用）。 */
    private String workStartDate;
    /** 前一证书取得日（年限按前一证书取得日起算时使用）。 */
    private String lastCertDate;
    /** 显式提交时点（yyyy-MM-dd HH:mm:ss），缺省当前；用于开放季压测与边界复核留档。 */
    private String submittedAt;
    @Valid
    private List<PriorCertMaterial> priorCerts;
    private String remark;
}
