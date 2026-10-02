package com.evops.appoint.controller;

import com.evops.appoint.dto.SettlementAdjustRequest;
import com.evops.appoint.dto.SettlementGenerateRequest;
import com.evops.appoint.entity.Settlement;
import com.evops.appoint.entity.SettlementAdjustment;
import com.evops.appoint.entity.SettlementItem;
import com.evops.appoint.service.SettlementService;
import com.evops.appoint.vo.SettlementPreview;
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
import java.util.Map;

/**
 * 月底监考费结算：一键出结算单（预检→草稿→确认锁定），人事看全量、本人看自己；锁定后调整留痕。
 *
 * 视图口径：请求头 X-Self-Code 传监考人员编码即“本人视图”，结算单详情仅返回本人那一行；
 * 不传为人事全量视图。
 */
@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    // ---------- 一键出结算单（两步） ----------

    /** 第一步：预检当月计费场次 / 人数 / 合计，不落库。 */
    @PostMapping("/preview")
    public ApiResponse<SettlementPreview> preview(@RequestBody(required = false) SettlementGenerateRequest request) {
        return ApiResponse.ok(settlementService.previewGenerate(request));
    }

    /** 第二步：生成草稿（已有草稿则按最新签到重算覆盖；已确认锁定的月份拒绝）。 */
    @PostMapping
    public ApiResponse<Settlement> generate(@RequestBody(required = false) SettlementGenerateRequest request) {
        return ApiResponse.ok(settlementService.generate(request));
    }

    /** 确认草稿并锁定：计费聘任置 settled，之后要改只能走调整。 */
    @PostMapping("/{id}/confirm")
    public ApiResponse<Settlement> confirm(@PathVariable Long id) {
        return ApiResponse.ok(settlementService.confirm(id));
    }

    // ---------- 查询：人事全量 / 本人 ----------

    /** 结算单列表（人事全量）。 */
    @GetMapping
    public ApiResponse<List<Settlement>> list(@RequestParam(required = false) String month) {
        return ApiResponse.ok(settlementService.listSettlements(month));
    }

    /** 结算单详情：人事返回全部明细，本人（X-Self-Code）只返回本人那一行。 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(settlementService.detail(id));
    }

    /** 本人跨结算单只看自己的明细（必须带 X-Self-Code）。 */
    @GetMapping("/my-items")
    public ApiResponse<List<SettlementItem>> myItems(@RequestParam(required = false) String month) {
        return ApiResponse.ok(settlementService.myItems(month));
    }

    // ---------- 锁定后调整（只追加台账） ----------

    /** 结算单确认锁定后唯一改动入口：按人追加调整（金额可正可负）。 */
    @PostMapping("/{id}/adjustments")
    public ApiResponse<SettlementAdjustment> adjust(@PathVariable Long id,
                                                     @Valid @RequestBody SettlementAdjustRequest request) {
        return ApiResponse.ok(settlementService.adjust(id, request));
    }

    /** 调整台账（只追加，人事查看）。 */
    @GetMapping("/{id}/adjustments")
    public ApiResponse<List<SettlementAdjustment>> adjustments(@PathVariable Long id) {
        return ApiResponse.ok(settlementService.adjustments(id));
    }
}
