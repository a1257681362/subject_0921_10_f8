package com.evops.absence.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.absence.common.AbsenceConstants;
import com.evops.absence.common.TimeFormats;
import com.evops.absence.dto.ExamineeCheckinRequest;
import com.evops.absence.dto.SupplementApproveRequest;
import com.evops.absence.dto.SupplementRequest;
import com.evops.absence.entity.AbsenceMark;
import com.evops.absence.entity.ExamineeCheckin;
import com.evops.absence.mapper.ExamineeCheckinMapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.Registration;
import com.evops.exam.service.ExamBatchService;
import com.evops.exam.service.RegistrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考生签到：
 * - 现场签到登记即生效（CONFIRMED），一人一批次一条生效签到；
 * - 补录走审批：补录落库 PENDING，审批人（非补录提交人）批准才生效；
 * - 补录批准后对已生效的缺考无效裁定自动发起变更（待双人复核），并重算统计。
 */
@Service
public class CheckinService {

    private final ExamineeCheckinMapper checkinMapper;
    private final ExamBatchService batchService;
    private final RegistrationService registrationService;
    private final AbsenceMarkService markService;
    private final AbsenceStatService statService;

    public CheckinService(ExamineeCheckinMapper checkinMapper,
                          ExamBatchService batchService,
                          RegistrationService registrationService,
                          AbsenceMarkService markService,
                          AbsenceStatService statService) {
        this.checkinMapper = checkinMapper;
        this.batchService = batchService;
        this.registrationService = registrationService;
        this.markService = markService;
        this.statService = statService;
    }

    // ==================== 现场签到（登记即生效） ====================

    @Transactional
    public ExamineeCheckin checkin(ExamineeCheckinRequest request) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        Registration reg = requireRegistration(batch.getId(), request.getRegNo());
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkInAt = TimeFormats.parse(request.getCheckInAt(), now);
        TimeFormats.assertNotFuture(checkInAt, now);

        assertNoConfirmedCheckin(reg);

        ExamineeCheckin checkin = new ExamineeCheckin();
        checkin.setBatchId(batch.getId());
        checkin.setRegistrationId(reg.getId());
        checkin.setRegNo(reg.getRegNo());
        checkin.setName(reg.getName());
        checkin.setCheckInAt(checkInAt);
        checkin.setSource(AbsenceConstants.CHECKIN_LIVE);
        checkin.setStatus(AbsenceConstants.CHECKIN_CONFIRMED);
        checkin.setOperator(operator);
        checkin.setRemark(request.getRemark());
        checkinMapper.insert(checkin);

        statService.recompute(batch.getId(), "现场签到 " + reg.getRegNo(), operator);
        return checkin;
    }

    // ==================== 补录（走审批） ====================

    @Transactional
    public ExamineeCheckin supplement(SupplementRequest request) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        Registration reg = requireRegistration(batch.getId(), request.getRegNo());
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkInAt = TimeFormats.parse(request.getCheckInAt(), null);
        TimeFormats.assertNotFuture(checkInAt, now);

        assertNoConfirmedCheckin(reg);
        Long pending = checkinMapper.selectCount(new QueryWrapper<ExamineeCheckin>()
                .eq("registration_id", reg.getId())
                .eq("status", AbsenceConstants.CHECKIN_PENDING));
        if (pending != null && pending > 0) {
            throw new BusinessException("SUPPLEMENT_PENDING",
                    "该学员已有待审批的补录申请，不能重复提交：" + reg.getRegNo());
        }

        ExamineeCheckin checkin = new ExamineeCheckin();
        checkin.setBatchId(batch.getId());
        checkin.setRegistrationId(reg.getId());
        checkin.setRegNo(reg.getRegNo());
        checkin.setName(reg.getName());
        checkin.setCheckInAt(checkInAt);
        checkin.setSource(AbsenceConstants.CHECKIN_SUPPLEMENT);
        checkin.setStatus(AbsenceConstants.CHECKIN_PENDING);
        checkin.setReason(request.getReason().trim());
        checkin.setOperator(operator);
        checkin.setRemark(request.getRemark());
        checkinMapper.insert(checkin);

        statService.recompute(batch.getId(), "签到补录申请 " + reg.getRegNo() + " 待审批", operator);
        return checkin;
    }

    /** 补录审批：批准才生效；审批人须非补录提交人；批准后自动发起缺考裁定变更并重算统计。 */
    @Transactional
    public ExamineeCheckin approve(Long checkinId, SupplementApproveRequest request) {
        ExamineeCheckin checkin = requireCheckin(checkinId);
        String approver = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        if (!AbsenceConstants.CHECKIN_PENDING.equals(checkin.getStatus())) {
            throw new BusinessException("APPROVE_STATUS_INVALID",
                    "补录申请当前状态 " + checkin.getStatus() + "，仅待审批（PENDING）可审批");
        }
        if (approver.equals(checkin.getOperator())) {
            throw new BusinessException("SUPPLEMENT_SELF_APPROVE",
                    "审批人不能是补录提交人（" + approver + "）");
        }

        if (Boolean.FALSE.equals(request.getApproved())) {
            if (request.getComment() == null || request.getComment().trim().isEmpty()) {
                throw new BusinessException("REJECT_COMMENT_REQUIRED", "驳回必须填写审批意见");
            }
            checkin.setStatus(AbsenceConstants.CHECKIN_REJECTED);
            fillReview(checkin, approver, request.getComment().trim(), now);
            checkinMapper.updateById(checkin);
            statService.recompute(checkin.getBatchId(), "补录驳回 " + checkin.getRegNo(), approver);
            return checkin;
        }

        // 批准生效前再校一次：等待期间可能已有现场签到生效
        Registration reg = registrationService.getRegistration(checkin.getRegistrationId());
        assertNoConfirmedCheckin(reg);

        checkin.setStatus(AbsenceConstants.CHECKIN_CONFIRMED);
        fillReview(checkin, approver,
                request.getComment() == null ? null : request.getComment().trim(), now);
        checkinMapper.updateById(checkin);

        // 补录生效 → 对已生效的缺考无效裁定自动发起变更（仍待双人复核）
        List<AbsenceMark> affected = markService.autoRecheckOnCheckinApproved(reg, approver);
        statService.recompute(checkin.getBatchId(),
                "补录批准 " + checkin.getRegNo()
                        + (affected.isEmpty() ? "" : "，自动发起裁定变更 " + affected.size() + " 笔"),
                approver);
        return checkin;
    }

    // ==================== 查询 ====================

    @Transactional(readOnly = true)
    public List<ExamineeCheckin> list(Long batchId, String status, String regNo) {
        QueryWrapper<ExamineeCheckin> wrapper = new QueryWrapper<ExamineeCheckin>()
                .orderByDesc("id");
        if (batchId != null) {
            wrapper.eq("batch_id", batchId);
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        if (regNo != null && !regNo.trim().isEmpty()) {
            wrapper.eq("reg_no", regNo.trim());
        }
        return checkinMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public ExamineeCheckin get(Long id) {
        return requireCheckin(id);
    }

    // ==================== 内部 ====================

    private ExamineeCheckin requireCheckin(Long id) {
        if (id == null) {
            throw new BusinessException("CHECKIN_ID_REQUIRED", "签到记录 id 不能为空");
        }
        ExamineeCheckin checkin = checkinMapper.selectById(id);
        if (checkin == null) {
            throw new BusinessException("CHECKIN_NOT_FOUND", "签到记录不存在：id=" + id);
        }
        return checkin;
    }

    private void assertNoConfirmedCheckin(Registration reg) {
        Long count = checkinMapper.selectCount(new QueryWrapper<ExamineeCheckin>()
                .eq("registration_id", reg.getId())
                .eq("status", AbsenceConstants.CHECKIN_CONFIRMED));
        if (count != null && count > 0) {
            throw new BusinessException("CHECKIN_EXISTS",
                    "该学员已有生效签到，不能重复签到/补录：" + reg.getRegNo());
        }
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

    private void fillReview(ExamineeCheckin checkin, String reviewer, String comment, LocalDateTime now) {
        checkin.setReviewer(reviewer);
        checkin.setReviewedAt(now);
        checkin.setReviewComment(comment);
    }
}
