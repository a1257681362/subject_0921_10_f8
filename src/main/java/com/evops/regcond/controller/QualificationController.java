package com.evops.regcond.controller;

import com.evops.common.ApiResponse;
import com.evops.exam.entity.Registration;
import com.evops.regcond.dto.ManualReviewRequest;
import com.evops.regcond.dto.QualificationSubmitRequest;
import com.evops.regcond.entity.RegistrationQualification;
import com.evops.regcond.service.QualificationService;
import com.evops.regcond.vo.QualificationView;
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
 * 报名条件核验：提交逐项比、不通过逐条给原因、存疑转人工、合格回写名册。
 */
@RestController
@RequestMapping("/api/qualifications")
public class QualificationController {

    private final QualificationService qualificationService;

    public QualificationController(QualificationService qualificationService) {
        this.qualificationService = qualificationService;
    }

    /** 提交报名材料，按提交时点生效版本逐项核验。 */
    @PostMapping("/submit")
    public ApiResponse<QualificationView> submit(@Valid @RequestBody QualificationSubmitRequest request) {
        return ApiResponse.ok(qualificationService.submit(request));
    }

    /** 存疑转人工：人工放行 / 拒收。 */
    @PostMapping("/{id}/manual-review")
    public ApiResponse<RegistrationQualification> manualReview(@PathVariable Long id,
                                                               @Valid @RequestBody ManualReviewRequest request) {
        return ApiResponse.ok(qualificationService.manualReview(id, request));
    }

    /** 合格核验单正式入册（回写报名名册）。 */
    @PostMapping("/{id}/enroll")
    public ApiResponse<Registration> enroll(@PathVariable Long id) {
        return ApiResponse.ok(qualificationService.enroll(id));
    }

    @GetMapping("/{id}")
    public ApiResponse<QualificationView> get(@PathVariable Long id) {
        return ApiResponse.ok(qualificationService.getView(id));
    }

    @GetMapping
    public ApiResponse<List<RegistrationQualification>> list(@RequestParam(required = false) Long batchId,
                                                             @RequestParam(required = false) String status,
                                                             @RequestParam(required = false) String projectCode,
                                                             @RequestParam(required = false) String levelCode) {
        return ApiResponse.ok(qualificationService.list(batchId, status, projectCode, levelCode));
    }

    /** 逐项核验结果（不通过逐条原因 / 申诉中标记）。 */
    @GetMapping("/{id}/items")
    public ApiResponse<?> items(@PathVariable Long id) {
        return ApiResponse.ok(qualificationService.getView(id).getItems());
    }
}
