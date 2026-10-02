package com.evops.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.exam.common.ExamConstants;
import com.evops.exam.dto.BatchRequest;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.mapper.ExamBatchMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExamBatchService {

    private final ExamBatchMapper batchMapper;
    private final ExamSiteService siteService;

    public ExamBatchService(ExamBatchMapper batchMapper, ExamSiteService siteService) {
        this.batchMapper = batchMapper;
        this.siteService = siteService;
    }

    @Transactional
    public ExamBatch createBatch(BatchRequest request) {
        siteService.getSite(request.getSiteId());
        validateWindow(request.getReviewStartAt(), request.getReviewEndAt());
        ExamBatch batch = new ExamBatch();
        batch.setCode(request.getCode().trim());
        batch.setName(request.getName().trim());
        batch.setSiteId(request.getSiteId());
        batch.setProjectCode(normalizeProject(request.getProjectCode()));
        batch.setSubject(request.getSubject());
        batch.setExamDate(request.getExamDate());
        batch.setStartTime(request.getStartTime());
        batch.setEndTime(request.getEndTime());
        batch.setReviewStartAt(request.getReviewStartAt());
        batch.setReviewEndAt(request.getReviewEndAt());
        batch.setStatus(ExamConstants.BATCH_CREATED);
        batch.setRemark(request.getRemark());
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_BATCH_CODE", "批次编码已存在：" + batch.getCode());
        }
        return batch;
    }

    /** 配置 / 调整复核窗口：窗口起止跟批次配置走，不写死；起止必须成对、起早于止。 */
    @Transactional
    public ExamBatch configureReviewWindow(Long batchId, java.time.LocalDateTime startAt,
                                           java.time.LocalDateTime endAt) {
        ExamBatch batch = getBatch(batchId);
        validateWindow(startAt, endAt);
        batch.setReviewStartAt(startAt);
        batch.setReviewEndAt(endAt);
        batchMapper.updateById(batch);
        return batch;
    }

    private void validateWindow(java.time.LocalDateTime startAt, java.time.LocalDateTime endAt) {
        boolean startMissing = startAt == null;
        boolean endMissing = endAt == null;
        if (startMissing != endMissing) {
            throw new BusinessException("REVIEW_WINDOW_HALF", "复核窗口起止必须同时配置或同时留空");
        }
        if (!startMissing && !startAt.isBefore(endAt)) {
            throw new BusinessException("REVIEW_WINDOW_RANGE", "复核窗口开始时间必须早于结束时间");
        }
    }

    private String normalizeProject(String projectCode) {
        if (projectCode == null || projectCode.trim().isEmpty()) {
            return com.evops.grade.common.GradeConstants.PROJECT_DEFAULT;
        }
        return projectCode.trim().toUpperCase();
    }

    public List<ExamBatch> listBatches() {
        return batchMapper.selectList(new QueryWrapper<ExamBatch>().orderByDesc("create_time"));
    }

    public ExamBatch getBatch(Long id) {
        ExamBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException("BATCH_NOT_FOUND", "批次不存在：id=" + id);
        }
        return batch;
    }

    public void updateBatch(ExamBatch batch) {
        batchMapper.updateById(batch);
    }

    public List<ExamBatch> listByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return batchMapper.selectBatchIds(ids);
    }
}
