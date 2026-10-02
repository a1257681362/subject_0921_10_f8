package com.evops.grade.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.grade.common.GradeConstants;
import com.evops.grade.entity.ScoreNotification;
import com.evops.grade.mapper.ScoreNotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内通知：本版先落库（复核结论、超窗关闭），短信等外发渠道下版再接。
 */
@Service
public class ScoreNotificationService {

    private final ScoreNotificationMapper notificationMapper;

    public ScoreNotificationService(ScoreNotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Transactional
    public ScoreNotification notify(Long batchId, Long registrationId, String regNo, String name,
                                    String notifyType, String title, String content,
                                    Long reviewId, String operator) {
        ScoreNotification n = new ScoreNotification();
        n.setBatchId(batchId);
        n.setRegistrationId(registrationId);
        n.setRegNo(regNo);
        n.setName(name);
        n.setNotifyType(notifyType);
        n.setTitle(title);
        n.setContent(content);
        n.setStatus(GradeConstants.NOTIFY_NEW);
        n.setReviewId(reviewId);
        n.setOperator(operator);
        n.setNotifiedAt(LocalDateTime.now());
        notificationMapper.insert(n);
        return n;
    }

    public List<ScoreNotification> list(String regNo, boolean unreadOnly) {
        QueryWrapper<ScoreNotification> wrapper = new QueryWrapper<ScoreNotification>()
                .orderByDesc("notified_at", "id");
        if (regNo != null && !regNo.trim().isEmpty()) {
            wrapper.eq("reg_no", regNo.trim());
        }
        if (unreadOnly) {
            wrapper.eq("status", GradeConstants.NOTIFY_NEW);
        }
        return notificationMapper.selectList(wrapper);
    }

    @Transactional
    public ScoreNotification markRead(Long id) {
        ScoreNotification n = notificationMapper.selectById(id);
        if (n == null) {
            throw new BusinessException("NOTIFICATION_NOT_FOUND", "通知不存在：id=" + id);
        }
        if (GradeConstants.NOTIFY_NEW.equals(n.getStatus())) {
            n.setStatus(GradeConstants.NOTIFY_READ);
            n.setReadAt(LocalDateTime.now());
            notificationMapper.updateById(n);
        }
        return n;
    }
}
