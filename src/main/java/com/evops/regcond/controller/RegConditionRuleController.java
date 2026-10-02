package com.evops.regcond.controller;

import com.evops.common.ApiResponse;
import com.evops.regcond.dto.RegRuleRequest;
import com.evops.regcond.dto.RegRuleVersionRequest;
import com.evops.regcond.entity.RegConditionRule;
import com.evops.regcond.entity.RegConditionRuleVersion;
import com.evops.regcond.service.RegConditionRuleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 条件规则模块：按项目+级别建档，版本带状态（草稿/生效/停用），核验项配置发布即冻结。
 */
@RestController
@RequestMapping("/api/reg-condition-rules")
public class RegConditionRuleController {

    private final RegConditionRuleService ruleService;

    public RegConditionRuleController(RegConditionRuleService ruleService) {
        this.ruleService = ruleService;
    }

    // ---------- 规则模块 ----------

    @PostMapping
    public ApiResponse<RegConditionRule> createRule(@Valid @RequestBody RegRuleRequest request) {
        return ApiResponse.ok(ruleService.createRule(request));
    }

    @GetMapping
    public ApiResponse<List<RegConditionRule>> listRules(@RequestParam(required = false) String projectCode,
                                                         @RequestParam(required = false) String levelCode) {
        return ApiResponse.ok(ruleService.listRules(projectCode, levelCode));
    }

    @GetMapping("/{ruleId}")
    public ApiResponse<RegConditionRule> getRule(@PathVariable Long ruleId) {
        return ApiResponse.ok(ruleService.getRule(ruleId));
    }

    // ---------- 规则版本 ----------

    /** 新建草稿版本（带学历/年限/前置证书核验项，发布时冻结）。 */
    @PostMapping("/{ruleId}/versions")
    public ApiResponse<RegConditionRuleVersion> createDraft(@PathVariable Long ruleId,
                                                            @Valid @RequestBody RegRuleVersionRequest request) {
        return ApiResponse.ok(ruleService.createDraftVersion(ruleId, request));
    }

    @GetMapping("/{ruleId}/versions")
    public ApiResponse<List<RegConditionRuleVersion>> listVersions(@PathVariable Long ruleId) {
        return ApiResponse.ok(ruleService.listVersions(ruleId));
    }

    @GetMapping("/versions/{versionId}")
    public ApiResponse<Map<String, Object>> getVersion(@PathVariable Long versionId) {
        RegConditionRuleVersion version = ruleService.getVersion(versionId);
        return ApiResponse.ok(ruleService.versionSummary(version));
    }

    /** 发布：草稿→生效，自动停用同一规则旧生效版。 */
    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<RegConditionRuleVersion> publish(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.publishVersion(versionId));
    }

    /** 人工停用生效版本。 */
    @PostMapping("/versions/{versionId}/disable")
    public ApiResponse<RegConditionRuleVersion> disable(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.disableVersion(versionId));
    }
}
