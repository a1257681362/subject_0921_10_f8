package com.evops.regcond.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.regcond.common.RegCondConstants;
import com.evops.regcond.dto.ConditionItem;
import com.evops.regcond.dto.ConditionRuleRequest;
import com.evops.regcond.dto.ConditionRuleVersionRequest;
import com.evops.regcond.entity.ConditionRule;
import com.evops.regcond.entity.ConditionRuleVersion;
import com.evops.regcond.mapper.ConditionRuleMapper;
import com.evops.regcond.mapper.ConditionRuleVersionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 条件规则（模块 / 版本 / 状态）：
 * - 规则按项目 + 级别建档（学历、年限、前置证书按级别各配一套），规则下挂多个版本；
 * - 版本 DRAFT → ACTIVE → DISABLED：发布即冻结核验项 JSON，同规则旧生效版自动停用；
 * - 报名提交只认提交时点项目+级别的 ACTIVE 版本，核验单永久记下版本号，规则升级不改历史口径；
 * - 停用不删版本，历史核验可追溯。
 */
@Service
public class ConditionRuleService {

    /** 学历等级编码 -> 顺序值（学历按"不低于要求"比较）。 */
    private static final Map<String, Integer> EDUCATION_RANK = new TreeMap<>();
    /** 学历编码 -> 中文名（原因文案用）。 */
    private static final Map<String, String> EDUCATION_LABEL = new LinkedHashMap<>();

    static {
        Object[][] rows = {
                {"NONE", 0, "无要求"},
                {"PRIMARY", 1, "小学"},
                {"JUNIOR_HIGH", 2, "初中"},
                {"SENIOR_HIGH", 3, "高中（含中专/职高）"},
                {"JUNIOR_COLLEGE", 4, "大专"},
                {"BACHELOR", 5, "本科"},
                {"MASTER", 6, "硕士研究生"},
                {"DOCTOR", 7, "博士研究生"},
        };
        for (Object[] r : rows) {
            EDUCATION_RANK.put((String) r[0], (Integer) r[1]);
            EDUCATION_LABEL.put((String) r[0], (String) r[2]);
        }
    }

    private final ConditionRuleMapper ruleMapper;
    private final ConditionRuleVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public ConditionRuleService(ConditionRuleMapper ruleMapper,
                                ConditionRuleVersionMapper versionMapper,
                                ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
    }

    // ---------- 规则模块 ----------

    @Transactional
    public ConditionRule createRule(ConditionRuleRequest request) {
        ConditionRule rule = new ConditionRule();
        rule.setCode(request.getCode().trim());
        rule.setName(request.getName().trim());
        rule.setProjectCode(normalizeProject(request.getProjectCode()));
        rule.setLevelCode(normalizeLevel(request.getLevelCode()));
        rule.setRemark(request.getRemark());
        try {
            ruleMapper.insert(rule);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_CONDITION_RULE",
                    "规则编码或同项目+级别规则已存在：" + rule.getCode()
                            + "（" + rule.getProjectCode() + "/" + rule.getLevelCode() + "）");
        }
        return rule;
    }

    public List<ConditionRule> listRules(String projectCode, String levelCode) {
        QueryWrapper<ConditionRule> wrapper = new QueryWrapper<ConditionRule>()
                .orderByAsc("project_code", "level_code", "code");
        if (projectCode != null && !projectCode.trim().isEmpty()) {
            wrapper.eq("project_code", projectCode.trim().toUpperCase());
        }
        if (levelCode != null && !levelCode.trim().isEmpty()) {
            wrapper.eq("level_code", levelCode.trim().toUpperCase());
        }
        return ruleMapper.selectList(wrapper);
    }

    public ConditionRule getRule(Long ruleId) {
        ConditionRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException("CONDITION_RULE_NOT_FOUND", "条件规则不存在：id=" + ruleId);
        }
        return rule;
    }

    // ---------- 规则版本 ----------

    /** 新建草稿版本：校验核验项后以 JSON 冻结落库（草稿也不允许半截口径）。 */
    @Transactional
    public ConditionRuleVersion createDraftVersion(Long ruleId, ConditionRuleVersionRequest request) {
        ConditionRule rule = getRule(ruleId);
        List<ConditionItem> items = validateItems(request.getItems());

        ConditionRuleVersion version = new ConditionRuleVersion();
        version.setRuleId(rule.getId());
        version.setVersionNo(nextVersionNo(rule.getId()));
        version.setStatus(RegCondConstants.VERSION_DRAFT);
        version.setItems(writeItems(items));
        version.setRemark(request.getRemark());
        version.setOperator(OperatorHolder.current());
        versionMapper.insert(version);
        return version;
    }

    /** 发布：DRAFT → ACTIVE；同规则旧 ACTIVE 自动 DISABLED。 */
    @Transactional
    public ConditionRuleVersion publishVersion(Long versionId) {
        ConditionRuleVersion version = requireVersion(versionId);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        if (RegCondConstants.VERSION_ACTIVE.equals(version.getStatus())) {
            throw new BusinessException("VERSION_ALREADY_ACTIVE",
                    "版本已是生效状态，无需重复发布：" + version.getVersionNo());
        }
        if (RegCondConstants.VERSION_DISABLED.equals(version.getStatus())) {
            throw new BusinessException("VERSION_DISABLED",
                    "停用版本不可重新发布，请新建版本：" + version.getVersionNo());
        }

        List<ConditionRuleVersion> actives = versionMapper.selectList(new QueryWrapper<ConditionRuleVersion>()
                .eq("rule_id", version.getRuleId())
                .eq("status", RegCondConstants.VERSION_ACTIVE));
        for (ConditionRuleVersion active : actives) {
            active.setStatus(RegCondConstants.VERSION_DISABLED);
            active.setDisabledBy(operator);
            active.setDisabledAt(now);
            versionMapper.updateById(active);
        }

        version.setStatus(RegCondConstants.VERSION_ACTIVE);
        version.setPublishedBy(operator);
        version.setPublishedAt(now);
        versionMapper.updateById(version);
        return version;
    }

    /** 人工停用生效版（停用后该级别在重新发布前无可执行口径，提交会明确报错转人工配置）。 */
    @Transactional
    public ConditionRuleVersion disableVersion(Long versionId) {
        ConditionRuleVersion version = requireVersion(versionId);
        if (!RegCondConstants.VERSION_ACTIVE.equals(version.getStatus())) {
            throw new BusinessException("VERSION_NOT_ACTIVE", "仅生效版本可停用：" + version.getVersionNo());
        }
        version.setStatus(RegCondConstants.VERSION_DISABLED);
        version.setDisabledBy(OperatorHolder.current());
        version.setDisabledAt(LocalDateTime.now());
        versionMapper.updateById(version);
        return version;
    }

    public List<ConditionRuleVersion> listVersions(Long ruleId) {
        getRule(ruleId);
        return versionMapper.selectList(new QueryWrapper<ConditionRuleVersion>()
                .eq("rule_id", ruleId).orderByDesc("id"));
    }

    public ConditionRuleVersion getVersion(Long versionId) {
        return requireVersion(versionId);
    }

    /**
     * 取项目 + 级别在提交日的生效版本。规则变更按提交时点生效：
     * 取发布时刻早于「提交日次日零点」的最新一版（发布日当天即生效）；
     * 该版本若为 DISABLED，须是被更晚版本顶替（停用时刻晚于提交日）才算当时有效；
     * 提交日之前已被人工停用的版本不再命中，报无生效版本。
     */
    @Transactional(readOnly = true)
    public ConditionRuleVersion requireVersionAt(String projectCode, String levelCode, LocalDate submitDate) {
        String project = normalizeProject(projectCode);
        String level = normalizeLevel(levelCode);
        List<ConditionRule> rules = ruleMapper.selectList(new QueryWrapper<ConditionRule>()
                .eq("project_code", project).eq("level_code", level));
        if (rules.isEmpty()) {
            throw new BusinessException("NO_CONDITION_RULE_FOR_LEVEL",
                    "项目 " + project + " 级别 " + level + " 尚未配置报名条件规则，请先建档并发布版本");
        }
        if (rules.size() > 1) {
            throw new BusinessException("MULTIPLE_CONDITION_RULES",
                    "项目 " + project + " 级别 " + level + " 存在多套条件规则，无法唯一核验");
        }
        Long ruleId = rules.get(0).getId();

        java.time.LocalDateTime cutoff = submitDate.plusDays(1).atStartOfDay();
        ConditionRuleVersion version = versionMapper.selectOne(new QueryWrapper<ConditionRuleVersion>()
                .eq("rule_id", ruleId)
                .isNotNull("published_at")
                .lt("published_at", cutoff)
                .in("status", RegCondConstants.VERSION_ACTIVE, RegCondConstants.VERSION_DISABLED)
                .orderByDesc("published_at", "id")
                .last("LIMIT 1"));
        if (version == null) {
            throw new BusinessException("NO_ACTIVE_CONDITION_RULE_VERSION",
                    "条件规则 " + rules.get(0).getCode() + " 在提交日 " + submitDate + " 尚无已发布版本，请先发布草稿");
        }
        if (RegCondConstants.VERSION_DISABLED.equals(version.getStatus())
                && version.getDisabledAt() != null
                && version.getDisabledAt().isBefore(cutoff)) {
            throw new BusinessException("NO_ACTIVE_CONDITION_RULE_VERSION",
                    "条件规则 " + rules.get(0).getCode() + " 的版本 " + version.getVersionNo()
                            + " 已于 " + version.getDisabledAt() + " 停用，提交日 " + submitDate
                            + " 无生效规则版本，请重新发布");
        }
        return version;
    }

    /** 取当前生效版本（管理端查看用）。 */
    @Transactional(readOnly = true)
    public ConditionRuleVersion requireCurrentActiveVersion(String projectCode, String levelCode) {
        String project = normalizeProject(projectCode);
        String level = normalizeLevel(levelCode);
        ConditionRule rule = ruleMapper.selectOne(new QueryWrapper<ConditionRule>()
                .eq("project_code", project).eq("level_code", level));
        if (rule == null) {
            throw new BusinessException("NO_CONDITION_RULE_FOR_LEVEL",
                    "项目 " + project + " 级别 " + level + " 尚未配置报名条件规则");
        }
        ConditionRuleVersion version = versionMapper.selectOne(new QueryWrapper<ConditionRuleVersion>()
                .eq("rule_id", rule.getId())
                .eq("status", RegCondConstants.VERSION_ACTIVE)
                .orderByDesc("id").last("LIMIT 1"));
        if (version == null) {
            throw new BusinessException("NO_ACTIVE_CONDITION_RULE_VERSION",
                    "条件规则 " + rule.getCode() + " 尚无生效版本，请先发布草稿");
        }
        return version;
    }

    // ---------- 核验项校验与序列化 ----------

    public List<ConditionItem> validateItems(List<ConditionItem> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException("CONDITION_ITEMS_REQUIRED", "核验项列表不能为空");
        }
        List<ConditionItem> items = new ArrayList<>();
        List<String> seenKeys = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            ConditionItem c = raw.get(i);
            int ordinal = i + 1;
            if (c == null || c.getItemType() == null || c.getItemType().trim().isEmpty()) {
                throw new BusinessException("ITEM_TYPE_REQUIRED", "第 " + ordinal + " 个核验项必须填写 itemType");
            }
            String type = c.getItemType().trim().toUpperCase();
            ConditionItem norm = new ConditionItem();
            norm.setItemType(type);
            norm.setName(trimToNull(c.getName()));

            switch (type) {
                case RegCondConstants.ITEM_EDUCATION: {
                    String edu = c.getRequiredEducation() == null ? "" : c.getRequiredEducation().trim().toUpperCase();
                    if (!EDUCATION_RANK.containsKey(edu)) {
                        throw new BusinessException("EDUCATION_CODE_INVALID",
                                "第 " + ordinal + " 个核验项学历编码非法：" + edu + "，合法值：" + EDUCATION_RANK.keySet());
                    }
                    norm.setRequiredEducation(edu);
                    if (norm.getName() == null) {
                        norm.setName("学历不低于" + EDUCATION_LABEL.get(edu));
                    }
                    break;
                }
                case RegCondConstants.ITEM_YEARS: {
                    if (c.getMinValue() == null || c.getMinValue().compareTo(BigDecimal.ZERO) < 0) {
                        throw new BusinessException("YEARS_MIN_REQUIRED",
                                "第 " + ordinal + " 个核验项最低年限不能为空且不能为负");
                    }
                    norm.setMinValue(c.getMinValue().stripTrailingZeros());
                    String unit = c.getUnit() == null || c.getUnit().trim().isEmpty()
                            ? RegCondConstants.UNIT_YEAR : c.getUnit().trim().toUpperCase();
                    if (!RegCondConstants.UNIT_YEAR.equals(unit) && !RegCondConstants.UNIT_MONTH.equals(unit)) {
                        throw new BusinessException("YEARS_UNIT_INVALID",
                                "第 " + ordinal + " 个核验项年限单位非法：" + unit + "（YEAR/MONTH）");
                    }
                    norm.setUnit(unit);
                    if (norm.getName() == null) {
                        norm.setName("从业年限满" + norm.getMinValue().toPlainString()
                                + (RegCondConstants.UNIT_MONTH.equals(unit) ? "个月" : "周年"));
                    }
                    break;
                }
                case RegCondConstants.ITEM_PREREQ_CERT: {
                    String subject = trimToNull(c.getRequiredSubject());
                    if (subject == null) {
                        throw new BusinessException("PREREQ_SUBJECT_REQUIRED",
                                "第 " + ordinal + " 个核验项前置证书科目/名称不能为空");
                    }
                    norm.setRequiredSubject(subject);
                    norm.setRequiredGrade(trimToNull(c.getRequiredGrade()));
                    norm.setCheckValidity(c.getCheckValidity() == null ? Boolean.TRUE : c.getCheckValidity());
                    norm.setValidityRequired(c.getValidityRequired() == null ? Boolean.FALSE : c.getValidityRequired());
                    if (norm.getName() == null) {
                        norm.setName("前置证书：" + subject + (norm.getRequiredGrade() == null
                                ? "" : "（" + norm.getRequiredGrade() + "）"));
                    }
                    break;
                }
                default:
                    throw new BusinessException("ITEM_TYPE_INVALID",
                            "第 " + ordinal + " 个核验项类型非法：" + type
                                    + "（EDUCATION/YEARS/PREREQ_CERT）");
            }

            String key = itemKey(norm);
            if (seenKeys.contains(key)) {
                throw new BusinessException("CONDITION_ITEM_DUPLICATED",
                        "核验项重复：" + norm.getItemType() + " " + (norm.getName() == null ? "" : norm.getName())
                                + "（同一类型/前置证书科目只能配一条）");
            }
            seenKeys.add(key);
            items.add(norm);
        }
        return items;
    }

    /** 稳定核验项键：学历/年限按类型；前置证书按 类型:科目。 */
    public static String itemKey(ConditionItem item) {
        if (RegCondConstants.ITEM_PREREQ_CERT.equals(item.getItemType())) {
            return RegCondConstants.ITEM_PREREQ_CERT + ":" + item.getRequiredSubject().trim().toUpperCase();
        }
        return item.getItemType();
    }

    public String writeItems(List<ConditionItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new BusinessException("ITEMS_SERIALIZE_FAILED", "核验项序列化失败：" + e.getMessage());
        }
    }

    public List<ConditionItem> readItems(String json) {
        try {
            List<ConditionItem> items = objectMapper.readValue(json, new TypeReference<List<ConditionItem>>() {
            });
            if (items == null || items.isEmpty()) {
                throw new BusinessException("ITEMS_BROKEN", "规则版本核验项为空或已损坏");
            }
            return items;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("ITEMS_BROKEN", "规则版本核验项无法解析：" + e.getMessage());
        }
    }

    public static String educationLabel(String code) {
        return EDUCATION_LABEL.get(code);
    }

    public static Integer educationRank(String code) {
        return code == null ? null : EDUCATION_RANK.get(code.trim().toUpperCase());
    }

    // ---------- 内部 ----------

    private ConditionRuleVersion requireVersion(Long versionId) {
        if (versionId == null) {
            throw new BusinessException("VERSION_ID_REQUIRED", "规则版本 id 不能为空");
        }
        ConditionRuleVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("CONDITION_RULE_VERSION_NOT_FOUND", "条件规则版本不存在：id=" + versionId);
        }
        return version;
    }

    /** 版本号：规则 code + -R + 递增序号。 */
    private String nextVersionNo(Long ruleId) {
        ConditionRule rule = getRule(ruleId);
        String prefix = rule.getCode() + "-R";
        int max = 0;
        for (ConditionRuleVersion v : versionMapper.selectList(
                new QueryWrapper<ConditionRuleVersion>().eq("rule_id", ruleId))) {
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

    private String normalizeProject(String projectCode) {
        return trimToNull(projectCode) == null
                ? RegCondConstants.PROJECT_DEFAULT : projectCode.trim().toUpperCase();
    }

    private String normalizeLevel(String levelCode) {
        if (levelCode == null || levelCode.trim().isEmpty()) {
            throw new BusinessException("LEVEL_CODE_REQUIRED", "适用级别不能为空");
        }
        return levelCode.trim().toUpperCase();
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
