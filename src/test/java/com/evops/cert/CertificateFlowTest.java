package com.evops.cert;

import com.evops.cert.dto.CertIssueItem;
import com.evops.cert.dto.CertIssueRequest;
import com.evops.cert.dto.CertLegacyRequest;
import com.evops.cert.dto.CertRuleConfig;
import com.evops.cert.dto.CertRuleRequest;
import com.evops.cert.dto.CertRuleVersionRequest;
import com.evops.cert.engine.CertNumberCodec;
import com.evops.cert.engine.CertVerifyRateLimiter;
import com.evops.cert.entity.CertNumberRule;
import com.evops.cert.entity.CertNumberRuleVersion;
import com.evops.cert.entity.Certificate;
import com.evops.cert.service.CertNumberRuleService;
import com.evops.cert.service.CertVerifyConfigService;
import com.evops.cert.service.CertVerifyService;
import com.evops.cert.service.CertificateService;
import com.evops.cert.vo.CertIssuePreview;
import com.evops.cert.vo.CertIssueResult;
import com.evops.cert.vo.CertVerifyView;
import com.evops.common.BusinessException;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.ExamSite;
import com.evops.exam.mapper.ExamBatchMapper;
import com.evops.exam.mapper.ExamSiteMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 证书编号与验真端到端：规则三段式 → 批量发号 → 验真 → 作废 → 规则升级只管新证 → 旧号防撞 → 限流。
 */
@SpringBootTest
@ActiveProfiles("test")
class CertificateFlowTest {

    @Autowired
    private CertNumberRuleService ruleService;
    @Autowired
    private CertificateService certificateService;
    @Autowired
    private CertVerifyService verifyService;
    @Autowired
    private CertVerifyConfigService configService;
    @Autowired
    private CertVerifyRateLimiter rateLimiter;
    @Autowired
    private ExamSiteMapper siteMapper;
    @Autowired
    private ExamBatchMapper batchMapper;

    private CertNumberRuleVersion publishRule(String project, String prefix, int seqDigits) {
        CertRuleRequest rr = new CertRuleRequest();
        rr.setCode("ZS" + project);
        rr.setName("证书编号规则-" + project);
        rr.setProjectCode(project);
        CertNumberRule rule = ruleService.createRule(rr);

        CertRuleConfig config = new CertRuleConfig();
        config.setPrefix(prefix);
        config.setYearDigits(4);
        config.setBatchDigits(2);
        config.setSeqDigits(seqDigits);
        config.setCheckEnabled(true);
        CertRuleVersionRequest vr = new CertRuleVersionRequest();
        vr.setParams(config);
        CertNumberRuleVersion draft = ruleService.createDraftVersion(rule.getId(), vr);
        return ruleService.publishVersion(draft.getId());
    }

    private ExamBatch createBatch(String code, String project) {
        ExamSite site = new ExamSite();
        site.setCode("S" + code);
        site.setName("考点" + code);
        site.setStatus("ENABLED");
        siteMapper.insert(site);
        ExamBatch batch = new ExamBatch();
        batch.setCode(code);
        batch.setName("批次" + code);
        batch.setSiteId(site.getId());
        batch.setProjectCode(project);
        batch.setExamDate(LocalDate.of(2026, 10, 1));
        batch.setStatus("CREATED");
        batchMapper.insert(batch);
        return batch;
    }

    private CertIssueRequest issueRequest(Long batchId, String project, String batch, CertIssueItem... items) {
        CertIssueRequest req = new CertIssueRequest();
        req.setProjectCode(project);
        req.setBatchId(batchId);
        req.setCertBatch(batch);
        req.setCertYear(2026);
        req.setItems(Arrays.asList(items));
        return req;
    }

    private CertIssueItem item(String name) {
        CertIssueItem i = new CertIssueItem();
        i.setHolderName(name);
        return i;
    }

    @Test
    void fullFlow_issue_verify_revoke_ruleUpgrade_legacyBump() {
        ExamBatch batch = createBatch("B2601", "ZHUKAO");
        CertNumberRuleVersion v1 = publishRule("ZHUKAO", "ZS", 5);

        CertIssueRequest req = issueRequest(batch.getId(), "ZHUKAO", "01",
                item("张三"), item("李四"), item("王五"));

        CertIssuePreview preview = certificateService.previewIssue(req);
        assertEquals(3, preview.getActionableCount());
        assertEquals(0, preview.getSkipCount());
        assertEquals(1, preview.getNextSeq());
        // 预检展示拟发编号：前缀+年度+批次+流水+校验码
        String firstPreviewNo = preview.getItems().get(0).getCertNo();
        assertTrue(firstPreviewNo.startsWith("ZS20260100001"));
        assertTrue(CertNumberCodec.isValid(firstPreviewNo));

        CertIssueResult result = certificateService.issue(req);
        assertEquals(3, result.getIssuedCount());
        String zsNo = result.getItems().get(0).getCertNo();
        String lsNo = result.getItems().get(1).getCertNo();
        String wwNo = result.getItems().get(2).getCertNo();
        assertEquals("ZS20260100001" + CertNumberCodec.checkDigit("ZS20260100001"), zsNo);
        assertEquals(firstPreviewNo, zsNo);
        assertTrue(CertNumberCodec.isValid(lsNo) && CertNumberCodec.isValid(wwNo));
        // 无重号
        assertFalse(zsNo.equals(lsNo) || lsNo.equals(wwNo));

        // 发证冻结规则版本
        Certificate zs = certificateService.getByCertNo(zsNo);
        assertEquals(v1.getVersionNo(), zs.getRuleVersionNo());
        assertEquals("ISSUED", zs.getStatus());

        // 验真：编号+姓名一致 → 有效，回批次，不回隐私字段（视图无 idNo 字段）
        CertVerifyView ok = verifyService.verify(zsNo, "张三");
        assertTrue(ok.isFound());
        assertTrue(ok.isValid());
        assertEquals("ISSUED", ok.getStatus());
        assertEquals("B2601", ok.getBatchCode());
        assertEquals(java.time.LocalDate.now().toString(), ok.getIssuedAt());
        assertNull(ok.getRevokeReason());

        // 姓名不匹配 → 查无（不暴露编号存在）
        CertVerifyView wrongName = verifyService.verify(zsNo, "赵六");
        assertFalse(wrongName.isFound());
        assertFalse(wrongName.isValid());

        // 编号不存在 → 同样查无
        CertVerifyView notFound = verifyService.verify("ZS99999999999X", "张三");
        assertFalse(notFound.isFound());

        // 作废：验真给明确作废提示，原证记录仍在且只读
        certificateService.revokeByNo(zsNo, "证书信息有误，作废重发");
        CertVerifyView revoked = verifyService.verify(zsNo, "张三");
        assertTrue(revoked.isFound());
        assertFalse(revoked.isValid());
        assertEquals("REVOKED", revoked.getStatus());
        assertTrue(revoked.getMessage().contains("已作废"));
        assertEquals("证书信息有误，作废重发", revoked.getRevokeReason());
        // 作废记录留痕
        assertEquals(1, certificateService.revokeRecords(zs.getId()).size());
        // 重复作废拒收
        assertThrows(BusinessException.class,
                () -> certificateService.revokeByNo(zsNo, "再次作废"));

        // 规则升级：发布 v2（流水加宽到 6 位）只管新证，老证编号不变
        CertNumberRule rule = ruleService.listRules("ZHUKAO").get(0);
        CertRuleConfig cfg2 = new CertRuleConfig();
        cfg2.setPrefix("ZS");
        cfg2.setYearDigits(4);
        cfg2.setBatchDigits(2);
        cfg2.setSeqDigits(6);
        cfg2.setCheckEnabled(true);
        CertRuleVersionRequest vr2 = new CertRuleVersionRequest();
        vr2.setParams(cfg2);
        CertNumberRuleVersion v2 = ruleService.publishVersion(
                ruleService.createDraftVersion(rule.getId(), vr2).getId());
        assertEquals("DISABLED", ruleService.getVersion(v1.getId()).getStatus());
        assertEquals("ACTIVE", ruleService.getVersion(v2.getId()).getStatus());

        ExamBatch batch2 = createBatch("B2602", "ZHUKAO");
        CertIssueResult second = certificateService.issue(
                issueRequest(batch2.getId(), "ZHUKAO", "02", item("赵六")));
        String zlNo = second.getItems().get(0).getCertNo();
        assertTrue(zlNo.startsWith("ZS202602000001"));
        assertEquals(v2.getVersionNo(), certificateService.getByCertNo(zlNo).getRuleVersionNo());
        // 老证仍挂 v1，编号不变
        assertEquals(v1.getVersionNo(), certificateService.getByCertNo(zsNo).getRuleVersionNo());
        assertEquals(zsNo, certificateService.getByCertNo(zsNo).getCertNo());

        // 手工旧号补登：同年度批次 01 补一个旧流水号 00099，之后系统发号自动跳过旧号
        CertLegacyRequest legacy = new CertLegacyRequest();
        legacy.setCertNo("ZS202601000999");
        legacy.setHolderName("旧证持有人");
        legacy.setProjectCode("ZHUKAO");
        legacy.setCertYear(2026);
        legacy.setCertBatch("01");
        legacy.setCertSeq(99);
        certificateService.registerLegacy(legacy);

        CertIssueRequest afterLegacy = issueRequest(batch.getId(), "ZHUKAO", "01", item("钱七"));
        CertIssueResult after = certificateService.issue(afterLegacy);
        // v2 流水 6 位：批次 01 已用到旧号 seq=99（5 位规则发的，库内 maxUsedSeq=99），新号从 100 起
        String qqNo = after.getItems().get(0).getCertNo();
        assertTrue(qqNo.startsWith("ZS202601000100"), "旧号补登后新号必须抬过旧流水，实际：" + qqNo);
        assertNotNull(certificateService.getByCertNo("ZS202601000999"));
    }

    @Test
    void duplicateHolderInSameYearBatchIsSkipped() {
        publishRule("DEFAULT", "RZ", 5);
        CertIssueRequest req = issueRequest(null, "DEFAULT", "03", item("张三"), item("张三"));
        CertIssueResult result = certificateService.issue(req);
        assertEquals(1, result.getIssuedCount());
        assertEquals(1, result.getSkipCount());
        // 再发一次，库内已有也跳过
        CertIssueResult again = certificateService.issue(req);
        assertEquals(0, again.getIssuedCount());
        assertEquals(2, again.getSkipCount());
    }

    @Test
    void verifyWindowSwitchAndRateLimit() {
        publishRule("RATE", "MS", 5);
        CertIssueResult r = certificateService.issue(
                issueRequest(null, "RATE", "09", item("限流测试")));
        String no = r.getItems().get(0).getCertNo();

        // 关闭窗口
        configService.update("VERIFY_ENABLED", "false");
        BusinessException ex = assertThrows(BusinessException.class, () -> verifyService.verify(no, "限流测试"));
        assertEquals("CERT_VERIFY_DISABLED", ex.getCode());
        configService.update("VERIFY_ENABLED", "true");

        // 限流：每分钟 3 次，第 4 次拒绝
        rateLimiter.reset();
        configService.update("VERIFY_RATE_PER_MINUTE", "3");
        assertTrue(verifyService.verify(no, "限流测试").isValid());
        assertTrue(verifyService.verify(no, "限流测试").isValid());
        assertTrue(verifyService.verify(no, "限流测试").isValid());
        BusinessException limited = assertThrows(BusinessException.class,
                () -> verifyService.verify(no, "限流测试"));
        assertEquals("CERT_VERIFY_RATE_LIMITED", limited.getCode());
        configService.update("VERIFY_RATE_PER_MINUTE", "0");
    }
}
