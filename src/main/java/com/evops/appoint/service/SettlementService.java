package com.evops.appoint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.appoint.common.AppointConstants;
import com.evops.appoint.common.SelfHolder;
import com.evops.appoint.dto.SettlementAdjustRequest;
import com.evops.appoint.dto.SettlementGenerateRequest;
import com.evops.appoint.entity.Appointment;
import com.evops.appoint.entity.Settlement;
import com.evops.appoint.entity.SettlementAdjustment;
import com.evops.appoint.entity.SettlementItem;
import com.evops.appoint.mapper.AppointmentMapper;
import com.evops.appoint.mapper.SettlementAdjustmentMapper;
import com.evops.appoint.mapper.SettlementItemMapper;
import com.evops.appoint.mapper.SettlementMapper;
import com.evops.appoint.vo.SettlementPreview;
import com.evops.appoint.vo.SettlementPreviewItem;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 月底结算：
 * - 一键出结算单：按签到月汇总已签到（CHECKED_IN / billable）且未锁定的聘任，一人一行；
 * - 两步：先 preview 看全量口径，确认后再 generate 出草稿；草稿可重复生成 / 重算；
 * - 人事看全量、本人（X-Self-Code）只看本人那一行；
 * - 确认后锁定：聘任置 settled 并挂结算单，之后要改只能走调整（只追加台账）。
 */
@Service
public class SettlementService {

    private final SettlementMapper settlementMapper;
    private final SettlementItemMapper itemMapper;
    private final SettlementAdjustmentMapper adjustmentMapper;
    private final AppointmentMapper appointmentMapper;

    public SettlementService(SettlementMapper settlementMapper,
                             SettlementItemMapper itemMapper,
                             SettlementAdjustmentMapper adjustmentMapper,
                             AppointmentMapper appointmentMapper) {
        this.settlementMapper = settlementMapper;
        this.itemMapper = itemMapper;
        this.adjustmentMapper = adjustmentMapper;
        this.appointmentMapper = appointmentMapper;
    }

    // ==================== 生成结算单：预检 / 草稿 / 确认 ====================

    @Transactional(readOnly = true)
    public SettlementPreview previewGenerate(SettlementGenerateRequest request) {
        YearMonth month = parseMonth(request == null ? null : request.getMonth());
        Period period = Period.of(month);
        List<AggRow> rows = aggregate(period);
        Settlement existing = findByPeriod(period);

        SettlementPreview preview = new SettlementPreview();
        preview.setMonth(month.toString());
        preview.setPeriodStart(period.start);
        preview.setPeriodEnd(period.end);
        preview.setPersonCount(rows.size());
        preview.setTotalAppointments(sumSessions(rows));
        preview.setTotalAmount(sumAmount(rows));
        preview.setExistingSettlementNo(existing == null ? null : existing.getSettlementNo());
        preview.setExistingStatus(existing == null ? null : existing.getStatus());
        preview.setNote(existing == null
                ? "区间内已签到且未锁定的聘任将生成草稿；缺勤 / 未签到 / 已取消 / 被换下不计费"
                : (AppointConstants.SETTLE_CONFIRMED.equals(existing.getStatus())
                        ? "该月结算单已确认锁定，不能重新生成；要改走调整"
                        : "该月已有草稿 " + existing.getSettlementNo() + "，再次生成将按最新签到重算覆盖草稿"));
        preview.setItems(toPreviewItems(rows));
        return preview;
    }

    /** 生成（或重算）草稿。 */
    @Transactional
    public Settlement generate(SettlementGenerateRequest request) {
        YearMonth month = parseMonth(request == null ? null : request.getMonth());
        Period period = Period.of(month);
        Settlement confirmed = findConfirmedByPeriod(period);
        if (confirmed != null) {
            throw new BusinessException("SETTLEMENT_ALREADY_CONFIRMED",
                    month + " 结算单 " + confirmed.getSettlementNo() + " 已确认锁定，不能重新生成；要改请走调整");
        }

        List<AggRow> rows = aggregate(period);
        String operator = OperatorHolder.current();

        Settlement settlement = findByPeriod(period);
        if (settlement == null) {
            settlement = new Settlement();
            settlement.setSettlementNo(nextSettlementNo(month));
            settlement.setPeriodStart(period.start);
            settlement.setPeriodEnd(period.end);
            settlement.setPeriodLabel(month.toString());
            settlement.setOperator(operator);
            settlement.setStatus(AppointConstants.SETTLE_DRAFT);
            fillTotals(settlement, rows);
            settlement.setAdjustCount(0);
            settlement.setRemark(request == null ? null : request.getRemark());
            settlementMapper.insert(settlement);
        } else {
            // 草稿重算：先删旧明细再重建；调整只可能在确认后产生，草稿无调整
            itemMapper.delete(new QueryWrapper<SettlementItem>()
                    .eq("settlement_id", settlement.getId()));
            fillTotals(settlement, rows);
            settlement.setAdjustCount(0);
            if (request != null && request.getRemark() != null) {
                settlement.setRemark(request.getRemark());
            }
            settlement.setOperator(operator);
            settlementMapper.updateById(settlement);
        }

        for (AggRow row : rows) {
            SettlementItem item = new SettlementItem();
            item.setSettlementId(settlement.getId());
            item.setPeriodStart(period.start);
            item.setPeriodEnd(period.end);
            item.setInvigilatorId(row.invigilatorId);
            item.setInvigilatorCode(row.code);
            item.setInvigilatorName(row.name);
            item.setSessionCount(row.sessionCount);
            item.setAmount(row.amount);
            item.setPayableAmount(row.amount);
            itemMapper.insert(item);
        }
        return settlement;
    }

    /** 确认草稿：重算一次以纳入补签到，随后把区间内计费聘任锁定到本结算单。 */
    @Transactional
    public Settlement confirm(Long settlementId) {
        Settlement settlement = requireSettlement(settlementId);
        if (AppointConstants.SETTLE_CONFIRMED.equals(settlement.getStatus())) {
            throw new BusinessException("SETTLEMENT_ALREADY_CONFIRMED",
                    "结算单 " + settlement.getSettlementNo() + " 已确认，不能重复确认");
        }
        String operator = OperatorHolder.current();
        Period period = Period.of(YearMonth.parse(settlement.getPeriodLabel()));

        // 确认前按最新签到重建明细（草稿期间的补签到在此纳入）
        List<AggRow> rows = aggregate(period);
        itemMapper.delete(new QueryWrapper<SettlementItem>().eq("settlement_id", settlement.getId()));
        fillTotals(settlement, rows);
        settlement.setAdjustCount(0);

        // 锁定计费聘任：settled=true + settlement_id；之后这些聘任不能再签到 / 换人 / 取消
        List<Appointment> billable = billableInPeriod(period);
        for (Appointment a : billable) {
            a.setSettled(true);
            a.setSettlementId(settlement.getId());
            appointmentMapper.updateById(a);
        }
        for (AggRow row : rows) {
            SettlementItem item = new SettlementItem();
            item.setSettlementId(settlement.getId());
            item.setPeriodStart(period.start);
            item.setPeriodEnd(period.end);
            item.setInvigilatorId(row.invigilatorId);
            item.setInvigilatorCode(row.code);
            item.setInvigilatorName(row.name);
            item.setSessionCount(row.sessionCount);
            item.setAmount(row.amount);
            item.setPayableAmount(row.amount);
            itemMapper.insert(item);
        }

        settlement.setStatus(AppointConstants.SETTLE_CONFIRMED);
        settlement.setConfirmedBy(operator);
        settlement.setConfirmedAt(java.time.LocalDateTime.now());
        settlement.setOperator(operator);
        settlementMapper.updateById(settlement);
        return settlement;
    }

    // ==================== 锁定后调整（只追加） ====================

    @Transactional
    public SettlementAdjustment adjust(Long settlementId, SettlementAdjustRequest request) {
        Settlement settlement = requireSettlement(settlementId);
        if (!AppointConstants.SETTLE_CONFIRMED.equals(settlement.getStatus())) {
            throw new BusinessException("SETTLEMENT_NOT_CONFIRMED",
                    "结算单 " + settlement.getSettlementNo() + " 尚未确认，草稿可直接重新生成，无需调整");
        }
        if (request.getAdjustAmount() == null || request.getAdjustAmount().compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("ADJUST_AMOUNT_ZERO", "调整金额不能为 0");
        }
        String code = request.getInvigilatorCode().trim();
        SettlementItem item = itemMapper.selectOne(new QueryWrapper<SettlementItem>()
                .eq("settlement_id", settlement.getId())
                .eq("invigilator_code", code)
                .last("LIMIT 1"));
        if (item == null) {
            throw new BusinessException("SETTLEMENT_ITEM_NOT_FOUND",
                    "结算单 " + settlement.getSettlementNo() + " 中没有监考人员 " + code + " 的明细，不能调整");
        }

        String operator = OperatorHolder.current();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        SettlementAdjustment adj = new SettlementAdjustment();
        adj.setSettlementId(settlement.getId());
        adj.setInvigilatorId(item.getInvigilatorId());
        adj.setInvigilatorCode(item.getInvigilatorCode());
        adj.setInvigilatorName(item.getInvigilatorName());
        adj.setAdjustAmount(request.getAdjustAmount());
        adj.setReason(request.getReason().trim());
        adj.setOperator(operator);
        adj.setAdjustedAt(now);
        adjustmentMapper.insert(adj);

        item.setPayableAmount(item.getPayableAmount().add(request.getAdjustAmount()));
        itemMapper.updateById(item);

        settlement.setPayableAmount(settlement.getPayableAmount().add(request.getAdjustAmount()));
        settlement.setAdjustCount(settlement.getAdjustCount() == null ? 1 : settlement.getAdjustCount() + 1);
        settlementMapper.updateById(settlement);
        return adj;
    }

    @Transactional(readOnly = true)
    public List<SettlementAdjustment> adjustments(Long settlementId) {
        requireSettlement(settlementId);
        return adjustmentMapper.selectList(new QueryWrapper<SettlementAdjustment>()
                .eq("settlement_id", settlementId)
                .orderByAsc("id"));
    }

    // ==================== 查询：人事全量 / 本人 ====================

    @Transactional(readOnly = true)
    public List<Settlement> listSettlements(String month) {
        QueryWrapper<Settlement> wrapper = new QueryWrapper<Settlement>().orderByDesc("period_start", "id");
        if (month != null && !month.trim().isEmpty()) {
            Period period = Period.of(parseMonth(month));
            wrapper.eq("period_start", period.start);
        }
        return settlementMapper.selectList(wrapper);
    }

    /** 结算单 + 明细；本人视图只返回本人那一行。 */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long settlementId) {
        Settlement settlement = requireSettlement(settlementId);
        List<SettlementItem> items = itemMapper.selectList(new QueryWrapper<SettlementItem>()
                .eq("settlement_id", settlement.getId())
                .orderByAsc("invigilator_code"));
        String selfCode = SelfHolder.currentSelfCode();
        if (selfCode != null) {
            String code = selfCode.trim();
            List<SettlementItem> mine = new ArrayList<>();
            for (SettlementItem it : items) {
                if (code.equals(it.getInvigilatorCode())) {
                    mine.add(it);
                }
            }
            items = mine;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("settlement", settlement);
        result.put("items", items);
        result.put("view", selfCode == null ? "HR_ALL" : "SELF");
        return result;
    }

    /** 本人视图：跨结算单只看本人明细（人事全量请勿带 X-Self-Code）。 */
    @Transactional(readOnly = true)
    public List<SettlementItem> myItems(String month) {
        String selfCode = SelfHolder.currentSelfCode();
        if (selfCode == null) {
            throw new BusinessException("SELF_CODE_REQUIRED",
                    "本人明细接口需带请求头 X-Self-Code；人事全量请用结算单详情接口");
        }
        QueryWrapper<SettlementItem> wrapper = new QueryWrapper<SettlementItem>()
                .eq("invigilator_code", selfCode.trim())
                .orderByDesc("period_start", "id");
        if (month != null && !month.trim().isEmpty()) {
            Period period = Period.of(parseMonth(month));
            wrapper.eq("period_start", period.start);
        }
        return itemMapper.selectList(wrapper);
    }

    // ==================== 汇总口径 ====================

    private List<AggRow> aggregate(Period period) {
        Map<Long, AggRow> byPerson = new LinkedHashMap<>();
        for (Appointment a : billableInPeriod(period)) {
            AggRow row = byPerson.get(a.getInvigilatorId());
            if (row == null) {
                row = new AggRow();
                row.invigilatorId = a.getInvigilatorId();
                row.code = a.getInvigilatorCode();
                row.name = a.getInvigilatorName();
                row.sessionCount = 0;
                row.amount = BigDecimal.ZERO;
                byPerson.put(a.getInvigilatorId(), row);
            }
            row.sessionCount++;
            row.amount = row.amount.add(a.getAllowanceAmount() == null ? BigDecimal.ZERO : a.getAllowanceAmount());
        }
        return new ArrayList<>(byPerson.values());
    }

    /** 区间内计费聘任：已签到、billable、签到时间落在周期 [start,end)、未被确认结算单锁定。 */
    private List<Appointment> billableInPeriod(Period period) {
        return appointmentMapper.selectList(new QueryWrapper<Appointment>()
                .eq("status", AppointConstants.APPT_CHECKED_IN)
                .eq("billable", true)
                .eq("settled", false)
                .ge("sign_in_at", period.start.atStartOfDay())
                .lt("sign_in_at", period.end.atStartOfDay())
                .orderByAsc("invigilator_id", "id"));
    }

    private List<SettlementPreviewItem> toPreviewItems(List<AggRow> rows) {
        List<SettlementPreviewItem> items = new ArrayList<>();
        for (AggRow row : rows) {
            SettlementPreviewItem item = new SettlementPreviewItem();
            item.setInvigilatorCode(row.code);
            item.setInvigilatorName(row.name);
            item.setSessionCount(row.sessionCount);
            item.setAmount(row.amount);
            items.add(item);
        }
        return items;
    }

    private int sumSessions(List<AggRow> rows) {
        int total = 0;
        for (AggRow row : rows) {
            total += row.sessionCount;
        }
        return total;
    }

    private BigDecimal sumAmount(List<AggRow> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (AggRow row : rows) {
            total = total.add(row.amount);
        }
        return total;
    }

    private void fillTotals(Settlement settlement, List<AggRow> rows) {
        settlement.setTotalAppointments(sumSessions(rows));
        BigDecimal amount = sumAmount(rows);
        settlement.setTotalAmount(amount);
        settlement.setPayableAmount(amount);
    }

    private Settlement requireSettlement(Long id) {
        if (id == null) {
            throw new BusinessException("SETTLEMENT_ID_REQUIRED", "结算单 id 不能为空");
        }
        Settlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("SETTLEMENT_NOT_FOUND", "结算单不存在：id=" + id);
        }
        return settlement;
    }

    private Settlement findByPeriod(Period period) {
        return settlementMapper.selectOne(new QueryWrapper<Settlement>()
                .eq("period_start", period.start)
                .orderByDesc("id")
                .last("LIMIT 1"));
    }

    private Settlement findConfirmedByPeriod(Period period) {
        return settlementMapper.selectOne(new QueryWrapper<Settlement>()
                .eq("period_start", period.start)
                .eq("status", AppointConstants.SETTLE_CONFIRMED)
                .last("LIMIT 1"));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.trim().isEmpty()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException("MONTH_INVALID", "结算月份格式不正确，应为 yyyy-MM：" + month);
        }
    }

    /** 结算单号：JS + yyyyMM + 两位流水。 */
    private String nextSettlementNo(YearMonth month) {
        String prefix = "JS" + month.toString().replace("-", "");
        int max = 0;
        for (Settlement s : settlementMapper.selectList(new QueryWrapper<Settlement>()
                .likeRight("settlement_no", prefix))) {
            String tail = s.getSettlementNo().substring(prefix.length());
            try {
                max = Math.max(max, Integer.parseInt(tail));
            } catch (NumberFormatException ignored) {
                // 非本周期流水忽略
            }
        }
        return prefix + String.format("%02d", max + 1);
    }

    /** 结算周期：起含止不含。 */
    private static class Period {
        private LocalDate start;
        private LocalDate end;

        private static Period of(YearMonth month) {
            Period p = new Period();
            p.start = month.atDay(1);
            p.end = month.plusMonths(1).atDay(1);
            return p;
        }
    }

    private static class AggRow {
        private Long invigilatorId;
        private String code;
        private String name;
        private int sessionCount;
        private BigDecimal amount;
    }
}
