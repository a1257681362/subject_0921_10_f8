package com.evops.vault.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 卷箱出入明细：必须挂在一条出入登记上（卷箱出入跟人员进出挂钩），方向与所挂登记一致，
 * 随登记复核确认生效；在室卷箱按已生效明细推导。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_vault_box_move")
public class VaultBoxMove extends BaseEntity {
    private Long accessId;
    private Long boxId;
    private String boxNo;
    /** 与所挂登记同向：IN 随人进 / OUT 随人出。 */
    private String direction;
    private LocalDateTime occurredAt;
}
