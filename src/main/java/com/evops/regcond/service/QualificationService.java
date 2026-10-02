package com.evops.regcond.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.common.ExamConstants;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.ExamBatchMapper;
import com.evops.exam.mapper.RegistrationMapper;
import com.evops.regcond.common.RegCondConstants;
import com.evops.regcond.dto.ConditionItem;
import com.evops.regcond.dto.ManualReviewRequest;
import com.evops.regcond.dto.QualificationSubmitRequest;
import com.evops.regcond.engine.CheckOutcome;
import com.evops.regcond.engine.QualificationCheckEngine;
import com.evops.regcond.entity.ConditionRuleVersion;
import com.evops.regcond.entity.QualificationAppeal;
import com.evops.regcond.entity.QualificationCheckItem;
import com.evops.regcond.entity.RegistrationQualification;
import com.evops.regcond.mapper.QualificationAppealMapper;
import com.evops.regcond.mapper.QualificationCheckItemMapper;
import com.evops.regcond.mapper.RegistrationQualificationMapper;
import com.evops.regcond.vo.QualificationView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 报名条件核验：
 * - 提交时按提交时点取项目+级别的生效规则版本（规则变更按提交时点生效），逐项机器比对；
 * - 逐项结论 PASS / FAIL（逐条原因）/ MANUAL（存疑转人工）；
 * - 申诉处理中的核验项记 APPEALING 暂不拦截（存在在办申诉即放行该项），已通过申诉按豁免放行；
 * - 无硬不符且无存疑即自动合格；有存疑转人工；有硬不符且未被申诉/豁免放行则拒收；
 * - 合格后可回写报名名册（规则版本随单永久留档）。
 */
@Service
public class QualificationService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final RegistrationQualificationMapper qualificationMapper;
    private final QualificationCheckItemMapper checkItemMapper;
    private final QualificationAppealMapper appealMapper;
    private final ConditionRuleService ruleService;
    private final QualificationCheckEngine engine;
    private final ExamBatchMapper batchMapper;
    private final RegistrationMapper registrationMapper;
    private final ObjectMapper objectMapper;

    public QualificationService(RegistrationQualificationMapper qualificationMapper,
                                QualificationCheckItemMapper checkItemMapper,
                                QualificationAppealMapper appealMapper,
                                ConditionRuleService ruleService,
                                QualificationCheckEngine engine,
                                ExamBatchMapper batchMapper,
                                RegistrationMapper registrationMapper,
                                ObjectMapper objectMapper) {
        this.qualificationMapper = qualificationMapper;
        this.checkItemMapper = checkItemMapper;
        this.appealMapper = appealMapper;
        this.ruleService = ruleService;
        this.engine = engine;
        this.batchMapper = batchMapper;
        this.registrationMapper = registrationMapper;
        this.objectMapper = objectMapper;
    }

    // ---------- 提交核验 ----------

    @Transactional
    public QualificationView submit(QualificationSubmitRequest request) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate submitDate = request.getSubmittedAt() == null ? now.toLocalDate() : request.getSubmittedAt();
        String operator = OperatorHolder.current();

        // 项目口径：显式传入优先，其次取批次项目，最后 DEFAULT
        String project = normalizeProject(request.getProjectCode());
        ExamBatch batch = null;
        if (request.getBatchId() != null) {
            batch = batchMapper.selectById(request.getBatchId());
            if (batch == null) {
                throw new BusinessException("BATCH_NOT_FOUND", "报名批次不存在：id=" + request.getBatchId());
            }
            if (isBlank(project)) {
                project = batch.getProjectCode() == null
                        ? RegCondConstants.PROJECT_DEFAULT : batch.getProjectCode().toUpperCase();
            }
        }
        String level = request.getLevelCode().trim().toUpperCase();
        String personKey = personKey(request.getIdNo(), request.getRegNo());

        // 规则按提交时点生效：取提交日之前（含当天发布）的最新版本
        ConditionRuleVersion version = ruleService.requireVersionAt(project, level, submitDate);
        List<ConditionItem> items = ruleService.readItems(version.getItems());

        RegistrationQualification qual = new RegistrationQualification();
        qual.setQualificationNo(nextQualificationNo(submitDate));
        qual.setBatchId(batch == null ? null : batch.getId());
        qual.setRegNo(request.getRegNo().trim());
        qual.setName(request.getName().trim());
        qual.setIdType(request.getIdType() == null || request.getIdType().trim().isEmpty()
                ? "ID_CARD" : request.getIdType().trim().toUpperCase());
        qual.setIdNo(trimToNull(request.getIdNo()));
        qual.setPhone(trimToNull(request.getPhone()));
        qual.setSubject(trimToNull(request.getSubject()));
        qual.setProjectCode(project);
        qual.setLevelCode(level);
        qual.setEducation(trimToNull(request.getEducation()));
        qual.setWorkStartDate(request.getWorkStartDate());
        qual.setPrereqCertNo(trimToNull(request.getPrereqCertNo()));
        qual.setRuleId(version.getRuleId());
        qual.setRuleVersionId(version.getId());
        qual.setRuleVersionNo(version.getVersionNo());
        qual.setSubmitSnapshot(writeSnapshot(request));
        qual.setOperator(operator);
        qual.setSubmittedAt(submitDate.atStartOfDay());
        // 结论在逐项核验后回算；插入前给 NOT NULL 列占位
        qual.setStatus(RegCondConstants.QUAL_MANUAL);
        qual.setPassCount(0);
        qual.setFailCount(0);
        qual.setManualCount(0);
        qual.setHasAppealingItem(false);
        qualificationMapper.insert(qual);

        // 逐项机器核验，再叠加申诉中/已豁免分层
        QualificationCheckEngine.Material material = QualificationCheckEngine.Material.of(
                qual.getName(), qual.getEducation(), qual.getWorkStartDate(), qual.getPrereqCertNo());

        int pass = 0;
        int fail = 0;
        int manual = 0;
        boolean appealing = false;
        Long waiverAppealId = null;

        List<QualificationCheckItem> rows = new ArrayList<>();
        for (ConditionItem item : items) {
            String itemKey = ConditionRuleService.itemKey(item);
            CheckOutcome outcome = engine.evaluate(item, material, submitDate);

            QualificationCheckItem row = new QualificationCheckItem();
            row.setQualificationId(qual.getId());
            row.setItemKey(itemKey);
            row.setItemType(item.getItemType());
            row.setItemName(item.getName());
            row.setExpected(outcome.getExpected());
            row.setActual(outcome.getActual());
            row.setRuleVersionNo(version.getVersionNo());

            if (RegCondConstants.CHECK_PASS.equals(outcome.getResult())) {
                row.setResult(RegCondConstants.CHECK_PASS);
                row.setReason(null);
                pass++;
            } else {
                // 申诉优先分层：处理中先不拦截；已有通过结论的按豁免放行
                QualificationAppeal inProgress = findInProgressAppeal(personKey, project, level, itemKey);
                QualificationAppeal approved = inProgress == null
                        ? findApprovedAppeal(personKey, project, level, itemKey) : null;
                if (inProgress != null) {
                    row.setResult(RegCondConstants.CHECK_APPEALING);
                    row.setReason(outcome.getReason() + "；该项已有处理中的申诉（" + inProgress.getAppealNo()
                            + "），申诉期间暂不拦截，待终裁");
                    row.setAppealId(inProgress.getId());
                    appealing = true;
                } else if (approved != null) {
                    row.setResult(RegCondConstants.CHECK_PASS);
                    row.setActual((row.getActual() == null ? "" : row.getActual())
                            + "；已按申诉 " + approved.getAppealNo() + " 的通过结论豁免放行");
                    row.setWaiverAppealId(approved.getId());
                    waiverAppealId = approved.getId();
                    pass++;
                } else if (RegCondConstants.CHECK_FAIL.equals(outcome.getResult())) {
                    row.setResult(RegCondConstants.CHECK_FAIL);
                    row.setReason(outcome.getReason());
                    fail++;
                } else {
                    row.setResult(RegCondConstants.CHECK_MANUAL);
                    row.setReason(outcome.getReason());
                    manual++;
                }
            }
            checkItemMapper.insert(row);
            rows.add(row);
        }

        qual.setPassCount(pass);
        qual.setFailCount(fail);
        qual.setManualCount(manual);
        qual.setHasAppealingItem(appealing);
        // 汇总结论：硬不符未放行即拒收；无硬不符但有存疑转人工；申诉中（无硬不符/存疑剩余）先按合格放行
        String status;
        String via = null;
        if (fail > 0) {
            status = RegCondConstants.QUAL_REJECTED;
        } else if (manual > 0) {
            status = RegCondConstants.QUAL_MANUAL;
        } else {
            status = RegCondConstants.QUAL_QUALIFIED;
            via = (waiverAppealId != null || appealing) ? RegCondConstants.VIA_APPEAL : RegCondConstants.VIA_AUTO;
            if (waiverAppealId != null) {
                qual.setWaiverAppealId(waiverAppealId);
            }
        }
        qual.setStatus(status);
        qual.setQualifiedVia(via);
        qualificationMapper.updateById(qual);

        return view(qual, rows);
    }

    // ---------- 存疑人工复核 ----------

    /** 存疑转人工：人工放行（合格）或人工拒收（不通过，逐条留意见）。 */
    @Transactional
    public RegistrationQualification manualReview(Long qualificationId, ManualReviewRequest request) {
        RegistrationQualification qual = requireQualification(qualificationId);
        if (!RegCondConstants.QUAL_MANUAL.equals(qual.getStatus())) {
            throw new BusinessException("QUALIFICATION_NOT_MANUAL",
                    "仅存疑待人工的核验单可人工复核，当前状态：" + qual.getStatus());
        }
        if (Boolean.FALSE.equals(request.getApproved())
                && (request.getComment() == null || request.getComment().trim().isEmpty())) {
            throw new BusinessException("MANUAL_REJECT_REASON_REQUIRED", "人工拒收必须写明原因");
        }
        String operator = OperatorHolder.current();
        if (Boolean.TRUE.equals(request.getApproved())) {
            qual.setStatus(RegCondConstants.QUAL_QUALIFIED);
            qual.setQualifiedVia(RegCondConstants.VIA_MANUAL_REVIEW);
            qual.setReviewer(operator);
            qual.setReviewedAt(LocalDateTime.now());
            qual.setReviewComment(trimToNull(request.getComment()));
        } else {
            qual.setStatus(RegCondConstants.QUAL_REJECTED);
            qual.setReviewer(operator);
            qual.setReviewedAt(LocalDateTime.now());
            qual.setReviewComment(request.getComment().trim());
        }
        qualificationMapper.updateById(qual);
        return qual;
    }

    // ---------- 合格后回写报名名册 ----------

    /** 合格核验单正式入册：生成报名名册行；已入册幂等返回。 */
    @Transactional
    public Registration enroll(Long qualificationId) {
        RegistrationQualification qual = requireQualification(qualificationId);
        if (!RegCondConstants.QUAL_QUALIFIED.equals(qual.getStatus())) {
            throw new BusinessException("QUALIFICATION_NOT_QUALIFIED",
                    "仅核验合格的报名可入册，当前状态：" + qual.getStatus());
        }
        if (qual.getRegistrationId() != null) {
            return registrationMapper.selectById(qual.getRegistrationId());
        }
        if (Boolean.TRUE.equals(qual.getHasAppealingItem())) {
            throw new BusinessException("QUALIFICATION_APPEAL_PENDING",
                    "核验单尚有处理中的申诉项，终裁前不能正式入册（申诉期间不拦截重复提交，但入册须等结论）");
        }
        if (qual.getBatchId() == null) {
            throw new BusinessException("BATCH_REQUIRED_FOR_ENROLL",
                    "核验单未关联报名批次，不能入册；请提交时带 batchId 或先补录批次");
        }
        Registration existing = registrationMapper.selectOne(
                new QueryWrapper<Registration>().eq("reg_no", qual.getRegNo()));
        if (existing != null) {
            // 已在名册（可能其他途径导入）：挂回核验单，保持关联
            qual.setRegistrationId(existing.getId());
            qual.setEnrolledAt(LocalDateTime.now());
            qualificationMapper.updateById(qual);
            return existing;
        }

        Registration reg = new Registration();
        reg.setRegNo(qual.getRegNo());
        reg.setName(qual.getName());
        reg.setIdType(qual.getIdType() == null ? "ID_CARD" : qual.getIdType());
        reg.setIdNo(qual.getIdNo());
        reg.setPhone(qual.getPhone());
        reg.setBatchId(qual.getBatchId());
        reg.setSubject(qual.getSubject());
        reg.setStatus(ExamConstants.REG_REGISTERED);
        reg.setCorrectionCount(0);
        reg.setRemark("条件核验单 " + qual.getQualificationNo() + "（规则版本 " + qual.getRuleVersionNo() + "）入册");
        try {
            registrationMapper.insert(reg);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("REG_DUP_EXISTING", "报名号已存在，不能重复入册：" + reg.getRegNo());
        }
        qual.setRegistrationId(reg.getId());
        qual.setEnrolledAt(LocalDateTime.now());
        qualificationMapper.updateById(qual);
        return reg;
    }

    // ---------- 查询 ----------

    public RegistrationQualification requireQualification(Long id) {
        if (id == null) {
            throw new BusinessException("QUALIFICATION_ID_REQUIRED", "核验单 id 不能为空");
        }
        RegistrationQualification qual = qualificationMapper.selectById(id);
        if (qual == null) {
            throw new BusinessException("QUALIFICATION_NOT_FOUND", "核验单不存在：id=" + id);
        }
        return qual;
    }

    public QualificationView getView(Long id) {
        RegistrationQualification qual = requireQualification(id);
        return view(qual, listItems(id));
    }

    public List<QualificationCheckItem> listItems(Long qualificationId) {
        return checkItemMapper.selectList(new QueryWrapper<QualificationCheckItem>()
                .eq("qualification_id", qualificationId).orderByAsc("id"));
    }

    public List<RegistrationQualification> list(Long batchId, String status, String projectCode, String levelCode) {
        QueryWrapper<RegistrationQualification> wrapper = new QueryWrapper<RegistrationQualification>()
                .orderByDesc("id");
        if (batchId != null) {
            wrapper.eq("batch_id", batchId);
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        if (projectCode != null && !projectCode.trim().isEmpty()) {
            wrapper.eq("project_code", projectCode.trim().toUpperCase());
        }
        if (levelCode != null && !levelCode.trim().isEmpty()) {
            wrapper.eq("level_code", levelCode.trim().toUpperCase());
        }
        return qualificationMapper.selectList(wrapper);
    }

    /** 按报名号查全部核验单（申诉列表反查用）。 */
    public List<RegistrationQualification> listByRegNo(String regNo) {
        return qualificationMapper.selectList(new QueryWrapper<RegistrationQualification>()
                .eq("reg_no", regNo).orderByDesc("id"));
    }

    // ---------- 申诉分层查询 ----------

    /** 处理中申诉：SUBMITTED / REVIEWING。存在即申诉期间不重复拦截。 */
    public QualificationAppeal findInProgressAppeal(String personKey, String project, String level, String itemKey) {
        return appealMapper.selectOne(new QueryWrapper<QualificationAppeal>()
                .eq("person_key", personKey)
                .eq("project_code", project)
                .eq("level_code", level)
                .eq("item_key", itemKey)
                .in("status", RegCondConstants.APPEAL_SUBMITTED, RegCondConstants.APPEAL_REVIEWING)
                .orderByDesc("id").last("LIMIT 1"));
    }

    /** 已通过申诉：按人+项目+级别+核验项维度豁免，跨规则版本保持。 */
    public QualificationAppeal findApprovedAppeal(String personKey, String project, String level, String itemKey) {
        return appealMapper.selectOne(new QueryWrapper<QualificationAppeal>()
                .eq("person_key", personKey)
                .eq("project_code", project)
                .eq("level_code", level)
                .eq("item_key", itemKey)
                .eq("status", RegCondConstants.APPEAL_APPROVED)
                .orderByDesc("id").last("LIMIT 1"));
    }

    /** 申诉终裁后重算核验单结论（申诉驳回要恢复拦截）。 */
    @Transactional
    public void recomputeAfterAppeal(Long qualificationId) {
        RegistrationQualification qual = requireQualification(qualificationId);
        // 已入册的不变（报名已成立）；只对未入册单回算结论
        if (qual.getRegistrationId() != null) {
            return;
        }
        List<QualificationCheckItem> rows = listItems(qualificationId);
        int fail = 0;
        int manual = 0;
        int pass = 0;
        boolean appealing = false;
        Long waiverAppealId = null;
        for (QualificationCheckItem row : rows) {
            switch (row.getResult()) {
                case RegCondConstants.CHECK_FAIL:
                    fail++;
                    break;
                case RegCondConstants.CHECK_MANUAL:
                    manual++;
                    break;
                case RegCondConstants.CHECK_APPEALING:
                    appealing = true;
                    break;
                default:
                    pass++;
                    if (waiverAppealId == null && row.getWaiverAppealId() != null) {
                        waiverAppealId = row.getWaiverAppealId();
                    }
            }
        }
        qual.setPassCount(pass);
        qual.setFailCount(fail);
        qual.setManualCount(manual);
        qual.setHasAppealingItem(appealing);
        String status;
        String via = null;
        if (fail > 0) {
            status = RegCondConstants.QUAL_REJECTED;
        } else if (manual > 0) {
            status = RegCondConstants.QUAL_MANUAL;
        } else {
            status = RegCondConstants.QUAL_QUALIFIED;
            via = (waiverAppealId != null || appealing) ? RegCondConstants.VIA_APPEAL : RegCondConstants.VIA_AUTO;
            if (waiverAppealId != null) {
                qual.setWaiverAppealId(waiverAppealId);
            }
        }
        qual.setStatus(status);
        qual.setQualifiedVia(via);
        if (RegCondConstants.QUAL_QUALIFIED.equals(status)) {
            qual.setReviewer(null);
            qual.setReviewedAt(null);
        }
        qualificationMapper.updateById(qual);
    }

    // ---------- 内部 ----------

    private QualificationView view(RegistrationQualification qual, List<QualificationCheckItem> items) {
        QualificationView v = new QualificationView();
        v.setQualification(qual);
        v.setItems(items);
        return v;
    }

    /** 人员键：证件号优先，缺则报名号。 */
    public static String personKey(String idNo, String regNo) {
        if (idNo != null && !idNo.trim().isEmpty()) {
            return "ID:" + idNo.trim().toUpperCase();
        }
        return "REG:" + regNo.trim().toUpperCase();
    }

    /** 核验单号：QS + yyMMdd + 4 位流水。 */
    private String nextQualificationNo(LocalDate date) {
        String prefix = "QS" + date.format(NO_DATE);
        int max = 0;
        for (RegistrationQualification q : qualificationMapper.selectList(
                new QueryWrapper<RegistrationQualification>().likeRight("qualification_no", prefix))) {
            String no = q.getQualificationNo();
            if (no != null && no.startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 非本规则流水忽略
                }
            }
        }
        return prefix + String.format("%04d", max + 1);
    }

    private String writeSnapshot(QualificationSubmitRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new BusinessException("QUALIFICATION_SNAPSHOT_FAILED", "提交材料留档失败：" + e.getMessage());
        }
    }

    private String normalizeProject(String projectCode) {
        return trimToNull(projectCode) == null
                ? null : projectCode.trim().toUpperCase();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
