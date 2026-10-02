package com.evops.exam.controller;

import com.evops.common.ApiResponse;
import com.evops.exam.dto.ArrangeRequest;
import com.evops.exam.entity.ArrangementRun;
import com.evops.exam.service.ArrangeService;
import com.evops.exam.vo.ActionPreview;
import com.evops.exam.vo.ActionResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/arrange")
public class ArrangeController {

    private final ArrangeService arrangeService;

    public ArrangeController(ArrangeService arrangeService) {
        this.arrangeService = arrangeService;
    }

    /** 批量动作第一步：展示每人考场 / 座位 / 证号方案、容量缺口及同校相邻统计，不落库。 */
    @PostMapping("/preview")
    public ApiResponse<ActionPreview> preview(@Valid @RequestBody ArrangeRequest request) {
        return ApiResponse.ok(arrangeService.previewArrange(request));
    }

    /** 批量动作第二步：确认后生成准考证，证号 / 座位撞号整批拒收。 */
    @PostMapping
    public ApiResponse<ActionResult> arrange(@Valid @RequestBody ArrangeRequest request) {
        return ApiResponse.ok(arrangeService.arrange(request));
    }

    /** 整批重排第一步：预检新混编方案（未打印 / 未发放才可重排），不落库。 */
    @PostMapping("/rearrange/preview")
    public ApiResponse<ActionPreview> previewRearrange(@Valid @RequestBody ArrangeRequest request) {
        return ApiResponse.ok(arrangeService.previewRearrange(request));
    }

    /**
     * 整批重排第二步：单一事务内删旧证、按新方案整批重建。
     * 要么全部用新座次，要么整批回滚，不留半新半旧。
     */
    @PostMapping("/rearrange")
    public ApiResponse<ActionResult> rearrange(@Valid @RequestBody ArrangeRequest request) {
        return ApiResponse.ok(arrangeService.rearrange(request));
    }

    /** 当前编排记录：混编版本、种子、座次与同校相邻统计（巡考抽查口径）。 */
    @GetMapping("/runs/current")
    public ApiResponse<ArrangementRun> currentRun(@org.springframework.web.bind.annotation.RequestParam Long batchId) {
        return ApiResponse.ok(arrangeService.getCurrentRun(batchId));
    }

    /** 批次历次编排 / 重排记录（只追加，旧座次保留可追溯）。 */
    @GetMapping("/runs")
    public ApiResponse<List<ArrangementRun>> listRuns(@org.springframework.web.bind.annotation.RequestParam Long batchId) {
        return ApiResponse.ok(arrangeService.listRuns(batchId));
    }

    /** 单次编排记录详情。 */
    @GetMapping("/runs/{runId}")
    public ApiResponse<ArrangementRun> getRun(@PathVariable Long runId) {
        return ApiResponse.ok(arrangeService.getRun(runId));
    }
}
