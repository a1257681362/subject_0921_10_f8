package com.evops.vault.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.vault.common.VaultConstants;
import com.evops.vault.dto.NoteRequest;
import com.evops.vault.dto.ReviewRequest;
import com.evops.vault.dto.SwipeRequest;
import com.evops.vault.entity.VaultAccess;
import com.evops.vault.entity.VaultAccessNote;
import com.evops.vault.entity.VaultAccessVersion;
import com.evops.vault.entity.VaultAlert;
import com.evops.vault.entity.VaultBox;
import com.evops.vault.entity.VaultBoxMove;
import com.evops.vault.entity.VaultPerson;
import com.evops.vault.mapper.VaultAccessMapper;
import com.evops.vault.mapper.VaultAccessNoteMapper;
import com.evops.vault.mapper.VaultAccessVersionMapper;
import com.evops.vault.mapper.VaultAlertMapper;
import com.evops.vault.mapper.VaultBoxMoveMapper;
import com.evops.vault.vo.InRoomBoxView;
import com.evops.vault.vo.InRoomPersonView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出入登记（模块 + 版本 + 状态）：
 * - 进出刷证：按卡号或人员编码登记，落库为待复核（PENDING），不生效、不计入在室清单；
 * - 第二人复核：复核人须为登记者本人以外的在册启用人员，确认才生效（CONFIRMED），驳回永不生效；
 * - 卷箱出入跟人员进出挂钩：卷箱必须挂在一条出入登记上、方向一致，随登记复核生效；
 * - 异常时段：发生时间落在允许窗口外自动标记，生效时告警保卫；
 * - 登记写入后不改：内容字段不落更新接口，纠错一律走补充说明（只追加）；
 * - 每次状态流转只追加一版 t_vault_access_version，主表只切当前版本号与状态。
 */
@Service
public class VaultAccessService {

    private final VaultAccessMapper accessMapper;
    private final VaultAccessVersionMapper versionMapper;
    private final VaultBoxMoveMapper boxMoveMapper;
    private final VaultAccessNoteMapper noteMapper;
    private final VaultAlertMapper alertMapper;
    private final VaultMasterDataService masterData;

    public VaultAccessService(VaultAccessMapper accessMapper,
                              VaultAccessVersionMapper versionMapper,
                              VaultBoxMoveMapper boxMoveMapper,
                              VaultAccessNoteMapper noteMapper,
                              VaultAlertMapper alertMapper,
                              VaultMasterDataService masterData) {
        this.accessMapper = accessMapper;
        this.versionMapper = versionMapper;
        this.boxMoveMapper = boxMoveMapper;
        this.noteMapper = noteMapper;
        this.alertMapper = alertMapper;
        this.masterData = masterData;
    }

    // ==================== 进出刷证（登记即待复核） ====================

    @Transactional
    public VaultAccess swipe(SwipeRequest request) {
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        VaultPerson person = masterData.resolveEnabledPerson(request.getPersonCode(), request.getCardNo());
        String direction = normalizeDirection(request.getDirection());
        LocalDateTime occurredAt = parseOccurredAt(request.getOccurredAt(), now);
        assertNotFuture(occurredAt, now);
        List<String> boxNos = normalizeBoxes(request.getBoxes());

        VaultAccess access = newAccess(person, direction, occurredAt, VaultConstants.SOURCE_LIVE,
                boxNos, request.getRemark(), operator, now);
        // 现场刷证：卷箱位置按已生效台账校验，带进/带出与在室口径对上才放行
        validateBoxPositions(boxNos, direction);
        assertNoNaturalDuplicate(access);

        accessMapper.insert(access);
        appendVersion(access, VaultConstants.ACCESS_PENDING, VaultConstants.CHANGE_CREATE,
                "刷证登记，待第二人复核", null, null, operator, now);
        insertBoxMoves(access, boxNos);
        return access;
    }

    /** 建一条待落库的登记（不校验重复与卷箱位置，由调用方按需校验）。 */
    VaultAccess newAccess(VaultPerson person, String direction, LocalDateTime occurredAt, String source,
                          List<String> boxNos, String remark, String operator, LocalDateTime now) {
        VaultAccess access = new VaultAccess();
        access.setAccessNo(nextAccessNo(occurredAt));
        access.setPersonId(person.getId());
        access.setPersonCode(person.getCode());
        access.setPersonName(person.getName());
        access.setCardNo(person.getCardNo());
        access.setDirection(direction);
        access.setOccurredAt(occurredAt);
        access.setSource(source);
        access.setStatus(VaultConstants.ACCESS_PENDING);
        access.setCurrentVersion(1);
        access.setBoxCount(boxNos.size());
        access.setOperator(operator);
        access.setRemark(remark);
        markAbnormal(access);
        return access;
    }

    // ==================== 第二人复核（确认才生效） ====================

    @Transactional
    public VaultAccess review(Long accessId, ReviewRequest request) {
        VaultAccess access = requireAccess(accessId);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        if (!VaultConstants.ACCESS_PENDING.equals(access.getStatus())) {
            throw new BusinessException("REVIEW_STATUS_INVALID",
                    "登记 " + access.getAccessNo() + " 当前状态 " + access.getStatus()
                            + "，仅待复核（PENDING）可复核");
        }
        VaultPerson reviewer = masterData.requireEnabledByCode(request.getReviewerCode());
        if (reviewer.getId().equals(access.getPersonId())) {
            throw new BusinessException("REVIEWER_SAME_AS_PERSON",
                    "复核人不能是登记者本人（双人双锁：须第二人复核）：" + reviewer.getCode());
        }

        if (Boolean.FALSE.equals(request.getApproved())) {
            if (request.getComment() == null || request.getComment().trim().isEmpty()) {
                throw new BusinessException("REJECT_COMMENT_REQUIRED", "驳回必须填写复核意见");
            }
            access.setStatus(VaultConstants.ACCESS_REJECTED);
            access.setCurrentVersion(access.getCurrentVersion() + 1);
            fillReview(access, reviewer, request.getComment(), operator, now);
            accessMapper.updateById(access);
            appendVersion(access, VaultConstants.ACCESS_REJECTED, VaultConstants.CHANGE_REJECT,
                    request.getComment().trim(), reviewer.getCode(), reviewer.getName(), operator, now);
            return access;
        }

        // 确认生效前复核卷箱位置：等待期间台账可能已被其他生效登记改变
        List<VaultBoxMove> moves = boxMoves(access.getId());
        List<String> boxNos = new ArrayList<>();
        for (VaultBoxMove move : moves) {
            boxNos.add(move.getBoxNo());
        }
        validateBoxPositions(boxNos, access.getDirection());

        access.setStatus(VaultConstants.ACCESS_CONFIRMED);
        access.setCurrentVersion(access.getCurrentVersion() + 1);
        fillReview(access, reviewer, request.getComment(), operator, now);
        accessMapper.updateById(access);
        appendVersion(access, VaultConstants.ACCESS_CONFIRMED, VaultConstants.CHANGE_CONFIRM,
                request.getComment() == null ? "第二人复核确认，登记生效" : request.getComment().trim(),
                reviewer.getCode(), reviewer.getName(), operator, now);
        raiseAlertIfAbnormal(access);
        return access;
    }

    private void fillReview(VaultAccess access, VaultPerson reviewer, String comment,
                            String operator, LocalDateTime now) {
        access.setReviewerCode(reviewer.getCode());
        access.setReviewerName(reviewer.getName());
        access.setReviewedAt(now);
        access.setReviewComment(comment == null ? null : comment.trim());
        access.setOperator(operator);
    }

    // ==================== 补充说明（纠错唯一入口，只追加） ====================

    @Transactional
    public VaultAccessNote addNote(Long accessId, NoteRequest request) {
        VaultAccess access = requireAccess(accessId);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        String noteType = request.getNoteType() == null || request.getNoteType().trim().isEmpty()
                ? VaultConstants.NOTE_SUPPLEMENT : request.getNoteType().trim().toUpperCase();
        if (!VaultConstants.NOTE_SUPPLEMENT.equals(noteType) && !VaultConstants.NOTE_CORRECTION.equals(noteType)) {
            throw new BusinessException("NOTE_TYPE_INVALID",
                    "说明类型仅支持 SUPPLEMENT 补充说明 / CORRECTION 纠错说明：" + noteType);
        }
        VaultAccessNote note = new VaultAccessNote();
        note.setAccessId(access.getId());
        note.setNoteType(noteType);
        note.setContent(request.getContent().trim());
        note.setOperator(operator);
        note.setNotedAt(now);
        noteMapper.insert(note);
        return note;
    }

    @Transactional(readOnly = true)
    public List<VaultAccessNote> listNotes(Long accessId) {
        requireAccess(accessId);
        return noteMapper.selectList(new QueryWrapper<VaultAccessNote>()
                .eq("access_id", accessId)
                .orderByAsc("noted_at", "id"));
    }

    // ==================== 查询 / 在室清单 ====================

    @Transactional(readOnly = true)
    public List<VaultAccess> list(String status, String direction, String personCode, String from, String to) {
        QueryWrapper<VaultAccess> wrapper = new QueryWrapper<VaultAccess>()
                .orderByDesc("occurred_at", "id");
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        if (direction != null && !direction.trim().isEmpty()) {
            wrapper.eq("direction", normalizeDirection(direction));
        }
        if (personCode != null && !personCode.trim().isEmpty()) {
            wrapper.eq("person_code", personCode.trim());
        }
        if (from != null && !from.trim().isEmpty()) {
            wrapper.ge("occurred_at", parseBoundary(from, false));
        }
        if (to != null && !to.trim().isEmpty()) {
            wrapper.le("occurred_at", parseBoundary(to, true));
        }
        return accessMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public VaultAccess get(Long accessId) {
        return requireAccess(accessId);
    }

    /** 版本链：登记 / 复核 / 驳回 / 导入全程只追加。 */
    @Transactional(readOnly = true)
    public List<VaultAccessVersion> versions(Long accessId) {
        requireAccess(accessId);
        return versionMapper.selectList(new QueryWrapper<VaultAccessVersion>()
                .eq("access_id", accessId)
                .orderByAsc("version_no"));
    }

    @Transactional(readOnly = true)
    public List<VaultBoxMove> boxMoves(Long accessId) {
        return boxMoveMapper.selectList(new QueryWrapper<VaultBoxMove>()
                .eq("access_id", accessId)
                .orderByAsc("box_no"));
    }

    /** 在室人员（实时）：只按已生效登记推导，最后一条生效方向为 IN 即在室。 */
    @Transactional(readOnly = true)
    public List<InRoomPersonView> inRoomPersons() {
        List<VaultAccess> confirmed = accessMapper.selectList(new QueryWrapper<VaultAccess>()
                .eq("status", VaultConstants.ACCESS_CONFIRMED)
                .orderByAsc("occurred_at", "id"));
        Map<Long, VaultAccess> latest = new LinkedHashMap<>();
        for (VaultAccess access : confirmed) {
            latest.put(access.getPersonId(), access);
        }
        List<InRoomPersonView> views = new ArrayList<>();
        for (VaultAccess access : latest.values()) {
            if (VaultConstants.DIRECTION_IN.equals(access.getDirection())) {
                views.add(new InRoomPersonView(access.getPersonCode(), access.getPersonName(),
                        access.getOccurredAt().toString(), access.getId(), access.getAccessNo()));
            }
        }
        views.sort(Comparator.comparing(InRoomPersonView::getSinceAt));
        return views;
    }

    /** 在室卷箱（实时）：只按已生效登记挂钩的卷箱明细推导。 */
    @Transactional(readOnly = true)
    public List<InRoomBoxView> inRoomBoxes() {
        List<VaultAccess> confirmed = accessMapper.selectList(new QueryWrapper<VaultAccess>()
                .eq("status", VaultConstants.ACCESS_CONFIRMED)
                .orderByAsc("occurred_at", "id"));
        Map<Long, VaultAccess> byId = new LinkedHashMap<>();
        for (VaultAccess access : confirmed) {
            byId.put(access.getId(), access);
        }
        Map<Long, VaultBoxMove> latestMove = new LinkedHashMap<>();
        if (!byId.isEmpty()) {
            List<VaultBoxMove> moves = boxMoveMapper.selectList(new QueryWrapper<VaultBoxMove>()
                    .in("access_id", byId.keySet())
                    .orderByAsc("occurred_at", "id"));
            for (VaultBoxMove move : moves) {
                latestMove.put(move.getBoxId(), move);
            }
        }
        Map<Long, VaultBox> boxes = new LinkedHashMap<>();
        for (VaultBox box : masterData.listBoxes()) {
            boxes.put(box.getId(), box);
        }
        List<InRoomBoxView> views = new ArrayList<>();
        for (Map.Entry<Long, VaultBoxMove> entry : latestMove.entrySet()) {
            VaultBoxMove move = entry.getValue();
            if (!VaultConstants.DIRECTION_IN.equals(move.getDirection())) {
                continue;
            }
            VaultAccess access = byId.get(move.getAccessId());
            VaultBox box = boxes.get(entry.getKey());
            views.add(new InRoomBoxView(move.getBoxNo(), box == null ? null : box.getName(),
                    move.getOccurredAt().toString(), move.getAccessId(),
                    access == null ? null : access.getAccessNo(),
                    access == null ? null : access.getPersonCode(),
                    access == null ? null : access.getPersonName()));
        }
        views.sort(Comparator.comparing(InRoomBoxView::getBoxNo));
        return views;
    }

    // ==================== 内部工具（导入共用） ====================

    public VaultAccess requireAccess(Long id) {
        if (id == null) {
            throw new BusinessException("ACCESS_ID_REQUIRED", "出入登记 id 不能为空");
        }
        VaultAccess access = accessMapper.selectById(id);
        if (access == null) {
            throw new BusinessException("ACCESS_NOT_FOUND", "出入登记不存在：id=" + id);
        }
        return access;
    }

    String normalizeDirection(String direction) {
        if (direction == null || direction.trim().isEmpty()) {
            throw new BusinessException("DIRECTION_REQUIRED", "进出方向不能为空（IN 进 / OUT 出）");
        }
        String normalized = direction.trim().toUpperCase();
        if (!VaultConstants.DIRECTION_IN.equals(normalized) && !VaultConstants.DIRECTION_OUT.equals(normalized)) {
            throw new BusinessException("DIRECTION_INVALID",
                    "进出方向仅支持 IN 进 / OUT 出：" + direction);
        }
        return normalized;
    }

    /** 发生时间解析：支持 yyyy-MM-ddTHH:mm:ss / yyyy-MM-dd HH:mm:ss / yyyy-MM-dd HH:mm / yyyy-MM-dd。 */
    LocalDateTime parseOccurredAt(String text, LocalDateTime fallback) {
        if (text == null || text.trim().isEmpty()) {
            return fallback;
        }
        String value = text.trim();
        List<DateTimeFormatter> formatters = new ArrayList<>();
        formatters.add(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        formatters.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        formatters.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (Exception e) {
            throw new BusinessException("OCCURRED_AT_INVALID",
                    "发生时间格式不正确（应为 yyyy-MM-dd HH:mm(:ss) 或 yyyy-MM-dd）：" + text);
        }
    }

    /** 列表查询边界：纯日期 from 按当日 00:00、to 按当日 23:59:59。 */
    private LocalDateTime parseBoundary(String text, boolean endOfDay) {
        String value = text.trim();
        if (value.length() == 10) {
            try {
                LocalDate day = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
                return endOfDay ? day.atTime(23, 59, 59) : day.atStartOfDay();
            } catch (Exception e) {
                throw new BusinessException("TIME_BOUNDARY_INVALID", "时间边界格式不正确：" + text);
            }
        }
        return parseOccurredAt(value, null);
    }

    void assertNotFuture(LocalDateTime occurredAt, LocalDateTime now) {
        if (occurredAt.isAfter(now)) {
            throw new BusinessException("OCCURRED_AT_FUTURE",
                    "发生时间不能晚于当前时间：" + occurredAt);
        }
    }

    List<String> normalizeBoxes(List<String> boxes) {
        List<String> result = new ArrayList<>();
        if (boxes == null) {
            return result;
        }
        for (String boxNo : boxes) {
            if (boxNo == null || boxNo.trim().isEmpty()) {
                continue;
            }
            String normalized = boxNo.trim();
            if (result.contains(normalized)) {
                throw new BusinessException("BOX_DUP_IN_REQ", "同一登记内卷箱重复：" + normalized);
            }
            result.add(normalized);
        }
        return result;
    }

    /**
     * 卷箱位置校验（按已生效台账）：带进要求箱不在室，带出要求箱在室。
     * 待复核登记不影响口径；确认生效前会再校一次。
     */
    void validateBoxPositions(List<String> boxNos, String direction) {
        for (String boxNo : boxNos) {
            VaultBox box = masterData.requireEnabledBox(boxNo);
            VaultBoxMove latest = latestConfirmedMove(box.getId());
            boolean inRoom = latest != null && VaultConstants.DIRECTION_IN.equals(latest.getDirection());
            if (VaultConstants.DIRECTION_OUT.equals(direction) && !inRoom) {
                throw new BusinessException("BOX_NOT_IN_ROOM",
                        "卷箱 " + boxNo + " 当前不在室（按已生效台账），不能登记带出");
            }
            if (VaultConstants.DIRECTION_IN.equals(direction) && inRoom) {
                throw new BusinessException("BOX_ALREADY_IN_ROOM",
                        "卷箱 " + boxNo + " 当前已在室（按已生效台账），不能重复登记带进");
            }
        }
    }

    private VaultBoxMove latestConfirmedMove(Long boxId) {
        List<VaultBoxMove> moves = boxMoveMapper.selectList(new QueryWrapper<VaultBoxMove>()
                .eq("box_id", boxId)
                .orderByDesc("occurred_at", "id"));
        for (VaultBoxMove move : moves) {
            VaultAccess access = accessMapper.selectById(move.getAccessId());
            if (access != null && VaultConstants.ACCESS_CONFIRMED.equals(access.getStatus())) {
                return move;
            }
        }
        return null;
    }

    /** 同人同向同钟点视为同一笔：现场防连刷，导入幂等。 */
    void assertNoNaturalDuplicate(VaultAccess access) {
        Long count = accessMapper.selectCount(new QueryWrapper<VaultAccess>()
                .eq("person_code", access.getPersonCode())
                .eq("direction", access.getDirection())
                .eq("occurred_at", access.getOccurredAt()));
        if (count != null && count > 0) {
            throw new BusinessException("ACCESS_DUPLICATE",
                    "同一人员同方向同钟点已有登记：" + access.getPersonCode() + " "
                            + access.getDirection() + " " + access.getOccurredAt());
        }
    }

    /** 异常时段标记：发生时间落在允许窗口外。 */
    void markAbnormal(VaultAccess access) {
        if (masterData.isOffHours(access.getOccurredAt())) {
            access.setAbnormal(true);
            access.setAbnormalReason("异常时段进出（允许时段 " + masterData.windowText() + "）");
        } else {
            access.setAbnormal(false);
            access.setAbnormalReason(null);
        }
    }

    /** 生效时异常时段自动告警保卫（同人同笔不重复告警）。 */
    void raiseAlertIfAbnormal(VaultAccess access) {
        if (!Boolean.TRUE.equals(access.getAbnormal())) {
            return;
        }
        Long exist = alertMapper.selectCount(new QueryWrapper<VaultAlert>()
                .eq("access_id", access.getId())
                .eq("alert_type", VaultConstants.ALERT_OFF_HOURS));
        if (exist != null && exist > 0) {
            return;
        }
        VaultAlert alert = new VaultAlert();
        alert.setAccessId(access.getId());
        alert.setAlertType(VaultConstants.ALERT_OFF_HOURS);
        alert.setTitle("保密室异常时段进出");
        alert.setContent(String.format("%s（%s）于 %s %s，登记号 %s，%s。",
                access.getPersonName(), access.getPersonCode(),
                access.getOccurredAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                VaultConstants.DIRECTION_IN.equals(access.getDirection()) ? "进室" : "出室",
                access.getAccessNo(), access.getAbnormalReason()));
        alert.setStatus(VaultConstants.ALERT_NEW);
        alertMapper.insert(alert);
    }

    void insertBoxMoves(VaultAccess access, List<String> boxNos) {
        for (String boxNo : boxNos) {
            // 现场刷证的启用校验已在 validateBoxPositions 完成；纸面导入按历史口径只要求已建档
            VaultBox box = masterData.findBoxByNo(boxNo);
            if (box == null) {
                throw new BusinessException("BOX_NOT_FOUND", "卷箱未建档：" + boxNo + "，请先建档");
            }
            VaultBoxMove move = new VaultBoxMove();
            move.setAccessId(access.getId());
            move.setBoxId(box.getId());
            move.setBoxNo(box.getBoxNo());
            move.setDirection(access.getDirection());
            move.setOccurredAt(access.getOccurredAt());
            boxMoveMapper.insert(move);
        }
    }

    void appendVersion(VaultAccess access, String status, String changeType, String reason,
                       String reviewerCode, String reviewerName, String operator, LocalDateTime now) {
        VaultAccessVersion version = new VaultAccessVersion();
        version.setAccessId(access.getId());
        version.setVersionNo(access.getCurrentVersion());
        version.setStatus(status);
        version.setChangeType(changeType);
        version.setChangeReason(reason);
        version.setReviewerCode(reviewerCode);
        version.setReviewerName(reviewerName);
        version.setOperator(operator);
        version.setChangedAt(now);
        versionMapper.insert(version);
    }

    /** 登记号：VA + 发生日期 8 位 + 4 位流水（按日递增，唯一约束兜底）。 */
    String nextAccessNo(LocalDateTime occurredAt) {
        String prefix = "VA" + occurredAt.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        int max = 0;
        List<VaultAccess> exist = accessMapper.selectList(new QueryWrapper<VaultAccess>()
                .likeRight("access_no", prefix));
        for (VaultAccess a : exist) {
            String no = a.getAccessNo();
            if (no != null && no.startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 非本规则流水忽略
                }
            }
        }
        return prefix + String.format("%04d", max + 1);
    }
}
