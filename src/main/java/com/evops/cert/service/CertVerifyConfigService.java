package com.evops.cert.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.cert.common.CertConstants;
import com.evops.cert.entity.CertVerifyConfig;
import com.evops.cert.mapper.CertVerifyConfigMapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 验真窗口配置：总开关与每分钟限流值落库可调，支撑“下周接用人单位、先小流量”。
 */
@Service
public class CertVerifyConfigService {

    private final CertVerifyConfigMapper configMapper;

    public CertVerifyConfigService(CertVerifyConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public List<CertVerifyConfig> list() {
        return configMapper.selectList(new QueryWrapper<CertVerifyConfig>().orderByAsc("config_key"));
    }

    public boolean isVerifyEnabled() {
        return "true".equalsIgnoreCase(getValue(CertConstants.CFG_VERIFY_ENABLED, "true").trim());
    }

    public int ratePerMinute() {
        try {
            return Integer.parseInt(getValue(CertConstants.CFG_VERIFY_RATE, "60").trim());
        } catch (NumberFormatException e) {
            return 60;
        }
    }

    @Transactional
    public CertVerifyConfig update(String key, String value) {
        if (key == null || key.trim().isEmpty()) {
            throw new BusinessException("CERT_CONFIG_KEY_REQUIRED", "配置键不能为空");
        }
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException("CERT_CONFIG_VALUE_REQUIRED", "配置值不能为空");
        }
        String k = key.trim();
        String v = value.trim();
        validate(k, v);
        CertVerifyConfig config = configMapper.selectOne(
                new QueryWrapper<CertVerifyConfig>().eq("config_key", k));
        if (config == null) {
            config = new CertVerifyConfig();
            config.setConfigKey(k);
        }
        config.setConfigValue(v);
        config.setOperator(OperatorHolder.current());
        if (config.getId() == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        return config;
    }

    private void validate(String key, String value) {
        if (CertConstants.CFG_VERIFY_ENABLED.equals(key)
                && !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new BusinessException("CERT_CONFIG_VALUE_INVALID", "验真开关只允许 true / false");
        }
        if (CertConstants.CFG_VERIFY_RATE.equals(key)) {
            int n;
            try {
                n = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new BusinessException("CERT_CONFIG_VALUE_INVALID", "每分钟限流值必须是非负整数（0 表示不限）");
            }
            if (n < 0 || n > 100000) {
                throw new BusinessException("CERT_CONFIG_VALUE_INVALID", "每分钟限流值必须在 0~100000 之间");
            }
        }
    }

    private String getValue(String key, String defaultValue) {
        CertVerifyConfig config = configMapper.selectOne(
                new QueryWrapper<CertVerifyConfig>().eq("config_key", key));
        return config == null ? defaultValue : config.getConfigValue();
    }
}
