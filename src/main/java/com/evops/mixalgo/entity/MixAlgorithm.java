package com.evops.mixalgo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 混编算法模块：一个项目可建一套（或多套）算法，算法下挂多个版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_mix_algorithm")
public class MixAlgorithm extends BaseEntity {
    private String code;
    private String name;
    /** 与批次 project_code 对齐 */
    private String projectCode;
    private String remark;
}
