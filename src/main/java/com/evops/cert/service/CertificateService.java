package com.evops.cert.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.cert.common.CertConstants;
import com.evops.cert.dto.CertIssueItem;
import com.evops.cert.dto.CertIssueRequest;
import com.evops.cert.dto.CertLegacyRequest;
import com.evops.cert.engine.CertNumberCodec;
import com.evops.cert.entity.CertNumberRule;
import com.evops.cert.entity.CertNumberRuleVersion;
import com.evops.cert.entity.CertSeq;
import com.evops.cert.entity.Certificate;
import com.evops.cert.entity.CertificateRevokeRecord;
import com.evops.cert.mapper.CertSeqMapper;
import com.evops.cert.mapper.CertificateMapper;
import com.evops.cert.mapper.CertificateRevokeRecordMapper;
import com.evops.cert.vo.CertAffectedItem;
import com.evops.cert.vo.CertIssuePreview;
import com.evops.cert.vo.CertIssueResult;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.mapper.ExamBatchMapper;
import com.evops.cert.dto.CertRuleConfig;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 证书发证 / 作废：
 * - 发号 = 年度 + 批次 + 流水 + 末位校验码，按规则当前 ACTIVE 版本的冻结参数生成；
 * - 序列按 规则+年度+批次 行锁取号，并以库内已用最大流水兜底起跳，手工旧号补登后自动抬过旧号，不撞旧号；
 * - 发证后持证人信息只读，没有修改/删除入口；
 * - 作废只切状态并追加作废记录，原证保留；
 * - 规则调整只管新证：每张证永久记下规则版本与冻结参数。
 */
@Service
public class CertificateService {

    private final CertificateMapper certificateMapper;
    private final CertificateRevokeRecordMapper revokeMapper;
    private final CertSeqMapper seqMapper;
    private final CertNumberRuleService ruleService;
    private final ExamBatchMapper batchMapper;

    public CertificateService(CertificateMapper certificateMapper,
                              CertificateRevokeRecordMapper revokeMapper,
                              CertSeqMapper seqMapper,
                              CertNumberRuleService ruleService,
                              ExamBatchMapper batchMapper) {
        this.certificateMapper = certificateMapper;
        this.revokeMapper = revokeMapper;
        this.seqMapper = seqMapper;
        this.ruleService = ruleService;
        this.batchMapper = batchMapper;
    }

    // ---------- 批量发证：两步预检 ----------

    @Transactional(readOnly = true)
    public CertIssuePreview previewIssue(CertIssueRequest request) {
        IssueContext ctx = resolveContext(request);
        int startSeq = nextSeqNoLock(ctx.rule.getId(), ctx.year, ctx.batchPart);

        List<CertAffectedItem> items = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        int actionable = 0;
        int skip = 0;
        int maxSeq = (int) Math.pow(10, ctx.config.getSeqDigits()) - 1;

        for (int i = 0; i < request.getItems().size(); i++) {
            CertIssueItem item = request.getItems().get(i);
            String name = item.getHolderName().trim();
            String dupKey = dupKey(name);

            // 与执行同一判定顺序：先清单内重复，再库内已有，保证两步预检口径一致。
            boolean inList = !seenKeys.add(dupKey);
            boolean exists = !inList && holderExists(ctx.rule.getId(), ctx.year, ctx.batchPart, name);
            boolean actionableFlag = !exists && !inList;

            String certNo = null;
            String reason = null;
            if (actionableFlag) {
                int seq = startSeq + actionable;
                if (seq > maxSeq) {
                    throw new BusinessException("CERT_SEQ_EXHAUSTED",
                            "流水段 " + ctx.config.getSeqDigits() + " 位已用尽（上限 " + maxSeq
                                    + "），请停用并发布更大流水位宽的新规则版本（只管新证）");
                }
                certNo = CertNumberCodec.format(ctx.config, ctx.year, ctx.batchPart, seq);
                actionable++;
            } else {
                reason = inList ? "发证清单内重复人员" : "该持证人在此年度批次下已有证书";
                skip++;
            }
            items.add(new CertAffectedItem(i + 1, name, actionableFlag, certNo,
                    actionableFlag ? "ISSUED" : "SKIPPED", reason));
        }

        CertIssuePreview preview = new CertIssuePreview();
        preview.setAction("CERT_ISSUE");
        preview.setProjectCode(ctx.project);
        preview.setRuleCode(ctx.rule.getCode());
        preview.setRuleVersionNo(ctx.version.getVersionNo());
        preview.setCertYear(ctx.year);
        preview.setCertBatch(ctx.batchPart);
        preview.setNextSeq(startSeq);
        preview.setTotalScanned(request.getItems().size());
        preview.setActionableCount(actionable);
        preview.setSkipCount(skip);
        preview.setSummary(String.format("规则 %s（%s），年度 %s 批次 %s：可发 %d 张、跳过 %d 张，下一流水 %0"
                + ctx.config.getSeqDigits() + "d",
                ctx.rule.getCode(), ctx.version.getVersionNo(), ctx.year, ctx.batchPart,
                actionable, skip, startSeq));
        preview.setItems(items);
        return preview;
    }

    @Transactional
    public CertIssueResult issue(CertIssueRequest request) {
        IssueContext ctx = resolveContext(request);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        // 行锁取序列，并发发证不重号。
        CertSeq seqRow = lockOrCreateSeq(ctx.rule.getId(), ctx.version.getId(), ctx.year, ctx.batchPart, operator);
        int startSeq = Math.max(seqRow.getCurrentSeq(),
                certificateMapper.maxUsedSeq(ctx.rule.getId(), ctx.year, ctx.batchPart));
        int allocated = 0;

        List<CertAffectedItem> items = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        int skip = 0;
        int maxSeq = (int) Math.pow(10, ctx.config.getSeqDigits()) - 1;

        for (int i = 0; i < request.getItems().size(); i++) {
            CertIssueItem item = request.getItems().get(i);
            String name = item.getHolderName().trim();
            String dupKey = dupKey(name);

            boolean inList = !seenKeys.add(dupKey);
            boolean exists = !inList && holderExists(ctx.rule.getId(), ctx.year, ctx.batchPart, name);
            if (exists || inList) {
                skip++;
                items.add(new CertAffectedItem(i + 1, name, false, null, "SKIPPED",
                        inList ? "发证清单内重复人员" : "该持证人在此年度批次下已有证书"));
                continue;
            }

            int seq = startSeq + allocated + 1;
            if (seq > maxSeq) {
                throw new BusinessException("CERT_SEQ_EXHAUSTED",
                        "流水段 " + ctx.config.getSeqDigits() + " 位已用尽（上限 " + maxSeq + "），整批回滚未落半条");
            }
            String certNo = CertNumberCodec.format(ctx.config, ctx.year, ctx.batchPart, seq);

            Certificate cert = new Certificate();
            cert.setCertNo(certNo);
            cert.setSource(CertConstants.SOURCE_SYSTEM);
            cert.setRuleId(ctx.rule.getId());
            cert.setRuleVersionId(ctx.version.getId());
            cert.setRuleVersionNo(ctx.version.getVersionNo());
            cert.setCertYear(ctx.year);
            cert.setCertBatch(ctx.batchPart);
            cert.setCertSeq(seq);
            cert.setHolderName(name);
            cert.setIdNo(trimToNull(item.getIdNo()));
            cert.setProjectCode(ctx.project);
            cert.setBatchId(ctx.batchId);
            cert.setBatchCode(ctx.batchCode);
            cert.setBatchName(ctx.batchName);
            cert.setSubject(trimToNull(item.getSubject()));
            cert.setGrade(trimToNull(item.getGrade()));
            cert.setValidFrom(item.getValidFrom());
            cert.setValidTo(item.getValidTo());
            validateValidity(cert.getValidFrom(), cert.getValidTo());
            cert.setIssuedAt(now);
            cert.setIssuedBy(operator);
            cert.setStatus(CertConstants.CERT_ISSUED);
            cert.setRemark(request.getRemark());
            try {
                certificateMapper.insert(cert);
            } catch (DuplicateKeyException e) {
                // 编号唯一约束兜底：理论上行锁 + 冻结参数下不会发生，发生即拒，绝不发出重号
                throw new BusinessException("CERT_NO_COLLISION", "证书编号撞号，已整批拒收：" + certNo);
            }

            allocated++;
            seqRow.setCurrentSeq(seq);
            seqRow.setUpdatedBy(operator);
            seqRow.setUpdatedAt(now);
            seqMapper.updateById(seqRow);

            items.add(new CertAffectedItem(i + 1, name, true, certNo, "ISSUED", null));
        }

        CertIssueResult result = new CertIssueResult();
        result.setProjectCode(ctx.project);
        result.setRuleVersionNo(ctx.version.getVersionNo());
        result.setCertYear(ctx.year);
        result.setCertBatch(ctx.batchPart);
        result.setIssuedCount(allocated);
        result.setSkipCount(skip);
        result.setSummary(String.format("实发 %d 张、跳过 %d 张（规则版本 %s，年度 %s 批次 %s）",
                allocated, skip, ctx.version.getVersionNo(), ctx.year, ctx.batchPart));
        result.setItems(items);
        return result;
    }

    // ---------- 手工旧号补登（不重发，抬序列防撞） ----------

    @Transactional
    public Certificate registerLegacy(CertLegacyRequest request) {
        String certNo = request.getCertNo().trim().toUpperCase();
        if (certificateMapper.selectOne(new QueryWrapper<Certificate>().eq("cert_no", certNo)) != null) {
            throw new BusinessException("CERT_NO_EXISTS", "证书编号已存在，不能重复登记：" + certNo);
        }
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        String project = normalizeProject(request.getProjectCode());

        // 能对应到项目当前规则且能辨认年度/批次/流水时，把序列抬过旧号，避免新号撞旧号。
        // 项目无规则/无生效版时旧号照样登记（findActiveVersion 返回 null，不抛错、不污染外层事务）。
        CertNumberRuleVersion version = ruleService.findActiveVersion(project);
        CertNumberRule rule = null;
        if (version != null) {
            rule = ruleService.getRule(version.getRuleId());
        }

        Certificate cert = new Certificate();
        cert.setCertNo(certNo);
        cert.setSource(CertConstants.SOURCE_LEGACY);
        cert.setRuleId(rule == null ? null : rule.getId());
        cert.setRuleVersionId(version == null ? null : version.getId());
        cert.setRuleVersionNo(version == null ? null : version.getVersionNo());
        cert.setCertYear(request.getCertYear());
        cert.setCertBatch(trimToNull(request.getCertBatch()));
        cert.setCertSeq(request.getCertSeq());
        cert.setHolderName(request.getHolderName().trim());
        cert.setIdNo(trimToNull(request.getIdNo()));
        cert.setProjectCode(project);
        if (request.getBatchId() != null) {
            ExamBatch batch = batchMapper.selectById(request.getBatchId());
            if (batch == null) {
                throw new BusinessException("BATCH_NOT_FOUND", "关联考试批次不存在：id=" + request.getBatchId());
            }
            cert.setBatchId(batch.getId());
            cert.setBatchCode(batch.getCode());
            cert.setBatchName(batch.getName());
        }
        cert.setSubject(trimToNull(request.getSubject()));
        cert.setGrade(trimToNull(request.getGrade()));
        cert.setValidFrom(request.getValidFrom());
        cert.setValidTo(request.getValidTo());
        validateValidity(cert.getValidFrom(), cert.getValidTo());
        cert.setIssuedAt(now);
        cert.setIssuedBy(operator);
        cert.setStatus(CertConstants.CERT_ISSUED);
        cert.setRemark(request.getRemark());
        try {
            certificateMapper.insert(cert);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("CERT_NO_EXISTS", "证书编号已存在，不能重复登记：" + certNo);
        }

        if (rule != null && request.getCertYear() != null
                && trimToNull(request.getCertBatch()) != null && request.getCertSeq() != null) {
            bumpSeqOverLegacy(rule.getId(), version.getId(), request.getCertYear(),
                    request.getCertBatch().trim(), request.getCertSeq(), operator, now);
        }
        return cert;
    }

    // ---------- 作废（只切状态 + 追加记录，信息仍只读） ----------

    @Transactional
    public Certificate revoke(Long certificateId, String reason) {
        Certificate cert = requireCertificate(certificateId);
        return doRevoke(cert, reason);
    }

    @Transactional
    public Certificate revokeByNo(String certNo, String reason) {
        Certificate cert = getByCertNo(certNo);
        return doRevoke(cert, reason);
    }

    private Certificate doRevoke(Certificate cert, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("CERT_REVOKE_REASON_REQUIRED", "作废原因不能为空");
        }
        if (CertConstants.CERT_REVOKED.equals(cert.getStatus())) {
            throw new BusinessException("CERT_ALREADY_REVOKED", "证书已作废，不能重复作废：" + cert.getCertNo());
        }
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        cert.setStatus(CertConstants.CERT_REVOKED);
        cert.setRevokedAt(now);
        cert.setRevokedBy(operator);
        cert.setRevokeReason(reason.trim());
        certificateMapper.updateById(cert);

        CertificateRevokeRecord record = new CertificateRevokeRecord();
        record.setCertificateId(cert.getId());
        record.setCertNo(cert.getCertNo());
        record.setHolderName(cert.getHolderName());
        record.setReason(reason.trim());
        record.setOperator(operator);
        record.setRevokedAt(now);
        revokeMapper.insert(record);
        return cert;
    }

    // ---------- 查询（内部管理视图） ----------

    public Certificate requireCertificate(Long id) {
        Certificate cert = certificateMapper.selectById(id);
        if (cert == null) {
            throw new BusinessException("CERT_NOT_FOUND", "证书不存在：id=" + id);
        }
        return cert;
    }

    public Certificate getByCertNo(String certNo) {
        if (certNo == null || certNo.trim().isEmpty()) {
            throw new BusinessException("CERT_NO_REQUIRED", "证书编号不能为空");
        }
        Certificate cert = certificateMapper.selectOne(
                new QueryWrapper<Certificate>().eq("cert_no", certNo.trim().toUpperCase()));
        if (cert == null) {
            throw new BusinessException("CERT_NOT_FOUND", "证书不存在：" + certNo.trim().toUpperCase());
        }
        return cert;
    }

    @Transactional(readOnly = true)
    public List<Certificate> list(Long batchId, String projectCode, String status, String holderName) {
        QueryWrapper<Certificate> wrapper = new QueryWrapper<Certificate>().orderByDesc("id");
        if (batchId != null) {
            wrapper.eq("batch_id", batchId);
        }
        if (projectCode != null && !projectCode.trim().isEmpty()) {
            wrapper.eq("project_code", projectCode.trim().toUpperCase());
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        if (holderName != null && !holderName.trim().isEmpty()) {
            wrapper.eq("holder_name", holderName.trim());
        }
        return certificateMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public List<CertificateRevokeRecord> revokeRecords(Long certificateId) {
        requireCertificate(certificateId);
        return revokeMapper.selectList(new QueryWrapper<CertificateRevokeRecord>()
                .eq("certificate_id", certificateId)
                .orderByAsc("id"));
    }

    // ---------- 内部 ----------

    /** 汇总一次发证的口径：项目/批次/年度/生效规则版本/冻结参数。 */
    private IssueContext resolveContext(CertIssueRequest request) {
        IssueContext ctx = new IssueContext();
        String project = normalizeProject(request.getProjectCode());
        Long batchId = request.getBatchId();
        ExamBatch batch = null;
        if (batchId != null) {
            batch = batchMapper.selectById(batchId);
            if (batch == null) {
                throw new BusinessException("BATCH_NOT_FOUND", "发证关联的考试批次不存在：id=" + batchId);
            }
            project = batch.getProjectCode() == null ? CertConstants.PROJECT_DEFAULT
                    : batch.getProjectCode().toUpperCase();
        }
        ctx.project = project;
        ctx.batchId = batchId;
        ctx.batchCode = batch == null ? null : batch.getCode();
        ctx.batchName = batch == null ? null : batch.getName();

        CertNumberRuleVersion version = ruleService.requireActiveVersion(project);
        CertNumberRule rule = ruleService.getRule(version.getRuleId());
        CertRuleConfig config = ruleService.readConfig(version.getParams());
        ctx.version = version;
        ctx.rule = rule;
        ctx.config = config;

        ctx.year = request.getCertYear() == null ? LocalDate.now().getYear() : request.getCertYear();
        if (ctx.year < 2000 || ctx.year > 2099) {
            throw new BusinessException("CERT_YEAR_INVALID", "证书年度超出支持范围 [2000,2099]：" + ctx.year);
        }
        String batchRaw = request.getCertBatch();
        if ((batchRaw == null || batchRaw.trim().isEmpty()) && batch != null) {
            batchRaw = digitsOf(batch.getCode());
        }
        if (batchRaw == null || batchRaw.trim().isEmpty()) {
            throw new BusinessException("CERT_BATCH_REQUIRED", "证书批次段不能为空（请传 certBatch 或关联考试批次）");
        }
        String digits = batchRaw.trim().replaceAll("\\D", "");
        if (digits.isEmpty()) {
            throw new BusinessException("CERT_BATCH_INVALID", "证书批次段必须含数字：" + batchRaw.trim());
        }
        if (digits.length() > config.getBatchDigits()) {
            digits = digits.substring(digits.length() - config.getBatchDigits());
        }
        // 规范化为固定位宽（左填零），作为序列键与编号批次段
        StringBuilder padded = new StringBuilder(digits);
        while (padded.length() < config.getBatchDigits()) {
            padded.insert(0, '0');
        }
        ctx.batchPart = padded.toString();
        return ctx;
    }

    private int nextSeqNoLock(Long ruleId, int year, String batchPart) {
        CertSeq row = seqMapper.selectOne(new QueryWrapper<CertSeq>()
                .eq("rule_id", ruleId).eq("cert_year", year).eq("cert_batch", batchPart));
        int inSeq = row == null ? 0 : row.getCurrentSeq();
        return Math.max(inSeq, certificateMapper.maxUsedSeq(ruleId, year, batchPart)) + 1;
    }

    private CertSeq lockOrCreateSeq(Long ruleId, Long versionId, int year, String batchPart,
                                    String operator) {
        CertSeq row = seqMapper.selectForUpdate(ruleId, year, batchPart);
        LocalDateTime now = LocalDateTime.now();
        if (row == null) {
            row = new CertSeq();
            row.setRuleId(ruleId);
            row.setRuleVersionId(versionId);
            row.setCertYear(year);
            row.setCertBatch(batchPart);
            row.setCurrentSeq(0);
            row.setUpdatedBy(operator);
            row.setUpdatedAt(now);
            try {
                seqMapper.insert(row);
            } catch (DuplicateKeyException e) {
                row = seqMapper.selectForUpdate(ruleId, year, batchPart);
            }
        }
        return row;
    }

    /** 旧号补登后把序列抬过旧流水（只增不减），保证之后发号不撞旧号。 */
    private void bumpSeqOverLegacy(Long ruleId, Long versionId, int year, String batchRaw,
                                   int legacySeq, String operator, LocalDateTime now) {
        String digits = batchRaw.replaceAll("\\D", "");
        CertNumberRuleVersion version = ruleService.getVersion(versionId);
        CertRuleConfig config = ruleService.readConfig(version.getParams());
        if (digits.length() > config.getBatchDigits()) {
            digits = digits.substring(digits.length() - config.getBatchDigits());
        }
        StringBuilder padded = new StringBuilder(digits);
        while (padded.length() < config.getBatchDigits()) {
            padded.insert(0, '0');
        }
        String batchPart = padded.toString();

        CertSeq row = lockOrCreateSeq(ruleId, versionId, year, batchPart, operator);
        int usedMax = certificateMapper.maxUsedSeq(ruleId, year, batchPart);
        int target = Math.max(Math.max(row.getCurrentSeq(), usedMax), legacySeq);
        if (target != row.getCurrentSeq()) {
            row.setCurrentSeq(target);
            row.setUpdatedBy(operator);
            row.setUpdatedAt(now);
            seqMapper.updateById(row);
        }
    }

    private void validateValidity(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException("CERT_VALIDITY_RANGE", "证书有效期起不能晚于有效期止：" + from + " ~ " + to);
        }
    }

    private boolean holderExists(Long ruleId, int year, String batchPart, String name) {
        return certificateMapper.selectCount(new QueryWrapper<Certificate>()
                .eq("rule_id", ruleId)
                .eq("cert_year", year)
                .eq("cert_batch", batchPart)
                .eq("holder_name", name)) > 0;
    }

    /** 同规则+年度+批次下，姓名相同即视为同一持证人，防重复发证（证件号仅留档、不作判重键）。 */
    private String dupKey(String name) {
        return "NAME:" + name;
    }

    private String digitsOf(String code) {
        return code == null ? "" : code.replaceAll("\\D", "");
    }

    private String normalizeProject(String projectCode) {
        return projectCode == null || projectCode.trim().isEmpty()
                ? CertConstants.PROJECT_DEFAULT : projectCode.trim().toUpperCase();
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 一次批量发证的解析口径。 */
    private static class IssueContext {
        private String project;
        private Long batchId;
        private String batchCode;
        private String batchName;
        private int year;
        private String batchPart;
        private CertNumberRule rule;
        private CertNumberRuleVersion version;
        private CertRuleConfig config;
    }
}
