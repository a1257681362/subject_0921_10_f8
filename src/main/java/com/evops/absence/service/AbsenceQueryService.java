package com.evops.absence.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.absence.entity.ScoreSnapshot;
import com.evops.absence.mapper.ScoreSnapshotMapper;
import com.evops.absence.vo.PassListItem;
import com.evops.absence.vo.PassListView;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.service.ExamBatchService;
import com.evops.grade.entity.ExamScore;
import com.evops.grade.mapper.ExamScoreMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 缺考裁定查询：合格名单（成绩有效即进名单，带等级）与成绩快照（逐人逐笔，只追加）。
 */
@Service
public class AbsenceQueryService {

    private final ExamScoreMapper scoreMapper;
    private final ScoreSnapshotMapper snapshotMapper;
    private final ExamBatchService batchService;

    public AbsenceQueryService(ExamScoreMapper scoreMapper,
                               ScoreSnapshotMapper snapshotMapper,
                               ExamBatchService batchService) {
        this.scoreMapper = scoreMapper;
        this.snapshotMapper = snapshotMapper;
        this.batchService = batchService;
    }

    /** 合格名单：本批次成绩有效（无生效的无效化裁定）的学员，按报名号排序，按等级汇总。 */
    @Transactional(readOnly = true)
    public PassListView passList(Long batchId) {
        ExamBatch batch = batchService.getBatch(batchId);
        List<ExamScore> scores = scoreMapper.selectList(new QueryWrapper<ExamScore>()
                .eq("batch_id", batchId)
                .eq("valid", true)
                .orderByAsc("reg_no"));
        List<PassListItem> items = new ArrayList<>();
        Map<String, Integer> gradeSummary = new LinkedHashMap<>();
        for (ExamScore score : scores) {
            items.add(new PassListItem(score.getRegNo(), score.getName(), score.getPaperScore(),
                    score.getGrade(), score.getRuleVersionNo()));
            String grade = score.getGrade() == null || score.getGrade().trim().isEmpty()
                    ? "未定级" : score.getGrade();
            gradeSummary.merge(grade, 1, Integer::sum);
        }
        PassListView view = new PassListView();
        view.setBatchId(batch.getId());
        view.setBatchCode(batch.getCode());
        view.setTotalCount(items.size());
        view.setGradeSummary(gradeSummary);
        view.setItems(items);
        return view;
    }

    /** 成绩快照：裁定确认时逐人逐笔留痕，只追加。 */
    @Transactional(readOnly = true)
    public List<ScoreSnapshot> snapshots(Long batchId, String regNo) {
        QueryWrapper<ScoreSnapshot> wrapper = new QueryWrapper<ScoreSnapshot>()
                .orderByDesc("snapshotted_at", "id");
        if (batchId != null) {
            wrapper.eq("batch_id", batchId);
        }
        if (regNo != null && !regNo.trim().isEmpty()) {
            wrapper.eq("reg_no", regNo.trim());
        }
        return snapshotMapper.selectList(wrapper);
    }
}
