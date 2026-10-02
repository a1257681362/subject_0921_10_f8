package com.evops.regcond.controller;

import com.evops.common.ApiResponse;
import com.evops.regcond.dto.ManualReviewRequest;
import com.evops.regcond.dto.RegSubmitRequest;
import com.evops.regcond.entity.RegConditionCheck;
import com.evops.regcond.entity.RegConditionSubmission;
import com.evops.regcond.entity.RegSubmissionEvent;
import com.evops.regcond.service.RegConditionSubmitService;
import com.evops.regcond.vo.SubmitPreview;
import com.evops.regcond.vo.SubmitResult;
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
 * 报名条件提交：两步预检；逐项核验、不通过逐条给原因、存疑转人工；规则版本按提交时点冻结。
 */
@RestController
@RequestMapping("/api/reg-conditions")
public class RegConditionSubmitController {

    private final RegConditionSubmitService submitService;

    public RegConditionSubmitController(RegConditionSubmitService submitService) {
        this.submitService = submitService;
    }

    /** 预检：逐人逐项列 PASS/FAIL/DOUBT 与中文原因、申诉中跳过、结构性错误。 */
    @PostMapping("/submit/preview")
    public ApiResponse<SubmitPreview> preview(@Valid @RequestBody RegSubmitRequest request) {
        return ApiResponse.ok(submitService.preview(request));
    }

    /** 执行：与预检同一口径落库；结构性问题整批拒收、不落半条；申诉处理中自动跳过。 */
    @PostMapping("/submit")
    public ApiResponse<SubmitResult> submit(@Valid @RequestBody RegSubmitRequest request) {
        return ApiResponse.ok(submitService.execute(request));
    }

    /** 存疑转人工：给出复核结论（可逐项改判存疑项）。 */
    @PostMapping("/submissions/{id}/manual-review")
    public ApiResponse<RegConditionSubmission> manualReview(@PathVariable Long id,
                                                            @Valid @RequestBody ManualReviewRequest request) {
        return ApiResponse.ok(submitService.manualReview(id, request));
    }

    @GetMapping("/submissions")
    public ApiResponse<List<RegConditionSubmission>> list(@RequestParam(required = false) Long batchId,
                                                          @RequestParam(required = false) String regNo,
                                                          @RequestParam(required = false) String levelCode,
                                                          @RequestParam(required = false) String status) {
        return ApiResponse.ok(submitService.listSubmissions(batchId, regNo, levelCode, status));
    }

    @GetMapping("/submissions/{id}")
    public ApiResponse<RegConditionSubmission> get(@PathVariable Long id) {
        return ApiResponse.ok(submitService.requireSubmission(id));
    }

    @GetMapping("/submissions/by-no/{submissionNo}")
    public ApiResponse<RegConditionSubmission> getByNo(@PathVariable String submissionNo) {
        return ApiResponse.ok(submitService.requireSubmissionByNo(submissionNo));
    }

    /** 逐项核验结果与逐条原因。 */
    @GetMapping("/submissions/{id}/checks")
    public ApiResponse<List<RegConditionCheck>> checks(@PathVariable Long id) {
        return ApiResponse.ok(submitService.listChecks(id));
    }

    /** 提交流转事件链（提交/自动通过/转人工/人工复核/申诉改判）。 */
    @GetMapping("/submissions/{id}/events")
    public ApiResponse<List<RegSubmissionEvent>> events(@PathVariable Long id) {
        return ApiResponse.ok(submitService.listEvents(id));
    }
}
