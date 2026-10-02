package com.evops.absence.controller;

import com.evops.absence.dto.ViolationRequest;
import com.evops.absence.entity.ViolationRecord;
import com.evops.absence.service.ViolationService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 违纪记录：直接登记，作为自动标缺考（VIOLATION 类型）的输入。
 */
@RestController
@RequestMapping("/api/absence/violations")
public class AbsenceViolationController {

    private final ViolationService violationService;

    public AbsenceViolationController(ViolationService violationService) {
        this.violationService = violationService;
    }

    @PostMapping
    public ApiResponse<ViolationRecord> record(@Valid @RequestBody ViolationRequest request) {
        return ApiResponse.ok(violationService.record(request));
    }

    @GetMapping
    public ApiResponse<List<ViolationRecord>> list(@RequestParam(required = false) Long batchId,
                                                   @RequestParam(required = false) String regNo) {
        return ApiResponse.ok(violationService.list(batchId, regNo));
    }
}
