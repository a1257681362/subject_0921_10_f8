package com.evops.mixalgo.controller;

import com.evops.common.ApiResponse;
import com.evops.mixalgo.dto.MixAlgorithmRequest;
import com.evops.mixalgo.dto.MixAlgorithmVersionRequest;
import com.evops.mixalgo.entity.MixAlgorithm;
import com.evops.mixalgo.entity.MixAlgorithmVersion;
import com.evops.mixalgo.service.MixAlgorithmService;
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
 * 混编算法模块（算法 + 版本 + 状态）。路径在 /api/** 下，自动走 HTTP Basic。
 */
@RestController
@RequestMapping("/api/mix-algorithms")
public class MixAlgorithmController {

    private final MixAlgorithmService mixAlgorithmService;

    public MixAlgorithmController(MixAlgorithmService mixAlgorithmService) {
        this.mixAlgorithmService = mixAlgorithmService;
    }

    @PostMapping
    public ApiResponse<MixAlgorithm> create(@Valid @RequestBody MixAlgorithmRequest request) {
        return ApiResponse.ok(mixAlgorithmService.createAlgorithm(request));
    }

    @GetMapping
    public ApiResponse<List<MixAlgorithm>> list(@RequestParam(required = false) String projectCode) {
        return ApiResponse.ok(mixAlgorithmService.listAlgorithms(projectCode));
    }

    @GetMapping("/{algorithmId}")
    public ApiResponse<MixAlgorithm> get(@PathVariable Long algorithmId) {
        return ApiResponse.ok(mixAlgorithmService.getAlgorithm(algorithmId));
    }

    /** 建草稿版本：参数校验通过后以 JSON 冻结。 */
    @PostMapping("/{algorithmId}/versions")
    public ApiResponse<MixAlgorithmVersion> createVersion(
            @PathVariable Long algorithmId,
            @Valid @RequestBody MixAlgorithmVersionRequest request) {
        return ApiResponse.ok(mixAlgorithmService.createDraftVersion(algorithmId, request));
    }

    @GetMapping("/{algorithmId}/versions")
    public ApiResponse<List<MixAlgorithmVersion>> listVersions(@PathVariable Long algorithmId) {
        return ApiResponse.ok(mixAlgorithmService.listVersions(algorithmId));
    }

    @GetMapping("/versions/{versionId}")
    public ApiResponse<MixAlgorithmVersion> getVersion(@PathVariable Long versionId) {
        return ApiResponse.ok(mixAlgorithmService.getVersion(versionId));
    }

    /** 发布：DRAFT → ACTIVE，旧生效版自动置 DISABLED。 */
    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<MixAlgorithmVersion> publish(@PathVariable Long versionId) {
        return ApiResponse.ok(mixAlgorithmService.publishVersion(versionId));
    }

    /** 人工停用生效版。 */
    @PostMapping("/versions/{versionId}/disable")
    public ApiResponse<MixAlgorithmVersion> disable(@PathVariable Long versionId) {
        return ApiResponse.ok(mixAlgorithmService.disableVersion(versionId));
    }
}
