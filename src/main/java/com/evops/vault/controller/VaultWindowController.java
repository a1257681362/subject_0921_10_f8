package com.evops.vault.controller;

import com.evops.common.ApiResponse;
import com.evops.vault.dto.AccessWindowRequest;
import com.evops.vault.service.VaultMasterDataService;
import com.evops.vault.vo.AccessWindowView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 允许进出时段：落在窗口外的登记自动标记并告警保卫；窗口可调，起止成对、起早于止。
 */
@RestController
@RequestMapping("/api/vault/access-window")
public class VaultWindowController {

    private final VaultMasterDataService masterData;

    public VaultWindowController(VaultMasterDataService masterData) {
        this.masterData = masterData;
    }

    @GetMapping
    public ApiResponse<AccessWindowView> get() {
        return ApiResponse.ok(masterData.getWindow());
    }

    @PostMapping
    public ApiResponse<AccessWindowView> set(@Valid @RequestBody AccessWindowRequest request) {
        return ApiResponse.ok(masterData.setWindow(request));
    }
}
