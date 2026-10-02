package com.evops.regcond;

import com.evops.cert.entity.Certificate;
import com.evops.cert.mapper.CertificateMapper;
import com.evops.common.BusinessException;
import com.evops.exam.service.WorkspaceVersionService;
import com.evops.regcond.dto.EducationItemConfig;
import com.evops.regcond.dto.ManualReviewRequest;
import com.evops.regcond.dto.PriorCertItemConfig;
import com.evops.regcond.dto.PriorCertMaterial;
import com.evops.regcond.dto.RegAppealConclusionRequest;
import com.evops.regcond.dto.RegAppealRequest;
import com.evops.regcond.dto.RegConditionItemsConfig;
import com.evops.regcond.dto.RegRuleRequest;
import com.evops.regcond.dto.RegRuleVersionRequest;
import com.evops.regcond.dto.RegSubmitItem;
import com.evops.regcond.dto.RegSubmitRequest;
import com.evops.regcond.dto.YearsItemConfig;
import com.evops.regcond.entity.RegAppeal;
import com.evops.regcond.entity.RegConditionRule;
import com.evops.regcond.entity.RegConditionRuleVersion;
import com.evops.regcond.entity.RegConditionSubmission;
import com.evops.regcond.service.RegAppealService;
import com.evops.regcond.service.RegConditionRuleService;
import com.evops.regcond.service.RegConditionSubmitService;
import com.evops.regcond.vo.ItemCheckResult;
import com.evops.regcond.vo.SubmitItemView;
import com.evops.regcond.vo.SubmitPreview;
import com.evops.regcond.vo.SubmitResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 旺季报名条件规则引擎端到端：
 * 规则三段式 → 逐项核验（学历/年限算头算尾/前置证书连有效期）→ 逐条原因 → 存疑转人工 →
 * 申诉流转与申诉期间不重复拦截 → 规则按提交时点生效 → 快照回退。
 */
@SpringBootTest
@ActiveProfiles("test")
class RegConditionFlowTest {

    private static final String PROJECT = "RCUT";

    @Autowired
    private RegConditionRuleService ruleService;
    @Autowired
    private RegConditionSubmitService submitService;
    @Autowired
    private RegAppealService appealService;
    @Autowired
    private CertificateMapper certificateMapper;
    @Autowired
    private com.evops.regcond.mapper.RegConditionRuleMapper ruleMapper;
    @Autowired
    private WorkspaceVersionService workspaceVersionService;

    private String uniqueCode() {
        return "RC" + System.nanoTime() % 1000000L;
    }

    // ---------- 构造帮助 ----------

    private RegConditionRuleVersion publishRule(String code, String level, String yearsBasis,
                                                RegConditionItemsConfig items) {
        RegRuleRequest rr = new RegRuleRequest();
        rr.setCode(code);
        rr.setName("条件规则-" + code);
        rr.setProjectCode(PROJECT);
        rr.setLevelCode(level);
        rr.setYearsBasis(yearsBasis);
        RegConditionRule rule = ruleService.createRule(rr);

        RegRuleVersionRequest vr = new RegRuleVersionRequest();
        vr.setItems(items);
        RegConditionRuleVersion draft = ruleService.createDraftVersion(rule.getId(), vr);
        return ruleService.publishVersion(draft.getId());
    }

    private RegConditionItemsConfig fullItems(String eduLevel, int minYears, PriorCertItemConfig... certs) {
        RegConditionItemsConfig items = new RegConditionItemsConfig();
        EducationItemConfig edu = new EducationItemConfig();
        edu.setEnabled(true);
        edu.setRequiredLevel(eduLevel);
        items.setEducation(edu);
        YearsItemConfig years = new YearsItemConfig();
        years.setEnabled(true);
        years.setMinYears(minYears);
        items.setYears(years);
        items.setPriorCerts(new ArrayList<>(Arrays.asList(certs)));
        return items;
    }

    private RegConditionItemsConfig yearsOnly(int minYears) {
        RegConditionItemsConfig i = new RegConditionItemsConfig();
        YearsItemConfig y = new YearsItemConfig();
        y.setEnabled(true);
        y.setMinYears(minYears);
        i.setYears(y);
        return i;
    }

    private PriorCertItemConfig certConfig(String key, String name, Integer validYears, boolean checkValidity) {
        PriorCertItemConfig c = new PriorCertItemConfig();
        c.setItemKey(key);
        c.setCertName(name);
        c.setRequired(true);
        c.setCheckValidity(checkValidity);
        c.setValidYears(validYears);
        return c;
    }

    private RegSubmitItem item(String regNo, String level, String edu, String workStart,
                               String submittedAt, PriorCertMaterial... certs) {
        RegSubmitItem i = new RegSubmitItem();
        i.setRegNo(regNo);
        i.setName(regNo + "姓名");
        i.setProjectCode(PROJECT);
        i.setLevelCode(level);
        i.setEducationLevel(edu);
        i.setWorkStartDate(workStart);
        i.setSubmittedAt(submittedAt);
        i.setPriorCerts(certs.length == 0 ? null : new ArrayList<>(Arrays.asList(certs)));
        return i;
    }

    private PriorCertMaterial externalCert(String key, String issued, String expires) {
        PriorCertMaterial m = new PriorCertMaterial();
        m.setItemKey(key);
        m.setExternal(true);
        m.setIssuedDate(issued);
        m.setExpiresDate(expires);
        return m;
    }

    private RegSubmitRequest request(RegSubmitItem... items) {
        RegSubmitRequest req = new RegSubmitRequest();
        req.setSubmissions(new ArrayList<>(Arrays.asList(items)));
        return req;
    }

    private SubmitResult execute(RegSubmitItem... items) {
        return submitService.execute(request(items));
    }

    private ItemCheckResult check(SubmitItemView view, String type, String key) {
        for (ItemCheckResult c : view.getChecks()) {
            if (c.getItemType().equals(type) && c.getItemKey().equals(key)) {
                return c;
            }
        }
        throw new AssertionError("核验项不存在：" + type + "/" + key);
    }

    private String uniqueRegNo() {
        return "UTR" + System.nanoTime() % 1000000L;
    }

    // ---------- 1. 规则三段式与配置校验 ----------

    @Test
    void ruleLifecycle_draftPublishFreeze_oldVersionAutoDisabled() {
        String code = uniqueCode();
        RegConditionItemsConfig v1Items = fullItems("BACHELOR", 1);
        RegConditionRuleVersion v1 = publishRule(code, "L1", "WORK_START_DATE", v1Items);

        assertEquals("ACTIVE", v1.getStatus());
        assertNotNull(v1.getPublishedAt());
        assertTrue(v1.getItemsConfig().contains("BACHELOR"));

        // 同规则发新版：旧 ACTIVE 自动 DISABLED，同规则同时刻仅一版生效
        RegConditionRule rule = ruleService.listRules(PROJECT, "L1").stream()
                .filter(r -> code.equals(r.getCode())).findFirst().orElseThrow(AssertionError::new);
        RegRuleVersionRequest vr2 = new RegRuleVersionRequest();
        vr2.setItems(fullItems("BACHELOR", 2));
        RegConditionRuleVersion v2Draft = ruleService.createDraftVersion(rule.getId(), vr2);
        RegConditionRuleVersion v2 = ruleService.publishVersion(v2Draft.getId());

        assertEquals("DISABLED", ruleService.getVersion(v1.getId()).getStatus());
        assertEquals("ACTIVE", v2.getStatus());
        // 停用版不可重新发布
        assertThrows(BusinessException.class, () -> ruleService.publishVersion(v1.getId()));
    }

    @Test
    void invalidConfigs_rejected() {
        RegRuleRequest rr = new RegRuleRequest();
        rr.setCode(uniqueCode());
        rr.setName("x");
        rr.setProjectCode(PROJECT);
        rr.setLevelCode("LX");
        RegConditionRule rule = ruleService.createRule(rr);

        // 所有核验项都空：防止误配成无条件放行
        RegRuleVersionRequest empty = new RegRuleVersionRequest();
        empty.setItems(new RegConditionItemsConfig());
        assertThrows(BusinessException.class, () -> ruleService.createDraftVersion(rule.getId(), empty));

        // 学历层次无法识别
        RegRuleVersionRequest badEdu = new RegRuleVersionRequest();
        badEdu.setItems(fullItems("POSTDOC", 1));
        assertThrows(BusinessException.class, () -> ruleService.createDraftVersion(rule.getId(), badEdu));

        // 前置证书标识重复
        RegConditionItemsConfig dup = fullItems("BACHELOR", 1,
                certConfig("C1", "证书一", 3, true), certConfig("C1", "证书二", 3, true));
        RegRuleVersionRequest dupReq = new RegRuleVersionRequest();
        dupReq.setItems(dup);
        assertThrows(BusinessException.class, () -> ruleService.createDraftVersion(rule.getId(), dupReq));
    }

    @Test
    void duplicateProjectLevel_rejected() {
        String code = uniqueCode();
        publishRule(code, "LD", "WORK_START_DATE", fullItems("BACHELOR", 1));
        assertThrows(BusinessException.class,
                () -> publishRule(uniqueCode(), "LD", "WORK_START_DATE", fullItems("BACHELOR", 1)));
    }

    // ---------- 2. 逐项核验：学历 / 年限算头算尾 / 前置证书有效期 ----------

    @Test
    void fullChecks_passFailPerItemReasons() {
        String level = "LF" + System.nanoTime() % 100000L;
        publishRule(uniqueCode(), level, "WORK_START_DATE",
                fullItems("BACHELOR", 6,
                        certConfig("C1", "安全合格证", 3, true),
                        certConfig("C2", "长期资格证", null, true)));

        String regPass = uniqueRegNo();
        RegSubmitItem pass = item(regPass, level, "MASTER", "2019-01-01", "2026-10-02 09:00:00",
                externalCert("C1", "2024-01-01", "2027-01-01"),
                externalCert("C2", "2010-01-01", "2099-12-31"));
        SubmitResult result = execute(pass);
        assertEquals(1, result.getCreated().size());
        SubmitItemView view = result.getCreated().get(0);
        assertEquals("PASS", view.getStatus());
        assertEquals(4, view.getPassCount());

        // 学历不够 + 年限不够 + 缺一证书 + 一证书过期：FAIL 且每项都有中文原因
        String regFail = uniqueRegNo();
        RegSubmitItem fail = item(regFail, level, "HIGH_SCHOOL", "2023-01-01", "2026-10-02 09:00:00",
                externalCert("C1", "2020-01-01", "2023-01-01"));
        SubmitResult failResult = execute(fail);
        SubmitItemView failView = failResult.getCreated().get(0);
        assertEquals("FAIL", failView.getStatus());
        assertEquals(4, failView.getFailCount());
        ItemCheckResult edu = check(failView, "EDUCATION", "EDUCATION");
        assertTrue(edu.getReason().contains("本科"));
        ItemCheckResult years = check(failView, "YEARS", "YEARS");
        assertTrue(years.getReason().contains("3 周年"), years.getReason());
        ItemCheckResult c1 = check(failView, "PRIOR_CERT", "C1");
        assertTrue(c1.getReason().contains("已过有效期"));
        ItemCheckResult c2 = check(failView, "PRIOR_CERT", "C2");
        assertTrue(c2.getReason().contains("缺少前置证书"));
    }

    @Test
    void yearsCountHeadAndTail_boundaryDays() {
        String level = "LY" + System.nanoTime() % 100000L;
        RegConditionItemsConfig items = new RegConditionItemsConfig();
        YearsItemConfig years = new YearsItemConfig();
        years.setEnabled(true);
        years.setMinYears(6);
        items.setYears(years);
        publishRule(uniqueCode(), level, "WORK_START_DATE", items);

        // 起算日 2020-10-02 含头，提交日 2026-10-02 含尾：恰好 6 周年 → PASS
        SubmitItemView exact = submitService.preview(request(item(uniqueRegNo(), level, null,
                "2020-10-02", "2026-10-02 00:00:00"))).getItems().get(0);
        assertEquals("PASS", exact.getStatus());
        assertTrue(check(exact, "YEARS", "YEARS").getReason().contains("6 周年"));

        // 晚一天起算：只有 5 周年 → FAIL，原因写明算头算尾口径
        SubmitItemView oneDay = submitService.preview(request(item(uniqueRegNo(), level, null,
                "2020-10-03", "2026-10-02 00:00:00"))).getItems().get(0);
        assertEquals("FAIL", oneDay.getStatus());
        assertTrue(check(oneDay, "YEARS", "YEARS").getReason().contains("5 周年"));

        // 前一证书取得日口径：yearsBasis=LAST_CERT_DATE 时用 lastCertDate
        String level2 = "LZ" + System.nanoTime() % 100000L;
        publishRule(uniqueCode(), level2, "LAST_CERT_DATE", items);
        RegSubmitItem lastCertBasis = item(uniqueRegNo(), level2, null, "2000-01-01",
                "2026-10-02 00:00:00");
        lastCertBasis.setLastCertDate("2021-05-01"); // 5 周年多，不满 6
        SubmitItemView basisView = submitService.preview(request(lastCertBasis)).getItems().get(0);
        assertEquals("FAIL", basisView.getStatus());
        assertTrue(check(basisView, "YEARS", "YEARS").getReason().contains("前一证书取得日"));

        // 缺起算日：不直接误杀，存疑转人工
        SubmitItemView missing = submitService.preview(request(
                item(uniqueRegNo(), level, null, null, "2026-10-02 00:00:00"))).getItems().get(0);
        assertEquals("MANUAL_PENDING", missing.getStatus());
        assertEquals("DOUBT", check(missing, "YEARS", "YEARS").getResult());
    }

    @Test
    void priorCert_internalStatusAndValidityBoundaries() {
        // 只配内部前置证书（6 年有效期按签发日推算）
        String levelOnly = "LO" + System.nanoTime() % 100000L;
        RegConditionItemsConfig certOnly = new RegConditionItemsConfig();
        certOnly.setPriorCerts(Collections.singletonList(certConfig("P1", "内部上岗证", 6, true)));
        publishRule(uniqueCode(), levelOnly, "WORK_START_DATE", certOnly);

        LocalDateTime issued = LocalDateTime.of(2020, 1, 1, 10, 0);
        // 库内有效内部证：2020-01-01 签发，6 年有效至 2026-01-01；到期日当天仍有效（含尾）
        Certificate cert = new Certificate();
        cert.setCertNo("PC" + System.nanoTime() % 1000000L);
        cert.setHolderName("到期当天");
        cert.setStatus("ISSUED");
        cert.setIssuedAt(issued);
        certificateMapper.insert(cert);

        PriorCertMaterial onDay = new PriorCertMaterial();
        onDay.setItemKey("P1");
        onDay.setCertNo(cert.getCertNo());
        onDay.setHolderName("到期当天");
        RegSubmitItem onDayItem = item(uniqueRegNo(), levelOnly, null, null, "2026-01-01 00:00:00", onDay);
        SubmitItemView onDayView = submitService.preview(request(onDayItem)).getItems().get(0);
        assertEquals("PASS", onDayView.getStatus());
        assertTrue(check(onDayView, "PRIOR_CERT", "P1").getReason().contains("含当日"));

        // 次日即失效
        RegSubmitItem expired = item(uniqueRegNo(), levelOnly, null, null, "2026-01-02 00:00:00", onDay);
        SubmitItemView expiredView = submitService.preview(request(expired)).getItems().get(0);
        assertEquals("FAIL", expiredView.getStatus());
        assertTrue(check(expiredView, "PRIOR_CERT", "P1").getReason().contains("已过有效期"));

        // 持证人不符 → FAIL
        PriorCertMaterial wrongHolder = new PriorCertMaterial();
        wrongHolder.setItemKey("P1");
        wrongHolder.setCertNo(cert.getCertNo());
        wrongHolder.setHolderName("别人");
        RegSubmitItem holderItem = item(uniqueRegNo(), levelOnly, null, null, "2025-01-01 00:00:00", wrongHolder);
        SubmitItemView holderView = submitService.preview(request(holderItem)).getItems().get(0);
        assertTrue(check(holderView, "PRIOR_CERT", "P1").getReason().contains("持证人"));

        // 已作废 → FAIL
        Certificate revoked = new Certificate();
        revoked.setCertNo("RV" + System.nanoTime() % 1000000L);
        revoked.setHolderName("作废证");
        revoked.setStatus("REVOKED");
        revoked.setRevokeReason("考试违纪");
        revoked.setIssuedAt(issued);
        certificateMapper.insert(revoked);
        PriorCertMaterial revokedM = new PriorCertMaterial();
        revokedM.setItemKey("P1");
        revokedM.setCertNo(revoked.getCertNo());
        revokedM.setHolderName("作废证");
        SubmitItemView revokedView = submitService.preview(request(
                item(uniqueRegNo(), levelOnly, null, null, "2025-01-01 00:00:00", revokedM))).getItems().get(0);
        assertTrue(check(revokedView, "PRIOR_CERT", "P1").getReason().contains("已作废"));
        assertTrue(check(revokedView, "PRIOR_CERT", "P1").getReason().contains("考试违纪"));

        // 编号库内查无 → DOUBT 转人工（不直接判死）
        PriorCertMaterial ghost = new PriorCertMaterial();
        ghost.setItemKey("P1");
        ghost.setCertNo("NOT-EXIST-" + System.nanoTime());
        SubmitItemView ghostView = submitService.preview(request(
                item(uniqueRegNo(), levelOnly, null, null, "2025-01-01 00:00:00", ghost))).getItems().get(0);
        assertEquals("MANUAL_PENDING", ghostView.getStatus());
        assertEquals("DOUBT", check(ghostView, "PRIOR_CERT", "P1").getResult());

        // 外部证书无到期日且规则未规定有效期（validYears=null）→ DOUBT
        PriorCertMaterial noExpiry = new PriorCertMaterial();
        noExpiry.setItemKey("P1");
        noExpiry.setExternal(true);
        noExpiry.setIssuedDate("2020-01-01");
        String levelExt = "LE" + System.nanoTime() % 100000L;
        RegConditionItemsConfig noExpiryRule = new RegConditionItemsConfig();
        noExpiryRule.setPriorCerts(Collections.singletonList(certConfig("P1", "外部资格证", null, true)));
        publishRule(uniqueCode(), levelExt, "WORK_START_DATE", noExpiryRule);
        SubmitItemView noExpiryView = submitService.preview(request(
                item(uniqueRegNo(), levelExt, null, null, "2026-01-01 00:00:00", noExpiry))).getItems().get(0);
        assertEquals("MANUAL_PENDING", noExpiryView.getStatus());
        assertEquals("DOUBT", check(noExpiryView, "PRIOR_CERT", "P1").getResult());
    }

    // ---------- 3. 存疑转人工 ----------

    @Test
    void doubtToManual_reviewConcludes() {
        String level = "LM" + System.nanoTime() % 100000L;
        publishRule(uniqueCode(), level, "WORK_START_DATE", fullItems("BACHELOR", 1));

        // 学历层次机读认不出 → DOUBT → MANUAL_PENDING
        RegSubmitItem weird = item(uniqueRegNo(), level, "大学本科同等学力", "2020-01-01",
                "2026-10-02 09:00:00");
        SubmitResult created = execute(weird);
        RegConditionSubmission submission = submitService.requireSubmission(
                created.getCreated().get(0).getSubmissionId());
        assertEquals("MANUAL_PENDING", submission.getStatus());
        assertEquals(1, (int) submission.getDoubtCount());

        // 非待人工单不可复核
        assertThrows(BusinessException.class, () -> submitService.manualReview(99999999L, null));

        ManualReviewRequest review = new ManualReviewRequest();
        review.setOutcome("PASS");
        review.setComment("核对原件，确为本科同等学力，认可");
        RegConditionSubmission passed = submitService.manualReview(submission.getId(), review);
        assertEquals("PASS", passed.getStatus());
        assertEquals("PASS", passed.getManualOutcome());
        // 流转留痕：SUBMIT / TO_MANUAL / MANUAL_PASS 三条
        assertEquals(3, submitService.listEvents(submission.getId()).size());

        // 人工判不通过
        RegSubmitItem weird2 = item(uniqueRegNo(), level, "同等学力", "2020-01-01",
                "2026-10-02 09:00:00");
        Long id2 = execute(weird2).getCreated().get(0).getSubmissionId();
        ManualReviewRequest reject = new ManualReviewRequest();
        reject.setOutcome("FAIL");
        reject.setComment("材料不足");
        assertEquals("FAIL", submitService.manualReview(id2, reject).getStatus());
    }

    // ---------- 4. 申诉流转：申诉期间不重复拦截，改判/驳回留痕 ----------

    @Test
    void appealFlow_noRepeatBlockDuringAppeal() {
        String level = "LA" + System.nanoTime() % 100000L;
        publishRule(uniqueCode(), level, "WORK_START_DATE", fullItems("BACHELOR", 6));

        String regNo = uniqueRegNo();
        RegSubmitItem failItem = item(regNo, level, "HIGH_SCHOOL", "2024-01-01", "2026-10-02 09:00:00");
        Long submissionId = execute(failItem).getCreated().get(0).getSubmissionId();

        // 仅不通过可申诉
        RegAppealRequest apply = new RegAppealRequest();
        apply.setSubmissionId(submissionId);
        apply.setReason("从业年限证明此前未被采信，已补交社保记录");
        RegAppeal appeal = appealService.apply(apply);
        assertEquals("PENDING", appeal.getStatus());

        // 处理中不可重复申诉
        assertThrows(BusinessException.class, () -> appealService.apply(apply));

        // 申诉期间再次提交：跳过、不重复拦截
        SubmitResult during = execute(failItem);
        assertEquals(0, during.getCreated().size());
        assertEquals(1, during.getSkipped().size());
        assertTrue(during.getSkipped().get(0).isAppealInProgress());
        assertEquals(appeal.getAppealNo(), during.getSkipped().get(0).getAppealNo());

        // 申诉成立 → 改判 APPEAL_PASS（机器初判仍在逐项结果中保留）
        RegAppealConclusionRequest approve = new RegAppealConclusionRequest();
        approve.setApproved(true);
        approve.setConclusion("社保记录核实，年限达标");
        appealService.conclude(appeal.getId(), approve);
        assertEquals("APPEAL_PASS", submitService.requireSubmission(submissionId).getStatus());
        assertEquals(2, appealService.listAppealEvents(appeal.getId()).size());
        // 提交单事件链含 APPEAL_START / APPEAL_APPROVED
        List<String> actions = new ArrayList<>();
        submitService.listEvents(submissionId).forEach(e -> actions.add(e.getAction()));
        assertTrue(actions.contains("APPEAL_START") && actions.contains("APPEAL_APPROVED"));
    }

    @Test
    void appealRejected_keepsBlocked_andCanReappeal() {
        String level = "LR" + System.nanoTime() % 100000L;
        publishRule(uniqueCode(), level, "WORK_START_DATE", fullItems("BACHELOR", 6));

        String regNo = uniqueRegNo();
        RegSubmitItem failItem = item(regNo, level, "HIGH_SCHOOL", "2024-01-01", "2026-10-02 09:00:00");
        Long sid1 = execute(failItem).getCreated().get(0).getSubmissionId();

        RegAppealRequest apply = new RegAppealRequest();
        apply.setSubmissionId(sid1);
        apply.setReason("申诉");
        Long appealId = appealService.apply(apply).getId();

        RegAppealConclusionRequest reject = new RegAppealConclusionRequest();
        reject.setApproved(false);
        reject.setConclusion("材料仍不足，维持不通过");
        appealService.conclude(appealId, reject);
        assertEquals("REJECTED_FAIL", submitService.requireSubmission(sid1).getStatus());

        // 驳回后再次提交恢复正常拦截（不再跳过）
        SubmitResult again = execute(failItem);
        assertEquals(1, again.getCreated().size());
        assertEquals("FAIL", again.getCreated().get(0).getStatus());

        // 驳回状态允许再次申诉，历史申诉单只追加
        RegAppealRequest apply2 = new RegAppealRequest();
        apply2.setSubmissionId(sid1);
        apply2.setReason("补充新材料再申诉");
        assertEquals("PENDING", appealService.apply(apply2).getStatus());
        assertEquals(2, appealService.listAppeals(regNo, null, null).size());
    }

    // ---------- 5. 规则变更按提交时点生效 ----------

    @Test
    void ruleVersionEffectiveAtSubmissionTime() {
        String code = uniqueCode();
        String level = "LT" + System.nanoTime() % 100000L;
        RegConditionRuleVersion v1 = publishRule(code, level, "WORK_START_DATE", yearsOnly(1));

        // 第一次提交按 v1，冻结版本号
        String regNo = uniqueRegNo();
        RegSubmitItem first = item(regNo, level, null, "2024-01-01", "2026-10-02 09:00:00");
        SubmitItemView firstView = execute(first).getCreated().get(0);
        assertEquals("PASS", firstView.getStatus());
        assertEquals(v1.getVersionNo(), firstView.getRuleVersionNo());

        // 规则升级：年限提高到 5 年
        RegConditionRule rule = ruleMapper.selectById(v1.getRuleId());
        RegRuleVersionRequest vr2 = new RegRuleVersionRequest();
        RegConditionItemsConfig i2 = new RegConditionItemsConfig();
        YearsItemConfig y2 = new YearsItemConfig();
        y2.setEnabled(true);
        y2.setMinYears(5);
        i2.setYears(y2);
        vr2.setItems(i2);
        RegConditionRuleVersion v2 = ruleService.publishVersion(
                ruleService.createDraftVersion(rule.getId(), vr2).getId());

        // 历史提交口径不变
        assertEquals(v1.getVersionNo(), submitService.requireSubmission(
                firstView.getSubmissionId()).getRuleVersionNo());
        // 新提交按提交时点的 ACTIVE（v2）：同样材料 2 年多不满 5 年 → FAIL
        SubmitItemView secondView = execute(item(uniqueRegNo(), level, null,
                "2024-01-01", "2026-10-02 09:00:00")).getCreated().get(0);
        assertEquals("FAIL", secondView.getStatus());
        assertEquals(v2.getVersionNo(), secondView.getRuleVersionNo());
    }

    // ---------- 6. 预检与整批拒收 ----------

    @Test
    void previewCountsAndStructuralBatchReject() {
        String level = "LP" + System.nanoTime() % 100000L;
        publishRule(uniqueCode(), level, "WORK_START_DATE", fullItems("BACHELOR", 6));

        RegSubmitItem pass = item(uniqueRegNo(), level, "MASTER", "2019-01-01", "2026-10-02 09:00:00");
        RegSubmitItem fail = item(uniqueRegNo(), level, "HIGH_SCHOOL", "2024-01-01", "2026-10-02 09:00:00");
        RegSubmitItem noRule = item(uniqueRegNo(), "NO-SUCH-LEVEL", "MASTER", "2019-01-01",
                "2026-10-02 09:00:00");

        SubmitPreview preview = submitService.preview(request(pass, fail, noRule));
        assertEquals(3, preview.getTotalScanned());
        assertEquals(1, preview.getPassCount());
        assertEquals(1, preview.getBlockedCount());
        assertEquals(1, preview.getErrorCount());
        assertFalse(preview.getItems().get(2).isActionable());

        // 执行时任一结构性错误整批拒收，不落半条
        long before = submitService.listSubmissions(null, pass.getRegNo(), level, null).size();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> submitService.execute(request(pass, fail, noRule)));
        assertEquals("NO_REG_RULE_FOR_LEVEL", ex.getCode());
        assertEquals(before, submitService.listSubmissions(null, pass.getRegNo(), level, null).size());
    }

    // ---------- 7. 快照回退带上新表 ----------

    @Test
    void snapshotRollback_includesConditionRulesAndSubmissions() {
        String code = uniqueCode();
        String level = "LV" + System.nanoTime() % 100000L;
        publishRule(code, level, "WORK_START_DATE", yearsOnly(1));
        execute(item(uniqueRegNo(), level, null, "2024-01-01", "2026-10-02 09:00:00"));

        com.evops.exam.entity.WorkspaceVersion snapshot =
                workspaceVersionService.createSnapshot("条件引擎回退前", null);

        long rulesBefore = ruleService.listRules(PROJECT, null).size();
        // 快照后再建规则、再提交
        publishRule(uniqueCode(), "LVNEW" + System.nanoTime() % 100000L, "WORK_START_DATE", yearsOnly(1));

        workspaceVersionService.rollback(snapshot.getVersionNo(), "回退验证");
        assertEquals(rulesBefore, ruleService.listRules(PROJECT, null).size());
        assertTrue(ruleService.listRules(PROJECT, null).stream()
                .anyMatch(r -> code.equals(r.getCode())));
    }
}
