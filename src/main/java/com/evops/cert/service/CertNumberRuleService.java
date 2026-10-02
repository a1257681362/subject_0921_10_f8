package com.evops.cert.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.cert.common.CertConstants;
import com.evops.cert.dto.CertRuleConfig;
import com.evops.cert.dto.CertRuleRequest;
import com.evops.cert.dto.CertRuleVersionRequest;
import com.evops.cert.entity.CertNumberRule;
import com.evops.cert.entity.CertNumberRuleVersion;
import com.evops.cert.mapper.CertNumberRuleMapper;
import com.evops.cert.mapper.CertNumberRuleVersionMapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 编号规则：
 * - 规则按项目建档，规则下挂多个版本；
 * - 版本 DRAFT → ACTIVE → DISABLED：发布即冻结发号参数，并自动停用同规则旧生效版；
 * - 发号只认生效版，已发证书永久保留版本号，规则调整只管新证；
 * - 停用不删版本，历史编号口径可追溯。
 */
@Service
public class CertNumberRuleService {

    private final CertNumberRuleMapper ruleMapper;
    private final CertNumberRuleVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public CertNumberRuleService(CertNumberRuleMapper ruleMapper,
                                 CertNumberRuleVersionMapper versionMapper,
                                 ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
    }

    // ---------- 规则模块 ----------

    @Transactional
    public CertNumberRule createRule(CertRuleRequest request) {
        CertNumberRule rule = new CertNumberRule();
        rule.setCode(request.getCode().trim().toUpperCase());
        rule.setName(request.getName().trim());
        rule.setProjectCode(normalizeProject(request.getProjectCode()));
        rule.setRemark(request.getRemark());
        try {
            ruleMapper.insert(rule);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_CERT_RULE_CODE", "编号规则编码已存在：" + rule.getCode());
        }
        return rule;
    }

    public List<CertNumberRule> listRules(String projectCode) {
        QueryWrapper<CertNumberRule> wrapper = new QueryWrapper<CertNumberRule>().orderByAsc("project_code", "code");
        if (projectCode != null && !projectCode.trim().isEmpty()) {
            wrapper.eq("project_code", projectCode.trim().toUpperCase());
        }
        return ruleMapper.selectList(wrapper);
    }

    public CertNumberRule getRule(Long ruleId) {
        CertNumberRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException("CERT_RULE_NOT_FOUND", "编号规则不存在：id=" + ruleId);
        }
        return rule;
    }

    // ---------- 规则版本 ----------

    /** 新建草稿版本：校验发号参数后以 JSON 冻结落库（草稿也不允许半截口径）。 */
    @Transactional
    public CertNumberRuleVersion createDraftVersion(Long ruleId, CertRuleVersionRequest request) {
        CertNumberRule rule = getRule(ruleId);
        CertRuleConfig config = validateConfig(request.getParams());

        CertNumberRuleVersion version = new CertNumberRuleVersion();
        version.setRuleId(rule.getId());
        version.setVersionNo(nextVersionNo(rule.getId()));
        version.setStatus(CertConstants.VERSION_DRAFT);
        version.setParams(writeConfig(config));
        version.setRemark(request.getRemark());
        version.setOperator(OperatorHolder.current());
        versionMapper.insert(version);
        return version;
    }

    /** 发布：DRAFT → ACTIVE；同一规则旧 ACTIVE 自动置 DISABLED，同规则同时刻仅一版生效。 */
    @Transactional
    public CertNumberRuleVersion publishVersion(Long versionId) {
        CertNumberRuleVersion version = requireVersion(versionId);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        if (CertConstants.VERSION_ACTIVE.equals(version.getStatus())) {
            throw new BusinessException("CERT_VERSION_ALREADY_ACTIVE",
                    "版本已是生效状态，无需重复发布：" + version.getVersionNo());
        }
        if (CertConstants.VERSION_DISABLED.equals(version.getStatus())) {
            throw new BusinessException("CERT_VERSION_DISABLED",
                    "停用版本不可重新发布，请新建版本：" + version.getVersionNo());
        }

        List<CertNumberRuleVersion> actives = versionMapper.selectList(new QueryWrapper<CertNumberRuleVersion>()
                .eq("rule_id", version.getRuleId())
                .eq("status", CertConstants.VERSION_ACTIVE));
        for (CertNumberRuleVersion active : actives) {
            active.setStatus(CertConstants.VERSION_DISABLED);
            active.setDisabledBy(operator);
            active.setDisabledAt(now);
            versionMapper.updateById(active);
        }

        version.setStatus(CertConstants.VERSION_ACTIVE);
        version.setPublishedBy(operator);
        version.setPublishedAt(now);
        versionMapper.updateById(version);
        return version;
    }

    /** 人工停用生效版（停用后该规则在重新发布前无生效口径，无法发号）。 */
    @Transactional
    public CertNumberRuleVersion disableVersion(Long versionId) {
        CertNumberRuleVersion version = requireVersion(versionId);
        if (!CertConstants.VERSION_ACTIVE.equals(version.getStatus())) {
            throw new BusinessException("CERT_VERSION_NOT_ACTIVE", "仅生效版本可停用：" + version.getVersionNo());
        }
        version.setStatus(CertConstants.VERSION_DISABLED);
        version.setDisabledBy(OperatorHolder.current());
        version.setDisabledAt(LocalDateTime.now());
        versionMapper.updateById(version);
        return version;
    }

    @Transactional(readOnly = true)
    public List<CertNumberRuleVersion> listVersions(Long ruleId) {
        getRule(ruleId);
        return versionMapper.selectList(new QueryWrapper<CertNumberRuleVersion>()
                .eq("rule_id", ruleId)
                .orderByDesc("id"));
    }

    public CertNumberRuleVersion getVersion(Long versionId) {
        return requireVersion(versionId);
    }

    /**
     * 取项目当前生效规则版本。项目无规则 / 无生效版 / 多条无法唯一确定时抛错，
     * 避免发证口径不唯一。本项目编号规则通常一项目一条，存在多条时取最近建档的唯一规则。
     */
    @Transactional(readOnly = true)
    public CertNumberRuleVersion requireActiveVersion(String projectCode) {
        CertNumberRuleVersion active = findActiveVersion(projectCode);
        if (active == null) {
            String project = normalizeProject(projectCode);
            throw new BusinessException("NO_ACTIVE_CERT_RULE_VERSION",
                    "项目 " + project + " 当前没有生效的证书编号规则版本，请先建档并发布草稿");
        }
        return active;
    }

    /**
     * 查项目当前生效版本，无规则 / 无生效版 / 多规则时返回 null（不抛错）。
     * 故意不加 @Transactional：手工旧号补登等外层事务要靠 null 走"无规则也登记"分支，
     * 若此处由声明式事务把外层事务标成 rollback-only，外层将 UnexpectedRollback。
     */
    public CertNumberRuleVersion findActiveVersion(String projectCode) {
        String project = normalizeProject(projectCode);
        List<CertNumberRule> rules = ruleMapper.selectList(
                new QueryWrapper<CertNumberRule>().eq("project_code", project).orderByDesc("id"));
        if (rules.size() != 1) {
            return null;
        }
        return versionMapper.selectOne(new QueryWrapper<CertNumberRuleVersion>()
                .eq("rule_id", rules.get(0).getId())
                .eq("status", CertConstants.VERSION_ACTIVE)
                .orderByDesc("id")
                .last("LIMIT 1"));
    }

    // ---------- 发号参数校验 / 序列化 ----------

    public CertRuleConfig validateConfig(CertRuleConfig raw) {
        if (raw == null) {
            throw new BusinessException("CERT_PARAMS_REQUIRED", "发号参数不能为空");
        }
        CertRuleConfig c = new CertRuleConfig();
        String prefix = raw.getPrefix() == null ? "" : raw.getPrefix().trim().toUpperCase();
        if (prefix.isEmpty() || !prefix.matches("[A-Z0-9]{1,6}")) {
            throw new BusinessException("CERT_PREFIX_INVALID",
                    "编号前缀必须为 1~6 位大写字母或数字：" + prefix);
        }
        c.setPrefix(prefix);
        c.setYearDigits(normalizeDigits(raw.getYearDigits(), 4, new int[]{2, 4}, "年度位数"));
        c.setBatchDigits(normalizeDigits(raw.getBatchDigits(), 2, range(1, 4), "批次段位数"));
        c.setSeqDigits(normalizeDigits(raw.getSeqDigits(), 5, range(3, 8), "流水段位数"));
        c.setCheckEnabled(raw.getCheckEnabled() == null ? Boolean.TRUE : raw.getCheckEnabled());
        return c;
    }

    private int[] range(int from, int to) {
        int[] v = new int[to - from + 1];
        for (int i = 0; i < v.length; i++) {
            v[i] = from + i;
        }
        return v;
    }

    private int normalizeDigits(Integer value, int defaultValue, int[] allowed, String label) {
        int v = value == null ? defaultValue : value;
        for (int a : allowed) {
            if (v == a) {
                return v;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int a : allowed) {
            sb.append(a).append('/');
        }
        sb.setLength(sb.length() - 1);
        throw new BusinessException("CERT_DIGITS_INVALID", label + "只允许：" + sb);
    }

    public String writeConfig(CertRuleConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new BusinessException("CERT_PARAMS_SERIALIZE_FAILED", "发号参数序列化失败：" + e.getMessage());
        }
    }

    public CertRuleConfig readConfig(String json) {
        try {
            CertRuleConfig config = objectMapper.readValue(json, new TypeReference<CertRuleConfig>() {
            });
            if (config == null) {
                throw new BusinessException("CERT_PARAMS_BROKEN", "规则版本发号参数为空或已损坏");
            }
            if (config.getCheckEnabled() == null) {
                config.setCheckEnabled(Boolean.TRUE);
            }
            return config;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("CERT_PARAMS_BROKEN", "规则版本发号参数无法解析：" + e.getMessage());
        }
    }

    // ---------- 内部 ----------

    private CertNumberRuleVersion requireVersion(Long versionId) {
        if (versionId == null) {
            throw new BusinessException("CERT_VERSION_ID_REQUIRED", "规则版本 id 不能为空");
        }
        CertNumberRuleVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("CERT_RULE_VERSION_NOT_FOUND", "编号规则版本不存在：id=" + versionId);
        }
        return version;
    }

    /** 版本号：规则 code + R + 递增序号（如 ZS-RULE-R1）。 */
    private String nextVersionNo(Long ruleId) {
        CertNumberRule rule = getRule(ruleId);
        int max = 0;
        String prefix = rule.getCode() + "-R";
        for (CertNumberRuleVersion v : versionMapper.selectList(
                new QueryWrapper<CertNumberRuleVersion>().eq("rule_id", ruleId))) {
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
        if (projectCode == null || projectCode.trim().isEmpty()) {
            return CertConstants.PROJECT_DEFAULT;
        }
        return projectCode.trim().toUpperCase();
    }
}
