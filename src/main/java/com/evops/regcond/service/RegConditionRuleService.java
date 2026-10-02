package com.evops.regcond.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.regcond.common.RegCondConstants;
import com.evops.regcond.dto.EducationItemConfig;
import com.evops.regcond.dto.PriorCertItemConfig;
import com.evops.regcond.dto.RegConditionItemsConfig;
import com.evops.regcond.dto.RegRuleRequest;
import com.evops.regcond.dto.RegRuleVersionRequest;
import com.evops.regcond.dto.YearsItemConfig;
import com.evops.regcond.entity.RegConditionRule;
import com.evops.regcond.entity.RegConditionRuleVersion;
import com.evops.regcond.mapper.RegConditionRuleMapper;
import com.evops.regcond.mapper.RegConditionRuleVersionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报名条件规则：
 * - 规则按 项目+报考级别 建档（同项目同级别唯一），规则下挂多个版本；
 * - 版本状态 DRAFT → ACTIVE → DISABLED：发布即冻结核验项配置，自动停用同规则旧生效版；
 * - 核验只认提交时点的 ACTIVE 版本，历史提交永久保留版本号；
 * - 停用不删版本，历史口径可追溯。
 */
@Service
public class RegConditionRuleService {

    /** 学历层次由低到高：学员层次达到或超过要求即通过。 */
    public static final List<String> EDUCATION_ORDER = new ArrayList<String>() {{
        add("HIGH_SCHOOL");
        add("JUNIOR_COLLEGE");
        add("BACHELOR");
        add("MASTER");
        add("DOCTOR");
    }};

    private final RegConditionRuleMapper ruleMapper;
    private final RegConditionRuleVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public RegConditionRuleService(RegConditionRuleMapper ruleMapper,
                                   RegConditionRuleVersionMapper versionMapper,
                                   ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
    }

    // ---------- 规则模块 ----------

    @Transactional
    public RegConditionRule createRule(RegRuleRequest request) {
        RegConditionRule rule = new RegConditionRule();
        rule.setCode(request.getCode().trim());
        rule.setName(request.getName().trim());
        rule.setProjectCode(normalizeProject(request.getProjectCode()));
        rule.setLevelCode(request.getLevelCode().trim().toUpperCase());
        rule.setYearsBasis(normalizeBasis(request.getYearsBasis()));
        rule.setRemark(request.getRemark());
        try {
            ruleMapper.insert(rule);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_REG_RULE",
                    "规则编码或项目+级别已存在：" + rule.getProjectCode() + "/" + rule.getLevelCode());
        }
        return rule;
    }

    public List<RegConditionRule> listRules(String projectCode, String levelCode) {
        QueryWrapper<RegConditionRule> wrapper = new QueryWrapper<RegConditionRule>()
                .orderByAsc("project_code", "level_code");
        if (projectCode != null && !projectCode.trim().isEmpty()) {
            wrapper.eq("project_code", projectCode.trim().toUpperCase());
        }
        if (levelCode != null && !levelCode.trim().isEmpty()) {
            wrapper.eq("level_code", levelCode.trim().toUpperCase());
        }
        return ruleMapper.selectList(wrapper);
    }

    public RegConditionRule getRule(Long ruleId) {
        RegConditionRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException("REG_RULE_NOT_FOUND", "条件规则不存在：id=" + ruleId);
        }
        return rule;
    }

    // ---------- 规则版本 ----------

    /** 新建草稿版本：校验核验项配置后以 JSON 冻结落库（草稿也不允许半截口径）。 */
    @Transactional
    public RegConditionRuleVersion createDraftVersion(Long ruleId, RegRuleVersionRequest request) {
        RegConditionRule rule = getRule(ruleId);
        RegConditionItemsConfig items = validateItems(request.getItems());

        RegConditionRuleVersion version = new RegConditionRuleVersion();
        version.setRuleId(rule.getId());
        version.setVersionNo(nextVersionNo(rule.getId()));
        version.setStatus(RegCondConstants.VERSION_DRAFT);
        version.setItemsConfig(writeItems(items));
        version.setRemark(request.getRemark());
        version.setOperator(OperatorHolder.current());
        versionMapper.insert(version);
        return version;
    }

    /** 发布：DRAFT → ACTIVE；同一规则旧 ACTIVE 自动置 DISABLED，同规则同时刻仅一版生效。 */
    @Transactional
    public RegConditionRuleVersion publishVersion(Long versionId) {
        RegConditionRuleVersion version = requireVersion(versionId);
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

        List<RegConditionRuleVersion> actives = versionMapper.selectList(
                new QueryWrapper<RegConditionRuleVersion>()
                        .eq("rule_id", version.getRuleId())
                        .eq("status", RegCondConstants.VERSION_ACTIVE));
        for (RegConditionRuleVersion active : actives) {
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

    /** 人工停用生效版（停用后该项目+级别在重新发布前无生效条件，提交时会明确报错，不放行也不乱拦）。 */
    @Transactional
    public RegConditionRuleVersion disableVersion(Long versionId) {
        RegConditionRuleVersion version = requireVersion(versionId);
        if (!RegCondConstants.VERSION_ACTIVE.equals(version.getStatus())) {
            throw new BusinessException("VERSION_NOT_ACTIVE", "仅生效版本可停用：" + version.getVersionNo());
        }
        version.setStatus(RegCondConstants.VERSION_DISABLED);
        version.setDisabledBy(OperatorHolder.current());
        version.setDisabledAt(LocalDateTime.now());
        versionMapper.updateById(version);
        return version;
    }

    @Transactional(readOnly = true)
    public List<RegConditionRuleVersion> listVersions(Long ruleId) {
        getRule(ruleId);
        return versionMapper.selectList(new QueryWrapper<RegConditionRuleVersion>()
                .eq("rule_id", ruleId)
                .orderByDesc("id"));
    }

    public RegConditionRuleVersion getVersion(Long versionId) {
        return requireVersion(versionId);
    }

    /**
     * 取项目+级别在指定时点的生效版本：规则变更按提交时点生效，新发布不影响历史提交。
     * 不加事务注解：提交预检/执行在外层事务中会吞掉本方法的"无生效版本"异常逐条展示，
     * readOnly 传播会把外层事务标记 rollback-only。
     */
    public RegConditionRuleVersion requireActiveVersion(String projectCode, String levelCode) {
        String project = normalizeProject(projectCode);
        String level = levelCode == null || levelCode.trim().isEmpty()
                ? null : levelCode.trim().toUpperCase();
        if (level == null) {
            throw new BusinessException("LEVEL_REQUIRED", "报考级别不能为空");
        }
        RegConditionRule rule = ruleMapper.selectOne(new QueryWrapper<RegConditionRule>()
                .eq("project_code", project)
                .eq("level_code", level)
                .last("LIMIT 1"));
        if (rule == null) {
            throw new BusinessException("NO_REG_RULE_FOR_LEVEL",
                    "项目 " + project + " 级别 " + level + " 尚未配置报名条件规则，请先建档并发布版本");
        }
        RegConditionRuleVersion active = versionMapper.selectOne(new QueryWrapper<RegConditionRuleVersion>()
                .eq("rule_id", rule.getId())
                .eq("status", RegCondConstants.VERSION_ACTIVE)
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (active == null) {
            throw new BusinessException("NO_ACTIVE_REG_RULE_VERSION",
                    "条件规则 " + rule.getCode() + " 尚无生效版本，请先发布草稿");
        }
        return active;
    }

    // ---------- 核验项配置校验 ----------

    public RegConditionItemsConfig validateItems(RegConditionItemsConfig raw) {
        if (raw == null) {
            throw new BusinessException("ITEMS_REQUIRED", "核验项配置不能为空");
        }
        RegConditionItemsConfig items = new RegConditionItemsConfig();
        items.setNote(raw.getNote());

        int enabledCount = 0;

        if (raw.getEducation() != null && !Boolean.FALSE.equals(raw.getEducation().getEnabled())) {
            EducationItemConfig edu = raw.getEducation();
            if (edu.getRequiredLevel() == null || edu.getRequiredLevel().trim().isEmpty()) {
                throw new BusinessException("EDUCATION_LEVEL_REQUIRED", "学历要求层次不能为空");
            }
            String required = edu.getRequiredLevel().trim().toUpperCase();
            if (!EDUCATION_ORDER.contains(required)) {
                throw new BusinessException("EDUCATION_LEVEL_UNKNOWN",
                        "学历要求层次无法识别：" + required + "，可选：" + EDUCATION_ORDER);
            }
            EducationItemConfig norm = new EducationItemConfig();
            norm.setEnabled(true);
            norm.setRequiredLevel(required);
            norm.setLabel(trimToEmpty(edu.getLabel(), "学历"));
            items.setEducation(norm);
            enabledCount++;
        }

        if (raw.getYears() != null && !Boolean.FALSE.equals(raw.getYears().getEnabled())) {
            YearsItemConfig years = raw.getYears();
            if (years.getMinYears() == null) {
                throw new BusinessException("YEARS_MIN_REQUIRED", "最低从业年限不能为空");
            }
            if (years.getMinYears() < 0) {
                throw new BusinessException("YEARS_MIN_INVALID", "最低从业年限不能为负：" + years.getMinYears());
            }
            YearsItemConfig norm = new YearsItemConfig();
            norm.setEnabled(true);
            norm.setMinYears(years.getMinYears());
            norm.setLabel(trimToEmpty(years.getLabel(), "从业年限"));
            items.setYears(norm);
            enabledCount++;
        }

        List<PriorCertItemConfig> certs = new ArrayList<>();
        if (raw.getPriorCerts() != null) {
            Set<String> keys = new HashSet<>();
            for (int i = 0; i < raw.getPriorCerts().size(); i++) {
                PriorCertItemConfig c = raw.getPriorCerts().get(i);
                if (c == null || Boolean.FALSE.equals(c.getRequired()) && c.getItemKey() == null) {
                    throw new BusinessException("PRIOR_CERT_ITEM_INVALID", "第 " + (i + 1) + " 条前置证书配置不完整");
                }
                if (c.getItemKey() == null || c.getItemKey().trim().isEmpty()) {
                    throw new BusinessException("PRIOR_CERT_KEY_REQUIRED",
                            "第 " + (i + 1) + " 条前置证书标识不能为空");
                }
                String key = c.getItemKey().trim();
                if (!keys.add(key)) {
                    throw new BusinessException("PRIOR_CERT_KEY_DUPLICATE", "前置证书标识在同一版本内重复：" + key);
                }
                if (c.getCertName() == null || c.getCertName().trim().isEmpty()) {
                    throw new BusinessException("PRIOR_CERT_NAME_REQUIRED", "前置证书名称不能为空：" + key);
                }
                if (c.getValidYears() != null && c.getValidYears() < 0) {
                    throw new BusinessException("PRIOR_CERT_VALID_YEARS_INVALID",
                            "前置证书有效期年数不能为负：" + key);
                }
                PriorCertItemConfig norm = new PriorCertItemConfig();
                norm.setItemKey(key);
                norm.setCertName(c.getCertName().trim());
                norm.setRequired(true);
                norm.setCheckValidity(c.getCheckValidity() == null || c.getCheckValidity());
                norm.setValidYears(c.getValidYears());
                norm.setLabel(trimToEmpty(c.getLabel(), c.getCertName().trim()));
                certs.add(norm);
                enabledCount++;
            }
        }
        items.setPriorCerts(certs);

        if (enabledCount == 0) {
            throw new BusinessException("ITEMS_ALL_EMPTY", "至少启用一个核验项（学历/年限/前置证书），防止误配成无条件放行");
        }
        return items;
    }

    // ---------- 序列化 ----------

    public String writeItems(RegConditionItemsConfig items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new BusinessException("ITEMS_SERIALIZE_FAILED", "核验项配置序列化失败：" + e.getMessage());
        }
    }

    public RegConditionItemsConfig readItems(String json) {
        try {
            RegConditionItemsConfig items = objectMapper.readValue(json,
                    new TypeReference<RegConditionItemsConfig>() {
                    });
            if (items == null) {
                throw new BusinessException("ITEMS_BROKEN", "规则版本核验项配置为空或已损坏");
            }
            return items;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("ITEMS_BROKEN", "规则版本核验项配置无法解析：" + e.getMessage());
        }
    }

    // ---------- 内部 ----------

    private RegConditionRuleVersion requireVersion(Long versionId) {
        if (versionId == null) {
            throw new BusinessException("VERSION_ID_REQUIRED", "规则版本 id 不能为空");
        }
        RegConditionRuleVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("REG_RULE_VERSION_NOT_FOUND", "条件规则版本不存在：id=" + versionId);
        }
        return version;
    }

    /** 版本号：规则 code + -R + 递增序号（如 BM-ZHUKAO-L2-R1）。 */
    private String nextVersionNo(Long ruleId) {
        RegConditionRule rule = getRule(ruleId);
        int max = 0;
        String prefix = rule.getCode() + "-R";
        for (RegConditionRuleVersion v : versionMapper.selectList(
                new QueryWrapper<RegConditionRuleVersion>().eq("rule_id", ruleId))) {
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

    private String normalizeBasis(String basis) {
        if (basis == null || basis.trim().isEmpty()) {
            return RegCondConstants.BASIS_WORK_START;
        }
        String b = basis.trim().toUpperCase();
        if (!RegCondConstants.BASIS_WORK_START.equals(b) && !RegCondConstants.BASIS_LAST_CERT.equals(b)) {
            throw new BusinessException("YEARS_BASIS_UNKNOWN",
                    "年限起算口径无法识别：" + b + "，可选 WORK_START_DATE / LAST_CERT_DATE");
        }
        return b;
    }

    private String normalizeProject(String projectCode) {
        return projectCode == null || projectCode.trim().isEmpty()
                ? RegCondConstants.PROJECT_DEFAULT : projectCode.trim().toUpperCase();
    }

    private String trimToEmpty(String s, String fallback) {
        if (s == null || s.trim().isEmpty()) {
            return fallback;
        }
        return s.trim();
    }

    /** 版本配置摘要（试算/列表展示用）。 */
    public Map<String, Object> versionSummary(RegConditionRuleVersion version) {
        RegConditionItemsConfig items = readItems(version.getItemsConfig());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("versionNo", version.getVersionNo());
        summary.put("status", version.getStatus());
        summary.put("education", items.getEducation());
        summary.put("years", items.getYears());
        summary.put("priorCerts", items.getPriorCerts());
        summary.put("note", items.getNote());
        return summary;
    }
}
