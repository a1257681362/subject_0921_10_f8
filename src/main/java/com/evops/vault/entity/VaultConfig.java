package com.evops.vault.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 保密室配置（键值对）：允许进出时段 ACCESS_WINDOW_START / ACCESS_WINDOW_END（HH:mm，起点含、终点不含）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_vault_config")
public class VaultConfig extends BaseEntity {
    private String configKey;
    private String configValue;
    private String remark;
    private String operator;
}
