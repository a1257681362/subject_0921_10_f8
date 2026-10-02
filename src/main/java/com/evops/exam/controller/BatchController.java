package com.evops.exam.controller;

import com.evops.common.ApiResponse;
import com.evops.exam.dto.BatchRequest;
import com.evops.exam.dto.ReviewWindowRequest;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.service.ExamBatchService;
import com.evops.grade.service.ScoreReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final ExamBatchService batchService;
    private final ScoreReviewService reviewService;

    public BatchController(ExamBatchService batchService, ScoreReviewService reviewService) {
        this.batchService = batchService;
        this.reviewService = reviewService;
    }

    @PostMapping
    public ApiResponse<ExamBatch> create(@Valid @RequestBody BatchRequest request) {
        return ApiResponse.ok(batchService.createBatch(request));
    }

    @GetMapping
    public ApiResponse<List<ExamBatch>> list() {
        return ApiResponse.ok(batchService.listBatches());
    }

    /** 配置 / 调整复核窗口：起止跟批次配置走，不写死。 */
    @PostMapping("/{batchId}/review-window")
    public ApiResponse<ExamBatch> configureWindow(@PathVariable Long batchId,
                                                  @Valid @RequestBody ReviewWindowRequest request) {
        return ApiResponse.ok(batchService.configureReviewWindow(
                batchId, request.getStartAt(), request.getEndAt()));
    }

    /** 查看窗口起止、当前状态（NOT_CONFIGURED/NOT_OPENED/OPEN/CLOSED）与各状态复核计数。 */
    @GetMapping("/{batchId}/review-window")
    public ApiResponse<Map<String, Object>> window(@PathVariable Long batchId) {
        return ApiResponse.ok(reviewService.windowInfo(batchId));
    }
}
