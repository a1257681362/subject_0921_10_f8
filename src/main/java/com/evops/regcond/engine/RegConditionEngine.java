package com.evops.regcond.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.cert.entity.Certificate;
import com.evops.cert.mapper.CertificateMapper;
import com.evops.common.BusinessException;
import com.evops.regcond.common.RegCondConstants;
import com.evops.regcond.dto.EducationItemConfig;
import com.evops.regcond.dto.PriorCertItemConfig;
import com.evops.regcond.dto.PriorCertMaterial;
import com.evops.regcond.dto.RegConditionItemsConfig;
import com.evops.regcond.dto.RegSubmitItem;
import com.evops.regcond.dto.YearsItemConfig;
import com.evops.regcond.service.RegConditionRuleService;
import com.evops.regcond.vo.ItemCheckResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报名条件核验引擎：按冻结的规则版本逐项比对。
 * 口径：
 * - 学历：学员层次达到或超过要求层次即通过；
 * - 年限：算头算尾——起算日含头、提交日含尾，按对日算周年（整周年数 ≥ 要求年限）；
 * - 前置证书：逐项必备，连有效期一起验（提交日须落在有效期内，到期日当天仍有效）；
 *   内部证书查库（不存在/已作废/持证人不符），外部证书按填报材料，机器判不准的一律 DOUBT 转人工。
 */
@Component
public class RegConditionEngine {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CertificateMapper certificateMapper;
    private final ObjectMapper objectMapper;

    public RegConditionEngine(CertificateMapper certificateMapper,
                              ObjectMapper objectMapper) {
        this.certificateMapper = certificateMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 逐项核验。
     *
     * @param config       提交时点冻结版本中的核验项配置
     * @param yearsBasis   规则主表的年限起算口径
     * @param item         学员提交材料
     * @param submittedAt  提交时点（规则按提交时点生效、年限含尾算到此时点）
     */
    public List<ItemCheckResult> evaluate(RegConditionItemsConfig config, String yearsBasis,
                                          RegSubmitItem item, LocalDateTime submittedAt) {
        List<ItemCheckResult> results = new ArrayList<>();
        LocalDate submitDate = submittedAt.toLocalDate();

        if (config.getEducation() != null) {
            results.add(checkEducation(config.getEducation(), item));
        }
        if (config.getYears() != null) {
            results.add(checkYears(config.getYears(), yearsBasis, item, submitDate));
        }
        if (config.getPriorCerts() != null) {
            for (PriorCertItemConfig certConfig : config.getPriorCerts()) {
                results.add(checkPriorCert(certConfig, item, submitDate));
            }
        }
        return results;
    }

    // ---------- 学历 ----------

    private ItemCheckResult checkEducation(EducationItemConfig config, RegSubmitItem item) {
        String label = config.getLabel();
        String actual = item.getEducationLevel();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("requiredLevel", config.getRequiredLevel());
        detail.put("actualLevel", actual);
        if (actual == null || actual.trim().isEmpty()) {
            return fail(RegCondConstants.ITEM_EDUCATION, RegCondConstants.ITEM_EDUCATION, label,
                    "未提交学历层次，不满足" + label + "要求（要求：" + levelName(config.getRequiredLevel()) + "）", detail);
        }
        String actualNorm = actual.trim().toUpperCase();
        List<String> order = RegConditionRuleService.EDUCATION_ORDER;
        if (!order.contains(actualNorm)) {
            // 层次编码认不出：可能是材料写法问题，存疑转人工，不直接误杀
            detail.put("unrecognized", true);
            return doubt(RegCondConstants.ITEM_EDUCATION, RegCondConstants.ITEM_EDUCATION, label,
                    "学历层次无法机读识别：" + actual + "，转人工核对材料", detail);
        }
        detail.put("actualLevel", actualNorm);
        if (order.indexOf(actualNorm) >= order.indexOf(config.getRequiredLevel())) {
            return pass(RegCondConstants.ITEM_EDUCATION, RegCondConstants.ITEM_EDUCATION, label,
                    levelName(actualNorm) + "，满足" + label + "要求（要求：" + levelName(config.getRequiredLevel()) + "）", detail);
        }
        return fail(RegCondConstants.ITEM_EDUCATION, RegCondConstants.ITEM_EDUCATION, label,
                label + "不满足：实际 " + levelName(actualNorm) + "，要求达到 " + levelName(config.getRequiredLevel()), detail);
    }

    // ---------- 年限（算头算尾） ----------

    private ItemCheckResult checkYears(YearsItemConfig config, String yearsBasis,
                                       RegSubmitItem item, LocalDate submitDate) {
        String label = config.getLabel();
        String basisDateText = RegCondConstants.BASIS_LAST_CERT.equals(yearsBasis)
                ? item.getLastCertDate() : item.getWorkStartDate();
        String basisName = RegCondConstants.BASIS_LAST_CERT.equals(yearsBasis) ? "前一证书取得日" : "工作起始日";
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("minYears", config.getMinYears());
        detail.put("basis", yearsBasis);
        detail.put("submitDate", submitDate.toString());
        detail.put("basisDate", basisDateText);

        if (basisDateText == null || basisDateText.trim().isEmpty()) {
            // 没给起算日：无法自动判，存疑转人工（可能是材料未带日期），不直接当不通过
            return doubt(RegCondConstants.ITEM_YEARS, RegCondConstants.ITEM_YEARS, label,
                    "缺少" + basisName + "，无法自动核算年限，转人工核对从业证明", detail);
        }
        LocalDate basisDate;
        try {
            basisDate = parseDate(basisDateText);
        } catch (BusinessException e) {
            detail.put("unparseable", true);
            return doubt(RegCondConstants.ITEM_YEARS, RegCondConstants.ITEM_YEARS, label,
                    basisName + "日期无法识别：" + basisDateText + "，转人工核对", detail);
        }
        detail.put("basisDate", basisDate.toString());
        if (basisDate.isAfter(submitDate)) {
            return fail(RegCondConstants.ITEM_YEARS, RegCondConstants.ITEM_YEARS, label,
                    basisName + " " + basisDate + " 晚于提交日 " + submitDate + "，年限核算不成立", detail);
        }

        // 算头算尾口径：起算日为工龄第 1 天（含头），提交日当天计入（含尾）；
        // 满 N 周年的判定 = 提交日已到起算日的 N 周年对日（对日当天即算满），对日未到按 N-1 周年。
        long actualYears = basisDate.until(submitDate).getYears();
        detail.put("actualYears", actualYears);
        if (actualYears >= config.getMinYears()) {
            return pass(RegCondConstants.ITEM_YEARS, RegCondConstants.ITEM_YEARS, label,
                    "从业年限 " + actualYears + " 周年（" + basisName + " " + basisDate
                            + " 起算，算头算尾至提交日 " + submitDate + "），满足要求 " + config.getMinYears() + " 周年", detail);
        }
        return fail(RegCondConstants.ITEM_YEARS, RegCondConstants.ITEM_YEARS, label,
                label + "不足：核算 " + actualYears + " 周年（" + basisName + " " + basisDate
                        + "，算头算尾至提交日 " + submitDate + "），要求满 " + config.getMinYears() + " 周年", detail);
    }

    // ---------- 前置证书（连有效期一起验） ----------

    private ItemCheckResult checkPriorCert(PriorCertItemConfig config, RegSubmitItem item,
                                           LocalDate submitDate) {
        String key = config.getItemKey();
        String label = config.getLabel();
        PriorCertMaterial material = findMaterial(item.getPriorCerts(), key);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("itemKey", key);
        detail.put("certName", config.getCertName());
        detail.put("checkValidity", config.getCheckValidity());
        detail.put("requiredValidYears", config.getValidYears());
        detail.put("submitDate", submitDate.toString());

        if (material == null) {
            return fail(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                    "缺少前置证书：" + label, detail);
        }
        if (Boolean.TRUE.equals(material.getDocumentDoubt())) {
            detail.put("documentDoubt", true);
            return doubt(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                    "前置证书《" + label + "》材料存疑（印章/编号/日期不清），转人工核对原件", detail);
        }

        boolean external = Boolean.TRUE.equals(material.getExternal()) || material.getCertNo() == null;
        detail.put("external", external);

        // 1) 证书存在性 / 持证人 / 作废状态
        LocalDate issuedDate = null;
        LocalDate expiresDate = null;
        if (!external) {
            Certificate cert = certificateMapper.selectOne(
                    new QueryWrapper<Certificate>().eq("cert_no", material.getCertNo().trim()).last("LIMIT 1"));
            detail.put("certNo", material.getCertNo());
            if (cert == null) {
                // 编号给了但库内查不到：可能是外单位证或假证，存疑转人工，不直接判死
                detail.put("certFound", false);
                return doubt(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                        "前置证书《" + label + "》编号 " + material.getCertNo() + " 在内部证书库查无记录，转人工核验来源", detail);
            }
            detail.put("certFound", true);
            detail.put("certStatus", cert.getStatus());
            String holder = material.getHolderName() == null || material.getHolderName().trim().isEmpty()
                    ? item.getName() : material.getHolderName().trim();
            detail.put("certHolder", cert.getHolderName());
            if (cert.getHolderName() != null && !cert.getHolderName().equals(holder)) {
                return fail(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                        "前置证书《" + label + "》持证人 " + cert.getHolderName() + " 与报名人 " + holder + " 不符", detail);
            }
            if (material.getIdNo() != null && cert.getIdNo() != null
                    && !material.getIdNo().trim().equals(cert.getIdNo())) {
                return fail(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                        "前置证书《" + label + "》证件号与报名人不符", detail);
            }
            if ("REVOKED".equals(cert.getStatus())) {
                detail.put("revokeReason", cert.getRevokeReason());
                return fail(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                        "前置证书《" + label + "》已作废（编号 " + cert.getCertNo()
                                + (cert.getRevokeReason() != null ? "，作废原因：" + cert.getRevokeReason() : "") + "）", detail);
            }
            if (cert.getIssuedAt() != null) {
                issuedDate = cert.getIssuedAt().toLocalDate();
            }
        } else {
            detail.put("certNo", material.getCertNo());
        }

        // 2) 签发日
        String issuedText = material.getIssuedDate();
        if (issuedDate == null && issuedText != null && !issuedText.trim().isEmpty()) {
            try {
                issuedDate = parseDate(issuedText);
            } catch (BusinessException e) {
                detail.put("issuedUnparseable", issuedText);
                return doubt(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                        "前置证书《" + label + "》签发日期无法识别：" + issuedText + "，转人工核对", detail);
            }
        }
        detail.put("issuedDate", issuedDate == null ? null : issuedDate.toString());
        if (issuedDate != null && issuedDate.isAfter(submitDate)) {
            return fail(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                    "前置证书《" + label + "》签发日 " + issuedDate + " 晚于提交日 " + submitDate + "，报名时尚未取得", detail);
        }

        // 3) 有效期（连有效期一起验；到期日当天含尾仍有效）
        if (Boolean.FALSE.equals(config.getCheckValidity())) {
            return pass(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                    "前置证书《" + label + "》有效（本项不核验有效期）", detail);
        }

        // 到期日优先级：证书自带到期日 > 规则规定 validYears 按签发日推算 > 无法判定存疑
        if (material.getExpiresDate() != null && !material.getExpiresDate().trim().isEmpty()) {
            try {
                expiresDate = parseDate(material.getExpiresDate());
            } catch (BusinessException e) {
                detail.put("expiresUnparseable", material.getExpiresDate());
                return doubt(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                        "前置证书《" + label + "》到期日期无法识别：" + material.getExpiresDate() + "，转人工核对", detail);
            }
            detail.put("expiresSource", "CERT");
        } else if (config.getValidYears() != null && issuedDate != null) {
            expiresDate = issuedDate.plusYears(config.getValidYears());
            // 签发日含头、到期日含尾：plusYears 当日为最后一个有效日
            detail.put("expiresSource", "RULE_VALID_YEARS");
        } else if (config.getValidYears() != null && issuedDate == null) {
            detail.put("needIssuedDate", true);
            return doubt(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                    "前置证书《" + label + "》缺签发日，无法按 " + config.getValidYears()
                            + " 年有效期推算，转人工核对", detail);
        } else {
            // 规则没规定年限、材料也没到期日：长期有效无法确认，存疑转人工
            detail.put("validityUnknown", true);
            return doubt(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                    "前置证书《" + label + "》未记载到期日且规则未规定有效期，无法机读确认是否长期有效，转人工核对", detail);
        }
        detail.put("expiresDate", expiresDate.toString());

        if (issuedDate != null && expiresDate.isBefore(issuedDate)) {
            return fail(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                    "前置证书《" + label + "》到期日 " + expiresDate + " 早于签发日 " + issuedDate + "，材料矛盾", detail);
        }
        // 到期日当天仍有效（含尾）：次日起算失效
        if (expiresDate.isBefore(submitDate)) {
            return fail(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                    "前置证书《" + label + "》已过有效期：到期日 " + expiresDate + "，提交日 " + submitDate, detail);
        }
        return pass(RegCondConstants.ITEM_PRIOR_CERT, key, label,
                "前置证书《" + label + "》有效（有效期至 " + expiresDate + "，含当日）", detail);
    }

    // ---------- 工具 ----------

    private PriorCertMaterial findMaterial(List<PriorCertMaterial> materials, String key) {
        if (materials == null) {
            return null;
        }
        for (PriorCertMaterial m : materials) {
            if (m != null && key.equals(m.getItemKey())) {
                return m;
            }
        }
        return null;
    }

    private LocalDate parseDate(String text) {
        String t = text.trim();
        try {
            return LocalDate.parse(t, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // 继续尝试兼容 yyyy/MM/dd 与带时间
        }
        try {
            return LocalDate.parse(t, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(t, DT_FMT).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        throw new BusinessException("DATE_FORMAT_INVALID", "日期格式无法识别：" + t + "（要求 yyyy-MM-dd）");
    }

    private ItemCheckResult pass(String type, String key, String label, String reason, Map<String, Object> detail) {
        return ItemCheckResult.of(type, key, label, RegCondConstants.CHECK_PASS, reason, toJson(detail));
    }

    private ItemCheckResult fail(String type, String key, String label, String reason, Map<String, Object> detail) {
        return ItemCheckResult.of(type, key, label, RegCondConstants.CHECK_FAIL, reason, toJson(detail));
    }

    private ItemCheckResult doubt(String type, String key, String label, String reason, Map<String, Object> detail) {
        return ItemCheckResult.of(type, key, label, RegCondConstants.CHECK_DOUBT, reason, toJson(detail));
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return detail.toString();
        }
    }

    /** 学历层次中文名。 */
    public static String levelName(String code) {
        if (code == null) {
            return "未填写";
        }
        switch (code) {
            case "HIGH_SCHOOL": return "高中/中专";
            case "JUNIOR_COLLEGE": return "大专";
            case "BACHELOR": return "本科";
            case "MASTER": return "硕士";
            case "DOCTOR": return "博士";
            default: return code;
        }
    }
}
