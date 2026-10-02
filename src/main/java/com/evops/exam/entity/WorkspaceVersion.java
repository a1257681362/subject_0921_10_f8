package com.evops.exam.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作区版本：版本选择与状态回退均可追溯。
 * kind: SNAPSHOT 快照 / ROLLBACK 回退生成；同一时刻仅一个 is_current=TRUE。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workspace_version")
public class WorkspaceVersion extends BaseEntity {
    private String versionNo;
    private String label;
    private String kind;
    private String restoredFromVersion;
    @TableField("is_current")
    private Boolean isCurrent;
    private String snapshot;
    private String remark;
    private String operator;
}
