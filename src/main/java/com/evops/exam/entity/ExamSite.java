package com.evops.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_exam_site")
public class ExamSite extends BaseEntity {
    private String code;
    private String name;
    private String address;
    private String contactPerson;
    private String contactPhone;
    private String status;
    private String remark;
}
