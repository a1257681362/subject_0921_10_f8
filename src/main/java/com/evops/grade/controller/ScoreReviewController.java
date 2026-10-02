package com.evops.grade.controller;

import com.evops.common.ApiResponse;
import com.evops.grade.dto.ReviewApplyRequest;
import com.evops.grade.dto.ReviewConclusionRequest;
import com.evops.grade.entity.ScoreReview;
import com.evops.grade.service.ScoreReviewService;
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
 * 成绩复核：窗口期内提交、结论回写通知、超窗自动关闭。
 */
@RestController
@RequestMapping("/api/reviews")
public class ScoreReviewController {

    private final ScoreReviewService reviewService;

    public ScoreReviewController(ScoreReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** 学员提交复核（仅批次窗口 OPEN 时可提交）。 */
    @PostMapping("/apply")
    public ApiResponse<ScoreReview> apply(@Valid @RequestBody ReviewApplyRequest request) {
        return ApiResponse.ok(reviewService.apply(request));
    }

    /** 复核结论：通过改分（按原定级版本重算等级并留痕）或驳回维持原分，均通知。 */
    @PostMapping("/{id}/conclusion")
    public ApiResponse<ScoreReview> conclude(@PathVariable Long id,
                                             @Valid @RequestBody ReviewConclusionRequest request) {
        return ApiResponse.ok(reviewService.conclude(id, request));
    }

    /** 手动执行超窗关闭（定时任务每分钟自动跑同一逻辑）。 */
    @PostMapping("/close-expired")
    public ApiResponse<Map<String, Object>> closeExpired() {
        return ApiResponse.ok(reviewService.closeExpired());
    }

    @GetMapping("/{id}")
    public ApiResponse<ScoreReview> get(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.getReview(id));
    }

    @GetMapping("/batch/{batchId}")
    public ApiResponse<List<ScoreReview>> listByBatch(@PathVariable Long batchId,
                                                      @RequestParam(required = false) String status) {
        return ApiResponse.ok(reviewService.listByBatch(batchId, status));
    }

    @GetMapping
    public ApiResponse<List<ScoreReview>> listAll(@RequestParam(required = false) String status) {
        return ApiResponse.ok(reviewService.listAll(status));
    }
}
