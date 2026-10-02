package com.evops.regcond.controller;

import com.evops.common.ApiResponse;
import com.evops.regcond.dto.AppealConclusionRequest;
import com.evops.regcond.dto.AppealRequest;
import com.evops.regcond.entity.QualificationAppeal;
import com.evops.regcond.entity.QualificationAppealVersion;
import com.evops.regcond.service.AppealService;
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
 * 条件核验申诉：学员申诉、受理、终裁；流转留痕，申诉期间不重复拦截。
 */
@RestController
@RequestMapping("/api/qualification-appeals")
public class QualificationAppealController {

    private final AppealService appealService;

    public QualificationAppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    /** 学员对不通过 / 存疑核验项发起申诉。 */
    @PostMapping
    public ApiResponse<QualificationAppeal> apply(@Valid @RequestBody AppealRequest request) {
        return ApiResponse.ok(appealService.apply(request));
    }

    /** 受理（待受理→处理中）。 */
    @PostMapping("/{id}/accept")
    public ApiResponse<QualificationAppeal> accept(@PathVariable Long id,
                                                   @RequestParam(required = false) String comment) {
        return ApiResponse.ok(appealService.accept(id, comment));
    }

    /** 终裁：通过豁免放行 / 驳回恢复拦截。 */
    @PostMapping("/{id}/conclusion")
    public ApiResponse<QualificationAppeal> conclude(@PathVariable Long id,
                                                     @Valid @RequestBody AppealConclusionRequest request) {
        return ApiResponse.ok(appealService.conclude(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<QualificationAppeal> get(@PathVariable Long id) {
        return ApiResponse.ok(appealService.requireAppeal(id));
    }

    @GetMapping
    public ApiResponse<List<QualificationAppeal>> list(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String regNo,
                                                       @RequestParam(required = false) String personKey) {
        return ApiResponse.ok(appealService.list(status, regNo, personKey));
    }

    /** 申诉流转版本链（CREATE/ACCEPT/APPROVE/REJECT）。 */
    @GetMapping("/{id}/versions")
    public ApiResponse<List<QualificationAppealVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(appealService.listVersions(id));
    }
}
