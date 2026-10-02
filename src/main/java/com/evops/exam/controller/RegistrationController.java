package com.evops.exam.controller;

import com.evops.common.ApiResponse;
import com.evops.exam.dto.CorrectionRequest;
import com.evops.exam.dto.RegistrationImportRequest;
import com.evops.exam.entity.Registration;
import com.evops.exam.entity.RegistrationCorrection;
import com.evops.exam.service.RegistrationService;
import com.evops.exam.vo.ActionPreview;
import com.evops.exam.vo.ActionResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /** 批量动作第一步：展示影响范围（可新增 / 拒收逐条列出），不落库。 */
    @PostMapping("/import/preview")
    public ApiResponse<ActionPreview> previewImport(@Valid @RequestBody RegistrationImportRequest request) {
        return ApiResponse.ok(registrationService.previewImport(request));
    }

    /** 批量动作第二步：确认后执行；任何报名号撞号整批拒收。 */
    @PostMapping("/import")
    public ApiResponse<ActionResult> importRegistrations(@Valid @RequestBody RegistrationImportRequest request) {
        return ApiResponse.ok(registrationService.importRegistrations(request));
    }

    @GetMapping
    public ApiResponse<List<Registration>> listByBatch(@RequestParam Long batchId) {
        return ApiResponse.ok(registrationService.listByBatch(batchId));
    }

    /** 勘误：旧值进勘误表留痕，主表只记新值与更正次数。 */
    @PostMapping("/{id}/corrections")
    public ApiResponse<RegistrationCorrection> correct(@PathVariable Long id,
                                                       @Valid @RequestBody CorrectionRequest request) {
        return ApiResponse.ok(registrationService.correct(
                id, request.getFieldName(), request.getNewValue(), request.getReason()));
    }

    @GetMapping("/{id}/corrections")
    public ApiResponse<List<RegistrationCorrection>> corrections(@PathVariable Long id) {
        return ApiResponse.ok(registrationService.listCorrections(id));
    }

    /** 按姓名查名册（包含匹配，重名全返回）。 */
    @GetMapping("/search/by-name")
    public ApiResponse<List<Registration>> findByName(@RequestParam String name) {
        return ApiResponse.ok(registrationService.findByName(name));
    }
}
