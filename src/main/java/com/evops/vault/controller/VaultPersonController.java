package com.evops.vault.controller;

import com.evops.common.ApiResponse;
import com.evops.vault.dto.PersonRequest;
import com.evops.vault.entity.VaultPerson;
import com.evops.vault.service.VaultMasterDataService;
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
 * 保密室准入人员：进出刷证按卡号或人员编码定位；停用后不能登记也不能复核。
 */
@RestController
@RequestMapping("/api/vault/persons")
public class VaultPersonController {

    private final VaultMasterDataService masterData;

    public VaultPersonController(VaultMasterDataService masterData) {
        this.masterData = masterData;
    }

    @PostMapping
    public ApiResponse<VaultPerson> create(@Valid @RequestBody PersonRequest request) {
        return ApiResponse.ok(masterData.createPerson(request));
    }

    @GetMapping
    public ApiResponse<List<VaultPerson>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(masterData.listPersons(status));
    }

    /** 停用：退出准入名单，之后不能登记也不能复核；历史登记保留。 */
    @PostMapping("/{id}/disable")
    public ApiResponse<VaultPerson> disable(@PathVariable Long id) {
        return ApiResponse.ok(masterData.disablePerson(id));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<VaultPerson> enable(@PathVariable Long id) {
        return ApiResponse.ok(masterData.enablePerson(id));
    }
}
