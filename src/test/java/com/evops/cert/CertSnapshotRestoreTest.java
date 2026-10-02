package com.evops.cert;

import com.evops.cert.dto.CertIssueItem;
import com.evops.cert.dto.CertIssueRequest;
import com.evops.cert.dto.CertRuleConfig;
import com.evops.cert.dto.CertRuleRequest;
import com.evops.cert.dto.CertRuleVersionRequest;
import com.evops.cert.entity.CertNumberRule;
import com.evops.cert.entity.CertNumberRuleVersion;
import com.evops.cert.service.CertNumberRuleService;
import com.evops.cert.service.CertificateService;
import com.evops.cert.vo.CertIssueResult;
import com.evops.common.BusinessException;
import com.evops.exam.service.WorkspaceVersionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 证书相关表纳入工作区快照 / 回退：回退后新增证书消失、快照内证书与规则保留、序列一并还原。
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CertSnapshotRestoreTest {

    @Autowired
    private WorkspaceVersionService workspaceVersionService;
    @Autowired
    private CertNumberRuleService ruleService;
    @Autowired
    private CertificateService certificateService;

    private String publishAndIssueOne(String project, String prefix, String name) {
        if (ruleService.listRules(project).isEmpty()) {
            CertRuleRequest rr = new CertRuleRequest();
            rr.setCode(prefix + "RULE");
            rr.setName("回退规则-" + project);
            rr.setProjectCode(project);
            CertNumberRule rule = ruleService.createRule(rr);

            CertRuleConfig config = new CertRuleConfig();
            config.setPrefix(prefix);
            config.setYearDigits(4);
            config.setBatchDigits(2);
            config.setSeqDigits(5);
            config.setCheckEnabled(true);
            CertRuleVersionRequest vr = new CertRuleVersionRequest();
            vr.setParams(config);
            CertNumberRuleVersion draft = ruleService.createDraftVersion(rule.getId(), vr);
            ruleService.publishVersion(draft.getId());
        }
        CertIssueItem item = new CertIssueItem();
        item.setHolderName(name);
        CertIssueRequest req = new CertIssueRequest();
        req.setProjectCode(project);
        req.setCertYear(2026);
        req.setCertBatch("07");
        req.setItems(Collections.singletonList(item));
        CertIssueResult r = certificateService.issue(req);
        return r.getItems().get(0).getCertNo();
    }

    @Test
    void rollbackRemovesNewCertsButKeepsSnapshot() {
        String project = "SNAP";
        // 快照点：一张已发证
        String firstNo = publishAndIssueOne(project, "SN", "快照证人");
        workspaceVersionService.createSnapshot("证书回退前", null);

        // 快照后再发一张
        String secondNo = publishAndIssueOne(project, "SN", "回退后应消失");
        assertEquals(2, certificateService.list(null, project, null, null).size());

        // 回退到快照
        workspaceVersionService.rollback(
                workspaceVersionService.listVersions(false).stream()
                        .filter(v -> "证书回退前".equals(v.getLabel()))
                        .findFirst().orElseThrow(IllegalStateException::new).getVersionNo(),
                "回退验证");

        // 快照内证书保留
        certificateService.getByCertNo(firstNo);
        // 快照后新发的证书随回退消失
        assertThrows(BusinessException.class, () -> certificateService.getByCertNo(secondNo));
        assertEquals(1, certificateService.list(null, project, null, null).size());
    }
}
