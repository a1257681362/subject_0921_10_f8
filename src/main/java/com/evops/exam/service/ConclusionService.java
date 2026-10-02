package com.evops.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.common.ExamConstants;
import com.evops.exam.entity.AdmissionTicket;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.FinalConclusion;
import com.evops.exam.mapper.AdmissionTicketMapper;
import com.evops.exam.mapper.FinalConclusionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 最终结论：每个批次一条，独立成表，与各阶段结果分开保存。
 */
@Service
public class ConclusionService {

    private final FinalConclusionMapper conclusionMapper;
    private final AdmissionTicketMapper ticketMapper;
    private final ExamBatchService batchService;

    public ConclusionService(FinalConclusionMapper conclusionMapper,
                             AdmissionTicketMapper ticketMapper,
                             ExamBatchService batchService) {
        this.conclusionMapper = conclusionMapper;
        this.ticketMapper = ticketMapper;
        this.batchService = batchService;
    }

    @Transactional
    public FinalConclusion conclude(Long batchId) {
        ExamBatch batch = batchService.getBatch(batchId);
        String operator = OperatorHolder.current();

        List<AdmissionTicket> tickets = ticketMapper.selectList(
                new QueryWrapper<AdmissionTicket>().eq("batch_id", batchId));
        int total = tickets.size();
        if (total == 0) {
            throw new BusinessException("NOT_ARRANGED", "批次尚未编排，没有准考证可办结：" + batch.getCode());
        }
        int printed = 0;
        int issued = 0;
        for (AdmissionTicket t : tickets) {
            if (ExamConstants.TICKET_PRINTED.equals(t.getStatus())
                    || ExamConstants.TICKET_ISSUED.equals(t.getStatus())) {
                printed++;
            }
            if (ExamConstants.TICKET_ISSUED.equals(t.getStatus())) {
                issued++;
            }
        }

        String code = issued == total ? "ALL_ISSUED" : (printed == total ? "ALL_PRINTED" : "PARTIAL");
        String summary = String.format(
                "批次 %s 办结：准考证 %d 张，已打印 %d 张，已发放 %d 张（结论码 %s）",
                batch.getCode(), total, printed, issued, code);

        FinalConclusion conclusion = conclusionMapper.selectOne(
                new QueryWrapper<FinalConclusion>().eq("batch_id", batchId));
        LocalDateTime now = LocalDateTime.now();
        if (conclusion == null) {
            conclusion = new FinalConclusion();
            conclusion.setBatchId(batchId);
        }
        conclusion.setConclusionCode(code);
        conclusion.setSummary(summary);
        conclusion.setTotalCount(total);
        conclusion.setPrintedCount(printed);
        conclusion.setIssuedCount(issued);
        conclusion.setOperator(operator);
        conclusion.setConcludedAt(now);
        if (conclusion.getId() == null) {
            conclusionMapper.insert(conclusion);
        } else {
            conclusionMapper.updateById(conclusion);
        }

        batch.setStatus(ExamConstants.BATCH_FINALIZED);
        batchService.updateBatch(batch);
        return conclusion;
    }

    public FinalConclusion getByBatch(Long batchId) {
        batchService.getBatch(batchId);
        FinalConclusion conclusion = conclusionMapper.selectOne(
                new QueryWrapper<FinalConclusion>().eq("batch_id", batchId));
        if (conclusion == null) {
            throw new BusinessException("CONCLUSION_NOT_FOUND", "批次尚未生成最终结论：id=" + batchId);
        }
        return conclusion;
    }

    /** 概览用：未办结时返回 null，不抛错。 */
    public FinalConclusion safeGetByBatch(Long batchId) {
        batchService.getBatch(batchId);
        return conclusionMapper.selectOne(
                new QueryWrapper<FinalConclusion>().eq("batch_id", batchId));
    }
}
