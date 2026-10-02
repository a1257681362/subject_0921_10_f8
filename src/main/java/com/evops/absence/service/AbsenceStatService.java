package com.evops.absence.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.absence.common.AbsenceConstants;
import com.evops.absence.entity.AbsenceMark;
import com.evops.absence.entity.AbsenceMarkVersion;
import com.evops.absence.entity.AbsenceStat;
import com.evops.absence.entity.ExamineeCheckin;
import com.evops.absence.mapper.AbsenceMarkMapper;
import com.evops.absence.mapper.AbsenceMarkVersionMapper;
import com.evops.absence.mapper.AbsenceStatMapper;
import com.evops.absence.mapper.ExamineeCheckinMapper;
import com.evops.common.OperatorHolder;
import com.evops.exam.mapper.RegistrationMapper;
import com.evops.exam.service.ExamBatchService;
import com.evops.grade.entity.ExamScore;
import com.evops.grade.mapper.ExamScoreMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批次缺考统计：每批次一行当前值。
 * 统计只认最终裁定——分类型计数取每个标记最新一条 CONFIRMED 版本的结论；
 * 裁定有变更就重算（generation 递增），重算触发：裁定确认 / 签到生效 / 补录审批 / 手动重算。
 */
@Service
public class AbsenceStatService {

    private final AbsenceStatMapper statMapper;
    private final AbsenceMarkMapper markMapper;
    private final AbsenceMarkVersionMapper versionMapper;
    private final ExamineeCheckinMapper checkinMapper;
    private final RegistrationMapper registrationMapper;
    private final ExamScoreMapper scoreMapper;
    private final ExamBatchService batchService;
    private final ObjectMapper objectMapper;

    public AbsenceStatService(AbsenceStatMapper statMapper,
                              AbsenceMarkMapper markMapper,
                              AbsenceMarkVersionMapper versionMapper,
                              ExamineeCheckinMapper checkinMapper,
                              RegistrationMapper registrationMapper,
                              ExamScoreMapper scoreMapper,
                              ExamBatchService batchService,
                              ObjectMapper objectMapper) {
        this.statMapper = statMapper;
        this.markMapper = markMapper;
        this.versionMapper = versionMapper;
        this.checkinMapper = checkinMapper;
        this.registrationMapper = registrationMapper;
        this.scoreMapper = scoreMapper;
        this.batchService = batchService;
        this.objectMapper = objectMapper;
    }

    /** 重算并落库（每批次一行，generation+1）。 */
    @Transactional
    public AbsenceStat recompute(Long batchId, String triggerReason, String operator) {
        batchService.getBatch(batchId);
        LocalDateTime now = LocalDateTime.now();

        AbsenceStat stat = statMapper.selectOne(new QueryWrapper<AbsenceStat>().eq("batch_id", batchId));
        if (stat == null) {
            stat = new AbsenceStat();
            stat.setBatchId(batchId);
            stat.setGeneration(0);
        }

        stat.setTotalRegistrations(countInt(registrationMapper.selectCount(
                new QueryWrapper<com.evops.exam.entity.Registration>().eq("batch_id", batchId))));
        stat.setCheckedInCount(countInt(checkinMapper.selectCount(new QueryWrapper<ExamineeCheckin>()
                .eq("batch_id", batchId).eq("status", AbsenceConstants.CHECKIN_CONFIRMED))));
        stat.setSupplementPendingCount(countInt(checkinMapper.selectCount(new QueryWrapper<ExamineeCheckin>()
                .eq("batch_id", batchId).eq("status", AbsenceConstants.CHECKIN_PENDING))));
        stat.setMarkPendingCount(countInt(markMapper.selectCount(new QueryWrapper<AbsenceMark>()
                .eq("batch_id", batchId).eq("status", AbsenceConstants.MARK_PENDING))));

        // 只认最终裁定：每个标记最新一条 CONFIRMED 版本的结论
        int absent = 0;
        int abandoned = 0;
        int violation = 0;
        for (AbsenceMarkVersion v : latestConfirmedVersions(batchId)) {
            if (!Boolean.FALSE.equals(v.getScoreValid())) {
                continue;
            }
            if (AbsenceConstants.TYPE_ABSENT.equals(v.getMarkType())) {
                absent++;
            } else if (AbsenceConstants.TYPE_ABANDONED.equals(v.getMarkType())) {
                abandoned++;
            } else if (AbsenceConstants.TYPE_VIOLATION.equals(v.getMarkType())) {
                violation++;
            }
        }
        stat.setAbsentCount(absent);
        stat.setAbandonedCount(abandoned);
        stat.setViolationCount(violation);

        List<ExamScore> scores = scoreMapper.selectList(new QueryWrapper<ExamScore>().eq("batch_id", batchId));
        int valid = 0;
        int invalid = 0;
        Map<String, Integer> gradeSummary = new LinkedHashMap<>();
        for (ExamScore score : scores) {
            if (Boolean.FALSE.equals(score.getValid())) {
                invalid++;
            } else {
                valid++;
                String grade = score.getGrade() == null || score.getGrade().trim().isEmpty()
                        ? "未定级" : score.getGrade();
                gradeSummary.merge(grade, 1, Integer::sum);
            }
        }
        stat.setScoreValidCount(valid);
        stat.setScoreInvalidCount(invalid);
        stat.setPassListCount(valid);
        try {
            stat.setGradeSummary(objectMapper.writeValueAsString(gradeSummary));
        } catch (Exception e) {
            stat.setGradeSummary("{}");
        }

        stat.setGeneration(stat.getGeneration() == null ? 1 : stat.getGeneration() + 1);
        stat.setTriggerReason(triggerReason);
        stat.setOperator(operator == null ? OperatorHolder.current() : operator);
        stat.setComputedAt(now);
        if (stat.getId() == null) {
            statMapper.insert(stat);
        } else {
            statMapper.updateById(stat);
        }
        return stat;
    }

    /** 查当前统计；还没有统计行时先算一代再返回。 */
    @Transactional
    public AbsenceStat getOrCompute(Long batchId) {
        batchService.getBatch(batchId);
        AbsenceStat stat = statMapper.selectOne(new QueryWrapper<AbsenceStat>().eq("batch_id", batchId));
        if (stat != null) {
            return stat;
        }
        return recompute(batchId, "首次统计", OperatorHolder.current());
    }

    /** 每个标记最新一条 CONFIRMED 版本（最终裁定口径，变更复核期间仍认原裁定）。 */
    List<AbsenceMarkVersion> latestConfirmedVersions(Long batchId) {
        List<AbsenceMark> marks = markMapper.selectList(new QueryWrapper<AbsenceMark>()
                .eq("batch_id", batchId));
        if (marks.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> markTypeById = new HashMap<>();
        List<Long> markIds = new ArrayList<>();
        for (AbsenceMark mark : marks) {
            markTypeById.put(mark.getId(), mark.getMarkType());
            markIds.add(mark.getId());
        }
        List<AbsenceMarkVersion> confirmed = versionMapper.selectList(new QueryWrapper<AbsenceMarkVersion>()
                .in("mark_id", markIds)
                .eq("status", AbsenceConstants.MARK_CONFIRMED)
                .orderByAsc("mark_id").orderByAsc("version_no"));
        Map<Long, AbsenceMarkVersion> latest = new LinkedHashMap<>();
        for (AbsenceMarkVersion v : confirmed) {
            latest.put(v.getMarkId(), v);
        }
        return new ArrayList<>(latest.values());
    }

    private int countInt(Long count) {
        return count == null ? 0 : count.intValue();
    }
}
