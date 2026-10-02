package com.evops.grade.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.service.ExamBatchService;
import com.evops.grade.common.GradeConstants;
import com.evops.grade.dto.ReviewApplyRequest;
import com.evops.grade.dto.ReviewConclusionRequest;
import com.evops.grade.entity.ExamScore;
import com.evops.grade.entity.ScoreChangeLog;
import com.evops.grade.entity.ScoreReview;
import com.evops.grade.mapper.ExamScoreMapper;
import com.evops.grade.mapper.ScoreReviewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成绩复核闭环：
 * - 窗口起止完全取批次配置（review_start_at / review_end_at），不写死；
 * - 仅窗口期 OPEN 可提交；未配置 / 未开始 / 已结束分别给可读原因；
 * - 超窗仍未处理的申请由定时任务自动 CLOSED 并通知；
 * - 结论回写复核单与成绩：通过则按原定级版本重算等级、改分留痕；驳回维持原分；两种结论都通知。
 */
@Service
public class ScoreReviewService {

    private static final Logger log = LoggerFactory.getLogger(ScoreReviewService.class);

    private final ScoreReviewMapper reviewMapper;
    private final ExamScoreMapper scoreMapper;
    private final ExamBatchService batchService;
    private final ScoreService scoreService;
    private final ScoreNotificationService notificationService;

    public ScoreReviewService(ScoreReviewMapper reviewMapper,
                              ExamScoreMapper scoreMapper,
                              ExamBatchService batchService,
                              ScoreService scoreService,
                              ScoreNotificationService notificationService) {
        this.reviewMapper = reviewMapper;
        this.scoreMapper = scoreMapper;
        this.batchService = batchService;
        this.scoreService = scoreService;
        this.notificationService = notificationService;
    }

    // ---------- 窗口状态 ----------

    /** 计算窗口状态：NOT_CONFIGURED / NOT_OPENED / OPEN / CLOSED。 */
    public String windowStatus(ExamBatch batch, LocalDateTime now) {
        if (batch.getReviewStartAt() == null || batch.getReviewEndAt() == null) {
            return GradeConstants.WINDOW_NOT_CONFIGURED;
        }
        if (now.isBefore(batch.getReviewStartAt())) {
            return GradeConstants.WINDOW_NOT_OPENED;
        }
        if (now.isAfter(batch.getReviewEndAt())) {
            return GradeConstants.WINDOW_CLOSED;
        }
        return GradeConstants.WINDOW_OPEN;
    }

    /** 窗口信息回显：含配置起止、当前状态与钟点，方便两个岗统一口径。 */
    public Map<String, Object> windowInfo(Long batchId) {
        ExamBatch batch = batchService.getBatch(batchId);
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("batchId", batch.getId());
        info.put("batchCode", batch.getCode());
        info.put("projectCode", batch.getProjectCode());
        info.put("reviewStartAt", batch.getReviewStartAt());
        info.put("reviewEndAt", batch.getReviewEndAt());
        info.put("windowStatus", windowStatus(batch, now));
        info.put("now", now);

        List<ScoreReview> reviews = reviewMapper.selectList(
                new QueryWrapper<ScoreReview>().eq("batch_id", batchId));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(GradeConstants.REVIEW_SUBMITTED, 0);
        counts.put(GradeConstants.REVIEW_APPROVED, 0);
        counts.put(GradeConstants.REVIEW_REJECTED, 0);
        counts.put(GradeConstants.REVIEW_CLOSED, 0);
        for (ScoreReview r : reviews) {
            counts.merge(r.getStatus(), 1, Integer::sum);
        }
        info.put("reviewCounts", counts);
        return info;
    }

    // ---------- 学员提交复核（仅窗口期内） ----------

    @Transactional
    public ScoreReview apply(ReviewApplyRequest request) {
        ExamScore score = locateScore(request);
        ExamBatch batch = batchService.getBatch(score.getBatchId());
        LocalDateTime now = LocalDateTime.now();

        String window = windowStatus(batch, now);
        if (GradeConstants.WINDOW_NOT_CONFIGURED.equals(window)) {
            throw new BusinessException("REVIEW_WINDOW_NOT_CONFIGURED",
                    "批次 " + batch.getCode() + " 尚未配置成绩复核窗口，无法提交复核");
        }
        if (GradeConstants.WINDOW_NOT_OPENED.equals(window)) {
            throw new BusinessException("REVIEW_WINDOW_NOT_OPENED",
                    "复核窗口尚未开始（" + batch.getReviewStartAt() + "），请在窗口期内提交");
        }
        if (GradeConstants.WINDOW_CLOSED.equals(window)) {
            throw new BusinessException("REVIEW_WINDOW_CLOSED",
                    "复核窗口已于 " + batch.getReviewEndAt() + " 关闭，无法再提交复核");
        }

        ScoreReview pending = reviewMapper.selectOne(new QueryWrapper<ScoreReview>()
                .eq("score_id", score.getId())
                .eq("status", GradeConstants.REVIEW_SUBMITTED));
        if (pending != null) {
            throw new BusinessException("REVIEW_DUPLICATE",
                    "该成绩已有处理中的复核申请（id=" + pending.getId() + "），请勿重复提交");
        }

        String applicant = request.getApplicant() == null || request.getApplicant().trim().isEmpty()
                ? OperatorHolder.current() : request.getApplicant().trim();

        ScoreReview review = new ScoreReview();
        review.setScoreId(score.getId());
        review.setBatchId(batch.getId());
        review.setRegistrationId(score.getRegistrationId());
        review.setRegNo(score.getRegNo());
        review.setName(score.getName());
        review.setApplyReason(request.getReason());
        review.setApplicant(applicant);
        review.setAppliedAt(now);
        review.setStatus(GradeConstants.REVIEW_SUBMITTED);
        reviewMapper.insert(review);

        scoreService.updateStatus(score, GradeConstants.SCORE_REVIEWING);
        return review;
    }

    // ---------- 复核结论：回写成绩 + 留痕 + 通知 ----------

    @Transactional
    public ScoreReview conclude(Long reviewId, ReviewConclusionRequest request) {
        ScoreReview review = requireReview(reviewId);
        if (!GradeConstants.REVIEW_SUBMITTED.equals(review.getStatus())) {
            throw new BusinessException("REVIEW_ALREADY_DONE",
                    "复核申请已处理，当前状态：" + review.getStatus());
        }
        ExamScore score = scoreService.getScore(review.getScoreId());
        String reviewer = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        boolean approved = Boolean.TRUE.equals(request.getApproved());
        if (approved) {
            if (request.getNewScore() == null) {
                throw new BusinessException("NEW_SCORE_REQUIRED", "复核通过必须填写复核后卷面分");
            }
            String reason = request.getConclusion() == null || request.getConclusion().trim().isEmpty()
                    ? "复核通过改分" : request.getConclusion().trim();

            // 改分前后快照先记到复核单
            review.setScoreBefore(score.getPaperScore());
            review.setGradeBefore(score.getGrade());

            // 按原定级规则版本重算等级，改分前后写只追加日志
            ScoreChangeLog changeLog = scoreService.applyReviewChange(
                    score, request.getNewScore(), reason, review.getId(), reviewer);

            review.setStatus(GradeConstants.REVIEW_APPROVED);
            review.setScoreAfter(changeLog.getScoreAfter());
            review.setGradeAfter(changeLog.getGradeAfter());
            review.setConclusion(reason);
            review.setReviewer(reviewer);
            review.setConcludedAt(now);
            reviewMapper.updateById(review);

            notificationService.notify(review.getBatchId(), review.getRegistrationId(),
                    review.getRegNo(), review.getName(),
                    GradeConstants.NOTIFY_REVIEW_RESULT,
                    "成绩复核结果：通过",
                    String.format("您的成绩复核已通过并改分：%s 分（%s）→ %s 分（%s）。规则版本 %s。",
                            changeLog.getScoreBefore(), changeLog.getGradeBefore(),
                            changeLog.getScoreAfter(), changeLog.getGradeAfter(),
                            changeLog.getRuleVersionNo()),
                    review.getId(), reviewer);
        } else {
            String conclusion = request.getConclusion() == null || request.getConclusion().trim().isEmpty()
                    ? "复核驳回，维持原分" : request.getConclusion().trim();
            review.setStatus(GradeConstants.REVIEW_REJECTED);
            review.setScoreBefore(score.getPaperScore());
            review.setGradeBefore(score.getGrade());
            review.setScoreAfter(score.getPaperScore());
            review.setGradeAfter(score.getGrade());
            review.setConclusion(conclusion);
            review.setReviewer(reviewer);
            review.setConcludedAt(now);
            reviewMapper.updateById(review);

            scoreService.updateStatus(score, GradeConstants.SCORE_RECORDED);

            notificationService.notify(review.getBatchId(), review.getRegistrationId(),
                    review.getRegNo(), review.getName(),
                    GradeConstants.NOTIFY_REVIEW_RESULT,
                    "成绩复核结果：维持原分",
                    String.format("您的成绩复核已处理：维持原分 %s 分（%s）。%s",
                            score.getPaperScore(), score.getGrade(), conclusion),
                    review.getId(), reviewer);
        }
        return review;
    }

    // ---------- 超窗自动关闭（定时任务 / 手动接口共用） ----------

    /**
     * 把所有“批次窗口已结束、仍在待处理”的复核单置 CLOSED，成绩解锁、通知学员。
     * 窗口起止只认批次配置。
     */
    @Transactional
    public Map<String, Object> closeExpired() {
        LocalDateTime now = LocalDateTime.now();
        List<ScoreReview> pending = reviewMapper.selectList(new QueryWrapper<ScoreReview>()
                .eq("status", GradeConstants.REVIEW_SUBMITTED));
        List<ScoreReview> closed = new ArrayList<>();
        for (ScoreReview review : pending) {
            ExamBatch batch = batchService.getBatch(review.getBatchId());
            if (!GradeConstants.WINDOW_CLOSED.equals(windowStatus(batch, now))) {
                continue;
            }
            review.setStatus(GradeConstants.REVIEW_CLOSED);
            review.setConclusion("超过批次复核窗口（" + batch.getReviewEndAt() + "）仍未处理，系统自动关闭");
            review.setReviewer("system");
            review.setConcludedAt(now);
            reviewMapper.updateById(review);

            ExamScore score = scoreService.getScore(review.getScoreId());
            scoreService.updateStatus(score, GradeConstants.SCORE_RECORDED);

            notificationService.notify(review.getBatchId(), review.getRegistrationId(),
                    review.getRegNo(), review.getName(),
                    GradeConstants.NOTIFY_REVIEW_CLOSED,
                    "成绩复核已超窗关闭",
                    String.format("您于 %s 提交的成绩复核，因超过复核窗口（%s）未处理，系统已自动关闭，成绩维持 %s 分（%s）。",
                            review.getAppliedAt(), batch.getReviewEndAt(),
                            score.getPaperScore(), score.getGrade()),
                    review.getId(), "system");
            closed.add(review);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("closedCount", closed.size());
        result.put("closedAt", now);
        result.put("reviewIds", collectIds(closed));
        return result;
    }

    // ---------- 查询 ----------

    public ScoreReview getReview(Long id) {
        return requireReview(id);
    }

    public List<ScoreReview> listByBatch(Long batchId, String status) {
        QueryWrapper<ScoreReview> wrapper = new QueryWrapper<ScoreReview>()
                .eq("batch_id", batchId)
                .orderByDesc("applied_at", "id");
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        return reviewMapper.selectList(wrapper);
    }

    public List<ScoreReview> listAll(String status) {
        QueryWrapper<ScoreReview> wrapper = new QueryWrapper<ScoreReview>()
                .orderByDesc("applied_at", "id");
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        return reviewMapper.selectList(wrapper);
    }

    // ---------- 内部 ----------

    private ExamScore locateScore(ReviewApplyRequest request) {
        if (request.getScoreId() != null) {
            return scoreService.getScore(request.getScoreId());
        }
        if (request.getBatchId() == null || request.getRegNo() == null || request.getRegNo().trim().isEmpty()) {
            throw new BusinessException("REVIEW_TARGET_REQUIRED",
                    "请提供 scoreId，或同时提供 batchId 与 regNo 定位成绩");
        }
        ExamScore score = scoreMapper.selectOne(new QueryWrapper<ExamScore>()
                .eq("batch_id", request.getBatchId())
                .eq("reg_no", request.getRegNo().trim()));
        if (score == null) {
            throw new BusinessException("SCORE_NOT_FOUND",
                    "批次内未找到该报名号的成绩：" + request.getRegNo().trim());
        }
        return score;
    }

    private ScoreReview requireReview(Long id) {
        if (id == null) {
            throw new BusinessException("REVIEW_ID_REQUIRED", "复核申请 id 不能为空");
        }
        ScoreReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException("REVIEW_NOT_FOUND", "复核申请不存在：id=" + id);
        }
        return review;
    }

    private List<Long> collectIds(List<ScoreReview> reviews) {
        List<Long> ids = new ArrayList<>();
        for (ScoreReview r : reviews) {
            ids.add(r.getId());
        }
        return ids;
    }
}
