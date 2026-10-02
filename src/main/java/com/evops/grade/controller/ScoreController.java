package com.evops.grade.controller;

import com.evops.common.ApiResponse;
import com.evops.grade.dto.ScoreImportRequest;
import com.evops.grade.entity.ExamScore;
import com.evops.grade.entity.ScoreChangeLog;
import com.evops.grade.service.ScoreService;
import com.evops.grade.vo.ScoreImportPreview;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 成绩：批量录入先预检后执行，卷面分按项目生效规则版本定级；改分留痕可查。
 */
@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    private final ScoreService scoreService;

    public ScoreController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    /** 录入预检：逐条列可录入/跳过（含定级结果与规则版本）。 */
    @PostMapping("/import/preview")
    public ApiResponse<ScoreImportPreview> preview(@Valid @RequestBody ScoreImportRequest request) {
        return ApiResponse.ok(scoreService.previewImport(request));
    }

    /** 录入执行：任一不通过整批拒收。 */
    @PostMapping("/import")
    public ApiResponse<List<ExamScore>> importScores(@Valid @RequestBody ScoreImportRequest request) {
        return ApiResponse.ok(scoreService.importScores(request));
    }

    @GetMapping("/batch/{batchId}")
    public ApiResponse<List<ExamScore>> listByBatch(@PathVariable Long batchId) {
        return ApiResponse.ok(scoreService.listByBatch(batchId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExamScore> get(@PathVariable Long id) {
        return ApiResponse.ok(scoreService.getScore(id));
    }

    /** 改分日志：可按批次查；不传 batchId 查全部（只追加留痕）。 */
    @GetMapping("/change-logs")
    public ApiResponse<List<ScoreChangeLog>> changeLogs(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(scoreService.listChangeLogs(batchId));
    }

    @GetMapping("/{id}/change-logs")
    public ApiResponse<List<ScoreChangeLog>> changeLogsOfScore(@PathVariable Long id) {
        return ApiResponse.ok(scoreService.listChangeLogsByScore(id));
    }
}
