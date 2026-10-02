package com.evops.cert.controller;

import com.evops.cert.dto.CertRuleRequest;
import com.evops.cert.dto.CertRuleVersionRequest;
import com.evops.cert.entity.CertNumberRule;
import com.evops.cert.entity.CertNumberRuleVersion;
import com.evops.cert.service.CertNumberRuleService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 编号规则模块：规则按项目建档，版本带状态（草稿/生效/停用），发号参数发布即冻结。
 * 规则调整只管新证：已发证书保留冻结版本号。
 */
@RestController
@RequestMapping("/api/cert-rules")
public class CertNumberRuleController {

    private final CertNumberRuleService ruleService;

    public CertNumberRuleController(CertNumberRuleService ruleService) {
        this.ruleService = ruleService;
    }

    // ---------- 规则模块 ----------

    @PostMapping
    public ApiResponse<CertNumberRule> createRule(@Valid @RequestBody CertRuleRequest request) {
        return ApiResponse.ok(ruleService.createRule(request));
    }

    @GetMapping
    public ApiResponse<List<CertNumberRule>> listRules(@RequestParam(required = false) String projectCode) {
        return ApiResponse.ok(ruleService.listRules(projectCode));
    }

    @GetMapping("/{ruleId}")
    public ApiResponse<CertNumberRule> getRule(@PathVariable Long ruleId) {
        return ApiResponse.ok(ruleService.getRule(ruleId));
    }

    // ---------- 规则版本 ----------

    /** 新建草稿版本（带发号参数，发布时冻结）。 */
    @PostMapping("/{ruleId}/versions")
    public ApiResponse<CertNumberRuleVersion> createDraft(@PathVariable Long ruleId,
                                                          @Valid @RequestBody CertRuleVersionRequest request) {
        return ApiResponse.ok(ruleService.createDraftVersion(ruleId, request));
    }

    @GetMapping("/{ruleId}/versions")
    public ApiResponse<List<CertNumberRuleVersion>> listVersions(@PathVariable Long ruleId) {
        return ApiResponse.ok(ruleService.listVersions(ruleId));
    }

    @GetMapping("/versions/{versionId}")
    public ApiResponse<CertNumberRuleVersion> getVersion(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.getVersion(versionId));
    }

    /** 发布：草稿→生效，自动停用同一规则旧生效版。 */
    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<CertNumberRuleVersion> publish(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.publishVersion(versionId));
    }

    /** 人工停用生效版本。 */
    @PostMapping("/versions/{versionId}/disable")
    public ApiResponse<CertNumberRuleVersion> disable(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.disableVersion(versionId));
    }
}
