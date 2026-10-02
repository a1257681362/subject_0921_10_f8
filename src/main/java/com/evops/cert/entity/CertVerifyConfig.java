package com.evops.cert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 验真配置：键值对（窗口总开关、每分钟限流值等），支撑对外验真先小流量灰度。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_cert_verify_config")
public class CertVerifyConfig extends BaseEntity {
    private String configKey;
    private String configValue;
    private String remark;
    private String operator;
}
