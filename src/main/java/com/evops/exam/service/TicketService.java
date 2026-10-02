package com.evops.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.common.ExamConstants;
import com.evops.exam.dto.TicketActionRequest;
import com.evops.exam.entity.AdmissionTicket;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.AdmissionTicketMapper;
import com.evops.exam.vo.ActionPreview;
import com.evops.exam.vo.ActionResult;
import com.evops.exam.vo.AffectedItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发证：打证（PENDING->PRINTED）、发放（PRINTED->ISSUED）分两步，
 * 各留经办人和钟点；批量动作先 preview 影响范围再 execute。
 */
@Service
public class TicketService {

    private final AdmissionTicketMapper ticketMapper;
    private final RegistrationService registrationService;
    private final StageResultService stageResultService;

    public TicketService(AdmissionTicketMapper ticketMapper,
                         RegistrationService registrationService,
                         StageResultService stageResultService) {
        this.ticketMapper = ticketMapper;
        this.registrationService = registrationService;
        this.stageResultService = stageResultService;
    }

    // ---------- 目标准考证解析 ----------

    private List<AdmissionTicket> resolveTargets(TicketActionRequest request) {
        boolean hasNos = request.getTicketNos() != null && !request.getTicketNos().isEmpty();
        if (hasNos) {
            List<AdmissionTicket> tickets = new ArrayList<>();
            Set<String> dedup = new HashSet<>();
            for (String no : request.getTicketNos()) {
                String key = no.trim();
                if (!dedup.add(key)) {
                    throw new BusinessException("TICKET_DUP_IN_REQ", "请求中准考证号重复：" + key);
                }
                AdmissionTicket ticket = ticketMapper.selectOne(
                        new QueryWrapper<AdmissionTicket>().eq("ticket_no", key));
                if (ticket == null) {
                    throw new BusinessException("TICKET_NOT_FOUND", "准考证不存在：" + key);
                }
                tickets.add(ticket);
            }
            return tickets;
        }
        if (request.getBatchId() == null) {
            throw new BusinessException("TARGET_REQUIRED", "必须提供 batchId 或 ticketNos");
        }
        return ticketMapper.selectList(new QueryWrapper<AdmissionTicket>()
                .eq("batch_id", request.getBatchId())
                .orderByAsc("ticket_no"));
    }

    private Map<Long, String> loadNames(List<AdmissionTicket> tickets) {
        List<Long> regIds = new ArrayList<>();
        for (AdmissionTicket t : tickets) {
            regIds.add(t.getRegistrationId());
        }
        Map<Long, Registration> regs = registrationService.loadByIds(regIds);
        Map<Long, String> names = new LinkedHashMap<>();
        for (AdmissionTicket t : tickets) {
            Registration reg = regs.get(t.getRegistrationId());
            names.put(t.getId(), reg == null ? "-" : reg.getName());
        }
        return names;
    }

    // ---------- 打证：预检 ----------

    @Transactional(readOnly = true)
    public ActionPreview previewPrint(TicketActionRequest request) {
        return preview(request, ExamConstants.TICKET_PRINTED, "PRINT");
    }

    // ---------- 打证：执行 ----------

    @Transactional
    public ActionResult print(TicketActionRequest request) {
        return execute(request, ExamConstants.TICKET_PRINTED, "PRINT",
                ExamConstants.STAGE_PRINTING, "打证");
    }

    // ---------- 发放：预检 ----------

    @Transactional(readOnly = true)
    public ActionPreview previewIssue(TicketActionRequest request) {
        return preview(request, ExamConstants.TICKET_ISSUED, "ISSUE");
    }

    // ---------- 发放：执行 ----------

    @Transactional
    public ActionResult issue(TicketActionRequest request) {
        return execute(request, ExamConstants.TICKET_ISSUED, "ISSUE",
                ExamConstants.STAGE_ISSUANCE, "发放");
    }

    private ActionPreview preview(TicketActionRequest request, String targetStatus, String action) {
        List<AdmissionTicket> tickets = resolveTargets(request);
        Map<Long, String> names = loadNames(tickets);

        List<AffectedItem> items = new ArrayList<>();
        int actionable = 0;
        int skip = 0;
        for (AdmissionTicket t : tickets) {
            boolean ok = canTransit(t.getStatus(), targetStatus);
            String note = transitNote(t.getStatus(), targetStatus);
            if (ok) {
                actionable++;
            } else {
                skip++;
            }
            items.add(new AffectedItem(t.getTicketNo(), names.get(t.getId()), t.getStatus(), ok, note));
        }
        ActionPreview preview = new ActionPreview();
        preview.setAction(action);
        preview.setTotalScanned(items.size());
        preview.setActionableCount(actionable);
        preview.setSkipCount(skip);
        preview.setSummary(String.format("本次%s范围 %d 张：可处理 %d 张，跳过 %d 张",
                actionLabel(action), items.size(), actionable, skip));
        preview.setItems(items);
        return preview;
    }

    private ActionResult execute(TicketActionRequest request, String targetStatus, String action,
                                 String stage, String stageLabel) {
        List<AdmissionTicket> tickets = resolveTargets(request);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        List<String> affected = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Long batchId = null;
        for (AdmissionTicket t : tickets) {
            batchId = t.getBatchId();
            if (!canTransit(t.getStatus(), targetStatus)) {
                skipped.add(t.getTicketNo() + "（当前状态 " + t.getStatus() + "，"
                        + transitNote(t.getStatus(), targetStatus) + "）");
                continue;
            }
            if (ExamConstants.TICKET_PRINTED.equals(targetStatus)) {
                t.setStatus(ExamConstants.TICKET_PRINTED);
                t.setPrintedBy(operator);
                t.setPrintedAt(now);
            } else {
                t.setStatus(ExamConstants.TICKET_ISSUED);
                t.setIssuedBy(operator);
                t.setIssuedAt(now);
            }
            ticketMapper.updateById(t);
            affected.add(t.getTicketNo());
        }

        if (batchId != null) {
            stageResultService.record(batchId, stage, affected.size(),
                    String.format("%s %d 张，跳过 %d 张，经办人 %s",
                            stageLabel, affected.size(), skipped.size(), operator),
                    operator);
        }

        ActionResult result = new ActionResult();
        result.setAction(action);
        result.setAffectedCount(affected.size());
        result.setSkipCount(skipped.size());
        result.setOperator(operator);
        result.setExecutedAt(now.toString());
        result.setAffectedKeys(affected);
        result.setSkipped(skipped);
        return result;
    }

    /** 状态机：PENDING -> PRINTED -> ISSUED，只能前进不能跳步 / 回退 */
    private boolean canTransit(String current, String target) {
        if (ExamConstants.TICKET_PRINTED.equals(target)) {
            return ExamConstants.TICKET_PENDING.equals(current);
        }
        if (ExamConstants.TICKET_ISSUED.equals(target)) {
            return ExamConstants.TICKET_PRINTED.equals(current);
        }
        return false;
    }

    private String transitNote(String current, String target) {
        if (ExamConstants.TICKET_PRINTED.equals(target)) {
            if (ExamConstants.TICKET_PENDING.equals(current)) {
                return "待打印，将标记已打印";
            }
            if (ExamConstants.TICKET_ISSUED.equals(current)) {
                return "已发放，无需再打印，跳过";
            }
            return "已打印，重复操作跳过";
        }
        if (ExamConstants.TICKET_ISSUED.equals(target)) {
            if (ExamConstants.TICKET_PRINTED.equals(current)) {
                return "已打印，将标记已发放";
            }
            if (ExamConstants.TICKET_ISSUED.equals(current)) {
                return "已发放，重复操作跳过";
            }
            return "未打印，不能发放，跳过（须先打证）";
        }
        return "状态不符，跳过";
    }

    private String actionLabel(String action) {
        return "ISSUE".equals(action) ? "发放" : "打证";
    }
}
