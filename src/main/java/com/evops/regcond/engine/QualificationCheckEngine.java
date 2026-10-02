package com.evops.regcond.engine;

import com.evops.cert.common.CertConstants;
import com.evops.cert.entity.Certificate;
import com.evops.cert.mapper.CertificateMapper;
import com.evops.regcond.common.RegCondConstants;
import com.evops.regcond.dto.ConditionItem;
import com.evops.regcond.service.ConditionRuleService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

/**
 * 条件核验引擎：按冻结核验项逐项机器比对，输出 PASS / FAIL / MANUAL。
 * 申诉中 / 已豁免的分层由 QualificationService 在外层处理，引擎只给原始结论。
 *
 * 口径：
 * - 学历：申报学历编码不低于要求等级；材料缺失/编码无法辨认转人工。
 * - 年限：算头算尾——从业起始日与提交日两天都计入，即按 [起始日, 提交日+1天) 折算周年/足月，
 *   不满一周期的零头不计；起始日缺失转人工，起始日晚于提交日为不通过。
 * - 前置证书：连有效期一起验。编号查不到/姓名对不上（可能曾用名、录入差错）转人工；
 *   科目、等级、状态、有效期明确不符为不通过；要求有效期但证书未登记有效期的转人工。
 */
@Component
public class QualificationCheckEngine {

    private final CertificateMapper certificateMapper;

    public QualificationCheckEngine(CertificateMapper certificateMapper) {
        this.certificateMapper = certificateMapper;
    }

    public CheckOutcome evaluate(ConditionItem item, Material material, LocalDate submitDate) {
        switch (item.getItemType()) {
            case RegCondConstants.ITEM_EDUCATION:
                return checkEducation(item, material);
            case RegCondConstants.ITEM_YEARS:
                return checkYears(item, material, submitDate);
            case RegCondConstants.ITEM_PREREQ_CERT:
                return checkPrereqCert(item, material, submitDate);
            default:
                return CheckOutcome.manual(item.getName(), null,
                        "未知核验项类型：" + item.getItemType() + "，转人工处理");
        }
    }

    // ---------- 学历 ----------

    private CheckOutcome checkEducation(ConditionItem item, Material material) {
        String required = item.getRequiredEducation();
        String requiredLabel = ConditionRuleService.educationLabel(required);
        String expected = "学历不低于" + requiredLabel;
        String actualEdu = material.getEducation();
        if (actualEdu == null || actualEdu.trim().isEmpty()) {
            return CheckOutcome.manual(expected, "未填报学历",
                    "申报材料未填写学历，无法机器核验，转人工核对学历证明");
        }
        Integer actualRank = ConditionRuleService.educationRank(actualEdu);
        if (actualRank == null) {
            return CheckOutcome.manual(expected, "学历编码无法辨认：" + actualEdu,
                    "申报学历编码不在系统字典内，转人工核对学历证明");
        }
        String actualLabel = ConditionRuleService.educationLabel(actualEdu.trim().toUpperCase());
        if (actualRank < ConditionRuleService.educationRank(required)) {
            return CheckOutcome.fail(expected, actualLabel,
                    "学历不满足：要求不低于" + requiredLabel + "，申报为" + actualLabel);
        }
        return CheckOutcome.pass(expected, actualLabel);
    }

    // ---------- 从业年限（算头算尾） ----------

    private CheckOutcome checkYears(ConditionItem item, Material material, LocalDate submitDate) {
        boolean byMonth = RegCondConstants.UNIT_MONTH.equals(item.getUnit());
        String unitLabel = byMonth ? "个月" : "周年";
        String expected = "从业年限满" + item.getMinValue().stripTrailingZeros().toPlainString() + unitLabel
                + "（算头算尾，起始日与提交日都计）";
        LocalDate start = material.getWorkStartDate();
        if (start == null) {
            return CheckOutcome.manual(expected, "未填报从业起始日期",
                    "申报材料未填写从业起始日期，无法机器计算年限，转人工核对从业证明");
        }
        if (start.isAfter(submitDate)) {
            return CheckOutcome.fail(expected, "从业起始日 " + start + " 晚于提交日 " + submitDate,
                    "从业起始日期晚于报名提交日期，年限不可能满足要求");
        }

        // 算头算尾：把区间右端补一天，[start, submit+1day) 的完整周期数即含头含尾的周年/月数。
        LocalDate exclusiveEnd = submitDate.plusDays(1);
        BigDecimal actual;
        if (byMonth) {
            long months = ChronoUnit.MONTHS.between(start, exclusiveEnd);
            actual = BigDecimal.valueOf(months);
        } else {
            Period period = Period.between(start, exclusiveEnd);
            // 完整周年 + 满月折算的小数年（保留两位），不足一月的零头不计
            BigDecimal years = BigDecimal.valueOf(period.getYears())
                    .add(BigDecimal.valueOf(period.getMonths())
                            .divide(BigDecimal.valueOf(12), 2, RoundingMode.DOWN));
            actual = years;
        }

        if (actual.compareTo(item.getMinValue()) < 0) {
            return CheckOutcome.fail(expected,
                    "实际年限 " + actual.stripTrailingZeros().toPlainString() + unitLabel
                            + "（" + start + " 至 " + submitDate + "，算头算尾）",
                    "从业年限不足：要求满" + item.getMinValue().stripTrailingZeros().toPlainString()
                            + unitLabel + "，实际折算 " + actual.stripTrailingZeros().toPlainString() + unitLabel);
        }
        return CheckOutcome.pass(expected,
                "实际年限 " + actual.stripTrailingZeros().toPlainString() + unitLabel
                        + "（" + start + " 至 " + submitDate + "，算头算尾）");
    }

    // ---------- 前置证书（连有效期一起验） ----------

    private CheckOutcome checkPrereqCert(ConditionItem item, Material material, LocalDate submitDate) {
        String gradePart = item.getRequiredGrade() == null ? "" : "（等级：" + item.getRequiredGrade() + "）";
        String expected = "持有效前置证书：" + item.getRequiredSubject() + gradePart
                + (Boolean.TRUE.equals(item.getCheckValidity()) ? "，且有效期覆盖提交日 " + submitDate : "");

        String certNo = material.getPrereqCertNo();
        if (certNo == null || certNo.trim().isEmpty()) {
            return CheckOutcome.fail(expected, "未填报前置证书编号",
                    "未提供前置证书编号，无法核验前置证书要求");
        }
        Certificate cert = certificateMapper.selectOne(new QueryWrapper<Certificate>()
                .eq("cert_no", certNo.trim().toUpperCase()).last("LIMIT 1"));
        if (cert == null) {
            // 外地证 / 历史旧证可能未入库：机器判不准，转人工，不直接误杀
            return CheckOutcome.manual(expected, "系统未查询到编号为 " + certNo.trim() + " 的证书",
                    "前置证书编号在证书库中无记录（可能为外地/旧证未入库），转人工核验证件原件");
        }
        String actualDesc = "证书 " + cert.getCertNo() + "（" + nullToDash(cert.getSubject())
                + (cert.getGrade() == null ? "" : "/" + cert.getGrade()) + "）";

        // 人证一致：姓名对不上不直接否决（曾用名、录入差错），转人工
        if (material.getName() != null && !material.getName().trim().equals(cert.getHolderName())) {
            return CheckOutcome.manual(expected, actualDesc + "，持证人「" + cert.getHolderName()
                            + "」与报名姓名「" + material.getName().trim() + "」不一致",
                    "前置证书持证人姓名与报名姓名不一致，转人工核对是否为同一人（曾用名/录入差错）");
        }
        if (CertConstants.CERT_REVOKED.equals(cert.getStatus())) {
            return CheckOutcome.fail(expected, actualDesc + "，已作废",
                    "前置证书已作废（作废原因：" + cert.getRevokeReason() + "），不符合前置证书要求");
        }
        if (!item.getRequiredSubject().equalsIgnoreCase(nullToEmpty(cert.getSubject()))) {
            return CheckOutcome.fail(expected, actualDesc,
                    "前置证书科目不符：要求「" + item.getRequiredSubject() + "」，证书为「"
                            + nullToDash(cert.getSubject()) + "」");
        }
        if (item.getRequiredGrade() != null
                && !item.getRequiredGrade().equalsIgnoreCase(nullToEmpty(cert.getGrade()))) {
            return CheckOutcome.fail(expected, actualDesc,
                    "前置证书等级不符：要求「" + item.getRequiredGrade() + "」，证书为「"
                            + nullToDash(cert.getGrade()) + "」");
        }

        // 连有效期一起验：提交日须落在 [validFrom, validTo] 闭区间
        if (Boolean.TRUE.equals(item.getCheckValidity())) {
            if (cert.getValidFrom() != null && submitDate.isBefore(cert.getValidFrom())) {
                return CheckOutcome.fail(expected, actualDesc + "，有效期 " + cert.getValidFrom()
                                + " 至 " + (cert.getValidTo() == null ? "长期" : cert.getValidTo()),
                        "前置证书尚未到有效期起期 " + cert.getValidFrom() + "（提交日 " + submitDate + "）");
            }
            if (cert.getValidTo() != null && submitDate.isAfter(cert.getValidTo())) {
                return CheckOutcome.fail(expected, actualDesc + "，有效期 "
                                + (cert.getValidFrom() == null ? "—" : cert.getValidFrom()) + " 至 " + cert.getValidTo(),
                        "前置证书已于 " + cert.getValidTo() + " 到期（提交日 " + submitDate + "），不符合有效期要求");
            }
            if (cert.getValidTo() == null) {
                if (Boolean.TRUE.equals(item.getValidityRequired())) {
                    return CheckOutcome.manual(expected, actualDesc + "，未登记有效期",
                            "规则要求登记证书有效期但该证书未登记有效期止，转人工核对是否仍在有效期内");
                }
                // 未登记有效期按长期有效处理，并在实际情况中写明，便于复核
                actualDesc += "，未登记有效期（按长期有效）";
            }
        }
        return CheckOutcome.pass(expected, actualDesc);
    }

    private static String nullToDash(String s) {
        return s == null ? "—" : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 核验时机器需要的学员材料视图。 */
    @Data
    public static class Material {
        private String name;
        private String education;
        private LocalDate workStartDate;
        private String prereqCertNo;

        public static Material of(String name, String education, LocalDate workStartDate, String prereqCertNo) {
            Material m = new Material();
            m.name = name;
            m.education = education;
            m.workStartDate = workStartDate;
            m.prereqCertNo = prereqCertNo;
            return m;
        }
    }
}
