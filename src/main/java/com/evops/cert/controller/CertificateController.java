package com.evops.cert.controller;

import com.evops.cert.dto.CertIssueRequest;
import com.evops.cert.dto.CertLegacyRequest;
import com.evops.cert.dto.CertRevokeRequest;
import com.evops.cert.entity.Certificate;
import com.evops.cert.entity.CertificateRevokeRecord;
import com.evops.cert.service.CertificateService;
import com.evops.cert.vo.CertIssuePreview;
import com.evops.cert.vo.CertIssueResult;
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
 * 证书发证与作废（内部管理端，HTTP Basic）：
 * - 批量发证两步预检；发证后信息只读，无修改/删除入口；
 * - 手工旧号补登不重发、抬序列防撞；
 * - 作废只追加记录并切状态。
 */
@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    // ---------- 批量发证（两步） ----------

    @PostMapping("/issue/preview")
    public ApiResponse<CertIssuePreview> previewIssue(@Valid @RequestBody CertIssueRequest request) {
        return ApiResponse.ok(certificateService.previewIssue(request));
    }

    @PostMapping("/issue")
    public ApiResponse<CertIssueResult> issue(@Valid @RequestBody CertIssueRequest request) {
        return ApiResponse.ok(certificateService.issue(request));
    }

    // ---------- 手工旧号补登 ----------

    @PostMapping("/legacy")
    public ApiResponse<Certificate> registerLegacy(@Valid @RequestBody CertLegacyRequest request) {
        return ApiResponse.ok(certificateService.registerLegacy(request));
    }

    // ---------- 作废（留记录） ----------

    @PostMapping("/{id}/revoke")
    public ApiResponse<Certificate> revoke(@PathVariable Long id,
                                           @Valid @RequestBody CertRevokeRequest request) {
        return ApiResponse.ok(certificateService.revoke(id, request.getReason()));
    }

    @PostMapping("/by-no/{certNo}/revoke")
    public ApiResponse<Certificate> revokeByNo(@PathVariable String certNo,
                                               @Valid @RequestBody CertRevokeRequest request) {
        return ApiResponse.ok(certificateService.revokeByNo(certNo, request.getReason()));
    }

    @GetMapping("/{id}/revoke-records")
    public ApiResponse<List<CertificateRevokeRecord>> revokeRecords(@PathVariable Long id) {
        return ApiResponse.ok(certificateService.revokeRecords(id));
    }

    // ---------- 查询（内部管理视图） ----------

    @GetMapping
    public ApiResponse<List<Certificate>> list(@RequestParam(required = false) Long batchId,
                                               @RequestParam(required = false) String projectCode,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String holderName) {
        return ApiResponse.ok(certificateService.list(batchId, projectCode, status, holderName));
    }

    @GetMapping("/{id}")
    public ApiResponse<Certificate> get(@PathVariable Long id) {
        return ApiResponse.ok(certificateService.requireCertificate(id));
    }

    @GetMapping("/by-no/{certNo}")
    public ApiResponse<Certificate> getByNo(@PathVariable String certNo) {
        return ApiResponse.ok(certificateService.getByCertNo(certNo));
    }
}
