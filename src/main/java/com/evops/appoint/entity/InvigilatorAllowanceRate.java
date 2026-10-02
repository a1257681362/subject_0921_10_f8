package com.evops.appoint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 监考津贴标准：角色不同标准不同；可按项目配置，按生效区间取值。
 * 取用时把 id/code/金额冻结进聘任版本，以后改标准不影响已生成聘任。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_invigilator_allowance_rate")
public class InvigilatorAllowanceRate extends BaseEntity {
    private String code;
    private String name;
    private String projectCode;
    /** CHIEF / INVIGILATOR / PATROL */
    private String roleCode;
    /** 单场津贴（元） */
    private BigDecimal amount;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    /** ENABLED / DISABLED */
    private String status;
    private String remark;
}
