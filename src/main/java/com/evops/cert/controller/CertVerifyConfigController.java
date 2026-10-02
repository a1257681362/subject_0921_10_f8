package com.evops.cert.controller;

import com.evops.cert.entity.CertVerifyConfig;
import com.evops.cert.service.CertVerifyConfigService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 验真窗口配置（内部管理端）：总开关与每分钟限流值，供“先小流量”灰度调节。
 */
@RestController
@RequestMapping("/api/cert-verify-config")
public class CertVerifyConfigController {

    private final CertVerifyConfigService configService;

    public CertVerifyConfigController(CertVerifyConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public ApiResponse<List<CertVerifyConfig>> list() {
        return ApiResponse.ok(configService.list());
    }

    @PutMapping("/{key}")
    public ApiResponse<CertVerifyConfig> update(@PathVariable String key,
                                                @RequestParam String value) {
        return ApiResponse.ok(configService.update(key, value));
    }
}
