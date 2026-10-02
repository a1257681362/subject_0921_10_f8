package com.evops.vault.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.vault.common.VaultConstants;
import com.evops.vault.entity.VaultAlert;
import com.evops.vault.mapper.VaultAlertMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 保卫异常告警：异常时段进出在登记生效时自动标记生成；保卫签收留痕，不删告警。
 */
@Service
public class VaultAlertService {

    private final VaultAlertMapper alertMapper;

    public VaultAlertService(VaultAlertMapper alertMapper) {
        this.alertMapper = alertMapper;
    }

    @Transactional(readOnly = true)
    public List<VaultAlert> list(String status) {
        QueryWrapper<VaultAlert> wrapper = new QueryWrapper<VaultAlert>()
                .orderByDesc("id");
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        return alertMapper.selectList(wrapper);
    }

    /** 保卫签收：NEW → ACKED，记签收人与钟点；重复签收直接拒绝。 */
    @Transactional
    public VaultAlert ack(Long alertId, String note) {
        if (alertId == null) {
            throw new BusinessException("ALERT_ID_REQUIRED", "告警 id 不能为空");
        }
        VaultAlert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new BusinessException("ALERT_NOT_FOUND", "告警不存在：id=" + alertId);
        }
        if (VaultConstants.ALERT_ACKED.equals(alert.getStatus())) {
            throw new BusinessException("ALERT_ALREADY_ACKED", "告警 " + alertId + " 已签收，不能重复签收");
        }
        alert.setStatus(VaultConstants.ALERT_ACKED);
        alert.setAckedBy(OperatorHolder.current());
        alert.setAckedAt(LocalDateTime.now());
        alert.setAckNote(note);
        alertMapper.updateById(alert);
        return alert;
    }
}
