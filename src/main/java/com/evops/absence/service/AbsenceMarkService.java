package com.evops.absence.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.absence.common.AbsenceConstants;
import com.evops.absence.dto.MarkChangeRequest;
import com.evops.absence.dto.MarkRequest;
import com.evops.absence.dto.MarkReviewRequest;
import com.evops.absence.dto.ScanRequest;
import com.evops.absence.entity.AbsenceMark;
import com.evops.absence.entity.AbsenceMarkVersion;
import com.evops.absence.entity.ExamineeCheckin;
import com.evops.absence.entity.ScoreSnapshot;
import com.evops.absence.entity.ViolationRecord;
import com.evops.absence.mapper.AbsenceMarkMapper;
import com.evops.absence.mapper.AbsenceMarkVersionMapper;
import com.evops.absence.mapper.ExamineeCheckinMapper;
import com.evops.absence.mapper.ScoreSnapshotMapper;
import com.evops.absence.mapper.ViolationRecordMapper;
import com.evops.absence.vo.ScanPreview;
import com.evops.absence.vo.ScanPreviewItem;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.Registration;
import com.evops.exam.service.ExamBatchService;
import com.evops.exam.service.RegistrationService;
import com.evops.grade.common.GradeConstants;
import com.evops.grade.entity.ExamScore;
import com.evops.grade.mapper.ExamScoreMapper;
import com.evops.grade.service.ScoreNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 缺考标记（模块 + 版本 + 状态）：
 * - 按签到与违纪记录自动标缺考（扫描两步：预检 → 执行），弃考由手动标记登记；
 * - 双人复核：复核人须非标记经办人，确认才生效（CONFIRMED 即最终裁定），驳回留痕；
 * - 结论决定成绩有没有效：生效即回写成绩 valid、逐人逐笔写成绩快照、通知本人；
 * - 已生效裁定可变更（回到待复核再走双人复核），变更确认即重算统计；
 * - 统计只认最终裁定（最新一条 CONFIRMED 版本），每次流转只追加版本，主表取当前版本。
 */
@Service
public class AbsenceMarkService {

    private final AbsenceMarkMapper markMapper;
    private final AbsenceMarkVersionMapper versionMapper;
    private final ExamineeCheckinMapper checkinMapper;
    private final ViolationRecordMapper violationMapper;
    private final ExamScoreMapper scoreMapper;
    private final ScoreSnapshotMapper snapshotMapper;
    private final ExamBatchService batchService;
    private final RegistrationService registrationService;
    private final ScoreNotificationService notificationService;
    private final AbsenceStatService statService;

    public AbsenceMarkService(AbsenceMarkMapper markMapper,
                              AbsenceMarkVersionMapper versionMapper,
                              ExamineeCheckinMapper checkinMapper,
                              ViolationRecordMapper violationMapper,
                              ExamScoreMapper scoreMapper,
                              ScoreSnapshotMapper snapshotMapper,
                              ExamBatchService batchService,
                              RegistrationService registrationService,
                              ScoreNotificationService notificationService,
                              AbsenceStatService statService) {
        this.markMapper = markMapper;
        this.versionMapper = versionMapper;
        this.checkinMapper = checkinMapper;
        this.violationMapper = violationMapper;
        this.scoreMapper = scoreMapper;
        this.snapshotMapper = snapshotMapper;
        this.batchService = batchService;
        this.registrationService = registrationService;
        this.notificationService = notificationService;
        this.statService = statService;
    }

    // ==================== 自动标缺考：扫描预检 / 执行（两步） ====================

    @Transactional(readOnly = true)
    public ScanPreview previewScan(ScanRequest request) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        List<ScanPlanItem> plan = buildScanPlan(batch);
        List<ScanPreviewItem> items = new ArrayList<>();
        int actionable = 0;
        int skip = 0;
        for (ScanPlanItem p : plan) {
            items.add(new ScanPreviewItem(p.reg.getRegNo(), p.reg.getName(), p.markType, p.source,
                    p.actionable, p.note));
            if (p.actionable) {
                actionable++;
            } else {
                skip++;
            }
        }
        ScanPreview preview = new ScanPreview();
        preview.setBatchId(batch.getId());
        preview.setBatchCode(batch.getCode());
        preview.setTotalScanned(items.size());
        preview.setActionableCount(actionable);
        preview.setSkipCount(skip);
        preview.setSummary(String.format("批次 %s：扫描出候选 %d 条，可标记 %d 条，跳过 %d 条",
                batch.getCode(), items.size(), actionable, skip));
        preview.setItems(items);
        return preview;
    }

    @Transactional
    public List<AbsenceMark> executeScan(ScanRequest request) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        List<AbsenceMark> created = new ArrayList<>();
        for (ScanPlanItem p : buildScanPlan(batch)) {
            if (!p.actionable) {
                continue;
            }
            // 执行时再校一次：等待期间可能已补签到或已标记
            if (hasConfirmedCheckin(p.reg.getId()) && AbsenceConstants.TYPE_ABSENT.equals(p.markType)) {
                continue;
            }
            if (hasOpenMark(p.reg.getId())) {
                continue;
            }
            AbsenceMark mark = newMark(batch, p.reg, p.markType, p.source, false,
                    p.defaultReason, "成绩无效，衔接下次考试安排", null, operator, null, now);
            markMapper.insert(mark);
            appendVersion(mark, AbsenceConstants.CHANGE_CREATE, p.defaultReason, null, operator, now);
            created.add(mark);
        }
        if (!created.isEmpty()) {
            statService.recompute(batch.getId(), "自动标缺考 " + created.size() + " 条待复核", operator);
        }
        return created;
    }

    /** 扫描候选：未签到且无在办缺考标记 → ABSENT；有违纪记录且无在办违纪标记 → VIOLATION。 */
    private List<ScanPlanItem> buildScanPlan(ExamBatch batch) {
        List<Registration> roster = registrationService.listByBatch(batch.getId());
        List<ViolationRecord> violations = violationMapper.selectList(new QueryWrapper<ViolationRecord>()
                .eq("batch_id", batch.getId()).orderByAsc("registration_id", "id"));
        Map<Long, List<ViolationRecord>> violationsByReg = new HashMap<>();
        for (ViolationRecord v : violations) {
            violationsByReg.computeIfAbsent(v.getRegistrationId(), k -> new ArrayList<>()).add(v);
        }

        List<ScanPlanItem> plan = new ArrayList<>();
        for (Registration reg : roster) {
            // 一人一裁定：已有在办/生效裁定（不限类型）的不再自动标记
            if (hasOpenMark(reg.getId())) {
                plan.add(ScanPlanItem.skip(reg, null, null, "已有在办/生效的缺考裁定，跳过"));
                continue;
            }
            List<ViolationRecord> regViolations = violationsByReg.get(reg.getId());
            boolean hasViolation = regViolations != null && !regViolations.isEmpty();
            // 有违纪记录说明人到场了，不再标缺考；只标违纪
            if (!hasViolation && !hasConfirmedCheckin(reg.getId())) {
                plan.add(ScanPlanItem.mark(reg, AbsenceConstants.TYPE_ABSENT,
                        AbsenceConstants.SRC_AUTO_CHECKIN, "未签到，按签到记录裁定缺考",
                        "未签到，自动标记缺考（待双人复核）"));
            }
            if (hasViolation) {
                appendViolationItems(plan, reg, regViolations);
            }
        }
        return plan;
    }

    private void appendViolationItems(List<ScanPlanItem> plan, Registration reg, List<ViolationRecord> violations) {
        StringBuilder reason = new StringBuilder("违纪记录：");
        for (ViolationRecord v : violations) {
            reason.append(violationLabel(v.getViolationType()));
            if (v.getDescription() != null && !v.getDescription().trim().isEmpty()) {
                reason.append("（").append(v.getDescription().trim()).append("）");
            }
            reason.append("；");
        }
        plan.add(ScanPlanItem.mark(reg, AbsenceConstants.TYPE_VIOLATION,
                AbsenceConstants.SRC_AUTO_VIOLATION, reason.toString(),
                "有违纪记录 " + violations.size() + " 条，自动标记违纪（待双人复核）"));
    }

    // ==================== 手动标记（弃考走这里） ====================

    @Transactional
    public AbsenceMark createMark(MarkRequest request) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        Registration reg = requireRegistration(batch.getId(), request.getRegNo());
        String markType = normalizeMarkType(request.getMarkType());
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        // 一人一裁定：同一学员已有在办/生效裁定（不限类型）时不再重复标记，要调整走复核/变更
        AbsenceMark open = findOpenMark(reg.getId());
        if (open != null) {
            throw new BusinessException("MARK_EXISTS",
                    "该学员已有在办/生效的缺考裁定 " + open.getMarkNo() + "（" + typeLabel(open.getMarkType())
                            + "，状态 " + open.getStatus() + "），不能重复标记；如需调整请走复核或变更");
        }
        if (request.getNextBatchId() != null) {
            batchService.getBatch(request.getNextBatchId());
        }
        AbsenceMark mark = newMark(batch, reg, markType, AbsenceConstants.SRC_MANUAL, false,
                request.getReason().trim(), trimToNull(request.getNextArrangement()),
                request.getNextBatchId(), operator, request.getRemark(), now);
        markMapper.insert(mark);
        appendVersion(mark, AbsenceConstants.CHANGE_CREATE, request.getReason().trim(), null, operator, now);
        statService.recompute(batch.getId(), "手动标记 " + mark.getMarkNo() + " 待复核", operator);
        return mark;
    }

    // ==================== 双人复核（确认才生效） ====================

    @Transactional
    public AbsenceMark review(Long markId, MarkReviewRequest request) {
        AbsenceMark mark = requireMark(markId);
        String reviewer = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        if (!AbsenceConstants.MARK_PENDING.equals(mark.getStatus())) {
            throw new BusinessException("REVIEW_STATUS_INVALID",
                    "裁定 " + mark.getMarkNo() + " 当前状态 " + mark.getStatus() + "，仅待复核（PENDING）可复核");
        }
        if (reviewer.equals(mark.getOperator())) {
            throw new BusinessException("REVIEWER_SAME_AS_OPERATOR",
                    "裁定须双人复核：复核人不能是经办人（" + reviewer + "）");
        }

        // 复核可修正拟定结论；留空按拟定值
        if (request.getMarkType() != null && !request.getMarkType().trim().isEmpty()) {
            mark.setMarkType(normalizeMarkType(request.getMarkType()));
        }
        if (request.getScoreValid() != null) {
            mark.setScoreValid(request.getScoreValid());
        }
        if (request.getInvalidReason() != null) {
            mark.setInvalidReason(trimToNull(request.getInvalidReason()));
        }
        if (request.getNextArrangement() != null) {
            mark.setNextArrangement(trimToNull(request.getNextArrangement()));
        }
        if (request.getNextBatchId() != null) {
            batchService.getBatch(request.getNextBatchId());
            mark.setNextBatchId(request.getNextBatchId());
        }

        if (Boolean.FALSE.equals(request.getApproved())) {
            if (request.getComment() == null || request.getComment().trim().isEmpty()) {
                throw new BusinessException("REJECT_COMMENT_REQUIRED", "驳回必须填写复核意见");
            }
            rejectMark(mark, request.getComment().trim(), reviewer, now);
            return mark;
        }

        // 确认生效：结论校验 + 签到一致性守卫（两个窗口同一口径）
        assertConclusionComplete(mark);
        if (Boolean.FALSE.equals(mark.getScoreValid())
                && AbsenceConstants.TYPE_ABSENT.equals(mark.getMarkType())
                && hasConfirmedCheckin(mark.getRegistrationId())) {
            throw new BusinessException("CHECKIN_EXISTS",
                    "该学员已有生效签到，不能裁定缺考成绩无效；请驳回本标记或改走变更");
        }

        mark.setStatus(AbsenceConstants.MARK_CONFIRMED);
        mark.setCurrentVersion(mark.getCurrentVersion() + 1);
        mark.setReviewer(reviewer);
        mark.setReviewedAt(now);
        mark.setReviewComment(request.getComment() == null ? null : request.getComment().trim());
        markMapper.updateById(mark);
        appendVersion(mark, AbsenceConstants.CHANGE_CONFIRM,
                request.getComment() == null || request.getComment().trim().isEmpty()
                        ? "双人复核确认，裁定生效" : request.getComment().trim(),
                reviewer, reviewer, now);
        applyConfirmedRuling(mark, reviewer, now);
        return mark;
    }

    /** 驳回：变更轮驳回恢复原裁定结论；新标记驳回则终结（REJECTED 永不生效）。 */
    private void rejectMark(AbsenceMark mark, String comment, String reviewer, LocalDateTime now) {
        AbsenceMarkVersion lastConfirmed = latestConfirmedVersion(mark.getId());
        mark.setCurrentVersion(mark.getCurrentVersion() + 1);
        mark.setReviewer(reviewer);
        mark.setReviewedAt(now);
        mark.setReviewComment(comment);
        if (lastConfirmed != null) {
            mark.setStatus(AbsenceConstants.MARK_CONFIRMED);
            mark.setMarkType(lastConfirmed.getMarkType());
            mark.setScoreValid(lastConfirmed.getScoreValid());
            mark.setInvalidReason(lastConfirmed.getInvalidReason());
            mark.setNextArrangement(lastConfirmed.getNextArrangement());
            mark.setNextBatchId(lastConfirmed.getNextBatchId());
            markMapper.updateById(mark);
            appendVersion(mark, AbsenceConstants.CHANGE_REJECT,
                    "变更被驳回，维持原裁定：" + comment, reviewer, reviewer, now);
            statService.recompute(mark.getBatchId(), "变更驳回 " + mark.getMarkNo() + "，维持原裁定", reviewer);
        } else {
            mark.setStatus(AbsenceConstants.MARK_REJECTED);
            markMapper.updateById(mark);
            appendVersion(mark, AbsenceConstants.CHANGE_REJECT, "复核驳回：" + comment, reviewer, reviewer, now);
            statService.recompute(mark.getBatchId(), "裁定驳回 " + mark.getMarkNo(), reviewer);
        }
    }

    // ==================== 变更裁定（已生效裁定回到待复核） ====================

    @Transactional
    public AbsenceMark change(Long markId, MarkChangeRequest request) {
        AbsenceMark mark = requireMark(markId);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        if (!AbsenceConstants.MARK_CONFIRMED.equals(mark.getStatus())) {
            throw new BusinessException("CHANGE_STATUS_INVALID",
                    "仅已生效（CONFIRMED）裁定可变更，当前状态 " + mark.getStatus() + "：" + mark.getMarkNo());
        }
        applyChange(mark, request.getScoreValid(), request.getMarkType(), request.getInvalidReason(),
                request.getNextArrangement(), request.getNextBatchId(), request.getReason().trim(),
                AbsenceConstants.SRC_MANUAL, operator, now);
        return mark;
    }

    /** 补录审批通过后，对该生已生效的缺考无效裁定自动发起变更（拟恢复成绩有效，仍待双人复核）。 */
    @Transactional
    public List<AbsenceMark> autoRecheckOnCheckinApproved(Registration reg, String operator) {
        LocalDateTime now = LocalDateTime.now();
        List<AbsenceMark> affected = markMapper.selectList(new QueryWrapper<AbsenceMark>()
                .eq("registration_id", reg.getId())
                .eq("mark_type", AbsenceConstants.TYPE_ABSENT)
                .eq("status", AbsenceConstants.MARK_CONFIRMED)
                .eq("score_valid", false));
        for (AbsenceMark mark : affected) {
            applyChange(mark, true, null, null, null, null,
                    "签到补录审批通过，自动发起变更：拟恢复成绩有效",
                    AbsenceConstants.SRC_RECHECK, operator, now);
        }
        return affected;
    }

    private void applyChange(AbsenceMark mark, Boolean scoreValid, String markType,
                             String invalidReason, String nextArrangement, Long nextBatchId,
                             String reason, String source, String operator, LocalDateTime now) {
        String newType = markType == null || markType.trim().isEmpty()
                ? mark.getMarkType() : normalizeMarkType(markType);
        Boolean newValid = Boolean.TRUE.equals(scoreValid);
        String newReason = newValid ? null
                : firstNonBlank(invalidReason, mark.getInvalidReason());
        String newArrangement = newValid ? null
                : firstNonBlank(nextArrangement, mark.getNextArrangement());
        Long newNextBatchId = newValid ? null
                : (nextBatchId != null ? nextBatchId : mark.getNextBatchId());

        mark.setMarkType(newType);
        mark.setScoreValid(newValid);
        mark.setInvalidReason(newReason);
        mark.setNextArrangement(newArrangement);
        mark.setNextBatchId(newNextBatchId);
        assertConclusionComplete(mark);
        if (Boolean.FALSE.equals(mark.getScoreValid())
                && AbsenceConstants.TYPE_ABSENT.equals(mark.getMarkType())
                && hasConfirmedCheckin(mark.getRegistrationId())) {
            throw new BusinessException("CHECKIN_EXISTS",
                    "该学员已有生效签到，不能变更为缺考成绩无效");
        }
        if (mark.getNextBatchId() != null) {
            batchService.getBatch(mark.getNextBatchId());
        }

        mark.setStatus(AbsenceConstants.MARK_PENDING);
        mark.setSource(source);
        mark.setOperator(operator);
        mark.setCurrentVersion(mark.getCurrentVersion() + 1);
        markMapper.updateById(mark);
        appendVersion(mark, AbsenceConstants.CHANGE_CHANGE, reason, null, operator, now);
        statService.recompute(mark.getBatchId(), "变更申请 " + mark.getMarkNo() + " 待复核", operator);
    }

    // ==================== 查询 ====================

    @Transactional(readOnly = true)
    public List<AbsenceMark> list(Long batchId, String status, String markType) {
        QueryWrapper<AbsenceMark> wrapper = new QueryWrapper<AbsenceMark>()
                .orderByDesc("id");
        if (batchId != null) {
            wrapper.eq("batch_id", batchId);
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        if (markType != null && !markType.trim().isEmpty()) {
            wrapper.eq("mark_type", markType.trim().toUpperCase());
        }
        return markMapper.selectList(wrapper);
    }

    public AbsenceMark requireMark(Long id) {
        if (id == null) {
            throw new BusinessException("MARK_ID_REQUIRED", "缺考标记 id 不能为空");
        }
        AbsenceMark mark = markMapper.selectById(id);
        if (mark == null) {
            throw new BusinessException("MARK_NOT_FOUND", "缺考标记不存在：id=" + id);
        }
        return mark;
    }

    /** 版本链：标记 / 复核 / 驳回 / 变更全程只追加。 */
    @Transactional(readOnly = true)
    public List<AbsenceMarkVersion> versions(Long markId) {
        requireMark(markId);
        return versionMapper.selectList(new QueryWrapper<AbsenceMarkVersion>()
                .eq("mark_id", markId)
                .orderByAsc("version_no"));
    }

    /** 该学员当前是否有生效的无效化裁定（成绩导入时校验用；变更复核期间仍认原裁定）。 */
    @Transactional(readOnly = true)
    public boolean isInvalidated(Long registrationId) {
        List<AbsenceMark> marks = markMapper.selectList(new QueryWrapper<AbsenceMark>()
                .eq("registration_id", registrationId));
        for (AbsenceMark mark : marks) {
            AbsenceMarkVersion v = latestConfirmedVersion(mark.getId());
            if (v != null && Boolean.FALSE.equals(v.getScoreValid())) {
                return true;
            }
        }
        return false;
    }

    // ==================== 内部 ====================

    private AbsenceMark newMark(ExamBatch batch, Registration reg, String markType, String source,
                                boolean scoreValid, String invalidReason, String nextArrangement,
                                Long nextBatchId, String operator, String remark, LocalDateTime now) {
        AbsenceMark mark = new AbsenceMark();
        mark.setMarkNo(nextMarkNo(batch));
        mark.setBatchId(batch.getId());
        mark.setRegistrationId(reg.getId());
        mark.setRegNo(reg.getRegNo());
        mark.setName(reg.getName());
        mark.setMarkType(markType);
        mark.setSource(source);
        mark.setCurrentVersion(1);
        mark.setStatus(AbsenceConstants.MARK_PENDING);
        mark.setScoreValid(scoreValid);
        mark.setInvalidReason(invalidReason);
        mark.setNextArrangement(nextArrangement);
        mark.setNextBatchId(nextBatchId);
        mark.setOperator(operator);
        mark.setRemark(remark);
        return mark;
    }

    /** 裁定确认生效：回写成绩 valid、逐人逐笔写成绩快照、通知本人、重算统计（同一事务）。 */
    private void applyConfirmedRuling(AbsenceMark mark, String operator, LocalDateTime now) {
        ExamScore score = scoreMapper.selectOne(new QueryWrapper<ExamScore>()
                .eq("registration_id", mark.getRegistrationId()));
        Boolean validBefore = score == null ? null : score.getValid();
        if (score != null && !mark.getScoreValid().equals(score.getValid())) {
            score.setValid(mark.getScoreValid());
            scoreMapper.updateById(score);
        }

        ScoreSnapshot snapshot = new ScoreSnapshot();
        snapshot.setBatchId(mark.getBatchId());
        snapshot.setRegistrationId(mark.getRegistrationId());
        snapshot.setRegNo(mark.getRegNo());
        snapshot.setName(mark.getName());
        if (score != null) {
            snapshot.setScoreId(score.getId());
            snapshot.setPaperScore(score.getPaperScore());
            snapshot.setGrade(score.getGrade());
            snapshot.setRuleVersionNo(score.getRuleVersionNo());
        }
        snapshot.setValidBefore(validBefore);
        snapshot.setValidAfter(mark.getScoreValid());
        snapshot.setMarkId(mark.getId());
        snapshot.setMarkNo(mark.getMarkNo());
        snapshot.setMarkType(mark.getMarkType());
        snapshot.setChangeReason(Boolean.TRUE.equals(mark.getScoreValid())
                ? "裁定确认：成绩有效"
                : "裁定确认：成绩无效——" + mark.getInvalidReason());
        snapshot.setOperator(operator);
        snapshot.setSnapshottedAt(now);
        snapshotMapper.insert(snapshot);

        String content;
        if (Boolean.TRUE.equals(mark.getScoreValid())) {
            content = String.format("裁定号 %s（%s）：经双人复核，您的成绩有效。",
                    mark.getMarkNo(), typeLabel(mark.getMarkType()));
        } else {
            content = String.format("裁定号 %s（%s）：经双人复核，您的成绩无效。原因：%s。下次安排：%s。",
                    mark.getMarkNo(), typeLabel(mark.getMarkType()),
                    mark.getInvalidReason(), mark.getNextArrangement());
        }
        notificationService.notify(mark.getBatchId(), mark.getRegistrationId(), mark.getRegNo(),
                mark.getName(), GradeConstants.NOTIFY_ABSENCE_RULING, "缺考裁定结果通知", content,
                null, operator);

        statService.recompute(mark.getBatchId(), "裁定确认 " + mark.getMarkNo(), operator);
    }

    private void assertConclusionComplete(AbsenceMark mark) {
        if (Boolean.FALSE.equals(mark.getScoreValid())) {
            if (mark.getInvalidReason() == null || mark.getInvalidReason().trim().isEmpty()
                    || mark.getNextArrangement() == null || mark.getNextArrangement().trim().isEmpty()) {
                throw new BusinessException("SCORE_INVALID_DETAIL_REQUIRED",
                        "裁定成绩无效必须写明原因并衔接下次安排：" + mark.getMarkNo());
            }
        }
    }

    private AbsenceMarkVersion latestConfirmedVersion(Long markId) {
        List<AbsenceMarkVersion> versions = versionMapper.selectList(new QueryWrapper<AbsenceMarkVersion>()
                .eq("mark_id", markId)
                .eq("status", AbsenceConstants.MARK_CONFIRMED)
                .orderByDesc("version_no"));
        return versions.isEmpty() ? null : versions.get(0);
    }

    boolean hasConfirmedCheckin(Long registrationId) {
        Long count = checkinMapper.selectCount(new QueryWrapper<ExamineeCheckin>()
                .eq("registration_id", registrationId)
                .eq("status", AbsenceConstants.CHECKIN_CONFIRMED));
        return count != null && count > 0;
    }

    /** 在办/生效裁定：PENDING 或 CONFIRMED 视为占用（REJECTED 不挡新裁定）；一人一裁定。 */
    AbsenceMark findOpenMark(Long registrationId) {
        List<AbsenceMark> open = markMapper.selectList(new QueryWrapper<AbsenceMark>()
                .eq("registration_id", registrationId)
                .in("status", AbsenceConstants.MARK_PENDING, AbsenceConstants.MARK_CONFIRMED)
                .orderByDesc("id"));
        return open.isEmpty() ? null : open.get(0);
    }

    boolean hasOpenMark(Long registrationId) {
        return findOpenMark(registrationId) != null;
    }

    private Registration requireRegistration(Long batchId, String regNo) {
        if (regNo == null || regNo.trim().isEmpty()) {
            throw new BusinessException("REG_NO_REQUIRED", "报名号不能为空");
        }
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

    private String normalizeMarkType(String markType) {
        if (markType == null || markType.trim().isEmpty()) {
            throw new BusinessException("MARK_TYPE_REQUIRED",
                    "标记类型不能为空（ABSENT 缺考 / ABANDONED 弃考 / VIOLATION 违纪）");
        }
        String normalized = markType.trim().toUpperCase();
        if (!AbsenceConstants.TYPE_ABSENT.equals(normalized)
                && !AbsenceConstants.TYPE_ABANDONED.equals(normalized)
                && !AbsenceConstants.TYPE_VIOLATION.equals(normalized)) {
            throw new BusinessException("MARK_TYPE_INVALID",
                    "标记类型仅支持 ABSENT 缺考 / ABANDONED 弃考 / VIOLATION 违纪：" + markType);
        }
        return normalized;
    }

    void appendVersion(AbsenceMark mark, String changeType, String reason,
                       String reviewer, String operator, LocalDateTime now) {
        AbsenceMarkVersion version = new AbsenceMarkVersion();
        version.setMarkId(mark.getId());
        version.setVersionNo(mark.getCurrentVersion());
        version.setStatus(mark.getStatus());
        version.setMarkType(mark.getMarkType());
        version.setScoreValid(mark.getScoreValid());
        version.setInvalidReason(mark.getInvalidReason());
        version.setNextArrangement(mark.getNextArrangement());
        version.setNextBatchId(mark.getNextBatchId());
        version.setChangeType(changeType);
        version.setChangeReason(reason);
        version.setReviewer(reviewer);
        version.setOperator(operator);
        version.setChangedAt(now);
        versionMapper.insert(version);
    }

    /** 裁定号：QK + 批次编码 + 4 位流水（唯一约束兜底）。 */
    private String nextMarkNo(ExamBatch batch) {
        String prefix = "QK" + batch.getCode() + "-";
        int max = 0;
        List<AbsenceMark> exist = markMapper.selectList(new QueryWrapper<AbsenceMark>()
                .likeRight("mark_no", prefix));
        for (AbsenceMark m : exist) {
            String no = m.getMarkNo();
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

    static String typeLabel(String markType) {
        if (AbsenceConstants.TYPE_ABSENT.equals(markType)) {
            return "缺考";
        }
        if (AbsenceConstants.TYPE_ABANDONED.equals(markType)) {
            return "弃考";
        }
        if (AbsenceConstants.TYPE_VIOLATION.equals(markType)) {
            return "违纪";
        }
        return markType;
    }

    static String violationLabel(String violationType) {
        if (AbsenceConstants.VIOLATION_CHEAT.equals(violationType)) {
            return "作弊";
        }
        if (AbsenceConstants.VIOLATION_IMPERSONATE.equals(violationType)) {
            return "替考";
        }
        if (AbsenceConstants.VIOLATION_PROHIBITED.equals(violationType)) {
            return "携带违禁品";
        }
        if (AbsenceConstants.VIOLATION_DISORDER.equals(violationType)) {
            return "扰乱秩序";
        }
        return "其他";
    }

    private static String trimToNull(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return text.trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a.trim();
        }
        return b;
    }

    /** 扫描候选项。 */
    private static class ScanPlanItem {
        Registration reg;
        String markType;
        String source;
        boolean actionable;
        String note;
        String defaultReason;

        static ScanPlanItem mark(Registration reg, String markType, String source,
                                 String defaultReason, String note) {
            ScanPlanItem item = new ScanPlanItem();
            item.reg = reg;
            item.markType = markType;
            item.source = source;
            item.actionable = true;
            item.defaultReason = defaultReason;
            item.note = note;
            return item;
        }

        static ScanPlanItem skip(Registration reg, String markType, String source, String note) {
            ScanPlanItem item = new ScanPlanItem();
            item.reg = reg;
            item.markType = markType;
            item.source = source;
            item.actionable = false;
            item.note = note;
            return item;
        }
    }
}
