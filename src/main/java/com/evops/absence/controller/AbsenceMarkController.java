package com.evops.absence.controller;

import com.evops.absence.dto.MarkChangeRequest;
import com.evops.absence.dto.MarkRequest;
import com.evops.absence.dto.MarkReviewRequest;
import com.evops.absence.dto.ScanRequest;
import com.evops.absence.entity.AbsenceMark;
import com.evops.absence.entity.AbsenceMarkVersion;
import com.evops.absence.service.AbsenceMarkService;
import com.evops.absence.vo.ScanPreview;
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
 * 缺考标记（模块/版本/状态）：自动扫描与手动标记都走双人复核；
 * 已生效裁定可变更（回到待复核），版本链全程留痕。
 */
@RestController
@RequestMapping("/api/absence")
public class AbsenceMarkController {

    private final AbsenceMarkService markService;

    public AbsenceMarkController(AbsenceMarkService markService) {
        this.markService = markService;
    }

    /** 自动标缺考预检：按签到与违纪记录逐条列可标记/跳过。 */
    @PostMapping("/scan/preview")
    public ApiResponse<ScanPreview> previewScan(@Valid @RequestBody ScanRequest request) {
        return ApiResponse.ok(markService.previewScan(request));
    }

    /** 自动标缺考执行：生成待复核标记（幂等，已有在办/生效标记的跳过）。 */
    @PostMapping("/scan")
    public ApiResponse<List<AbsenceMark>> executeScan(@Valid @RequestBody ScanRequest request) {
        return ApiResponse.ok(markService.executeScan(request));
    }

    /** 手动标记：弃考（ABANDONED）等由人工登记，登记即待复核。 */
    @PostMapping("/marks")
    public ApiResponse<AbsenceMark> createMark(@Valid @RequestBody MarkRequest request) {
        return ApiResponse.ok(markService.createMark(request));
    }

    @GetMapping("/marks")
    public ApiResponse<List<AbsenceMark>> list(@RequestParam(required = false) Long batchId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String markType) {
        return ApiResponse.ok(markService.list(batchId, status, markType));
    }

    @GetMapping("/marks/{id}")
    public ApiResponse<AbsenceMark> get(@PathVariable Long id) {
        return ApiResponse.ok(markService.requireMark(id));
    }

    /** 版本链：标记 / 复核 / 驳回 / 变更全程只追加。 */
    @GetMapping("/marks/{id}/versions")
    public ApiResponse<List<AbsenceMarkVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(markService.versions(id));
    }

    /** 双人复核：复核人（X-Operator）须非经办人；确认才生效并通知本人。 */
    @PostMapping("/marks/{id}/review")
    public ApiResponse<AbsenceMark> review(@PathVariable Long id,
                                           @Valid @RequestBody MarkReviewRequest request) {
        return ApiResponse.ok(markService.review(id, request));
    }

    /** 变更裁定：仅已生效裁定可变更；变更回到待复核，确认后重算统计。 */
    @PostMapping("/marks/{id}/change")
    public ApiResponse<AbsenceMark> change(@PathVariable Long id,
                                           @Valid @RequestBody MarkChangeRequest request) {
        return ApiResponse.ok(markService.change(id, request));
    }
}
