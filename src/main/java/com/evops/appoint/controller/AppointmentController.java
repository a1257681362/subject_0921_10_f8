package com.evops.appoint.controller;

import com.evops.appoint.dto.CheckInRequest;
import com.evops.appoint.dto.ReplaceRequest;
import com.evops.appoint.dto.ShiftGenerateRequest;
import com.evops.appoint.entity.Appointment;
import com.evops.appoint.entity.AppointmentReplacement;
import com.evops.appoint.entity.AppointmentVersion;
import com.evops.appoint.service.AppointmentService;
import com.evops.appoint.vo.ShiftPreview;
import com.evops.appoint.vo.ShiftResult;
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
 * 聘任记录模块：监考随排班生成聘任；场次结束签到计津贴、缺勤不计费；换人留替换记录；版本链可追溯。
 *
 * 视图口径：请求头 X-Self-Code 传监考人员编码即“本人视图”，只能看本人聘任 / 版本 / 替换记录；
 * 不传为人事全量视图。
 */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    // ---------- 监考随排班生成聘任（两步） ----------

    /** 第一步：预检每条排班的人员 / 角色 / 津贴标准与可生成 / 跳过原因，不落库。 */
    @PostMapping("/shifts/preview")
    public ApiResponse<ShiftPreview> preview(@Valid @RequestBody ShiftGenerateRequest request) {
        return ApiResponse.ok(appointmentService.previewGenerate(request));
    }

    /** 第二步：确认后按排班生成聘任与 CREATE 版本（已排班的幂等跳过）。 */
    @PostMapping("/shifts")
    public ApiResponse<ShiftResult> generate(@Valid @RequestBody ShiftGenerateRequest request) {
        return ApiResponse.ok(appointmentService.generate(request));
    }

    // ---------- 查询（人事全量 / 本人） ----------

    @GetMapping
    public ApiResponse<List<Appointment>> list(@RequestParam(required = false) Long batchId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String invigilatorCode) {
        return ApiResponse.ok(appointmentService.list(batchId, status, invigilatorCode));
    }

    @GetMapping("/{id}")
    public ApiResponse<Appointment> get(@PathVariable Long id) {
        return ApiResponse.ok(appointmentService.get(id));
    }

    /** 聘任记录版本链（生成 / 换人 / 签到 / 缺勤 / 取消全程只追加）。 */
    @GetMapping("/{id}/versions")
    public ApiResponse<List<AppointmentVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(appointmentService.versions(id));
    }

    /** 换人替换记录：按批次查全部（本人视图只返回涉及本人者）。 */
    @GetMapping("/replacements")
    public ApiResponse<List<AppointmentReplacement>> replacementsByBatch(
            @RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(appointmentService.replacements(batchId, null));
    }

    /** 单条聘任的换人替换记录。 */
    @GetMapping("/{id}/replacements")
    public ApiResponse<List<AppointmentReplacement>> replacements(@PathVariable Long id) {
        return ApiResponse.ok(appointmentService.replacements(null, id));
    }

    // ---------- 场次结束：签到计津贴 / 缺勤不计费 ----------

    /** 签到：APPOINTED → CHECKED_IN，按聘任时冻结口径计津贴。 */
    @PostMapping("/{id}/check-in")
    public ApiResponse<Appointment> checkIn(@PathVariable Long id,
                                            @RequestBody(required = false) CheckInRequest request) {
        return ApiResponse.ok(appointmentService.checkIn(id, request));
    }

    /** 记缺勤：APPOINTED → ABSENT，不计费（已签到计费的不能改记缺勤）。 */
    @PostMapping("/{id}/absent")
    public ApiResponse<Appointment> absent(@PathVariable Long id,
                                           @RequestParam(required = false) String reason) {
        return ApiResponse.ok(appointmentService.markAbsent(id, reason));
    }

    // ---------- 换人（留替换记录） / 取消 ----------

    /** 换人：原持聘人留 REPLACED 版本不计费，接任者生成新版本并重新冻结津贴口径。 */
    @PostMapping("/{id}/replace")
    public ApiResponse<Appointment> replace(@PathVariable Long id,
                                            @Valid @RequestBody ReplaceRequest request) {
        return ApiResponse.ok(appointmentService.replace(id, request));
    }

    /** 场次取消：→ CANCELLED，不计聘任计费（已签到计费的不能取消）。 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Appointment> cancel(@PathVariable Long id,
                                           @RequestParam(required = false) String reason) {
        return ApiResponse.ok(appointmentService.cancel(id, reason));
    }
}
