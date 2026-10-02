package com.evops.appoint.controller;

import com.evops.appoint.dto.AllowanceRateRequest;
import com.evops.appoint.dto.InvigilatorRequest;
import com.evops.appoint.entity.Invigilator;
import com.evops.appoint.entity.InvigilatorAllowanceRate;
import com.evops.appoint.service.MasterDataService;
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
 * 聘任主数据：兼职监考人员与按角色配置的津贴标准。
 */
@RestController
@RequestMapping("/api/invigilators")
public class InvigilatorController {

    private final MasterDataService masterData;

    public InvigilatorController(MasterDataService masterData) {
        this.masterData = masterData;
    }

    @PostMapping
    public ApiResponse<Invigilator> create(@Valid @RequestBody InvigilatorRequest request) {
        return ApiResponse.ok(masterData.createInvigilator(request));
    }

    @GetMapping
    public ApiResponse<List<Invigilator>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(masterData.listInvigilators(status));
    }

    /** 监考津贴标准建档：角色不同标准不同，可按项目/生效区间配置。 */
    @PostMapping("/allowance-rates")
    public ApiResponse<InvigilatorAllowanceRate> createRate(@Valid @RequestBody AllowanceRateRequest request) {
        return ApiResponse.ok(masterData.createRate(request));
    }

    @GetMapping("/allowance-rates")
    public ApiResponse<List<InvigilatorAllowanceRate>> listRates(
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String roleCode) {
        return ApiResponse.ok(masterData.listRates(projectCode, roleCode));
    }
}
