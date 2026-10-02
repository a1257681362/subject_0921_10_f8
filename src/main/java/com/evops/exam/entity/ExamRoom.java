package com.evops.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_exam_room")
public class ExamRoom extends BaseEntity {
    private Long siteId;
    private String roomNo;
    private String building;
    private Integer floorNo;
    private Integer capacity;
    private String locationDesc;
    private String status;
}
