package com.evops.cert.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.cert.common.CertConstants;
import com.evops.cert.entity.Certificate;
import com.evops.cert.mapper.CertificateMapper;
import com.evops.cert.vo.CertVerifyView;
import com.evops.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

/**
 * 对外验真：凭“证书编号 + 姓名”查询，只回有效性与批次。
 *
 * 隐私与安全口径：
 * - 编号不存在、校验码非法、姓名不匹配统一返回 found=false，不区分原因，防止按编号枚举探测；
 * - 只回证面公开项（编号、姓名、批次、科目、等级、发证日期、项目），不回证件号等隐私字段；
 * - 已作废证书返回 valid=false 并给明确作废提示与原因，不与“查无此证”混淆。
 */
@Service
public class CertVerifyService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CertificateMapper certificateMapper;
    private final CertVerifyConfigService configService;
    private final com.evops.cert.engine.CertVerifyRateLimiter rateLimiter;

    public CertVerifyService(CertificateMapper certificateMapper,
                             CertVerifyConfigService configService,
                             com.evops.cert.engine.CertVerifyRateLimiter rateLimiter) {
        this.certificateMapper = certificateMapper;
        this.configService = configService;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public CertVerifyView verify(String certNo, String holderName) {
        if (certNo == null || certNo.trim().isEmpty()) {
            throw new BusinessException("CERT_NO_REQUIRED", "证书编号不能为空");
        }
        if (holderName == null || holderName.trim().isEmpty()) {
            throw new BusinessException("HOLDER_NAME_REQUIRED", "姓名不能为空");
        }

        // 小流量灰度：窗口关闭直接给可读提示；超每分钟上限返回 429 语义的业务码。
        if (!configService.isVerifyEnabled()) {
            throw new BusinessException("CERT_VERIFY_DISABLED", "证书验真窗口当前未开放，请稍后再试或联系发证机构");
        }
        if (!rateLimiter.tryAcquire(configService.ratePerMinute())) {
            throw new BusinessException("CERT_VERIFY_RATE_LIMITED",
                    "验真请求过于频繁，已触发小流量限流，请稍后再试");
        }

        String no = certNo.trim().toUpperCase();
        String name = holderName.trim();

        Certificate cert = certificateMapper.selectOne(
                new QueryWrapper<Certificate>().eq("cert_no", no));

        // 查无此证（含编号抄错、校验码错）或姓名不匹配：统一口径，不暴露编号是否存在。
        if (cert == null || !name.equals(cert.getHolderName())) {
            return notFound();
        }
        return toView(cert);
    }

    private CertVerifyView notFound() {
        CertVerifyView view = new CertVerifyView();
        view.setFound(false);
        view.setValid(false);
        view.setStatus("NOT_FOUND");
        view.setMessage("未查询到编号与姓名一致的证书，请核对编号与姓名后重试");
        return view;
    }

    private CertVerifyView toView(Certificate cert) {
        CertVerifyView view = new CertVerifyView();
        view.setFound(true);
        view.setCertNo(cert.getCertNo());
        view.setHolderName(cert.getHolderName());
        view.setBatchCode(cert.getBatchCode());
        view.setBatchName(cert.getBatchName());
        view.setSubject(cert.getSubject());
        view.setGrade(cert.getGrade());
        view.setProjectCode(cert.getProjectCode());
        view.setIssuedAt(cert.getIssuedAt() == null ? null : cert.getIssuedAt().format(DATE));

        if (CertConstants.CERT_REVOKED.equals(cert.getStatus())) {
            view.setValid(false);
            view.setStatus(CertConstants.CERT_REVOKED);
            String reason = cert.getRevokeReason() == null ? "" : "（作废原因：" + cert.getRevokeReason() + "）";
            view.setMessage("该证书已作废，不再有效" + reason);
            view.setRevokeReason(cert.getRevokeReason());
        } else {
            view.setValid(true);
            view.setStatus(CertConstants.CERT_ISSUED);
            view.setMessage("该证书真实有效");
        }
        return view;
    }
}
