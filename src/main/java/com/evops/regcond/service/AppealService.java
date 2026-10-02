package com.evops.regcond.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.grade.service.ScoreNotificationService;
import com.evops.regcond.common.RegCondConstants;
import com.evops.regcond.dto.AppealConclusionRequest;
import com.evops.regcond.dto.AppealRequest;
import com.evops.regcond.entity.QualificationAppeal;
import com.evops.regcond.entity.QualificationAppealVersion;
import com.evops.regcond.entity.QualificationCheckItem;
import com.evops.regcond.entity.RegistrationQualification;
import com.evops.regcond.mapper.QualificationAppealMapper;
import com.evops.regcond.mapper.QualificationAppealVersionMapper;
import com.evops.regcond.mapper.QualificationCheckItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 条件核验申诉：
 * - 学员对某条不通过 / 存疑核验项发起申诉，流转版本只追加（CREATE/ACCEPT/APPROVE/REJECT）；
 * - 申诉期间该人该项不重复拦截：在办申诉（SUBMITTED/REVIEWING）使新提交的同项记 APPEALING 放行；
 * - 终裁通过：该核验项按豁免放行（按 人+项目+级别+核验项 维度，跨规则版本保持），核验单回算合格；
 * - 终裁驳回：核验项恢复机器原始结论，核验单回到不通过 / 存疑；
 * - 终裁结论站内通知学员。
 */
@Service
public class AppealService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final QualificationAppealMapper appealMapper;
    private final QualificationAppealVersionMapper versionMapper;
    private final QualificationCheckItemMapper checkItemMapper;
    private final QualificationService qualificationService;
    private final ScoreNotificationService notificationService;

    public AppealService(QualificationAppealMapper appealMapper,
                         QualificationAppealVersionMapper versionMapper,
                         QualificationCheckItemMapper checkItemMapper,
                         QualificationService qualificationService,
                         ScoreNotificationService notificationService) {
        this.appealMapper = appealMapper;
        this.versionMapper = versionMapper;
        this.checkItemMapper = checkItemMapper;
        this.qualificationService = qualificationService;
        this.notificationService = notificationService;
    }

    // ---------- 学员发起申诉 ----------

    @Transactional
    public QualificationAppeal apply(AppealRequest request) {
        QualificationCheckItem item = resolveCheckItem(request);
        RegistrationQualification qual = qualificationService.requireQualification(item.getQualificationId());

        if (RegCondConstants.CHECK_PASS.equals(item.getResult())
                || RegCondConstants.CHECK_APPEALING.equals(item.getResult())) {
            if (RegCondConstants.CHECK_APPEALING.equals(item.getResult()) && item.getAppealId() != null) {
                throw new BusinessException("APPEAL_IN_PROGRESS",
                        "该核验项已有处理中的申诉：" + item.getAppealId() + "，申诉期间无需重复申诉，也不会被拦截");
            }
            throw new BusinessException("APPEAL_ITEM_NOT_APPEALABLE",
                    "仅不通过 / 存疑的核验项可申诉，当前结论：" + item.getResult());
        }

        // 同一核验项行已有终裁申诉：不再叠加，要补材料请重新提交报名生成新核验单
        List<QualificationAppeal> existed = appealMapper.selectList(new QueryWrapper<QualificationAppeal>()
                .eq("check_item_id", item.getId()));
        for (QualificationAppeal a : existed) {
            if (RegCondConstants.APPEAL_APPROVED.equals(a.getStatus())) {
                throw new BusinessException("APPEAL_ALREADY_APPROVED",
                        "该核验项已有申诉通过（" + a.getAppealNo() + "），按豁免放行，无需重复申诉");
            }
            if (RegCondConstants.APPEAL_REJECTED.equals(a.getStatus())) {
                throw new BusinessException("APPEAL_ALREADY_REJECTED",
                        "该核验项的申诉已驳回（" + a.getAppealNo()
                                + "）；如补充了新材料，请重新提交报名生成新核验单后再申诉");
            }
            if (RegCondConstants.APPEAL_SUBMITTED.equals(a.getStatus())
                    || RegCondConstants.APPEAL_REVIEWING.equals(a.getStatus())) {
                throw new BusinessException("APPEAL_IN_PROGRESS",
                        "该核验项已有处理中的申诉：" + a.getAppealNo() + "，申诉期间不会被拦截");
            }
        }

        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        String applicant = request.getApplicant() == null || request.getApplicant().trim().isEmpty()
                ? operator : request.getApplicant().trim();
        String personKey = QualificationService.personKey(qual.getIdNo(), qual.getRegNo());

        QualificationAppeal appeal = new QualificationAppeal();
        appeal.setAppealNo(nextAppealNo(now.toLocalDate()));
        appeal.setQualificationId(qual.getId());
        appeal.setCheckItemId(item.getId());
        appeal.setProjectCode(qual.getProjectCode());
        appeal.setLevelCode(qual.getLevelCode());
        appeal.setPersonKey(personKey);
        appeal.setItemKey(item.getItemKey());
        appeal.setItemType(item.getItemType());
        appeal.setItemName(item.getItemName());
        appeal.setApplicant(applicant);
        appeal.setApplicantName(qual.getName());
        appeal.setReason(request.getReason().trim());
        appeal.setEvidence(request.getEvidence());
        appeal.setStatus(RegCondConstants.APPEAL_SUBMITTED);
        appeal.setCurrentVersion(1);
        appeal.setOperator(operator);
        appeal.setAppliedAt(now);
        appealMapper.insert(appeal);

        appendVersion(appeal.getId(), 1, RegCondConstants.APPEAL_SUBMITTED,
                RegCondConstants.CHANGE_CREATE, request.getReason().trim(), null, operator, now);

        // 核验项进入申诉中：保存机器原始结论，暂不拦截；机器原因保留在 reason 不覆盖
        item.setOriginalResult(item.getResult());
        item.setResult(RegCondConstants.CHECK_APPEALING);
        item.setAppealId(appeal.getId());
        checkItemMapper.updateById(item);

        qualificationService.recomputeAfterAppeal(qual.getId());
        return appeal;
    }

    // ---------- 受理 ----------

    @Transactional
    public QualificationAppeal accept(Long appealId, String comment) {
        QualificationAppeal appeal = requireAppeal(appealId);
        if (!RegCondConstants.APPEAL_SUBMITTED.equals(appeal.getStatus())) {
            throw new BusinessException("APPEAL_NOT_SUBMITTED",
                    "仅待受理申诉可受理，当前状态：" + appeal.getStatus());
        }
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        appeal.setStatus(RegCondConstants.APPEAL_REVIEWING);
        appeal.setCurrentVersion(appeal.getCurrentVersion() + 1);
        appeal.setReviewer(operator);
        appealMapper.updateById(appeal);
        appendVersion(appeal.getId(), appeal.getCurrentVersion(), RegCondConstants.APPEAL_REVIEWING,
                RegCondConstants.CHANGE_ACCEPT, comment, operator, operator, now);
        return appeal;
    }

    // ---------- 终裁 ----------

    @Transactional
    public QualificationAppeal conclude(Long appealId, AppealConclusionRequest request) {
        QualificationAppeal appeal = requireAppeal(appealId);
        if (RegCondConstants.APPEAL_APPROVED.equals(appeal.getStatus())
                || RegCondConstants.APPEAL_REJECTED.equals(appeal.getStatus())) {
            throw new BusinessException("APPEAL_ALREADY_CONCLUDED",
                    "申诉已终裁（" + appeal.getStatus() + "），结论不可更改：" + appeal.getAppealNo());
        }
        if (Boolean.FALSE.equals(request.getApproved())
                && (request.getConclusion() == null || request.getConclusion().trim().isEmpty())) {
            throw new BusinessException("APPEAL_REJECT_REASON_REQUIRED", "申诉驳回必须写明意见");
        }

        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        QualificationCheckItem item = checkItemMapper.selectById(appeal.getCheckItemId());
        if (item == null) {
            throw new BusinessException("CHECK_ITEM_NOT_FOUND",
                    "申诉关联的核验项不存在：id=" + appeal.getCheckItemId());
        }

        if (Boolean.TRUE.equals(request.getApproved())) {
            appeal.setStatus(RegCondConstants.APPEAL_APPROVED);
            appeal.setConclusion(request.getConclusion() == null ? "申诉通过，该核验项豁免放行"
                    : request.getConclusion().trim());
            appendVersion(appeal.getId(), appeal.getCurrentVersion() + 1, RegCondConstants.APPEAL_APPROVED,
                    RegCondConstants.CHANGE_APPROVE, appeal.getConclusion(), operator, operator, now);

            // 该项按豁免放行（机器原因保留在 reason，豁免依据 waiver_appeal_id）
            item.setResult(RegCondConstants.CHECK_PASS);
            item.setWaiverAppealId(appeal.getId());
            checkItemMapper.updateById(item);
        } else {
            appeal.setStatus(RegCondConstants.APPEAL_REJECTED);
            appeal.setConclusion(request.getConclusion().trim());
            appendVersion(appeal.getId(), appeal.getCurrentVersion() + 1, RegCondConstants.APPEAL_REJECTED,
                    RegCondConstants.CHANGE_REJECT, request.getConclusion().trim(), operator, operator, now);

            // 恢复机器原始结论（FAIL/MANUAL），解除申诉中标记
            String restore = item.getOriginalResult() == null
                    ? RegCondConstants.CHECK_FAIL : item.getOriginalResult();
            item.setResult(restore);
            checkItemMapper.updateById(item);
        }

        appeal.setCurrentVersion(appeal.getCurrentVersion() + 1);
        appeal.setReviewer(operator);
        appeal.setConcludedAt(now);
        appealMapper.updateById(appeal);

        // 申诉期间其他新提交的核验单同项也记了 APPEALING 并指向本申诉，终裁时一并处理
        RegistrationQualification qual = qualificationService.requireQualification(appeal.getQualificationId());
        List<QualificationCheckItem> linked = checkItemMapper.selectList(new QueryWrapper<QualificationCheckItem>()
                .eq("appeal_id", appeal.getId())
                .ne("id", item.getId()));
        java.util.Set<Long> requalify = new java.util.HashSet<>();
        requalify.add(qual.getId());
        for (QualificationCheckItem linkedItem : linked) {
            if (Boolean.TRUE.equals(request.getApproved())) {
                linkedItem.setResult(RegCondConstants.CHECK_PASS);
                linkedItem.setWaiverAppealId(appeal.getId());
            } else {
                linkedItem.setResult(linkedItem.getOriginalResult() == null
                        ? RegCondConstants.CHECK_FAIL : linkedItem.getOriginalResult());
            }
            checkItemMapper.updateById(linkedItem);
            requalify.add(linkedItem.getQualificationId());
        }

        // 回算核验单结论（通过→可能合格；驳回→恢复不通过/存疑；已入册的不动）
        for (Long qualificationId : requalify) {
            qualificationService.recomputeAfterAppeal(qualificationId);
        }

        notifyConclusion(appeal, qual);
        return appeal;
    }

    // ---------- 查询 ----------

    public QualificationAppeal requireAppeal(Long id) {
        if (id == null) {
            throw new BusinessException("APPEAL_ID_REQUIRED", "申诉单 id 不能为空");
        }
        QualificationAppeal appeal = appealMapper.selectById(id);
        if (appeal == null) {
            throw new BusinessException("APPEAL_NOT_FOUND", "申诉单不存在：id=" + id);
        }
        return appeal;
    }

    public List<QualificationAppeal> list(String status, String regNo, String personKey) {
        QueryWrapper<QualificationAppeal> wrapper = new QueryWrapper<QualificationAppeal>()
                .orderByDesc("id");
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        if (regNo != null && !regNo.trim().isEmpty()) {
            // 申诉表未冗余报名号，按核验单反查
            List<RegistrationQualification> quals = qualificationService.listByRegNo(regNo.trim());
            java.util.Set<Long> ids = new java.util.HashSet<>();
            for (RegistrationQualification q : quals) {
                ids.add(q.getId());
            }
            if (ids.isEmpty()) {
                return new java.util.ArrayList<>();
            }
            wrapper.in("qualification_id", ids);
        }
        if (personKey != null && !personKey.trim().isEmpty()) {
            wrapper.eq("person_key", personKey.trim().toUpperCase());
        }
        return appealMapper.selectList(wrapper);
    }

    public List<QualificationAppealVersion> listVersions(Long appealId) {
        requireAppeal(appealId);
        return versionMapper.selectList(new QueryWrapper<QualificationAppealVersion>()
                .eq("appeal_id", appealId).orderByAsc("version_no"));
    }

    // ---------- 内部 ----------

    private QualificationCheckItem resolveCheckItem(AppealRequest request) {
        Long itemId = request.getCheckItemId();
        if (itemId == null) {
            if (request.getQualificationId() == null || request.getItemKey() == null
                    || request.getItemKey().trim().isEmpty()) {
                throw new BusinessException("APPEAL_TARGET_REQUIRED",
                        "申诉须指定 checkItemId，或同时给出 qualificationId 与 itemKey");
            }
            QualificationCheckItem item = checkItemMapper.selectOne(new QueryWrapper<QualificationCheckItem>()
                    .eq("qualification_id", request.getQualificationId())
                    .eq("item_key", request.getItemKey().trim().toUpperCase())
                    .orderByDesc("id").last("LIMIT 1"));
            if (item == null) {
                throw new BusinessException("CHECK_ITEM_NOT_FOUND",
                        "核验项不存在：核验单 " + request.getQualificationId() + " / " + request.getItemKey());
            }
            return item;
        }
        QualificationCheckItem item = checkItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException("CHECK_ITEM_NOT_FOUND", "核验项不存在：id=" + itemId);
        }
        return item;
    }

    private void appendVersion(Long appealId, int versionNo, String status, String changeType,
                               String reason, String reviewer, String operator, LocalDateTime at) {
        QualificationAppealVersion v = new QualificationAppealVersion();
        v.setAppealId(appealId);
        v.setVersionNo(versionNo);
        v.setStatus(status);
        v.setChangeType(changeType);
        v.setChangeReason(reason);
        v.setReviewer(reviewer);
        v.setOperator(operator);
        v.setChangedAt(at);
        versionMapper.insert(v);
    }

    private void notifyConclusion(QualificationAppeal appeal, RegistrationQualification qual) {
        boolean approved = RegCondConstants.APPEAL_APPROVED.equals(appeal.getStatus());
        String title = "报名条件申诉" + (approved ? "通过" : "驳回") + "通知（" + appeal.getAppealNo() + "）";
        String content = "您就「" + appeal.getItemName() + "」的申诉已" + (approved ? "通过" : "驳回")
                + "。" + appeal.getConclusion()
                + (approved ? "该核验项已豁免放行，可继续完成报名。" : "如有新材料，请重新提交报名后再行申诉。");
        notificationService.notify(qual.getBatchId(), qual.getRegistrationId(),
                qual.getRegNo(), qual.getName(),
                approved ? "COND_APPEAL_APPROVED" : "COND_APPEAL_REJECTED",
                title, content, appeal.getId(), appeal.getReviewer());
    }

    /** 申诉单号：SS + yyMMdd + 4 位流水。 */
    private String nextAppealNo(LocalDate date) {
        String prefix = "SS" + date.format(NO_DATE);
        int max = 0;
        for (QualificationAppeal a : appealMapper.selectList(
                new QueryWrapper<QualificationAppeal>().likeRight("appeal_no", prefix))) {
            String no = a.getAppealNo();
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
}
