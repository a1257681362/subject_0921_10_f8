package com.evops.absence.controller;

import com.evops.absence.dto.ExamineeCheckinRequest;
import com.evops.absence.dto.SupplementApproveRequest;
import com.evops.absence.dto.SupplementRequest;
import com.evops.absence.entity.ExamineeCheckin;
import com.evops.absence.service.CheckinService;
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
 * 考生签到：现场签到登记即生效；补录走审批（批准才生效，审批人非提交人）。
 */
@RestController
@RequestMapping("/api/absence/checkins")
public class AbsenceCheckinController {

    private final CheckinService checkinService;

    public AbsenceCheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    /** 现场签到：登记即生效。 */
    @PostMapping
    public ApiResponse<ExamineeCheckin> checkin(@Valid @RequestBody ExamineeCheckinRequest request) {
        return ApiResponse.ok(checkinService.checkin(request));
    }

    /** 签到补录：走审批，落库待审批（PENDING）。 */
    @PostMapping("/supplement")
    public ApiResponse<ExamineeCheckin> supplement(@Valid @RequestBody SupplementRequest request) {
        return ApiResponse.ok(checkinService.supplement(request));
    }

    /** 补录审批：批准才生效；批准后对已生效缺考无效裁定自动发起变更。 */
    @PostMapping("/{id}/approve")
    public ApiResponse<ExamineeCheckin> approve(@PathVariable Long id,
                                                @Valid @RequestBody SupplementApproveRequest request) {
        return ApiResponse.ok(checkinService.approve(id, request));
    }

    @GetMapping
    public ApiResponse<List<ExamineeCheckin>> list(@RequestParam(required = false) Long batchId,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String regNo) {
        return ApiResponse.ok(checkinService.list(batchId, status, regNo));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExamineeCheckin> get(@PathVariable Long id) {
        return ApiResponse.ok(checkinService.get(id));
    }
}
