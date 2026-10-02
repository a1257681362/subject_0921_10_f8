package com.evops.cert.controller;

import com.evops.cert.service.CertVerifyService;
import com.evops.cert.vo.CertVerifyView;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外验真窗口（用人单位接入）：凭“证书编号 + 姓名”查询。
 * 使用专用用人单位账号（employer/employer），只能访问本验真接口；
 * 只回有效性与批次，不带证件号等隐私字段；作废证给明确提示。
 */
@RestController
@RequestMapping("/api/cert-verify")
public class CertVerifyController {

    private final CertVerifyService verifyService;

    public CertVerifyController(CertVerifyService verifyService) {
        this.verifyService = verifyService;
    }

    @GetMapping
    public ApiResponse<CertVerifyView> verify(@RequestParam String certNo,
                                              @RequestParam String name) {
        return ApiResponse.ok(verifyService.verify(certNo, name));
    }
}
