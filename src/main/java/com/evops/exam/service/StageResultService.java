package com.evops.exam.service;

import com.evops.exam.entity.StageResult;
import com.evops.exam.mapper.StageResultMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 阶段结果落库：与最终结论分表保存，互不覆盖。
 */
@Service
public class StageResultService {

    private final StageResultMapper stageResultMapper;

    public StageResultService(StageResultMapper stageResultMapper) {
        this.stageResultMapper = stageResultMapper;
    }

    public void record(Long batchId, String stage, int affectedCount, String detail, String operator) {
        StageResult result = new StageResult();
        result.setBatchId(batchId);
        result.setStage(stage);
        result.setAffectedCount(affectedCount);
        result.setDetail(detail);
        result.setOperator(operator);
        result.setExecutedAt(LocalDateTime.now());
        stageResultMapper.insert(result);
    }
}
