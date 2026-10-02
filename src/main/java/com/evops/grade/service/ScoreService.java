package com.evops.grade.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.absence.service.AbsenceMarkService;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.Registration;
import com.evops.exam.service.ExamBatchService;
import com.evops.exam.service.RegistrationService;
import com.evops.grade.common.GradeConstants;
import com.evops.grade.dto.ScoreImportItem;
import com.evops.grade.dto.ScoreImportRequest;
import com.evops.grade.entity.ExamScore;
import com.evops.grade.entity.GradeRule;
import com.evops.grade.entity.GradeRuleVersion;
import com.evops.grade.entity.ScoreChangeLog;
import com.evops.grade.mapper.ExamScoreMapper;
import com.evops.grade.mapper.ScoreChangeLogMapper;
import com.evops.grade.vo.ScoreImportPreview;
import com.evops.grade.vo.ScorePreviewItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 成绩：卷面分批量录入，按批次项目的生效规则版本划段定级；
 * 成绩行记下规则版本，规则以后升级不影响本批次口径；改分前后都进改分日志。
 */
@Service
public class ScoreService {

    private final ExamScoreMapper scoreMapper;
    private final ScoreChangeLogMapper changeLogMapper;
    private final ExamBatchService batchService;
    private final RegistrationService registrationService;
    private final GradeRuleService ruleService;
    private final AbsenceMarkService absenceMarkService;

    public ScoreService(ExamScoreMapper scoreMapper,
                        ScoreChangeLogMapper changeLogMapper,
                        ExamBatchService batchService,
                        RegistrationService registrationService,
                        GradeRuleService ruleService,
                        AbsenceMarkService absenceMarkService) {
        this.scoreMapper = scoreMapper;
        this.changeLogMapper = changeLogMapper;
        this.batchService = batchService;
        this.registrationService = registrationService;
        this.ruleService = ruleService;
        this.absenceMarkService = absenceMarkService;
    }

    // ---------- 批量录入预检 ----------

    @Transactional(readOnly = true)
    public ScoreImportPreview previewImport(ScoreImportRequest request) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        Map<String, Registration> regMap = loadRegistrations(request);
        Map<String, ExamScore> existing = loadExistingScores(request);
        RuleContext rule = safeResolveRule(batch);

        List<ScorePreviewItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int actionable = 0;
        int skip = 0;
        for (ScoreImportItem in : request.getScores()) {
            String regNo = in.getRegNo().trim();
            boolean dupInFile = !seen.add(regNo);

            String note;
            boolean ok = false;
            String grade = null;
            if (dupInFile) {
                note = "文件内报名号重复，将拒收";
            } else {
                Registration reg = regMap.get(regNo);
                if (reg == null) {
                    note = "名册中查无此报名号，将跳过";
                } else if (!reg.getBatchId().equals(batch.getId())) {
                    note = "报名号不属于本批次（属于批次 id=" + reg.getBatchId() + "），将跳过";
                } else if (existing.containsKey(regNo)) {
                    note = "成绩已录入，将跳过（改分须走复核）";
                } else if (rule.error != null) {
                    note = rule.error;
                } else {
                    BigDecimal s = normalizeScore(in.getPaperScore());
                    if (s.compareTo(BigDecimal.ZERO) < 0
                            || s.compareTo(BigDecimal.valueOf(rule.rule.getMaxScore())) > 0) {
                        note = "卷面分超出规则范围 [0, " + rule.rule.getMaxScore() + "]，将跳过";
                    } else {
                        grade = ruleService.gradeOf(rule.version, s);
                        note = "新增成绩，定级：" + grade + "（规则版本 " + rule.version.getVersionNo() + "）";
                        if (absenceMarkService.isInvalidated(reg.getId())) {
                            note += "；该学员有生效的无效化裁定，成绩将记为无效";
                        }
                        ok = true;
                    }
                }
            }
            if (ok) {
                actionable++;
            } else {
                skip++;
            }
            items.add(new ScorePreviewItem(regNo, in.getPaperScore(), grade, ok, note));
        }

        ScoreImportPreview preview = new ScoreImportPreview();
        preview.setBatchId(batch.getId());
        preview.setBatchCode(batch.getCode());
        preview.setProjectCode(batch.getProjectCode());
        preview.setTotalScanned(items.size());
        preview.setActionableCount(actionable);
        preview.setSkipCount(skip);
        preview.setRuleVersionNo(rule.version == null ? null : rule.version.getVersionNo());
        preview.setSummary(String.format("批次 %s 成绩 %d 条：可录入 %d 条，跳过 %d 条",
                batch.getCode(), items.size(), actionable, skip));
        preview.setItems(items);
        return preview;
    }

    // ---------- 批量录入执行（任何一条不通过整批拒收，不落半条） ----------

    @Transactional
    public List<ExamScore> importScores(ScoreImportRequest request) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        Map<String, Registration> regMap = loadRegistrations(request);
        Map<String, ExamScore> existing = loadExistingScores(request);
        Set<String> seen = new HashSet<>();

        List<ExamScore> toInsert = new ArrayList<>();
        for (ScoreImportItem in : request.getScores()) {
            String regNo = in.getRegNo().trim();
            if (!seen.add(regNo)) {
                throw new BusinessException("SCORE_DUP_IN_FILE", "成绩文件内报名号重复，整批拒收：" + regNo);
            }
            Registration reg = regMap.get(regNo);
            if (reg == null) {
                throw new BusinessException("REG_NOT_FOUND", "名册中查无此报名号，整批拒收：" + regNo);
            }
            if (!reg.getBatchId().equals(batch.getId())) {
                throw new BusinessException("REG_NOT_IN_BATCH", "报名号不属于本批次，整批拒收：" + regNo);
            }
            if (existing.containsKey(regNo)) {
                throw new BusinessException("SCORE_EXISTS", "成绩已录入，改分须走复核流程，整批拒收：" + regNo);
            }
            // 每条都按批次项目的生效版本定级（项目内多条规则时按科目匹配）
            GradeRuleVersion version = ruleService.requireActiveVersion(
                    batch.getProjectCode(), resolveSubject(batch, reg));
            GradeRule rule = ruleService.getRule(version.getRuleId());
            BigDecimal s = normalizeScore(in.getPaperScore());
            if (s.compareTo(BigDecimal.ZERO) < 0
                    || s.compareTo(BigDecimal.valueOf(rule.getMaxScore())) > 0) {
                throw new BusinessException("SCORE_OUT_OF_RANGE",
                        "卷面分超出规则范围 [0, " + rule.getMaxScore() + "]，整批拒收：" + regNo + "=" + s);
            }

            ExamScore score = new ExamScore();
            score.setBatchId(batch.getId());
            score.setRegistrationId(reg.getId());
            score.setRegNo(reg.getRegNo());
            score.setName(reg.getName());
            score.setPaperScore(s);
            score.setGrade(ruleService.gradeOf(version, s));
            score.setRuleVersionId(version.getId());
            score.setRuleVersionNo(version.getVersionNo());
            score.setGradedAt(now);
            score.setStatus(GradeConstants.SCORE_RECORDED);
            // 已有生效的无效化裁定（缺考/弃考/违纪）时，成绩落库即无效，与裁定口径一致
            score.setValid(!absenceMarkService.isInvalidated(reg.getId()));
            score.setRemark(in.getRemark());
            toInsert.add(score);
        }

        for (ExamScore score : toInsert) {
            scoreMapper.insert(score);
        }
        return toInsert;
    }

    // ---------- 查询 ----------

    public List<ExamScore> listByBatch(Long batchId) {
        batchService.getBatch(batchId);
        return scoreMapper.selectList(new QueryWrapper<ExamScore>()
                .eq("batch_id", batchId)
                .orderByAsc("reg_no"));
    }

    public ExamScore getScore(Long id) {
        ExamScore score = scoreMapper.selectById(id);
        if (score == null) {
            throw new BusinessException("SCORE_NOT_FOUND", "成绩不存在：id=" + id);
        }
        return score;
    }

    public ExamScore getByRegistration(Long registrationId) {
        return scoreMapper.selectOne(new QueryWrapper<ExamScore>().eq("registration_id", registrationId));
    }

    public List<ScoreChangeLog> listChangeLogs(Long batchId) {
        if (batchId != null) {
            return changeLogMapper.selectList(new QueryWrapper<ScoreChangeLog>()
                    .eq("batch_id", batchId)
                    .orderByAsc("changed_at", "id"));
        }
        return changeLogMapper.selectList(new QueryWrapper<ScoreChangeLog>()
                .orderByAsc("changed_at", "id"));
    }

    public List<ScoreChangeLog> listChangeLogsByScore(Long scoreId) {
        return changeLogMapper.selectList(new QueryWrapper<ScoreChangeLog>()
                .eq("score_id", scoreId)
                .orderByAsc("changed_at", "id"));
    }

    // ---------- 复核改分：按原定级版本重算等级，前后都留痕 ----------

    /**
     * 复核通过改分。按成绩原定级的规则版本重算等级（口径不变），
     * 改分前后写一条只追加日志；分数没变不允许走改分。
     */
    @Transactional
    public ScoreChangeLog applyReviewChange(ExamScore score, BigDecimal newScoreRaw,
                                            String reason, Long reviewId, String operator) {
        BigDecimal newScore = normalizeScore(newScoreRaw);
        GradeRuleVersion version = score.getRuleVersionId() == null
                ? null
                : ruleService.getVersion(score.getRuleVersionId());
        GradeRule rule = version == null ? null : ruleService.getRule(version.getRuleId());
        if (rule != null && (newScore.compareTo(BigDecimal.ZERO) < 0
                || newScore.compareTo(BigDecimal.valueOf(rule.getMaxScore())) > 0)) {
            throw new BusinessException("SCORE_OUT_OF_RANGE",
                    "改分后卷面分超出规则范围 [0, " + rule.getMaxScore() + "]：" + newScore);
        }

        BigDecimal oldScore = score.getPaperScore();
        String oldGrade = score.getGrade();
        if (newScore.compareTo(oldScore) == 0) {
            throw new BusinessException("NO_SCORE_CHANGE",
                    "复核通过但新分与原分相同（" + oldScore + "），请改走驳回或填写不同的分数");
        }
        String newGrade = version == null ? oldGrade : ruleService.gradeOf(version, newScore);
        LocalDateTime now = LocalDateTime.now();

        ScoreChangeLog log = new ScoreChangeLog();
        log.setScoreId(score.getId());
        log.setReviewId(reviewId);
        log.setBatchId(score.getBatchId());
        log.setRegNo(score.getRegNo());
        log.setName(score.getName());
        log.setScoreBefore(oldScore);
        log.setScoreAfter(newScore);
        log.setGradeBefore(oldGrade);
        log.setGradeAfter(newGrade);
        log.setChangeReason(reason);
        log.setRuleVersionNo(score.getRuleVersionNo());
        log.setOperator(operator);
        log.setChangedAt(now);
        changeLogMapper.insert(log);

        score.setPaperScore(newScore);
        score.setGrade(newGrade);
        score.setGradedAt(now);
        score.setStatus(GradeConstants.SCORE_RECORDED);
        scoreMapper.updateById(score);
        return log;
    }

    public void updateStatus(ExamScore score, String status) {
        score.setStatus(status);
        scoreMapper.updateById(score);
    }

    // ---------- 内部 ----------

    private RuleContext safeResolveRule(ExamBatch batch) {
        RuleContext ctx = new RuleContext();
        try {
            // 预检不绑定某个学员：通用规则用批次科目即可定位；无规则时逐条给出跳过原因
            GradeRuleVersion version = ruleService.requireActiveVersion(
                    batch.getProjectCode(), batch.getSubject());
            ctx.version = version;
            ctx.rule = ruleService.getRule(version.getRuleId());
        } catch (BusinessException e) {
            ctx.error = e.getMessage();
        }
        return ctx;
    }

    private String resolveSubject(ExamBatch batch, Registration reg) {
        if (reg.getSubject() != null && !reg.getSubject().trim().isEmpty()) {
            return reg.getSubject();
        }
        return batch.getSubject();
    }

    private Map<String, Registration> loadRegistrations(ScoreImportRequest request) {
        List<String> regNos = new ArrayList<>();
        for (ScoreImportItem in : request.getScores()) {
            regNos.add(in.getRegNo().trim());
        }
        Map<String, Registration> map = new HashMap<>();
        if (!regNos.isEmpty()) {
            for (Registration reg : registrationService.findByRegNos(regNos)) {
                map.put(reg.getRegNo(), reg);
            }
        }
        return map;
    }

    private Map<String, ExamScore> loadExistingScores(ScoreImportRequest request) {
        List<String> regNos = new ArrayList<>();
        for (ScoreImportItem in : request.getScores()) {
            regNos.add(in.getRegNo().trim());
        }
        Map<String, ExamScore> map = new HashMap<>();
        if (!regNos.isEmpty()) {
            for (ExamScore score : scoreMapper.selectList(
                    new QueryWrapper<ExamScore>().in("reg_no", regNos))) {
                map.put(score.getRegNo(), score);
            }
        }
        return map;
    }

    private BigDecimal normalizeScore(BigDecimal score) {
        if (score == null) {
            throw new BusinessException("SCORE_REQUIRED", "卷面分不能为空");
        }
        return score.setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    /** 预检内携带规则解析结果，失败时带中文原因，不打断整份预检。 */
    private static class RuleContext {
        GradeRuleVersion version;
        GradeRule rule;
        String error;
    }
}
