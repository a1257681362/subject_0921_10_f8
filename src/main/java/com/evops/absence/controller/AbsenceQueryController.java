package com.evops.absence.controller;

import com.evops.absence.dto.StatRecomputeRequest;
import com.evops.absence.entity.AbsenceStat;
import com.evops.absence.entity.ScoreSnapshot;
import com.evops.absence.service.AbsenceQueryService;
import com.evops.absence.service.AbsenceStatService;
import com.evops.absence.vo.PassListView;
import com.evops.common.ApiResponse;
import com.evops.common.OperatorHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 缺考裁定查询：合格名单（成绩有效即进名单）、统计（只认最终裁定）、成绩快照（逐人逐笔）。
 */
@RestController
@RequestMapping("/api/absence")
public class AbsenceQueryController {

    private final AbsenceQueryService queryService;
    private final AbsenceStatService statService;

    public AbsenceQueryController(AbsenceQueryService queryService, AbsenceStatService statService) {
        this.queryService = queryService;
        this.statService = statService;
    }

    /** 合格名单：本批次成绩有效的学员名单，带等级与各等级计数。 */
    @GetMapping("/pass-list")
    public ApiResponse<PassListView> passList(@RequestParam Long batchId) {
        return ApiResponse.ok(queryService.passList(batchId));
    }

    /** 批次缺考统计：只认最终裁定（CONFIRMED），裁定/签到变动即重算。 */
    @GetMapping("/stats")
    public ApiResponse<AbsenceStat> stats(@RequestParam Long batchId) {
        return ApiResponse.ok(statService.getOrCompute(batchId));
    }

    /** 手动重算统计（平时由裁定确认、签到生效自动重算）。 */
    @PostMapping("/stats/recompute")
    public ApiResponse<AbsenceStat> recompute(@Valid @RequestBody StatRecomputeRequest request) {
        return ApiResponse.ok(statService.recompute(request.getBatchId(), "手动重算", OperatorHolder.current()));
    }

    /** 成绩快照：裁定确认时逐人逐笔留痕（只追加）。 */
    @GetMapping("/score-snapshots")
    public ApiResponse<List<ScoreSnapshot>> snapshots(@RequestParam(required = false) Long batchId,
                                                      @RequestParam(required = false) String regNo) {
        return ApiResponse.ok(queryService.snapshots(batchId, regNo));
    }
}
