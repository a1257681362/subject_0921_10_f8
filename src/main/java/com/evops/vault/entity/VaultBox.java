package com.evops.vault.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 卷箱建档：箱号先建档再登记出入；在室口径由已生效出入明细推导。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_vault_box")
public class VaultBox extends BaseEntity {
    private String boxNo;
    private String name;
    private String status;
    private String remark;
}
