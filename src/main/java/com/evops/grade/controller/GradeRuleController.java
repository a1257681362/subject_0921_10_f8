package com.evops.grade.controller;

import com.evops.common.ApiResponse;
import com.evops.grade.dto.GradeRuleRequest;
import com.evops.grade.dto.GradeRuleVersionRequest;
import com.evops.grade.dto.GradeTrialRequest;
import com.evops.grade.entity.GradeRule;
import com.evops.grade.entity.GradeRuleVersion;
import com.evops.grade.service.GradeRuleService;
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
 * 等级规则模块：规则按项目建档，版本带状态（草稿/生效/停用），卷面分按版本分数段定级。
 */
@RestController
@RequestMapping("/api/grade-rules")
public class GradeRuleController {

    private final GradeRuleService ruleService;

    public GradeRuleController(GradeRuleService ruleService) {
        this.ruleService = ruleService;
    }

    // ---------- 规则模块 ----------

    @PostMapping
    public ApiResponse<GradeRule> createRule(@Valid @RequestBody GradeRuleRequest request) {
        return ApiResponse.ok(ruleService.createRule(request));
    }

    @GetMapping
    public ApiResponse<List<GradeRule>> listRules(@RequestParam(required = false) String projectCode) {
        return ApiResponse.ok(ruleService.listRules(projectCode));
    }

    @GetMapping("/{ruleId}")
    public ApiResponse<GradeRule> getRule(@PathVariable Long ruleId) {
        return ApiResponse.ok(ruleService.getRule(ruleId));
    }

    // ---------- 规则版本 ----------

    /** 新建草稿版本（带分数段，发布时冻结）。 */
    @PostMapping("/{ruleId}/versions")
    public ApiResponse<GradeRuleVersion> createDraft(@PathVariable Long ruleId,
                                                     @Valid @RequestBody GradeRuleVersionRequest request) {
        return ApiResponse.ok(ruleService.createDraftVersion(ruleId, request));
    }

    @GetMapping("/{ruleId}/versions")
    public ApiResponse<List<GradeRuleVersion>> listVersions(@PathVariable Long ruleId) {
        return ApiResponse.ok(ruleService.listVersions(ruleId));
    }

    @GetMapping("/versions/{versionId}")
    public ApiResponse<GradeRuleVersion> getVersion(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.getVersion(versionId));
    }

    /** 发布：草稿→生效，自动停用同一规则旧生效版。 */
    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<GradeRuleVersion> publish(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.publishVersion(versionId));
    }

    /** 人工停用生效版本。 */
    @PostMapping("/versions/{versionId}/disable")
    public ApiResponse<GradeRuleVersion> disable(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.disableVersion(versionId));
    }

    /** 按版本试算等级，不写成绩。 */
    @PostMapping("/versions/{versionId}/trial")
    public ApiResponse<Map<String, Object>> trial(@PathVariable Long versionId,
                                                  @Valid @RequestBody GradeTrialRequest request) {
        return ApiResponse.ok(ruleService.trial(versionId, request.getScore()));
    }
}
