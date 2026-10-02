package com.evops.regcond.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.ExamBatchMapper;
import com.evops.exam.mapper.RegistrationMapper;
import com.evops.regcond.common.RegCondConstants;
import com.evops.regcond.dto.ManualReviewRequest;
import com.evops.regcond.dto.RegConditionItemsConfig;
import com.evops.regcond.dto.RegSubmitItem;
import com.evops.regcond.dto.RegSubmitRequest;
import com.evops.regcond.engine.RegConditionEngine;
import com.evops.regcond.entity.RegAppeal;
import com.evops.regcond.entity.RegConditionCheck;
import com.evops.regcond.entity.RegConditionRule;
import com.evops.regcond.entity.RegConditionRuleVersion;
import com.evops.regcond.entity.RegConditionSubmission;
import com.evops.regcond.entity.RegSubmissionEvent;
import com.evops.regcond.mapper.RegAppealMapper;
import com.evops.regcond.mapper.RegConditionCheckMapper;
import com.evops.regcond.mapper.RegConditionRuleMapper;
import com.evops.regcond.mapper.RegConditionRuleVersionMapper;
import com.evops.regcond.mapper.RegConditionSubmissionMapper;
import com.evops.regcond.mapper.RegSubmissionEventMapper;
import com.evops.regcond.vo.ItemCheckResult;
import com.evops.regcond.vo.SubmitItemView;
import com.evops.regcond.vo.SubmitPreview;
import com.evops.regcond.vo.SubmitResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报名条件提交：
 * - 两步：preview 逐人逐项给结论与原因，execute 同口径重算落库（结构性问题整批拒收，不落半条）；
 * - 提交时点冻结 ACTIVE 规则版本号，规则变更不翻旧账；
 * - 有明确 FAIL 项即不通过；无 FAIL 有 DOUBT 转人工；全 PASS 自动通过；
 * - 申诉处理中（该报名号+批次+级别存在 PENDING 申诉）直接跳过、不再重复拦截；
 * - 每次流转追加 t_reg_submission_event。
 */
@Service
public class RegConditionSubmitService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RegConditionRuleService ruleService;
    private final RegConditionEngine engine;
    private final RegConditionRuleMapper ruleMapper;
    private final RegConditionRuleVersionMapper versionMapper;
    private final RegConditionSubmissionMapper submissionMapper;
    private final RegConditionCheckMapper checkMapper;
    private final RegSubmissionEventMapper eventMapper;
    private final RegAppealMapper appealMapper;
    private final ExamBatchMapper batchMapper;
    private final RegistrationMapper registrationMapper;

    public RegConditionSubmitService(RegConditionRuleService ruleService,
                                     RegConditionEngine engine,
                                     RegConditionRuleMapper ruleMapper,
                                     RegConditionRuleVersionMapper versionMapper,
                                     RegConditionSubmissionMapper submissionMapper,
                                     RegConditionCheckMapper checkMapper,
                                     RegSubmissionEventMapper eventMapper,
                                     RegAppealMapper appealMapper,
                                     ExamBatchMapper batchMapper,
                                     RegistrationMapper registrationMapper) {
        this.ruleService = ruleService;
        this.engine = engine;
        this.ruleMapper = ruleMapper;
        this.versionMapper = versionMapper;
        this.submissionMapper = submissionMapper;
        this.checkMapper = checkMapper;
        this.eventMapper = eventMapper;
        this.appealMapper = appealMapper;
        this.batchMapper = batchMapper;
        this.registrationMapper = registrationMapper;
    }

    // ---------- 预检 ----------

    @Transactional(readOnly = true)
    public SubmitPreview preview(RegSubmitRequest request) {
        List<RegSubmitItem> items = request.getSubmissions();
        Set<String> fileKeys = new HashSet<>();
        List<SubmitItemView> views = new ArrayList<>();
        int blocked = 0, manual = 0, pass = 0, appealSkip = 0, errors = 0;

        for (RegSubmitItem item : items) {
            SubmitItemView view = evaluateView(item);
            String fileKey = item.getRegNo().trim() + "|" + item.getLevelCode().trim().toUpperCase()
                    + "|" + (item.getBatchId() == null ? "" : item.getBatchId());
            if (!fileKeys.add(fileKey) && view.getErrorCode() == null && !view.isAppealInProgress()) {
                view.setActionable(false);
                view.setErrorCode("DUP_SUBMIT_IN_FILE");
                view.setNote("清单内同一报名号+级别重复提交，将整批拒收：" + item.getRegNo());
            }
            if (view.getErrorCode() != null) {
                errors++;
                view.setActionable(false);
            }
            if (view.isAppealInProgress()) {
                appealSkip++;
            } else if (RegCondConstants.SUB_FAIL.equals(view.getStatus())) {
                blocked++;
            } else if (RegCondConstants.SUB_MANUAL_PENDING.equals(view.getStatus())) {
                manual++;
            } else if (RegCondConstants.SUB_PASS.equals(view.getStatus())) {
                pass++;
            }
            views.add(view);
        }

        SubmitPreview preview = new SubmitPreview();
        preview.setTotalScanned(views.size());
        preview.setActionableCount(views.size() - errors - appealSkip);
        preview.setBlockedCount(blocked);
        preview.setManualPendingCount(manual);
        preview.setPassCount(pass);
        preview.setAppealSkipCount(appealSkip);
        preview.setErrorCount(errors);
        preview.setItems(views);
        preview.setSummary(String.format(
                "本批 %d 人：自动通过 %d，不通过 %d，存疑转人工 %d，申诉处理中跳过 %d，结构性错误 %d（执行整批拒收）",
                views.size(), pass, blocked, manual, appealSkip, errors));
        return preview;
    }

    // ---------- 执行 ----------

    @Transactional
    public SubmitResult execute(RegSubmitRequest request) {
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        SubmitResult result = new SubmitResult();
        result.setOperator(operator);
        result.setExecutedAt(now.toString());

        // 同一口径只评估一次：预检与执行共用同一份逐项结果，防止两趟之间规则发布导致版本漂移
        List<SubmitItemView> evaluated = new ArrayList<>();
        Set<String> fileKeys = new HashSet<>();
        for (RegSubmitItem item : request.getSubmissions()) {
            SubmitItemView view = evaluateView(item);
            if (view.isAppealInProgress()) {
                result.getSkipped().add(view);
                evaluated.add(view);
                continue;
            }
            if (view.getErrorCode() != null) {
                throw new BusinessException(view.getErrorCode(), view.getNote());
            }
            String fileKey = item.getRegNo().trim() + "|" + item.getLevelCode().trim().toUpperCase()
                    + "|" + (item.getBatchId() == null ? "" : item.getBatchId());
            if (!fileKeys.add(fileKey)) {
                throw new BusinessException("DUP_SUBMIT_IN_FILE",
                        "清单内同一报名号+级别重复提交，整批拒收：" + item.getRegNo());
            }
            evaluated.add(view);
        }

        for (int i = 0; i < request.getSubmissions().size(); i++) {
            RegSubmitItem item = request.getSubmissions().get(i);
            SubmitItemView view = evaluated.get(i);
            if (view.isAppealInProgress()) {
                continue;
            }
            RegConditionSubmission submission = persistSubmission(item, view, operator);
            view.setSubmissionId(submission.getId());
            view.setSubmissionNo(submission.getSubmissionNo());
            result.getCreated().add(view);
        }
        return result;
    }

    // ---------- 单人核验视图（不落库） ----------

    private SubmitItemView evaluateView(RegSubmitItem item) {
        SubmitItemView view = new SubmitItemView();
        view.setRegNo(item.getRegNo().trim());
        view.setName(item.getName().trim());
        view.setLevelCode(item.getLevelCode().trim().toUpperCase());

        // 1) 申诉期间不重复拦截：存在 PENDING 申诉直接跳过
        RegAppeal pendingAppeal = findPendingAppeal(item);
        if (pendingAppeal != null) {
            view.setActionable(false);
            view.setAppealInProgress(true);
            view.setAppealNo(pendingAppeal.getAppealNo());
            view.setNote("该报名条件不通过结论申诉处理中（申诉单 " + pendingAppeal.getAppealNo()
                    + "），申诉期间不重复拦截，本次跳过");
            return view;
        }

        // 2) 解析批次/项目、关联名册
        String projectCode;
        Long registrationId = null;
        try {
            projectCode = resolveProjectAndLevel(item);
            if (item.getBatchId() != null) {
                Registration reg = registrationMapper.selectOne(new QueryWrapper<Registration>()
                        .eq("batch_id", item.getBatchId())
                        .eq("reg_no", item.getRegNo().trim())
                        .last("LIMIT 1"));
                if (reg != null) {
                    registrationId = reg.getId();
                }
            }
        } catch (BusinessException e) {
            view.setActionable(false);
            view.setErrorCode(e.getCode());
            view.setNote(e.getMessage());
            return view;
        }

        // 3) 提交时点取 ACTIVE 版本（规则变更按提交时点生效）
        LocalDateTime submittedAt = parseSubmittedAt(item.getSubmittedAt());
        RegConditionRuleVersion version;
        RegConditionRule rule;
        try {
            version = ruleService.requireActiveVersion(projectCode, item.getLevelCode());
            rule = ruleMapper.selectById(version.getRuleId());
        } catch (BusinessException e) {
            view.setActionable(false);
            view.setErrorCode(e.getCode());
            view.setNote(e.getMessage());
            return view;
        }

        // 4) 逐项比对
        RegConditionItemsConfig items = ruleService.readItems(version.getItemsConfig());
        List<ItemCheckResult> checks = engine.evaluate(items, rule.getYearsBasis(), item, submittedAt);
        int pass = 0, fail = 0, doubt = 0;
        for (ItemCheckResult c : checks) {
            if (RegCondConstants.CHECK_PASS.equals(c.getResult())) {
                pass++;
            } else if (RegCondConstants.CHECK_FAIL.equals(c.getResult())) {
                fail++;
            } else {
                doubt++;
            }
        }
        view.setChecks(checks);
        view.setPassCount(pass);
        view.setFailCount(fail);
        view.setDoubtCount(doubt);
        view.setRuleVersionNo(version.getVersionNo());
        view.setActionable(true);

        // 有明确 FAIL 即不通过；无 FAIL 有 DOUBT 转人工；全过自动通过
        if (fail > 0) {
            view.setStatus(RegCondConstants.SUB_FAIL);
            view.setNote("核验不通过，共 " + fail + " 项不符合，详见逐项原因");
        } else if (doubt > 0) {
            view.setStatus(RegCondConstants.SUB_MANUAL_PENDING);
            view.setNote("有 " + doubt + " 项材料存疑，转人工复核");
        } else {
            view.setStatus(RegCondConstants.SUB_PASS);
            view.setNote("全部核验项通过");
        }
        return view;
    }

    // ---------- 落库 ----------

    private RegConditionSubmission persistSubmission(RegSubmitItem item, SubmitItemView view, String operator) {
        LocalDateTime submittedAt = parseSubmittedAt(item.getSubmittedAt());
        RegConditionRuleVersion version = versionMapper.selectOne(
                new QueryWrapper<RegConditionRuleVersion>().eq("version_no", view.getRuleVersionNo()));

        RegConditionSubmission submission = new RegConditionSubmission();
        submission.setSubmissionNo(nextSubmissionNo(submittedAt));
        submission.setProjectCode(version == null ? RegCondConstants.PROJECT_DEFAULT
                : ruleMapper.selectById(version.getRuleId()).getProjectCode());
        submission.setLevelCode(view.getLevelCode());
        submission.setBatchId(item.getBatchId());
        submission.setRegistrationId(findRegistrationId(item));
        submission.setRegNo(view.getRegNo());
        submission.setName(view.getName());
        submission.setSubmittedAt(submittedAt);
        if (version != null) {
            submission.setRuleId(version.getRuleId());
            submission.setRuleVersionId(version.getId());
            submission.setRuleVersionNo(version.getVersionNo());
        }
        submission.setStatus(view.getStatus());
        submission.setFailCount(view.getFailCount());
        submission.setDoubtCount(view.getDoubtCount());
        submission.setOperator(operator);
        submission.setRemark(item.getRemark());
        submissionMapper.insert(submission);

        for (ItemCheckResult c : view.getChecks()) {
            RegConditionCheck check = new RegConditionCheck();
            check.setSubmissionId(submission.getId());
            check.setItemType(c.getItemType());
            check.setItemKey(c.getItemKey());
            check.setItemLabel(c.getItemLabel());
            check.setResult(c.getResult());
            check.setReason(c.getReason());
            check.setDetailJson(c.getDetailJson());
            checkMapper.insert(check);
        }

        recordEvent(submission.getId(), "SUBMIT", null, submission.getStatus(),
                "按规则版本 " + submission.getRuleVersionNo() + " 逐项核验", operator, submittedAt);
        if (RegCondConstants.SUB_PASS.equals(submission.getStatus())) {
            recordEvent(submission.getId(), "AUTO_PASS", null, RegCondConstants.SUB_PASS,
                    "全部核验项机器判定通过", operator, submittedAt);
        } else if (RegCondConstants.SUB_MANUAL_PENDING.equals(submission.getStatus())) {
            recordEvent(submission.getId(), "TO_MANUAL", null, RegCondConstants.SUB_MANUAL_PENDING,
                    view.getDoubtCount() + " 项存疑，转人工复核", operator, submittedAt);
        }
        return submission;
    }

    /** 人工复核存疑提交单：仅 MANUAL_PENDING 可复核，逐项存疑项改判并给整体结论。 */
    @Transactional
    public RegConditionSubmission manualReview(Long submissionId, ManualReviewRequest request) {
        RegConditionSubmission submission = requireSubmission(submissionId);
        if (!RegCondConstants.SUB_MANUAL_PENDING.equals(submission.getStatus())) {
            throw new BusinessException("SUBMISSION_NOT_PENDING",
                    "仅存疑待人工的提交单可复核，当前状态：" + submission.getStatus());
        }
        String outcome = request.getOutcome() == null ? "" : request.getOutcome().trim().toUpperCase();
        if (!RegCondConstants.MANUAL_OUTCOME_PASS.equals(outcome)
                && !RegCondConstants.MANUAL_OUTCOME_FAIL.equals(outcome)) {
            throw new BusinessException("MANUAL_OUTCOME_INVALID", "人工结论必须为 PASS 或 FAIL");
        }
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        Map<Long, ManualReviewRequest.ManualItemResult> overrides = new HashMap<>();
        if (request.getItemResults() != null) {
            for (ManualReviewRequest.ManualItemResult ir : request.getItemResults()) {
                if (ir != null && ir.getCheckId() != null) {
                    overrides.put(ir.getCheckId(), ir);
                }
            }
        }

        List<RegConditionCheck> checks = checkMapper.selectList(new QueryWrapper<RegConditionCheck>()
                .eq("submission_id", submissionId).orderByAsc("id"));
        for (RegConditionCheck check : checks) {
            if (!RegCondConstants.CHECK_DOUBT.equals(check.getResult())) {
                continue;
            }
            ManualReviewRequest.ManualItemResult ir = overrides.get(check.getId());
            String itemResult = ir == null ? outcome : ir.getResult().trim().toUpperCase();
            if (!RegCondConstants.MANUAL_OUTCOME_PASS.equals(itemResult)
                    && !RegCondConstants.MANUAL_OUTCOME_FAIL.equals(itemResult)) {
                throw new BusinessException("MANUAL_ITEM_RESULT_INVALID",
                        "逐项人工结论必须为 PASS 或 FAIL：checkId=" + check.getId());
            }
            check.setManualResult(itemResult);
            check.setManualReason(ir != null && ir.getReason() != null ? ir.getReason()
                    : (request.getComment() == null ? "人工复核" : request.getComment()));
            check.setReviewer(operator);
            check.setReviewedAt(now);
            checkMapper.updateById(check);
        }

        String fromStatus = submission.getStatus();
        submission.setStatus(RegCondConstants.MANUAL_OUTCOME_PASS.equals(outcome)
                ? RegCondConstants.SUB_PASS : RegCondConstants.SUB_FAIL);
        submission.setManualOutcome(outcome);
        submission.setReviewedBy(operator);
        submission.setReviewedAt(now);
        submission.setReviewComment(request.getComment());
        submissionMapper.updateById(submission);

        recordEvent(submissionId, RegCondConstants.MANUAL_OUTCOME_PASS.equals(outcome)
                        ? "MANUAL_PASS" : "MANUAL_FAIL", fromStatus, submission.getStatus(),
                request.getComment(), operator, now);
        return submission;
    }

    // ---------- 查询 ----------

    public RegConditionSubmission requireSubmission(Long id) {
        RegConditionSubmission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException("SUBMISSION_NOT_FOUND", "报名条件提交单不存在：id=" + id);
        }
        return submission;
    }

    public RegConditionSubmission requireSubmissionByNo(String no) {
        if (no == null || no.trim().isEmpty()) {
            throw new BusinessException("SUBMISSION_NO_REQUIRED", "提交单号不能为空");
        }
        RegConditionSubmission submission = submissionMapper.selectOne(
                new QueryWrapper<RegConditionSubmission>().eq("submission_no", no.trim()));
        if (submission == null) {
            throw new BusinessException("SUBMISSION_NOT_FOUND", "报名条件提交单不存在：" + no.trim());
        }
        return submission;
    }

    public List<RegConditionSubmission> listSubmissions(Long batchId, String regNo,
                                                        String levelCode, String status) {
        QueryWrapper<RegConditionSubmission> wrapper = new QueryWrapper<RegConditionSubmission>()
                .orderByDesc("submitted_at", "id");
        if (batchId != null) {
            wrapper.eq("batch_id", batchId);
        }
        if (regNo != null && !regNo.trim().isEmpty()) {
            wrapper.eq("reg_no", regNo.trim());
        }
        if (levelCode != null && !levelCode.trim().isEmpty()) {
            wrapper.eq("level_code", levelCode.trim().toUpperCase());
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        return submissionMapper.selectList(wrapper);
    }

    public List<RegConditionCheck> listChecks(Long submissionId) {
        return checkMapper.selectList(new QueryWrapper<RegConditionCheck>()
                .eq("submission_id", submissionId).orderByAsc("id"));
    }

    public List<RegSubmissionEvent> listEvents(Long submissionId) {
        return eventMapper.selectList(new QueryWrapper<RegSubmissionEvent>()
                .eq("submission_id", submissionId).orderByAsc("event_at", "id"));
    }

    /** 追加流转事件（申诉服务在改判时调用）。 */
    public void recordEvent(Long submissionId, String action, String fromStatus, String toStatus,
                            String detail, String operator, LocalDateTime at) {
        RegSubmissionEvent event = new RegSubmissionEvent();
        event.setSubmissionId(submissionId);
        event.setAction(action);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setDetail(detail);
        event.setOperator(operator);
        event.setEventAt(at == null ? LocalDateTime.now() : at);
        eventMapper.insert(event);
    }

    // ---------- 内部 ----------

    private RegAppeal findPendingAppeal(RegSubmitItem item) {
        QueryWrapper<RegAppeal> wrapper = new QueryWrapper<RegAppeal>()
                .eq("reg_no", item.getRegNo().trim())
                .eq("level_code", item.getLevelCode().trim().toUpperCase())
                .eq("status", RegCondConstants.APPEAL_PENDING)
                .orderByDesc("id")
                .last("LIMIT 1");
        if (item.getBatchId() == null) {
            wrapper.isNull("batch_id");
        } else {
            wrapper.eq("batch_id", item.getBatchId());
        }
        return appealMapper.selectOne(wrapper);
    }

    private Long findRegistrationId(RegSubmitItem item) {
        if (item.getBatchId() == null) {
            return null;
        }
        Registration reg = registrationMapper.selectOne(new QueryWrapper<Registration>()
                .eq("batch_id", item.getBatchId())
                .eq("reg_no", item.getRegNo().trim())
                .last("LIMIT 1"));
        return reg == null ? null : reg.getId();
    }

    private String resolveProjectAndLevel(RegSubmitItem item) {
        if (item.getLevelCode() == null || item.getLevelCode().trim().isEmpty()) {
            throw new BusinessException("LEVEL_REQUIRED", "报考级别不能为空");
        }
        if (item.getBatchId() != null) {
            ExamBatch batch = batchMapper.selectById(item.getBatchId());
            if (batch == null) {
                throw new BusinessException("BATCH_NOT_FOUND", "批次不存在：id=" + item.getBatchId());
            }
            return batch.getProjectCode() == null ? RegCondConstants.PROJECT_DEFAULT : batch.getProjectCode();
        }
        if (item.getProjectCode() == null || item.getProjectCode().trim().isEmpty()) {
            return RegCondConstants.PROJECT_DEFAULT;
        }
        return item.getProjectCode().trim().toUpperCase();
    }

    private LocalDateTime parseSubmittedAt(String text) {
        if (text == null || text.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        String t = text.trim();
        try {
            return LocalDateTime.parse(t, DT_FMT);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return java.time.LocalDate.parse(t, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new BusinessException("SUBMITTED_AT_FORMAT_INVALID",
                    "提交时点格式无法识别：" + t + "（要求 yyyy-MM-dd HH:mm:ss）");
        }
    }

    /** 提交号：BM + yyyyMMdd + 4 位流水（全局按日递增，取现有最大序号 +1）。 */
    private String nextSubmissionNo(LocalDateTime at) {
        String prefix = "BM" + at.format(DateTimeFormatter.BASIC_ISO_DATE);
        int max = 0;
        for (RegConditionSubmission s : submissionMapper.selectList(
                new QueryWrapper<RegConditionSubmission>().likeRight("submission_no", prefix))) {
            String no = s.getSubmissionNo();
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
