package com.evops.grade.schedule;

import com.evops.grade.service.ScoreReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 复核窗口兜底：每分钟扫描一次，窗口（按批次配置）结束后仍未处理的复核申请自动关闭并通知。
 * 人工也可调 POST /api/reviews/close-expired 立即执行同一逻辑。
 */
@Component
public class ReviewCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReviewCloseScheduler.class);

    private final ScoreReviewService reviewService;

    public ReviewCloseScheduler(ScoreReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Scheduled(cron = "${evops.review.close-cron:0 * * * * *}")
    public void closeExpiredReviews() {
        Map<String, Object> result = reviewService.closeExpired();
        int closed = (Integer) result.get("closedCount");
        if (closed > 0) {
            log.info("复核窗口兜底扫描：自动关闭超窗复核 {} 条 {}", closed, result.get("reviewIds"));
        }
    }
}
