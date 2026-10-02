package com.evops.vault.controller;

import com.evops.common.ApiResponse;
import com.evops.vault.dto.BoxRequest;
import com.evops.vault.entity.VaultBox;
import com.evops.vault.service.VaultAccessService;
import com.evops.vault.service.VaultMasterDataService;
import com.evops.vault.vo.InRoomBoxView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 卷箱：箱号先建档再登记出入；在室卷箱按已生效出入明细实时推导。
 */
@RestController
@RequestMapping("/api/vault/boxes")
public class VaultBoxController {

    private final VaultMasterDataService masterData;
    private final VaultAccessService accessService;

    public VaultBoxController(VaultMasterDataService masterData, VaultAccessService accessService) {
        this.masterData = masterData;
        this.accessService = accessService;
    }

    @PostMapping
    public ApiResponse<VaultBox> create(@Valid @RequestBody BoxRequest request) {
        return ApiResponse.ok(masterData.createBox(request));
    }

    @GetMapping
    public ApiResponse<List<VaultBox>> list() {
        return ApiResponse.ok(masterData.listBoxes());
    }

    /** 在室卷箱（实时）：只按已生效登记挂钩的卷箱明细推导。 */
    @GetMapping("/in-room")
    public ApiResponse<List<InRoomBoxView>> inRoom() {
        return ApiResponse.ok(accessService.inRoomBoxes());
    }
}
