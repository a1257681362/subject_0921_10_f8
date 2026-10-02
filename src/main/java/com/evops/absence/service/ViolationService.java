package com.evops.absence.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.absence.common.AbsenceConstants;
import com.evops.absence.common.TimeFormats;
import com.evops.absence.dto.ViolationRequest;
import com.evops.absence.entity.ViolationRecord;
import com.evops.absence.mapper.ViolationRecordMapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.Registration;
import com.evops.exam.service.ExamBatchService;
import com.evops.exam.service.RegistrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 违纪记录：直接登记（监考/考务报备），作为自动标缺考（VIOLATION 类型）的输入。
 */
@Service
public class ViolationService {

    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
            AbsenceConstants.VIOLATION_CHEAT,
            AbsenceConstants.VIOLATION_IMPERSONATE,
            AbsenceConstants.VIOLATION_PROHIBITED,
            AbsenceConstants.VIOLATION_DISORDER,
            AbsenceConstants.VIOLATION_OTHER));

    private final ViolationRecordMapper violationMapper;
    private final ExamBatchService batchService;
    private final RegistrationService registrationService;

    public ViolationService(ViolationRecordMapper violationMapper,
                            ExamBatchService batchService,
                            RegistrationService registrationService) {
        this.violationMapper = violationMapper;
        this.batchService = batchService;
        this.registrationService = registrationService;
    }

    @Transactional
    public ViolationRecord record(ViolationRequest request) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        Registration reg = requireRegistration(batch.getId(), request.getRegNo());
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime occurredAt = TimeFormats.parse(request.getOccurredAt(), now);
        TimeFormats.assertNotFuture(occurredAt, now);

        String type = request.getViolationType().trim().toUpperCase();
        if (!TYPES.contains(type)) {
            throw new BusinessException("VIOLATION_TYPE_INVALID",
                    "违纪类型仅支持 CHEAT 作弊 / IMPERSONATE 替考 / PROHIBITED 携带违禁品 / DISORDER 扰乱秩序 / OTHER 其他："
                            + request.getViolationType());
        }

        ViolationRecord record = new ViolationRecord();
        record.setBatchId(batch.getId());
        record.setRegistrationId(reg.getId());
        record.setRegNo(reg.getRegNo());
        record.setName(reg.getName());
        record.setViolationType(type);
        record.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        record.setOccurredAt(occurredAt);
        record.setOperator(operator);
        violationMapper.insert(record);
        return record;
    }

    @Transactional(readOnly = true)
    public List<ViolationRecord> list(Long batchId, String regNo) {
        QueryWrapper<ViolationRecord> wrapper = new QueryWrapper<ViolationRecord>()
                .orderByDesc("id");
        if (batchId != null) {
            wrapper.eq("batch_id", batchId);
        }
        if (regNo != null && !regNo.trim().isEmpty()) {
            wrapper.eq("reg_no", regNo.trim());
        }
        return violationMapper.selectList(wrapper);
    }

    private Registration requireRegistration(Long batchId, String regNo) {
        Registration reg = registrationService.findByRegNo(regNo.trim());
        if (reg == null) {
            throw new BusinessException("REG_NOT_FOUND", "名册中查无此报名号：" + regNo.trim());
        }
        if (!reg.getBatchId().equals(batchId)) {
            throw new BusinessException("REG_NOT_IN_BATCH",
                    "报名号不属于本批次（属于批次 id=" + reg.getBatchId() + "）：" + regNo.trim());
        }
        return reg;
    }
}
