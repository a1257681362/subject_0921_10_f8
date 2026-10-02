package com.evops.regcond.controller;

import com.evops.common.ApiResponse;
import com.evops.regcond.dto.RegAppealConclusionRequest;
import com.evops.regcond.dto.RegAppealRequest;
import com.evops.regcond.entity.RegAppeal;
import com.evops.regcond.entity.RegAppealEvent;
import com.evops.regcond.service.RegAppealService;
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
 * 报名条件申诉：学员发起、人工给结论；申诉期间不重复拦截；申诉单与提交单各自留痕。
 */
@RestController
@RequestMapping("/api/reg-condition-appeals")
public class RegAppealController {

    private final RegAppealService appealService;

    public RegAppealController(RegAppealService appealService) {
        this.appealService = appealService;
    }

    @PostMapping
    public ApiResponse<RegAppeal> apply(@Valid @RequestBody RegAppealRequest request) {
        return ApiResponse.ok(appealService.apply(request));
    }

    /** 申诉结论：approved=true 成立改判通过，false 驳回维持不通过。 */
    @PostMapping("/{id}/conclusion")
    public ApiResponse<RegAppeal> conclude(@PathVariable Long id,
                                           @Valid @RequestBody RegAppealConclusionRequest request) {
        return ApiResponse.ok(appealService.conclude(id, request));
    }

    @GetMapping
    public ApiResponse<List<RegAppeal>> list(@RequestParam(required = false) String regNo,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(appealService.listAppeals(regNo, status, batchId));
    }

    @GetMapping("/{id}")
    public ApiResponse<RegAppeal> get(@PathVariable Long id) {
        return ApiResponse.ok(appealService.requireAppeal(id));
    }

    @GetMapping("/{id}/events")
    public ApiResponse<List<RegAppealEvent>> events(@PathVariable Long id) {
        return ApiResponse.ok(appealService.listAppealEvents(id));
    }
}
