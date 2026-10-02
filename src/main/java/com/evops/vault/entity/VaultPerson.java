package com.evops.vault.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 保密室准入人员：进出刷证按卡号或人员编码定位；停用后不能登记也不能当复核人。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_vault_person")
public class VaultPerson extends BaseEntity {
    private String code;
    private String name;
    /** 刷证卡号，全局唯一。 */
    private String cardNo;
    private String duty;
    private String status;
    private String remark;
}
