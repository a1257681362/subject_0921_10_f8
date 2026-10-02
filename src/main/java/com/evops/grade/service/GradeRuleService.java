package com.evops.grade.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.grade.common.GradeConstants;
import com.evops.grade.dto.GradeBand;
import com.evops.grade.dto.GradeRuleRequest;
import com.evops.grade.dto.GradeRuleVersionRequest;
import com.evops.grade.entity.GradeRule;
import com.evops.grade.entity.GradeRuleVersion;
import com.evops.grade.mapper.GradeRuleMapper;
import com.evops.grade.mapper.GradeRuleVersionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 等级规则：
 * - 规则按项目（可按科目）建档，规则下挂多个版本；
 * - 版本状态 DRAFT → ACTIVE → DISABLED：发布即冻结分数段，并自动停用同一规则的旧生效版；
 * - 卷面分按分数段落等级，定级只认生效版，历史成绩保留版本号；
 * - 停用不删版本，历史口径可追溯。
 */
@Service
public class GradeRuleService {

    private final GradeRuleMapper ruleMapper;
    private final GradeRuleVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public GradeRuleService(GradeRuleMapper ruleMapper,
                            GradeRuleVersionMapper versionMapper,
                            ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
    }

    // ---------- 规则模块 ----------

    @Transactional
    public GradeRule createRule(GradeRuleRequest request) {
        GradeRule rule = new GradeRule();
        rule.setCode(request.getCode().trim());
        rule.setName(request.getName().trim());
        rule.setProjectCode(normalizeProject(request.getProjectCode()));
        rule.setSubject(trimToNull(request.getSubject()));
        rule.setMaxScore(request.getMaxScore() == null ? 100 : request.getMaxScore());
        rule.setRemark(request.getRemark());
        try {
            ruleMapper.insert(rule);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_RULE_CODE", "规则编码已存在：" + rule.getCode());
        }
        return rule;
    }

    public List<GradeRule> listRules(String projectCode) {
        QueryWrapper<GradeRule> wrapper = new QueryWrapper<GradeRule>().orderByAsc("project_code", "code");
        if (projectCode != null && !projectCode.trim().isEmpty()) {
            wrapper.eq("project_code", projectCode.trim());
        }
        return ruleMapper.selectList(wrapper);
    }

    public GradeRule getRule(Long ruleId) {
        GradeRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException("RULE_NOT_FOUND", "等级规则不存在：id=" + ruleId);
        }
        return rule;
    }

    // ---------- 规则版本 ----------

    /** 新建草稿版本：校验分数段后以 JSON 冻结落库（草稿也不允许半截口径）。 */
    @Transactional
    public GradeRuleVersion createDraftVersion(Long ruleId, GradeRuleVersionRequest request) {
        GradeRule rule = getRule(ruleId);
        List<GradeBand> bands = validateBands(rule, request.getBands());

        GradeRuleVersion version = new GradeRuleVersion();
        version.setRuleId(rule.getId());
        version.setVersionNo(nextVersionNo(rule.getId()));
        version.setStatus(GradeConstants.VERSION_DRAFT);
        version.setGradeBands(writeBands(bands));
        version.setRemark(request.getRemark());
        version.setOperator(OperatorHolder.current());
        versionMapper.insert(version);
        return version;
    }

    /** 发布：DRAFT → ACTIVE；同一规则的旧 ACTIVE 自动置 DISABLED，同规则同时刻仅一版生效。 */
    @Transactional
    public GradeRuleVersion publishVersion(Long versionId) {
        GradeRuleVersion version = requireVersion(versionId);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        if (GradeConstants.VERSION_ACTIVE.equals(version.getStatus())) {
            throw new BusinessException("VERSION_ALREADY_ACTIVE",
                    "版本已是生效状态，无需重复发布：" + version.getVersionNo());
        }
        if (GradeConstants.VERSION_DISABLED.equals(version.getStatus())) {
            throw new BusinessException("VERSION_DISABLED", "停用版本不可重新发布，请新建版本：" + version.getVersionNo());
        }

        // 先顶替旧生效版（含可能的历史停用标记，保留停用经办）
        List<GradeRuleVersion> actives = versionMapper.selectList(new QueryWrapper<GradeRuleVersion>()
                .eq("rule_id", version.getRuleId())
                .eq("status", GradeConstants.VERSION_ACTIVE));
        for (GradeRuleVersion active : actives) {
            active.setStatus(GradeConstants.VERSION_DISABLED);
            active.setDisabledBy(operator);
            active.setDisabledAt(now);
            versionMapper.updateById(active);
        }

        version.setStatus(GradeConstants.VERSION_ACTIVE);
        version.setPublishedBy(operator);
        version.setPublishedAt(now);
        versionMapper.updateById(version);
        return version;
    }

    /** 人工停用生效版（停用后该规则在重新发布前无生效口径，无法定级）。 */
    @Transactional
    public GradeRuleVersion disableVersion(Long versionId) {
        GradeRuleVersion version = requireVersion(versionId);
        if (!GradeConstants.VERSION_ACTIVE.equals(version.getStatus())) {
            throw new BusinessException("VERSION_NOT_ACTIVE", "仅生效版本可停用：" + version.getVersionNo());
        }
        version.setStatus(GradeConstants.VERSION_DISABLED);
        version.setDisabledBy(OperatorHolder.current());
        version.setDisabledAt(LocalDateTime.now());
        versionMapper.updateById(version);
        return version;
    }

    @Transactional(readOnly = true)
    public List<GradeRuleVersion> listVersions(Long ruleId) {
        getRule(ruleId);
        return versionMapper.selectList(new QueryWrapper<GradeRuleVersion>()
                .eq("rule_id", ruleId)
                .orderByDesc("id"));
    }

    public GradeRuleVersion getVersion(Long versionId) {
        return requireVersion(versionId);
    }

    public GradeRuleVersion getVersionByNo(String versionNo) {
        if (versionNo == null || versionNo.trim().isEmpty()) {
            throw new BusinessException("VERSION_NO_REQUIRED", "规则版本号不能为空");
        }
        GradeRuleVersion version = versionMapper.selectOne(
                new QueryWrapper<GradeRuleVersion>().eq("version_no", versionNo.trim()));
        if (version == null) {
            throw new BusinessException("RULE_VERSION_NOT_FOUND", "规则版本不存在：" + versionNo.trim());
        }
        return version;
    }

    /**
     * 取项目当前生效版本：优先科目精确匹配，其次项目通用规则（subject 为空）。
     * 找不到或存在多条无法唯一确定时抛错，避免两个岗口径打架。
     */
    @Transactional(readOnly = true)
    public GradeRuleVersion requireActiveVersion(String projectCode, String subject) {
        String project = normalizeProject(projectCode);
        List<GradeRule> rules = ruleMapper.selectList(
                new QueryWrapper<GradeRule>().eq("project_code", project));
        if (rules.isEmpty()) {
            throw new BusinessException("NO_RULE_FOR_PROJECT",
                    "项目尚未配置等级规则：" + project + "，请先建档并发布规则版本");
        }

        GradeRule matched = null;
        for (GradeRule rule : rules) {
            boolean subjectHit = subject != null && subject.equals(rule.getSubject());
            if (subjectHit) {
                matched = rule;
                break;
            }
        }
        if (matched == null) {
            List<GradeRule> general = new ArrayList<>();
            for (GradeRule rule : rules) {
                if (rule.getSubject() == null) {
                    general.add(rule);
                }
            }
            if (general.size() == 1) {
                matched = general.get(0);
            } else if (general.size() > 1) {
                throw new BusinessException("MULTIPLE_GENERAL_RULES",
                        "项目 " + project + " 存在多条通用规则，无法唯一定级，请按科目精确配置");
            }
        }
        if (matched == null) {
            throw new BusinessException("NO_RULE_FOR_SUBJECT",
                    "项目 " + project + " 下没有匹配科目（" + subject + "）或通用规则的生效版本");
        }

        GradeRuleVersion active = versionMapper.selectOne(new QueryWrapper<GradeRuleVersion>()
                .eq("rule_id", matched.getId())
                .eq("status", GradeConstants.VERSION_ACTIVE)
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (active == null) {
            throw new BusinessException("NO_ACTIVE_RULE_VERSION",
                    "规则 " + matched.getCode() + " 尚无生效版本，请先发布草稿");
        }
        return active;
    }

    /** 试算：按指定版本给卷面分划等级，不写任何数据。 */
    @Transactional(readOnly = true)
    public Map<String, Object> trial(Long versionId, BigDecimal score) {
        GradeRuleVersion version = requireVersion(versionId);
        GradeRule rule = getRule(version.getRuleId());
        if (score == null) {
            throw new BusinessException("SCORE_REQUIRED", "卷面分不能为空");
        }
        BigDecimal s = score.setScale(2, BigDecimal.ROUND_HALF_UP);
        if (s.compareTo(BigDecimal.ZERO) < 0 || s.compareTo(BigDecimal.valueOf(rule.getMaxScore())) > 0) {
            throw new BusinessException("SCORE_OUT_OF_RANGE",
                    "卷面分超出规则范围 [0, " + rule.getMaxScore() + "]：" + s);
        }
        String grade = gradeOf(version, s);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", s);
        result.put("grade", grade);
        result.put("ruleCode", rule.getCode());
        result.put("versionNo", version.getVersionNo());
        return result;
    }

    // ---------- 定级核心：卷面分按分数段落等级 ----------

    /**
     * 按版本分数段定级。段定义为左闭右开 [min,max)，末段右端含满分。
     */
    public String gradeOf(GradeRuleVersion version, BigDecimal score) {
        List<GradeBand> bands = readBands(version.getGradeBands());
        BigDecimal max = resolveMaxScore(version.getRuleId());
        for (int i = 0; i < bands.size(); i++) {
            GradeBand band = bands.get(i);
            boolean geMin = score.compareTo(band.getMinScore()) >= 0;
            boolean ltMax = i == bands.size() - 1
                    ? score.compareTo(max) <= 0
                    : score.compareTo(band.getMaxScore()) < 0;
            if (geMin && ltMax) {
                return band.getGrade();
            }
        }
        throw new BusinessException("GRADE_NOT_MATCHED",
                "卷面分 " + score + " 未落入规则版本 " + version.getVersionNo() + " 的任何分数段");
    }

    // ---------- 分数段校验：连续、不重叠、不缺口，从 0 起、到满分止 ----------

    public List<GradeBand> validateBands(GradeRule rule, List<GradeBand> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException("BANDS_REQUIRED", "分数段不能为空");
        }
        BigDecimal maxScore = BigDecimal.valueOf(rule.getMaxScore());
        List<GradeBand> bands = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            GradeBand b = raw.get(i);
            if (b == null || b.getMinScore() == null || b.getMaxScore() == null) {
                throw new BusinessException("BAND_BOUNDARY_REQUIRED", "第 " + (i + 1) + " 段必须填写起止分");
            }
            if (b.getGrade() == null || b.getGrade().trim().isEmpty()) {
                throw new BusinessException("BAND_GRADE_REQUIRED", "第 " + (i + 1) + " 段等级名称不能为空");
            }
            GradeBand norm = new GradeBand();
            norm.setMinScore(b.getMinScore().setScale(2, BigDecimal.ROUND_HALF_UP));
            norm.setMaxScore(b.getMaxScore().setScale(2, BigDecimal.ROUND_HALF_UP));
            norm.setGrade(b.getGrade().trim());
            if (norm.getMinScore().compareTo(norm.getMaxScore()) >= 0) {
                throw new BusinessException("BAND_RANGE_INVALID",
                        "第 " + (i + 1) + " 段起分必须小于止分：" + norm.getMinScore() + " ~ " + norm.getMaxScore());
            }
            if (norm.getMinScore().compareTo(BigDecimal.ZERO) < 0
                    || norm.getMaxScore().compareTo(maxScore) > 0) {
                throw new BusinessException("BAND_OUT_OF_RANGE",
                        "第 " + (i + 1) + " 段超出满分范围 [0, " + rule.getMaxScore() + "]");
            }
            bands.add(norm);
        }
        bands.sort(Comparator.comparing(GradeBand::getMinScore));

        if (bands.get(0).getMinScore().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("BANDS_MUST_START_ZERO", "首段必须从 0 分起，当前起分：" + bands.get(0).getMinScore());
        }
        GradeBand last = bands.get(bands.size() - 1);
        if (last.getMaxScore().compareTo(maxScore) != 0) {
            throw new BusinessException("BANDS_MUST_END_MAX",
                    "末段止分必须等于满分 " + rule.getMaxScore() + "，当前：" + last.getMaxScore());
        }
        for (int i = 1; i < bands.size(); i++) {
            BigDecimal prevEnd = bands.get(i - 1).getMaxScore();
            BigDecimal curStart = bands.get(i).getMinScore();
            if (curStart.compareTo(prevEnd) != 0) {
                throw new BusinessException("BANDS_NOT_CONTIGUOUS",
                        "分数段必须首尾相连不缺口不重叠：上一段止分 " + prevEnd + "，下一段起分 " + curStart);
            }
        }
        return bands;
    }

    // ---------- 序列化 ----------

    public String writeBands(List<GradeBand> bands) {
        try {
            return objectMapper.writeValueAsString(bands);
        } catch (Exception e) {
            throw new BusinessException("BANDS_SERIALIZE_FAILED", "分数段序列化失败：" + e.getMessage());
        }
    }

    public List<GradeBand> readBands(String json) {
        try {
            List<GradeBand> bands = objectMapper.readValue(json, new TypeReference<List<GradeBand>>() {
            });
            if (bands == null || bands.isEmpty()) {
                throw new BusinessException("BANDS_BROKEN", "规则版本分数段为空或已损坏");
            }
            return bands;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("BANDS_BROKEN", "规则版本分数段无法解析：" + e.getMessage());
        }
    }

    // ---------- 内部 ----------

    private GradeRuleVersion requireVersion(Long versionId) {
        if (versionId == null) {
            throw new BusinessException("VERSION_ID_REQUIRED", "规则版本 id 不能为空");
        }
        GradeRuleVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("RULE_VERSION_NOT_FOUND", "规则版本不存在：id=" + versionId);
        }
        return version;
    }

    /** 版本号：规则 code + R + 递增序号（如 ZK-RULE-R1）。 */
    private String nextVersionNo(Long ruleId) {
        GradeRule rule = getRule(ruleId);
        int max = 0;
        String prefix = rule.getCode() + "-R";
        for (GradeRuleVersion v : versionMapper.selectList(
                new QueryWrapper<GradeRuleVersion>().eq("rule_id", ruleId))) {
            String no = v.getVersionNo();
            if (no != null && no.startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 非本规则序号忽略
                }
            }
        }
        return prefix + (max + 1);
    }

    private BigDecimal resolveMaxScore(Long ruleId) {
        return BigDecimal.valueOf(getRule(ruleId).getMaxScore());
    }

    private String normalizeProject(String projectCode) {
        return trimToNull(projectCode) == null ? GradeConstants.PROJECT_DEFAULT : projectCode.trim().toUpperCase();
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
