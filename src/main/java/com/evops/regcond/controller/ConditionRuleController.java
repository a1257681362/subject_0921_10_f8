package com.evops.regcond.controller;

import com.evops.common.ApiResponse;
import com.evops.regcond.dto.ConditionRuleRequest;
import com.evops.regcond.dto.ConditionRuleVersionRequest;
import com.evops.regcond.entity.ConditionRule;
import com.evops.regcond.entity.ConditionRuleVersion;
import com.evops.regcond.service.ConditionRuleService;
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
 * 条件规则模块：规则按项目+级别建档，版本带状态（草稿/生效/停用），核验项发布即冻结。
 */
@RestController
@RequestMapping("/api/condition-rules")
public class ConditionRuleController {

    private final ConditionRuleService ruleService;

    public ConditionRuleController(ConditionRuleService ruleService) {
        this.ruleService = ruleService;
    }

    // ---------- 规则模块 ----------

    @PostMapping
    public ApiResponse<ConditionRule> createRule(@Valid @RequestBody ConditionRuleRequest request) {
        return ApiResponse.ok(ruleService.createRule(request));
    }

    @GetMapping
    public ApiResponse<List<ConditionRule>> listRules(@RequestParam(required = false) String projectCode,
                                                      @RequestParam(required = false) String levelCode) {
        return ApiResponse.ok(ruleService.listRules(projectCode, levelCode));
    }

    @GetMapping("/{ruleId}")
    public ApiResponse<ConditionRule> getRule(@PathVariable Long ruleId) {
        return ApiResponse.ok(ruleService.getRule(ruleId));
    }

    // ---------- 规则版本 ----------

    @PostMapping("/{ruleId}/versions")
    public ApiResponse<ConditionRuleVersion> createDraft(@PathVariable Long ruleId,
                                                         @Valid @RequestBody ConditionRuleVersionRequest request) {
        return ApiResponse.ok(ruleService.createDraftVersion(ruleId, request));
    }

    @GetMapping("/{ruleId}/versions")
    public ApiResponse<List<ConditionRuleVersion>> listVersions(@PathVariable Long ruleId) {
        return ApiResponse.ok(ruleService.listVersions(ruleId));
    }

    @GetMapping("/versions/{versionId}")
    public ApiResponse<ConditionRuleVersion> getVersion(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.getVersion(versionId));
    }

    /** 发布：草稿→生效，自动停用同一规则旧生效版。 */
    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<ConditionRuleVersion> publish(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.publishVersion(versionId));
    }

    /** 人工停用生效版本。 */
    @PostMapping("/versions/{versionId}/disable")
    public ApiResponse<ConditionRuleVersion> disable(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.disableVersion(versionId));
    }
}
