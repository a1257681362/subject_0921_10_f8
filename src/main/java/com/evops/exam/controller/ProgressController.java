package com.evops.exam.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.ApiResponse;
import com.evops.exam.entity.FinalConclusion;
import com.evops.exam.entity.StageResult;
import com.evops.exam.mapper.StageResultMapper;
import com.evops.exam.service.ConclusionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段结果与最终结论：分接口、分表保存。
 */
@RestController
@RequestMapping("/api/batches/{batchId}/progress")
public class ProgressController {

    private final StageResultMapper stageResultMapper;
    private final ConclusionService conclusionService;

    public ProgressController(StageResultMapper stageResultMapper,
                              ConclusionService conclusionService) {
        this.stageResultMapper = stageResultMapper;
        this.conclusionService = conclusionService;
    }

    /** 阶段结果：报名 / 编排 / 打证 / 发放每步一条，独立成表。 */
    @GetMapping("/stages")
    public ApiResponse<List<StageResult>> stages(@PathVariable Long batchId) {
        return ApiResponse.ok(stageResultMapper.selectList(new QueryWrapper<StageResult>()
                .eq("batch_id", batchId)
                .orderByAsc("executed_at", "id")));
    }

    /** 最终结论：办结后每批次一条。 */
    @PostMapping("/conclusion")
    public ApiResponse<FinalConclusion> conclude(@PathVariable Long batchId) {
        return ApiResponse.ok(conclusionService.conclude(batchId));
    }

    @GetMapping("/conclusion")
    public ApiResponse<FinalConclusion> conclusion(@PathVariable Long batchId) {
        return ApiResponse.ok(conclusionService.getByBatch(batchId));
    }

    /** 汇总：阶段结果与最终结论作为两个字段分开返回。 */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(@PathVariable Long batchId) {
        List<StageResult> stages = stageResultMapper.selectList(new QueryWrapper<StageResult>()
                .eq("batch_id", batchId)
                .orderByAsc("executed_at", "id"));
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("stageResults", stages);
        overview.put("finalConclusion", conclusionService.safeGetByBatch(batchId));
        return ApiResponse.ok(overview);
    }
}
