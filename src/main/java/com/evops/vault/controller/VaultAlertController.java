package com.evops.vault.controller;

import com.evops.common.ApiResponse;
import com.evops.vault.entity.VaultAlert;
import com.evops.vault.service.VaultAlertService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 保卫异常告警：异常时段进出自动标记生成；保卫签收留痕。
 */
@RestController
@RequestMapping("/api/vault/alerts")
public class VaultAlertController {

    private final VaultAlertService alertService;

    public VaultAlertController(VaultAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ApiResponse<List<VaultAlert>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(alertService.list(status));
    }

    /** 保卫签收：NEW → ACKED，记签收人与钟点。 */
    @PostMapping("/{id}/ack")
    public ApiResponse<VaultAlert> ack(@PathVariable Long id,
                                       @RequestParam(required = false) String note) {
        return ApiResponse.ok(alertService.ack(id, note));
    }
}
