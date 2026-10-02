package com.evops.vault.controller;

import com.evops.common.ApiResponse;
import com.evops.vault.dto.AccessImportRequest;
import com.evops.vault.dto.NoteRequest;
import com.evops.vault.dto.ReviewRequest;
import com.evops.vault.dto.SwipeRequest;
import com.evops.vault.entity.VaultAccess;
import com.evops.vault.entity.VaultAccessNote;
import com.evops.vault.entity.VaultAccessVersion;
import com.evops.vault.entity.VaultBoxMove;
import com.evops.vault.service.VaultAccessImportService;
import com.evops.vault.service.VaultAccessService;
import com.evops.vault.vo.ImportPreview;
import com.evops.vault.vo.ImportResult;
import com.evops.vault.vo.InRoomPersonView;
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
 * 出入登记模块（模块 / 版本 / 状态）：
 * 进出刷证 → 待复核；第二人复核确认才生效；在室清单实时可查；
 * 登记写入后不改，纠错走补充说明；纸面存量两步导入。
 */
@RestController
@RequestMapping("/api/vault/access")
public class VaultAccessController {

    private final VaultAccessService accessService;
    private final VaultAccessImportService importService;

    public VaultAccessController(VaultAccessService accessService,
                                 VaultAccessImportService importService) {
        this.accessService = accessService;
        this.importService = importService;
    }

    // ---------- 进出刷证（登记即待复核） ----------

    /** 刷证登记：按卡号或人员编码，可随登记挂钩卷箱；落库为待复核。 */
    @PostMapping("/swipe")
    public ApiResponse<VaultAccess> swipe(@Valid @RequestBody SwipeRequest request) {
        return ApiResponse.ok(accessService.swipe(request));
    }

    /** 第二人复核：确认才生效；复核人不能是登记者本人，驳回必须写意见。 */
    @PostMapping("/{id}/review")
    public ApiResponse<VaultAccess> review(@PathVariable Long id,
                                           @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(accessService.review(id, request));
    }

    // ---------- 查询 / 在室清单 ----------

    @GetMapping
    public ApiResponse<List<VaultAccess>> list(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) String direction,
                                               @RequestParam(required = false) String personCode,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to) {
        return ApiResponse.ok(accessService.list(status, direction, personCode, from, to));
    }

    /** 在室人员（实时）：只按已生效登记推导。 */
    @GetMapping("/in-room")
    public ApiResponse<List<InRoomPersonView>> inRoom() {
        return ApiResponse.ok(accessService.inRoomPersons());
    }

    @GetMapping("/{id}")
    public ApiResponse<VaultAccess> get(@PathVariable Long id) {
        return ApiResponse.ok(accessService.get(id));
    }

    /** 出入登记版本链（登记 / 复核 / 驳回 / 导入全程只追加）。 */
    @GetMapping("/{id}/versions")
    public ApiResponse<List<VaultAccessVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(accessService.versions(id));
    }

    /** 本次登记挂钩的卷箱明细（卷箱出入跟人员进出挂钩）。 */
    @GetMapping("/{id}/boxes")
    public ApiResponse<List<VaultBoxMove>> boxes(@PathVariable Long id) {
        return ApiResponse.ok(accessService.boxMoves(id));
    }

    // ---------- 补充说明（纠错唯一入口，只追加） ----------

    /** 登记写入后不改；纠错一律走补充说明。 */
    @PostMapping("/{id}/notes")
    public ApiResponse<VaultAccessNote> addNote(@PathVariable Long id,
                                                @Valid @RequestBody NoteRequest request) {
        return ApiResponse.ok(accessService.addNote(id, request));
    }

    @GetMapping("/{id}/notes")
    public ApiResponse<List<VaultAccessNote>> notes(@PathVariable Long id) {
        return ApiResponse.ok(accessService.listNotes(id));
    }

    // ---------- 纸面存量导入（两步） ----------

    /** 第一步：预检影响范围（可导入 / 跳过逐条列出），不落库。 */
    @PostMapping("/import/preview")
    public ApiResponse<ImportPreview> importPreview(@Valid @RequestBody AccessImportRequest request) {
        return ApiResponse.ok(importService.preview(request));
    }

    /** 第二步：确认后执行；历史既成事实落库即生效，异常时段自动标记并告警保卫。 */
    @PostMapping("/import")
    public ApiResponse<ImportResult> importExecute(@Valid @RequestBody AccessImportRequest request) {
        return ApiResponse.ok(importService.execute(request));
    }
}
