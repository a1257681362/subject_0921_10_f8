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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用人单位接入的接口边界：employer 仅能访问验真窗口，其余 API 返回 403；未认证 401。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CertVerifySecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private CertificateService certificateService;
    @Autowired
    private CertNumberRuleService ruleService;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String seedOne(String name) {
        String project = "SECPROJ";
        if (ruleService.listRules(project).isEmpty()) {
            CertRuleRequest rr = new CertRuleRequest();
            rr.setCode("SC");
            rr.setName("安全测试规则");
            rr.setProjectCode(project);
            CertNumberRule rule = ruleService.createRule(rr);

            CertRuleConfig config = new CertRuleConfig();
            config.setPrefix("SC");
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
        req.setCertBatch("01");
        req.setItems(Collections.singletonList(item));
        CertIssueResult r = certificateService.issue(req);
        return r.getItems().get(0).getCertNo();
    }

    @Test
    void employerCanOnlyAccessVerifyWindow() {
        String certNo = seedOne("张三");
        // 用 URI 模板变量，交给 RestTemplate 做编码，避免手工 URLEncoder 后被二次编码
        String path = "/api/cert-verify?certNo={no}&name={name}";

        // 未认证 → 401
        ResponseEntity<String> anon = restTemplate.getForEntity(
                url(path), String.class, certNo, "张三");
        assertEquals(HttpStatus.UNAUTHORIZED, anon.getStatusCode());

        // 用人单位 employer：验真窗口 200，返回有效，且不回隐私字段
        ResponseEntity<String> verify = restTemplate.withBasicAuth("employer", "employer")
                .getForEntity(url(path), String.class, certNo, "张三");
        assertEquals(HttpStatus.OK, verify.getStatusCode());
        assertTrue(verify.getBody().contains("\"valid\":true"));
        assertFalse(verify.getBody().contains("idNo"));

        // employer 访问内部管理接口 → 403
        ResponseEntity<String> certs = restTemplate.withBasicAuth("employer", "employer")
                .getForEntity(url("/api/certificates"), String.class);
        assertEquals(HttpStatus.FORBIDDEN, certs.getStatusCode());
        ResponseEntity<String> rules = restTemplate.withBasicAuth("employer", "employer")
                .getForEntity(url("/api/cert-rules"), String.class);
        assertEquals(HttpStatus.FORBIDDEN, rules.getStatusCode());

        // bootstrap 内部账号访问管理接口 → 200
        ResponseEntity<String> admin = restTemplate.withBasicAuth("bootstrap", "bootstrap")
                .getForEntity(url("/api/certificates"), String.class);
        assertEquals(HttpStatus.OK, admin.getStatusCode());
    }
}
