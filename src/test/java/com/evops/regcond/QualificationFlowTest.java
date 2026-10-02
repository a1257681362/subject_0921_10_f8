package com.evops.regcond;

import com.evops.cert.dto.CertLegacyRequest;
import com.evops.cert.entity.Certificate;
import com.evops.cert.service.CertificateService;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.ExamSite;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.ExamBatchMapper;
import com.evops.exam.mapper.ExamSiteMapper;
import com.evops.exam.mapper.RegistrationMapper;
import com.evops.grade.entity.ScoreNotification;
import com.evops.grade.service.ScoreNotificationService;
import com.evops.regcond.common.RegCondConstants;
import com.evops.regcond.dto.AppealConclusionRequest;
import com.evops.regcond.dto.AppealRequest;
import com.evops.regcond.dto.ConditionItem;
import com.evops.regcond.dto.ConditionRuleRequest;
import com.evops.regcond.dto.ConditionRuleVersionRequest;
import com.evops.regcond.dto.ManualReviewRequest;
import com.evops.regcond.dto.QualificationSubmitRequest;
import com.evops.regcond.entity.ConditionRule;
import com.evops.regcond.entity.ConditionRuleVersion;
import com.evops.regcond.entity.QualificationAppeal;
import com.evops.regcond.entity.QualificationCheckItem;
import com.evops.regcond.entity.RegistrationQualification;
import com.evops.regcond.mapper.RegistrationQualificationMapper;
import com.evops.regcond.service.AppealService;
import com.evops.regcond.service.ConditionRuleService;
import com.evops.regcond.service.QualificationService;
import com.evops.regcond.vo.QualificationView;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报名条件规则引擎端到端：
 * 规则三段式 → 逐项核验（学历/年限算头算尾/前置证书连有效期）→ 不通过逐条原因、存疑转人工
 * → 规则按提交时点生效 → 申诉流转留痕、申诉期间不重复拦截 → 合格入册 → 快照回退。
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QualificationFlowTest {

    private static final String PROJECT = "ZHUKAO";
    private static final LocalDate SUBMIT_DAY = LocalDate.of(2026, 10, 2);

    /** 当前用例的级别（publishRule 时设定，submitRequest 自动带上）。 */
    private String level;

    @Autowired
    private ConditionRuleService ruleService;
    @Autowired
    private QualificationService qualificationService;
    @Autowired
    private AppealService appealService;
    @Autowired
    private CertificateService certificateService;
    @Autowired
    private ScoreNotificationService notificationService;
    @Autowired
    private ExamSiteMapper siteMapper;
    @Autowired
    private ExamBatchMapper batchMapper;
    @Autowired
    private RegistrationMapper registrationMapper;
    @Autowired
    private RegistrationQualificationMapper qualificationMapper;

    // ---------- 辅助 ----------

    private ConditionRuleVersion publishRule(String code, String levelCode, List<ConditionItem> items) {
        this.level = levelCode;
        ConditionRuleRequest rr = new ConditionRuleRequest();
        rr.setCode(code);
        rr.setName("条件规则-" + code);
        rr.setProjectCode(PROJECT);
        rr.setLevelCode(levelCode);
        ConditionRule rule = ruleService.createRule(rr);

        ConditionRuleVersionRequest vr = new ConditionRuleVersionRequest();
        vr.setItems(items);
        ConditionRuleVersion draft = ruleService.createDraftVersion(rule.getId(), vr);
        return ruleService.publishVersion(draft.getId());
    }

    private ConditionItem education(String required) {
        ConditionItem c = new ConditionItem();
        c.setItemType(RegCondConstants.ITEM_EDUCATION);
        c.setRequiredEducation(required);
        return c;
    }

    private ConditionItem years(String min, String unit) {
        ConditionItem c = new ConditionItem();
        c.setItemType(RegCondConstants.ITEM_YEARS);
        c.setMinValue(new BigDecimal(min));
        c.setUnit(unit);
        return c;
    }

    private ConditionItem prereqCert(String subject, String grade, boolean checkValidity, boolean validityRequired) {
        ConditionItem c = new ConditionItem();
        c.setItemType(RegCondConstants.ITEM_PREREQ_CERT);
        c.setRequiredSubject(subject);
        c.setRequiredGrade(grade);
        c.setCheckValidity(checkValidity);
        c.setValidityRequired(validityRequired);
        return c;
    }

    private List<ConditionItem> standardRule() {
        List<ConditionItem> items = new ArrayList<>();
        items.add(education("JUNIOR_COLLEGE"));
        items.add(years("3", "YEAR"));
        items.add(prereqCert("助考一级", null, true, false));
        return items;
    }

    private Certificate legacyCert(String certNo, String name, String subject, String grade,
                                   LocalDate validFrom, LocalDate validTo) {
        CertLegacyRequest req = new CertLegacyRequest();
        req.setCertNo(certNo);
        req.setHolderName(name);
        req.setProjectCode(PROJECT);
        req.setSubject(subject);
        req.setGrade(grade);
        req.setValidFrom(validFrom);
        req.setValidTo(validTo);
        return certificateService.registerLegacy(req);
    }

    private ExamBatch createBatch(String code) {
        ExamSite site = new ExamSite();
        site.setCode("S" + code);
        site.setName("考点" + code);
        site.setStatus("ENABLED");
        siteMapper.insert(site);
        ExamBatch batch = new ExamBatch();
        batch.setCode(code);
        batch.setName("批次" + code);
        batch.setSiteId(site.getId());
        batch.setProjectCode(PROJECT);
        batch.setExamDate(SUBMIT_DAY);
        batch.setStatus("CREATED");
        batchMapper.insert(batch);
        return batch;
    }

    private QualificationSubmitRequest submitRequest(ExamBatch batch, String regNo, String name, String idNo,
                                                     String edu, LocalDate workStart, String certNo) {
        QualificationSubmitRequest req = new QualificationSubmitRequest();
        req.setBatchId(batch == null ? null : batch.getId());
        req.setRegNo(regNo);
        req.setName(name);
        req.setIdNo(idNo);
        req.setProjectCode(PROJECT);
        req.setLevelCode(level);
        req.setEducation(edu);
        req.setWorkStartDate(workStart);
        req.setPrereqCertNo(certNo);
        req.setSubmittedAt(SUBMIT_DAY);
        return req;
    }

    private QualificationCheckItem itemOf(QualificationView v, String type) {
        for (QualificationCheckItem i : v.getItems()) {
            if (i.getItemType().equals(type)) {
                return i;
            }
        }
        throw new IllegalStateException("核验项不存在：" + type);
    }

    // ---------- 用例 ----------

    @Test
    void fullPass_thenEnroll() {
        ExamBatch batch = createBatch("BRC01");
        publishRule("TJRZ01", "LV01", standardRule());
        legacyCert("OLD-001", "张三", "助考一级", null,
                LocalDate.of(2024, 1, 1), LocalDate.of(2028, 12, 31));

        // 年限边界算头算尾：起始日 2023-10-03，提交日 2026-10-02，
        // 普通口径差 2 年 364 天，算头算尾正好 3 周年，应当通过
        QualificationView v = qualificationService.submit(submitRequest(
                batch, "RC001", "张三", "11010119900101001X",
                "BACHELOR", LocalDate.of(2023, 10, 3), "OLD-001"));

        assertEquals(RegCondConstants.QUAL_QUALIFIED, v.getQualification().getStatus());
        assertEquals(RegCondConstants.VIA_AUTO, v.getQualification().getQualifiedVia());
        assertEquals(3, v.getItems().size());
        for (QualificationCheckItem i : v.getItems()) {
            assertEquals(RegCondConstants.CHECK_PASS, i.getResult(), i.getItemName() + " 应通过");
        }
        assertNotNull(v.getQualification().getRuleVersionNo());

        Registration reg = qualificationService.enroll(v.getQualification().getId());
        assertEquals("RC001", reg.getRegNo());
        assertEquals(batch.getId(), reg.getBatchId());
        // 入册幂等
        Registration again = qualificationService.enroll(v.getQualification().getId());
        assertEquals(reg.getId(), again.getId());
    }

    @Test
    void years_inclusiveBoundary_oneDayMoreFails() {
        publishRule("TJRZ02", "LV02", standardRule());

        // 起始再晚一天：2023-10-04 → 算头算尾仅 2 年 11 个足月，不满足 3 周年
        QualificationView v = qualificationService.submit(submitRequest(
                null, "RC002", "李四", "11010119900202002X",
                "BACHELOR", LocalDate.of(2023, 10, 4), null));

        QualificationCheckItem years = itemOf(v, RegCondConstants.ITEM_YEARS);
        assertEquals(RegCondConstants.CHECK_FAIL, years.getResult());
        assertTrue(years.getReason().contains("从业年限不足"), years.getReason());
        assertEquals(RegCondConstants.QUAL_REJECTED, v.getQualification().getStatus());
        // 未提供前置证书编号也是硬不符
        assertEquals(2, v.getQualification().getFailCount());
        assertThrows(com.evops.common.BusinessException.class,
                () -> qualificationService.enroll(v.getQualification().getId()));
    }

    @Test
    void education_failGivesItemReason() {
        publishRule("TJRZ03", "LV03", standardRule());
        QualificationView v = qualificationService.submit(submitRequest(
                null, "RC003", "王五", "11010119900303003X",
                "SENIOR_HIGH", LocalDate.of(2020, 1, 1), null));

        QualificationCheckItem edu = itemOf(v, RegCondConstants.ITEM_EDUCATION);
        assertEquals(RegCondConstants.CHECK_FAIL, edu.getResult());
        assertTrue(edu.getReason().contains("学历不满足"), edu.getReason());
        assertEquals(RegCondConstants.QUAL_REJECTED, v.getQualification().getStatus());
    }

    @Test
    void prereqCert_expiredAndRevokedFail_notFoundManual() {
        publishRule("TJRZ04", "LV04", standardRule());
        legacyCert("OLD-EXP", "赵六", "助考一级", null,
                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31));

        QualificationView expired = qualificationService.submit(submitRequest(
                null, "RC004", "赵六", "11010119900404004X",
                "BACHELOR", LocalDate.of(2020, 1, 1), "OLD-EXP"));
        QualificationCheckItem certItem = itemOf(expired, RegCondConstants.ITEM_PREREQ_CERT);
        assertEquals(RegCondConstants.CHECK_FAIL, certItem.getResult());
        assertTrue(certItem.getReason().contains("已于 2025-12-31 到期"), certItem.getReason());

        // 作废证：硬不符
        Certificate cert = certificateService.getByCertNo("OLD-EXP");
        certificateService.revoke(cert.getId(), "考试违纪作废");
        QualificationView revoked = qualificationService.submit(submitRequest(
                null, "RC004B", "赵六", "11010119900404004X",
                "BACHELOR", LocalDate.of(2020, 1, 1), "OLD-EXP"));
        assertTrue(itemOf(revoked, RegCondConstants.ITEM_PREREQ_CERT).getReason().contains("已作废"));

        // 库内查无此证：存疑转人工，不直接误杀
        QualificationView missing = qualificationService.submit(submitRequest(
                null, "RC004C", "赵六", "11010119900404004X",
                "BACHELOR", LocalDate.of(2020, 1, 1), "OUT-OF-TOWN-9"));
        QualificationCheckItem missingItem = itemOf(missing, RegCondConstants.ITEM_PREREQ_CERT);
        assertEquals(RegCondConstants.CHECK_MANUAL, missingItem.getResult());
        assertEquals(RegCondConstants.QUAL_MANUAL, missing.getQualification().getStatus());

        // 人工放行后合格入册
        ExamBatch batch = createBatch("BRC04");
        missing.getQualification().setBatchId(batch.getId());
        qualificationMapper.updateById(missing.getQualification());
        ManualReviewRequest review = new ManualReviewRequest();
        review.setApproved(true);
        review.setComment("原件已核，外地证真实有效");
        RegistrationQualification reviewed =
                qualificationService.manualReview(missing.getQualification().getId(), review);
        assertEquals(RegCondConstants.QUAL_QUALIFIED, reviewed.getStatus());
        assertEquals(RegCondConstants.VIA_MANUAL_REVIEW, reviewed.getQualifiedVia());
        assertNotNull(qualificationService.enroll(reviewed.getId()));
    }

    @Test
    void validityRequired_butCertWithoutValidity_goesManual() {
        List<ConditionItem> items = new ArrayList<>();
        items.add(prereqCert("助考一级", null, true, true));
        publishRule("TJRZ05", "LV05", items);
        // 老证未登记有效期（null validTo）
        legacyCert("OLD-NOEXP", "钱七", "助考一级", null, null, null);

        QualificationView v = qualificationService.submit(submitRequest(
                null, "RC005", "钱七", "11010119900505005X",
                "BACHELOR", LocalDate.of(2020, 1, 1), "OLD-NOEXP"));
        assertEquals(RegCondConstants.CHECK_MANUAL,
                itemOf(v, RegCondConstants.ITEM_PREREQ_CERT).getResult());
    }

    @Test
    void ruleTakesEffectAtSubmissionTime_historyKeepsOldVersion() {
        ConditionRuleVersion v1 = publishRule("TJRZ06", "LV06", standardRule());
        ExamBatch batch = createBatch("BRC06");
        legacyCert("OLD-V1", "孙八", "助考一级", null,
                LocalDate.of(2024, 1, 1), LocalDate.of(2028, 12, 31));

        QualificationView first = qualificationService.submit(submitRequest(
                batch, "RC006", "孙八", "11010119900606006X",
                "BACHELOR", LocalDate.of(2020, 1, 1), "OLD-V1"));
        assertEquals(v1.getVersionNo(), first.getQualification().getRuleVersionNo());

        // 规则升级：学历提到本科以上→硕士；旧 ACTIVE 自动 DISABLED
        ConditionRule rule = ruleService.listRules(PROJECT, level).get(0);
        List<ConditionItem> stricter = new ArrayList<>();
        stricter.add(education("MASTER"));
        ConditionRuleVersion v2 = ruleService.publishVersion(
                ruleService.createDraftVersion(rule.getId(),
                        buildVersionRequest(stricter)).getId());

        // 新提交按新版本：本科不再合格
        QualificationView after = qualificationService.submit(submitRequest(
                batch, "RC006B", "孙八", "11010119900606006X",
                "BACHELOR", LocalDate.of(2020, 1, 1), "OLD-V1"));
        assertEquals(v2.getVersionNo(), after.getQualification().getRuleVersionNo());
        assertEquals(RegCondConstants.CHECK_FAIL, itemOf(after, RegCondConstants.ITEM_EDUCATION).getResult());

        // 历史核验单口径不变
        RegistrationQualification history =
                qualificationService.requireQualification(first.getQualification().getId());
        assertEquals(v1.getVersionNo(), history.getRuleVersionNo());

        // 停用版本不能重新发布
        assertThrows(com.evops.common.BusinessException.class, () -> ruleService.publishVersion(v1.getId()));
    }

    @Test
    void appealFlow_duringAppealNotBlocked_rejectRestoresBlock_approveWaivesForever() {
        publishRule("TJRZ07", "LV07", standardRule());
        // 证书已过期 → 前置证书项硬不符
        legacyCert("OLD-APL", "周九", "助考一级", null,
                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 6, 30));

        // 第一次提交：拒收
        QualificationView q1 = qualificationService.submit(submitRequest(
                null, "RC007", "周九", "11010119900707007X",
                "BACHELOR", LocalDate.of(2020, 1, 1), "OLD-APL"));
        QualificationCheckItem failItem = itemOf(q1, RegCondConstants.ITEM_PREREQ_CERT);
        assertEquals(RegCondConstants.CHECK_FAIL, failItem.getResult());
        assertEquals(RegCondConstants.QUAL_REJECTED, q1.getQualification().getStatus());

        // 学员申诉
        AppealRequest appealReq = new AppealRequest();
        appealReq.setCheckItemId(failItem.getId());
        appealReq.setReason("证书有效期已延期，新证补办中");
        appealReq.setApplicant("周九");
        QualificationAppeal appeal = appealService.apply(appealReq);
        assertEquals(RegCondConstants.APPEAL_SUBMITTED, appeal.getStatus());

        // 核验项进入申诉中、核验单暂不拦截
        QualificationView q1Again = qualificationService.getView(q1.getQualification().getId());
        assertEquals(RegCondConstants.CHECK_APPEALING,
                itemOf(q1Again, RegCondConstants.ITEM_PREREQ_CERT).getResult());
        assertEquals(RegCondConstants.QUAL_QUALIFIED, q1Again.getQualification().getStatus());
        assertTrue(q1Again.getQualification().getHasAppealingItem());

        // 申诉期间重新提交：不重复拦截
        QualificationView q2 = qualificationService.submit(submitRequest(
                null, "RC007B", "周九", "11010119900707007X",
                "BACHELOR", LocalDate.of(2020, 1, 1), "OLD-APL"));
        assertEquals(RegCondConstants.CHECK_APPEALING,
                itemOf(q2, RegCondConstants.ITEM_PREREQ_CERT).getResult());
        assertEquals(RegCondConstants.QUAL_QUALIFIED, q2.getQualification().getStatus());
        // 重复申诉被拒
        assertThrows(com.evops.common.BusinessException.class, () -> appealService.apply(appealReq));

        // 受理（流转留痕），驳回必须写意见
        appealService.accept(appeal.getId(), "材料待补");
        AppealConclusionRequest reject = new AppealConclusionRequest();
        reject.setApproved(false);
        reject.setConclusion("未提供有效延期证明，驳回");
        appealService.conclude(appeal.getId(), reject);

        // 驳回：两张核验单都恢复拦截
        assertEquals(RegCondConstants.QUAL_REJECTED,
                qualificationService.getView(q1.getQualification().getId()).getQualification().getStatus());
        assertEquals(RegCondConstants.CHECK_FAIL,
                itemOf(qualificationService.getView(q1.getQualification().getId()),
                        RegCondConstants.ITEM_PREREQ_CERT).getResult());
        assertEquals(RegCondConstants.QUAL_REJECTED,
                qualificationService.getView(q2.getQualification().getId()).getQualification().getStatus());
        // 流转版本：CREATE/ACCEPT/REJECT
        assertEquals(3, appealService.listVersions(appeal.getId()).size());
        // 驳回通知已发
        List<ScoreNotification> notes = notificationService.list("RC007", false);
        assertTrue(notes.stream().anyMatch(n -> "COND_APPEAL_REJECTED".equals(n.getNotifyType())));

        // 新材料重新提交后再申诉，这次通过
        QualificationView q3 = qualificationService.submit(submitRequest(
                null, "RC007C", "周九", "11010119900707007X",
                "BACHELOR", LocalDate.of(2020, 1, 1), "OLD-APL"));
        QualificationCheckItem failAgain = itemOf(q3, RegCondConstants.ITEM_PREREQ_CERT);
        AppealRequest appeal2Req = new AppealRequest();
        appeal2Req.setCheckItemId(failAgain.getId());
        appeal2Req.setReason("已取到主管部门延期证明");
        QualificationAppeal appeal2 = appealService.apply(appeal2Req);
        AppealConclusionRequest approve = new AppealConclusionRequest();
        approve.setApproved(true);
        approve.setConclusion("情况属实，豁免该项");
        appealService.conclude(appeal2.getId(), approve);

        QualificationView q3Done = qualificationService.getView(q3.getQualification().getId());
        assertEquals(RegCondConstants.QUAL_QUALIFIED, q3Done.getQualification().getStatus());
        assertEquals(RegCondConstants.VIA_APPEAL, q3Done.getQualification().getQualifiedVia());
        assertEquals(appeal2.getId(), q3Done.getQualification().getWaiverAppealId());

        // 豁免永久保持：再提交（规则版本不变）自动放行，不再拦截也无需再申诉
        QualificationView q4 = qualificationService.submit(submitRequest(
                null, "RC007D", "周九", "11010119900707007X",
                "BACHELOR", LocalDate.of(2020, 1, 1), "OLD-APL"));
        QualificationCheckItem waived = itemOf(q4, RegCondConstants.ITEM_PREREQ_CERT);
        assertEquals(RegCondConstants.CHECK_PASS, waived.getResult());
        assertEquals(appeal2.getId(), waived.getWaiverAppealId());
        assertEquals(RegCondConstants.QUAL_QUALIFIED, q4.getQualification().getStatus());
    }

    @Test
    void snapshotRollback_restoresConditionTables() {
        ExamBatch batch = createBatch("BRC08");
        publishRule("TJRZ08", "LV08", standardRule());
        legacyCert("OLD-SNAP", "吴十", "助考一级", null,
                LocalDate.of(2024, 1, 1), LocalDate.of(2028, 12, 31));
        QualificationView before = qualificationService.submit(submitRequest(
                batch, "RC008", "吴十", "11010119900808008X",
                "BACHELOR", LocalDate.of(2023, 10, 2), "OLD-SNAP"));
        assertEquals(RegCondConstants.QUAL_QUALIFIED, before.getQualification().getStatus());

        // 先打快照，再造一条核验单
        com.evops.exam.entity.WorkspaceVersion snapshot =
                workspaceVersionService.createSnapshot("regcond 回退前快照", null);

        QualificationView extra = qualificationService.submit(submitRequest(
                batch, "RC008B", "吴十B", "11010119900808008B",
                "BACHELOR", LocalDate.of(2023, 10, 2), "OLD-SNAP"));
        assertNotNull(qualificationMapper.selectById(extra.getQualification().getId()));

        // 回退到快照，快照后新增的核验单应消失
        workspaceVersionService.rollback(snapshot.getVersionNo(), "regcond 回退验证");

        assertNull(qualificationMapper.selectById(extra.getQualification().getId()));
        assertNotNull(qualificationMapper.selectById(before.getQualification().getId()));
        // 规则与版本仍在
        assertEquals(1, ruleService.listRules(PROJECT, level).size());
        // 回退后仍可正常落新单（序列已重置，不撞主键）
        QualificationView after = qualificationService.submit(submitRequest(
                batch, "RC008C", "吴十C", "11010119900808008C",
                "BACHELOR", LocalDate.of(2023, 10, 2), "OLD-SNAP"));
        assertNotNull(after.getQualification().getId());
    }

    @Autowired
    private com.evops.exam.service.WorkspaceVersionService workspaceVersionService;

    private ConditionRuleVersionRequest buildVersionRequest(List<ConditionItem> items) {
        ConditionRuleVersionRequest vr = new ConditionRuleVersionRequest();
        vr.setItems(items);
        return vr;
    }
}
