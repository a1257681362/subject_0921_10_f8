package com.evops.regcond.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.regcond.common.RegCondConstants;
import com.evops.regcond.dto.RegAppealConclusionRequest;
import com.evops.regcond.dto.RegAppealRequest;
import com.evops.regcond.entity.RegAppeal;
import com.evops.regcond.entity.RegAppealEvent;
import com.evops.regcond.entity.RegConditionSubmission;
import com.evops.regcond.mapper.RegAppealEventMapper;
import com.evops.regcond.mapper.RegAppealMapper;
import com.evops.regcond.mapper.RegConditionSubmissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 报名条件申诉：
 * - 仅不通过结论（FAIL / 申诉驳回 REJECTED_FAIL）可申诉；存疑转人工中的单子走人工复核，不走申诉；
 * - 同一提交单同时刻只允许一条 PENDING 申诉，历史申诉只追加；
 * - PENDING 期间报名重复提交直接跳过、不再拦截（见 RegConditionSubmitService）；
 * - 申诉成立改判 APPEAL_PASS（机器初判与逐项原因原样保留可追溯），驳回维持拦截 REJECTED_FAIL；
 * - 申诉单与提交单各自追加事件链，流转全程留痕。
 */
@Service
public class RegAppealService {

    private final RegAppealMapper appealMapper;
    private final RegAppealEventMapper appealEventMapper;
    private final RegConditionSubmissionMapper submissionMapper;
    private final RegConditionSubmitService submitService;

    public RegAppealService(RegAppealMapper appealMapper,
                            RegAppealEventMapper appealEventMapper,
                            RegConditionSubmissionMapper submissionMapper,
                            RegConditionSubmitService submitService) {
        this.appealMapper = appealMapper;
        this.appealEventMapper = appealEventMapper;
        this.submissionMapper = submissionMapper;
        this.submitService = submitService;
    }

    @Transactional
    public RegAppeal apply(RegAppealRequest request) {
        RegConditionSubmission submission = request.getSubmissionId() != null
                ? submitService.requireSubmission(request.getSubmissionId())
                : submitService.requireSubmissionByNo(request.getSubmissionNo());

        if (!RegCondConstants.SUB_FAIL.equals(submission.getStatus())
                && !RegCondConstants.SUB_REJECTED_FAIL.equals(submission.getStatus())) {
            throw new BusinessException("APPEAL_STATUS_NOT_ALLOWED",
                    "仅核验不通过的提交单可申诉，当前状态：" + submission.getStatus());
        }
        RegAppeal existing = appealMapper.selectOne(new QueryWrapper<RegAppeal>()
                .eq("submission_id", submission.getId())
                .eq("status", RegCondConstants.APPEAL_PENDING)
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException("APPEAL_IN_PROGRESS",
                    "该提交单已有处理中的申诉：" + existing.getAppealNo() + "，申诉期间请勿重复申诉");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BusinessException("APPEAL_REASON_REQUIRED", "申诉理由不能为空");
        }
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        RegAppeal appeal = new RegAppeal();
        appeal.setAppealNo(nextAppealNo(now));
        appeal.setSubmissionId(submission.getId());
        appeal.setProjectCode(submission.getProjectCode());
        appeal.setLevelCode(submission.getLevelCode());
        appeal.setBatchId(submission.getBatchId());
        appeal.setRegNo(submission.getRegNo());
        appeal.setName(submission.getName());
        appeal.setReason(request.getReason().trim());
        appeal.setAppellant(request.getAppellant() == null || request.getAppellant().trim().isEmpty()
                ? submission.getRegNo() : request.getAppellant().trim());
        appeal.setAppliedAt(now);
        appeal.setStatus(RegCondConstants.APPEAL_PENDING);
        appealMapper.insert(appeal);

        recordEvent(appeal.getId(), "APPLY", null, RegCondConstants.APPEAL_PENDING,
                "学员发起申诉：" + appeal.getReason(), appeal.getAppellant(), now);
        // 提交单同步记一笔，申诉期间拦截状态可追溯
        submitService.recordEvent(submission.getId(), "APPEAL_START",
                submission.getStatus(), submission.getStatus(),
                "学员发起申诉 " + appeal.getAppealNo() + "，申诉期间不重复拦截", appeal.getAppellant(), now);
        return appeal;
    }

    /** 申诉结论：approved=true 改判通过；false 驳回维持拦截。 */
    @Transactional
    public RegAppeal conclude(Long appealId, RegAppealConclusionRequest request) {
        RegAppeal appeal = requireAppeal(appealId);
        if (!RegCondConstants.APPEAL_PENDING.equals(appeal.getStatus())) {
            throw new BusinessException("APPEAL_NOT_PENDING",
                    "仅处理中的申诉可给出结论，当前状态：" + appeal.getStatus());
        }
        if (request.getApproved() == null) {
            throw new BusinessException("APPEAL_APPROVED_REQUIRED", "申诉结论不能为空");
        }
        if (request.getConclusion() == null || request.getConclusion().trim().isEmpty()) {
            throw new BusinessException("APPEAL_CONCLUSION_REQUIRED", "申诉结论说明不能为空");
        }
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        RegConditionSubmission submission = submitService.requireSubmission(appeal.getSubmissionId());
        String fromStatus = submission.getStatus();
        String toStatus = request.getApproved()
                ? RegCondConstants.SUB_APPEAL_PASS : RegCondConstants.SUB_REJECTED_FAIL;

        appeal.setStatus(request.getApproved()
                ? RegCondConstants.APPEAL_APPROVED : RegCondConstants.APPEAL_REJECTED);
        appeal.setConclusion(request.getConclusion().trim());
        appeal.setReviewer(operator);
        appeal.setReviewedAt(now);
        appealMapper.updateById(appeal);

        recordEvent(appeal.getId(), "CONCLUDE", RegCondConstants.APPEAL_PENDING, appeal.getStatus(),
                (request.getApproved() ? "申诉成立，改判通过：" : "申诉驳回，维持不通过：") + request.getConclusion(),
                operator, now);
        submitService.recordEvent(submission.getId(),
                request.getApproved() ? "APPEAL_APPROVED" : "APPEAL_REJECTED",
                fromStatus, toStatus,
                "申诉单 " + appeal.getAppealNo() + " 结论：" + request.getConclusion(),
                operator, now);

        submission.setStatus(toStatus);
        submission.setReviewedBy(operator);
        submission.setReviewedAt(now);
        submission.setReviewComment("申诉 " + appeal.getAppealNo() + "：" + request.getConclusion());
        submissionMapper.updateById(submission);
        return appeal;
    }

    public RegAppeal requireAppeal(Long id) {
        RegAppeal appeal = appealMapper.selectById(id);
        if (appeal == null) {
            throw new BusinessException("APPEAL_NOT_FOUND", "申诉单不存在：id=" + id);
        }
        return appeal;
    }

    public List<RegAppeal> listAppeals(String regNo, String status, Long batchId) {
        QueryWrapper<RegAppeal> wrapper = new QueryWrapper<RegAppeal>().orderByDesc("applied_at", "id");
        if (regNo != null && !regNo.trim().isEmpty()) {
            wrapper.eq("reg_no", regNo.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        if (batchId != null) {
            wrapper.eq("batch_id", batchId);
        }
        return appealMapper.selectList(wrapper);
    }

    public List<RegAppealEvent> listAppealEvents(Long appealId) {
        requireAppeal(appealId);
        return appealEventMapper.selectList(new QueryWrapper<RegAppealEvent>()
                .eq("appeal_id", appealId).orderByAsc("event_at", "id"));
    }

    private void recordEvent(Long appealId, String action, String from, String to,
                             String detail, String operator, LocalDateTime at) {
        RegAppealEvent event = new RegAppealEvent();
        event.setAppealId(appealId);
        event.setAction(action);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setDetail(detail);
        event.setOperator(operator);
        event.setEventAt(at);
        appealEventMapper.insert(event);
    }

    /** 申诉号：SS + yyyyMMdd + 4 位流水。 */
    private String nextAppealNo(LocalDateTime at) {
        String prefix = "SS" + at.format(DateTimeFormatter.BASIC_ISO_DATE);
        int max = 0;
        for (RegAppeal a : appealMapper.selectList(
                new QueryWrapper<RegAppeal>().likeRight("appeal_no", prefix))) {
            String no = a.getAppealNo();
            if (no != null && no.startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 非本前缀流水忽略
                }
            }
        }
        return prefix + String.format("%04d", max + 1);
    }
}
