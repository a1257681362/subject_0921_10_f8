package com.evops.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工作区版本事件：CREATE / SELECT / ROLLBACK 全程留痕，回退不清此表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workspace_version_event")
public class WorkspaceVersionEvent extends BaseEntity {
    private Long versionId;
    private String versionNo;
    private String action;
    private String detail;
    private String operator;
    private LocalDateTime eventAt;
}
