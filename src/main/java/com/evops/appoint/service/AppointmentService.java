package com.evops.appoint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.appoint.common.AppointConstants;
import com.evops.appoint.common.SelfHolder;
import com.evops.appoint.dto.CheckInRequest;
import com.evops.appoint.dto.ReplaceRequest;
import com.evops.appoint.dto.ShiftGenerateRequest;
import com.evops.appoint.entity.Appointment;
import com.evops.appoint.entity.AppointmentReplacement;
import com.evops.appoint.entity.AppointmentVersion;
import com.evops.appoint.entity.InvigilationShift;
import com.evops.appoint.entity.Invigilator;
import com.evops.appoint.entity.InvigilatorAllowanceRate;
import com.evops.appoint.mapper.AppointmentMapper;
import com.evops.appoint.mapper.AppointmentReplacementMapper;
import com.evops.appoint.mapper.AppointmentVersionMapper;
import com.evops.appoint.mapper.InvigilationShiftMapper;
import com.evops.appoint.vo.ShiftPreview;
import com.evops.appoint.vo.ShiftPreviewItem;
import com.evops.appoint.vo.ShiftResult;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.ExamRoom;
import com.evops.exam.mapper.ExamRoomMapper;
import com.evops.exam.service.ExamBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 聘任记录（模块 + 版本 + 状态）：
 * - 监考随排班生成聘任：按批次把监考排进考场角色场次，一条在岗排班生成一条聘任与 CREATE 版本；
 * - 场次结束签到计津贴（CHECKED_IN 才 billable），缺勤 / 取消 / 被换下不计费；
 * - 换人留替换记录：原持聘人追加 REPLACED 版本，接任者追加 APPOINTED 新版本，金额按接任口径重新冻结；
 * - 所有状态变化只追加版本，主表取当前版本；已进入确认结算单（settled）后锁定，要改走调整。
 */
@Service
public class AppointmentService {

    private final InvigilationShiftMapper shiftMapper;
    private final AppointmentMapper appointmentMapper;
    private final AppointmentVersionMapper versionMapper;
    private final AppointmentReplacementMapper replacementMapper;
    private final MasterDataService masterData;
    private final ExamBatchService batchService;
    private final ExamRoomMapper roomMapper;

    public AppointmentService(InvigilationShiftMapper shiftMapper,
                              AppointmentMapper appointmentMapper,
                              AppointmentVersionMapper versionMapper,
                              AppointmentReplacementMapper replacementMapper,
                              MasterDataService masterData,
                              ExamBatchService batchService,
                              ExamRoomMapper roomMapper) {
        this.shiftMapper = shiftMapper;
        this.appointmentMapper = appointmentMapper;
        this.versionMapper = versionMapper;
        this.replacementMapper = replacementMapper;
        this.masterData = masterData;
        this.batchService = batchService;
        this.roomMapper = roomMapper;
    }

    // ==================== 排班生成聘任：预检 / 执行（两步） ====================

    @Transactional(readOnly = true)
    public ShiftPreview previewGenerate(ShiftGenerateRequest request) {
        List<ResolvedShift> plan = buildPlan(request);
        List<ShiftPreviewItem> items = new ArrayList<>();
        int actionable = 0;
        int skip = 0;
        for (ResolvedShift r : plan) {
            ShiftPreviewItem item = new ShiftPreviewItem();
            item.setKey(r.room.getId() + "|" + r.roleCode + "|" + r.invigilator.getCode());
            item.setRoomNo(r.room.getRoomNo());
            item.setRoleCode(r.roleCode);
            item.setInvigilatorCode(r.invigilator.getCode());
            item.setInvigilatorName(r.invigilator.getName());
            item.setActionable(r.actionable);
            item.setCurrentStatus(r.currentStatus);
            if (r.rate != null) {
                item.setRateCode(r.rate.getCode());
                item.setAmount(r.rate.getAmount().toPlainString());
            }
            item.setNote(r.note);
            if (r.actionable) {
                actionable++;
            } else {
                skip++;
            }
            items.add(item);
        }
        ShiftPreview preview = new ShiftPreview();
        preview.setAction("GENERATE_APPOINTMENT");
        preview.setBatchId(plan.get(0).batch.getId());
        preview.setBatchCode(plan.get(0).batch.getCode());
        preview.setTotalScanned(plan.size());
        preview.setActionableCount(actionable);
        preview.setSkipCount(skip);
        preview.setSummary(String.format("批次 %s：排班 %d 条，可生成聘任 %d 条，跳过 %d 条",
                plan.get(0).batch.getCode(), plan.size(), actionable, skip));
        preview.setItems(items);
        return preview;
    }

    @Transactional
    public ShiftResult generate(ShiftGenerateRequest request) {
        List<ResolvedShift> plan = buildPlan(request);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        List<String> appointmentNos = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (ResolvedShift r : plan) {
            if (!r.actionable) {
                skipped.add(r.room.getRoomNo() + " " + roleLabel(r.roleCode) + " "
                        + r.invigilator.getCode() + "（" + r.note + "）");
                continue;
            }
            LocalDateTime start = r.shiftStart;
            LocalDateTime end = r.shiftEnd;

            InvigilationShift shift = new InvigilationShift();
            shift.setBatchId(r.batch.getId());
            shift.setRoomId(r.room.getId());
            shift.setRoleCode(r.roleCode);
            shift.setInvigilatorId(r.invigilator.getId());
            shift.setShiftStart(start);
            shift.setShiftEnd(end);
            shift.setStatus(AppointConstants.SHIFT_SCHEDULED);
            shiftMapper.insert(shift);

            String appointmentNo = nextAppointmentNo(r.batch);
            Appointment appt = new Appointment();
            appt.setAppointmentNo(appointmentNo);
            appt.setBatchId(r.batch.getId());
            appt.setRoomId(r.room.getId());
            appt.setShiftId(shift.getId());
            appt.setProjectCode(r.batch.getProjectCode() == null ? "DEFAULT" : r.batch.getProjectCode());
            appt.setInvigilatorId(r.invigilator.getId());
            appt.setInvigilatorCode(r.invigilator.getCode());
            appt.setInvigilatorName(r.invigilator.getName());
            appt.setRoleCode(r.roleCode);
            appt.setCurrentVersion(1);
            appt.setStatus(AppointConstants.APPT_APPOINTED);
            appt.setRateId(r.rate.getId());
            appt.setRateCode(r.rate.getCode());
            appt.setAllowanceAmount(r.rate.getAmount());
            appt.setBillable(false);
            appt.setSettled(false);
            appt.setOperator(operator);
            appointmentMapper.insert(appt);

            appendVersion(appt, AppointConstants.APPT_APPOINTED, r.invigilator, r.rate,
                    false, AppointConstants.CHANGE_CREATE, "随排班生成聘任", null, null, operator, now);

            appointmentNos.add(appointmentNo);
        }

        ShiftResult result = new ShiftResult();
        result.setAction("GENERATE_APPOINTMENT");
        result.setBatchId(plan.get(0).batch.getId());
        result.setBatchCode(plan.get(0).batch.getCode());
        result.setCreatedCount(appointmentNos.size());
        result.setSkipCount(skipped.size());
        result.setOperator(operator);
        result.setExecutedAt(now.toString());
        result.setAppointmentNos(appointmentNos);
        result.setSkipped(skipped);
        return result;
    }

    /** 预检与执行共用同一解析结果：结构性问题整批拒收，已排班 / 缺标准逐条标跳过。 */
    private List<ResolvedShift> buildPlan(ShiftGenerateRequest request) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        LocalDate rateDate = batch.getExamDate() == null ? LocalDate.now() : batch.getExamDate();
        LocalDateTime defaultStart = combine(rateDate, batch.getStartTime());
        LocalDateTime defaultEnd = combine(rateDate, batch.getEndTime());

        Set<String> seenKeys = new HashSet<>();
        Set<Long> seenInvigilators = new HashSet<>();
        List<ResolvedShift> plan = new ArrayList<>();

        for (ShiftGenerateRequest.ShiftAssignItem item : request.getAssignments()) {
            ExamRoom room = roomMapper.selectById(item.getRoomId());
            if (room == null) {
                throw new BusinessException("ROOM_NOT_FOUND", "考场不存在：id=" + item.getRoomId());
            }
            if (!room.getSiteId().equals(batch.getSiteId())) {
                throw new BusinessException("ROOM_NOT_IN_SITE",
                        "考场 " + room.getRoomNo() + " 不属于本批次考点，不能排班");
            }
            String role = normalizeRole(item.getRoleCode());
            Invigilator inv = masterData.requireEnabledByCode(item.getInvigilatorCode());

            String key = room.getId() + "|" + role + "|" + inv.getId();
            if (!seenKeys.add(key)) {
                throw new BusinessException("SHIFT_DUP_IN_REQ",
                        "请求内排班重复：考场 " + room.getRoomNo() + " " + roleLabel(role) + " " + inv.getCode());
            }
            if (!seenInvigilators.add(inv.getId())) {
                throw new BusinessException("INVIGILATOR_DOUBLE_BOOKED_IN_REQ",
                        "同一监考在本批次被排了多个场次：" + inv.getCode() + " " + inv.getName());
            }

            ResolvedShift r = new ResolvedShift();
            r.batch = batch;
            r.room = room;
            r.roleCode = role;
            r.invigilator = inv;
            r.shiftStart = request.getShiftStart() != null ? request.getShiftStart() : defaultStart;
            r.shiftEnd = request.getShiftEnd() != null ? request.getShiftEnd() : defaultEnd;

            // 已存在同场次在岗排班 → 幂等跳过（不重复生成聘任）
            InvigilationShift existing = shiftMapper.selectOne(new QueryWrapper<InvigilationShift>()
                    .eq("batch_id", batch.getId())
                    .eq("room_id", room.getId())
                    .eq("role_code", role)
                    .eq("invigilator_id", inv.getId())
                    .eq("status", AppointConstants.SHIFT_SCHEDULED)
                    .last("LIMIT 1"));
            if (existing != null) {
                Appointment appt = appointmentMapper.selectOne(new QueryWrapper<Appointment>()
                        .eq("shift_id", existing.getId()));
                r.actionable = false;
                r.currentStatus = appt == null ? null : appt.getStatus();
                r.note = "该场次已排班并生成聘任（当前状态 "
                        + (appt == null ? "-" : appt.getStatus()) + "），重复生成跳过";
                plan.add(r);
                continue;
            }

            // 同人本批次已有未取消聘任 → 冲突拒收（一个人同一场考试不能在两个考场）
            Long occupied = appointmentMapper.selectCount(new QueryWrapper<Appointment>()
                    .eq("batch_id", batch.getId())
                    .eq("invigilator_id", inv.getId())
                    .ne("status", AppointConstants.APPT_CANCELLED));
            if (occupied != null && occupied > 0) {
                throw new BusinessException("INVIGILATOR_ALREADY_APPOINTED",
                        "监考 " + inv.getCode() + " " + inv.getName()
                                + " 在本批次已有其他在岗聘任，不能重复排班");
            }

            try {
                r.rate = masterData.findRate(batch.getProjectCode(), role, rateDate);
                if (r.rate == null) {
                    r.actionable = false;
                    r.note = "项目 " + (batch.getProjectCode() == null ? "DEFAULT" : batch.getProjectCode())
                            + " 角色 " + role + " 在 " + rateDate + " 没有适用的启用津贴标准，跳过（请先建档）";
                } else {
                    r.actionable = true;
                    r.note = "将生成聘任，按标准 " + r.rate.getCode() + " 单场 "
                            + r.rate.getAmount().toPlainString() + " 元冻结，签到后计津贴";
                }
            } catch (BusinessException e) {
                // 多标准口径冲突等：该条标跳过，不中断整批
                r.actionable = false;
                r.note = e.getMessage();
            }
            plan.add(r);
        }
        return plan;
    }

    // ==================== 签到 / 缺勤 ====================

    /** 场次结束签到：仅 APPOINTED 可签到，签到即计费，金额沿用聘任时冻结口径。 */
    @Transactional
    public Appointment checkIn(Long appointmentId, CheckInRequest request) {
        Appointment appt = requireAppointment(appointmentId);
        assertNotSettled(appt, "签到");
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime signInAt = parseSignInAt(request == null ? null : request.getSignInAt(), now);

        if (AppointConstants.APPT_CHECKED_IN.equals(appt.getStatus())) {
            throw new BusinessException("ALREADY_CHECKED_IN",
                    "聘任 " + appt.getAppointmentNo() + " 已签到，不能重复签到");
        }
        if (!AppointConstants.APPT_APPOINTED.equals(appt.getStatus())) {
            throw new BusinessException("CHECKIN_STATUS_INVALID",
                    "仅已聘任（未签到）状态可签到，当前状态 " + appt.getStatus());
        }

        Invigilator inv = masterData.getInvigilator(appt.getInvigilatorId());
        InvigilatorAllowanceRate rate = frozenRate(appt);

        appt.setStatus(AppointConstants.APPT_CHECKED_IN);
        appt.setBillable(true);
        appt.setSignInAt(signInAt);
        appt.setCurrentVersion(appt.getCurrentVersion() + 1);
        appt.setOperator(operator);
        appointmentMapper.updateById(appt);

        appendVersion(appt, AppointConstants.APPT_CHECKED_IN, inv, rate, true,
                AppointConstants.CHANGE_CHECKIN, "场次结束签到，计津贴", null, signInAt, operator, now);
        return appt;
    }

    /** 缺勤：APPOINTED → ABSENT，不计费；已签到（已计费）不能改记缺勤。 */
    @Transactional
    public Appointment markAbsent(Long appointmentId, String reason) {
        Appointment appt = requireAppointment(appointmentId);
        assertNotSettled(appt, "记缺勤");
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        if (AppointConstants.APPT_ABSENT.equals(appt.getStatus())) {
            throw new BusinessException("ALREADY_ABSENT", "聘任 " + appt.getAppointmentNo() + " 已记缺勤");
        }
        if (!AppointConstants.APPT_APPOINTED.equals(appt.getStatus())) {
            throw new BusinessException("ABSENT_STATUS_INVALID",
                    "仅已聘任（未签到）状态可记缺勤，当前状态 " + appt.getStatus()
                            + "（已签到计费的需走结算调整）");
        }

        Invigilator inv = masterData.getInvigilator(appt.getInvigilatorId());
        appt.setStatus(AppointConstants.APPT_ABSENT);
        appt.setBillable(false);
        appt.setSignInAt(null);
        appt.setCurrentVersion(appt.getCurrentVersion() + 1);
        appt.setOperator(operator);
        appointmentMapper.updateById(appt);

        appendVersion(appt, AppointConstants.APPT_ABSENT, inv, frozenRate(appt), false,
                AppointConstants.CHANGE_ABSENT, reason == null ? "缺勤，不计费" : reason,
                null, null, operator, now);
        return appt;
    }

    // ==================== 换人（留替换记录） ====================

    /**
     * 换人：APPOINTED / ABSENT 可换。原持聘人追加 REPLACED 版本（不计费），
     * 接任者追加 APPOINTED 新版本，津贴按接任者角色与场次日期重新冻结，排班持聘人同步更新。
     */
    @Transactional
    public Appointment replace(Long appointmentId, ReplaceRequest request) {
        Appointment appt = requireAppointment(appointmentId);
        assertNotSettled(appt, "换人");
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        if (!AppointConstants.APPT_APPOINTED.equals(appt.getStatus())
                && !AppointConstants.APPT_ABSENT.equals(appt.getStatus())) {
            throw new BusinessException("REPLACE_STATUS_INVALID",
                    "仅已聘任 / 缺勤状态可换人，当前状态 " + appt.getStatus()
                            + "（已签到或已取消不可换人）");
        }

        Invigilator from = masterData.getInvigilator(appt.getInvigilatorId());
        Invigilator to = masterData.requireEnabledByCode(request.getToInvigilatorCode());
        if (to.getId().equals(from.getId())) {
            throw new BusinessException("REPLACE_SAME_PERSON", "接任监考与当前持聘人相同，无需换人：" + to.getCode());
        }
        // 接任者在本批次不能已有未取消聘任（同一时间一人一岗）
        Long occupied = appointmentMapper.selectCount(new QueryWrapper<Appointment>()
                .eq("batch_id", appt.getBatchId())
                .eq("invigilator_id", to.getId())
                .ne("status", AppointConstants.APPT_CANCELLED)
                .ne("id", appt.getId()));
        if (occupied != null && occupied > 0) {
            throw new BusinessException("REPLACEMENT_ALREADY_APPOINTED",
                    "接任监考 " + to.getCode() + " " + to.getName() + " 在本批次已有其他在岗聘任，不能接任");
        }

        ExamBatch batch = batchService.getBatch(appt.getBatchId());
        LocalDate rateDate = batch.getExamDate() == null ? LocalDate.now() : batch.getExamDate();
        InvigilatorAllowanceRate newRate = masterData.resolveRate(batch.getProjectCode(), appt.getRoleCode(), rateDate);
        InvigilatorAllowanceRate oldRate = frozenRate(appt);

        AppointmentReplacement replacement = new AppointmentReplacement();
        replacement.setAppointmentId(appt.getId());
        replacement.setBatchId(appt.getBatchId());
        replacement.setFromInvigilatorId(from.getId());
        replacement.setFromCode(from.getCode());
        replacement.setFromName(from.getName());
        replacement.setToInvigilatorId(to.getId());
        replacement.setToCode(to.getCode());
        replacement.setToName(to.getName());
        replacement.setRoleCode(appt.getRoleCode());
        replacement.setReason(request.getReason());
        replacement.setOperator(operator);
        replacement.setReplacedAt(now);
        replacementMapper.insert(replacement);

        int nextNo = appt.getCurrentVersion() + 1;
        // 被换下者：REPLACED 版本，沿用原口径，不计费
        AppointmentVersion fromVersion = newVersion(appt, nextNo, AppointConstants.APPT_REPLACED,
                from, oldRate, false, AppointConstants.CHANGE_REPLACE,
                request.getReason() == null ? "换人，被换下，不计本场费" : request.getReason(),
                replacement.getId(), null, operator, now);
        versionMapper.insert(fromVersion);
        // 接任者：APPOINTED 新版本，按新口径冻结，尚未签到不计费
        AppointmentVersion toVersion = newVersion(appt, nextNo + 1, AppointConstants.APPT_APPOINTED,
                to, newRate, false, AppointConstants.CHANGE_REPLACE_NEW,
                "接任本场监考（换自 " + from.getCode() + " " + from.getName() + "）",
                replacement.getId(), null, operator, now);
        versionMapper.insert(toVersion);

        // 主表切到接任者与新口径
        appt.setInvigilatorId(to.getId());
        appt.setInvigilatorCode(to.getCode());
        appt.setInvigilatorName(to.getName());
        appt.setStatus(AppointConstants.APPT_APPOINTED);
        appt.setBillable(false);
        appt.setSignInAt(null);
        appt.setRateId(newRate.getId());
        appt.setRateCode(newRate.getCode());
        appt.setAllowanceAmount(newRate.getAmount());
        appt.setCurrentVersion(nextNo + 1);
        appt.setOperator(operator);
        appointmentMapper.updateById(appt);

        // 排班持聘人同步为接任者
        InvigilationShift shift = shiftMapper.selectById(appt.getShiftId());
        if (shift != null) {
            shift.setInvigilatorId(to.getId());
            shiftMapper.updateById(shift);
        }
        return appt;
    }

    // ==================== 取消 ====================

    /** 场次取消：APPOINTED / ABSENT → CANCELLED，不计聘任计费；已签到（已计费）不能取消。 */
    @Transactional
    public Appointment cancel(Long appointmentId, String reason) {
        Appointment appt = requireAppointment(appointmentId);
        assertNotSettled(appt, "取消");
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        if (AppointConstants.APPT_CANCELLED.equals(appt.getStatus())) {
            throw new BusinessException("ALREADY_CANCELLED", "聘任 " + appt.getAppointmentNo() + " 已取消");
        }
        if (AppointConstants.APPT_CHECKED_IN.equals(appt.getStatus())) {
            throw new BusinessException("CANCEL_STATUS_INVALID",
                    "聘任已签到计费，不能取消；如确需冲销请走结算调整");
        }

        Invigilator inv = masterData.getInvigilator(appt.getInvigilatorId());
        appt.setStatus(AppointConstants.APPT_CANCELLED);
        appt.setBillable(false);
        appt.setSignInAt(null);
        appt.setCurrentVersion(appt.getCurrentVersion() + 1);
        appt.setOperator(operator);
        appointmentMapper.updateById(appt);

        appendVersion(appt, AppointConstants.APPT_CANCELLED, inv, frozenRate(appt), false,
                AppointConstants.CHANGE_CANCEL, reason == null ? "场次取消，不计费" : reason,
                null, null, operator, now);

        InvigilationShift shift = shiftMapper.selectById(appt.getShiftId());
        if (shift != null && AppointConstants.SHIFT_SCHEDULED.equals(shift.getStatus())) {
            shift.setStatus(AppointConstants.SHIFT_CANCELLED);
            shiftMapper.updateById(shift);
        }
        return appt;
    }

    // ==================== 查询 ====================

    @Transactional(readOnly = true)
    public List<Appointment> list(Long batchId, String status, String invigilatorCode) {
        String selfCode = SelfHolder.currentSelfCode();
        QueryWrapper<Appointment> wrapper = new QueryWrapper<Appointment>()
                .orderByDesc("batch_id", "id");
        if (batchId != null) {
            wrapper.eq("batch_id", batchId);
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        if (selfCode != null) {
            // 本人视图：强制只看自己，忽略任何试图查看他人的入参
            wrapper.eq("invigilator_code", selfCode.trim());
        } else if (invigilatorCode != null && !invigilatorCode.trim().isEmpty()) {
            wrapper.eq("invigilator_code", invigilatorCode.trim());
        }
        return appointmentMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public Appointment get(Long appointmentId) {
        Appointment appt = requireAppointment(appointmentId);
        assertSelf(appt);
        return appt;
    }

    /** 版本链：按版本号升序，完整保留生成 / 换人 / 签到 / 缺勤 / 取消全过程。 */
    @Transactional(readOnly = true)
    public List<AppointmentVersion> versions(Long appointmentId) {
        Appointment appt = requireAppointment(appointmentId);
        assertSelf(appt);
        return versionMapper.selectList(new QueryWrapper<AppointmentVersion>()
                .eq("appointment_id", appt.getId())
                .orderByAsc("version_no"));
    }

    /** 替换记录：可按批次或单条聘任查；本人视图只返回涉及本人的记录。 */
    @Transactional(readOnly = true)
    public List<AppointmentReplacement> replacements(Long batchId, Long appointmentId) {
        String selfCode = SelfHolder.currentSelfCode();
        QueryWrapper<AppointmentReplacement> wrapper = new QueryWrapper<AppointmentReplacement>()
                .orderByDesc("id");
        if (batchId != null) {
            wrapper.eq("batch_id", batchId);
        }
        if (appointmentId != null) {
            wrapper.eq("appointment_id", appointmentId);
        }
        List<AppointmentReplacement> list = replacementMapper.selectList(wrapper);
        if (selfCode != null) {
            String code = selfCode.trim();
            List<AppointmentReplacement> mine = new ArrayList<>();
            for (AppointmentReplacement r : list) {
                if (code.equals(r.getFromCode()) || code.equals(r.getToCode())) {
                    mine.add(r);
                }
            }
            return mine;
        }
        return list;
    }

    // ==================== 内部工具 ====================

    public Appointment requireAppointment(Long id) {
        if (id == null) {
            throw new BusinessException("APPOINTMENT_ID_REQUIRED", "聘任 id 不能为空");
        }
        Appointment appt = appointmentMapper.selectById(id);
        if (appt == null) {
            throw new BusinessException("APPOINTMENT_NOT_FOUND", "聘任记录不存在：id=" + id);
        }
        return appt;
    }

    private void assertSelf(Appointment appt) {
        String selfCode = SelfHolder.currentSelfCode();
        if (selfCode != null && !selfCode.trim().equals(appt.getInvigilatorCode())) {
            throw new BusinessException("FORBIDDEN_NOT_OWN", "本人视图只能查看本人的聘任记录");
        }
    }

    private void assertNotSettled(Appointment appt, String action) {
        if (Boolean.TRUE.equals(appt.getSettled())) {
            throw new BusinessException("APPOINTMENT_LOCKED",
                    "聘任 " + appt.getAppointmentNo() + " 已进入确认结算单并锁定，不能" + action
                            + "；如需改动请走结算调整");
        }
    }

    private InvigilatorAllowanceRate frozenRate(Appointment appt) {
        InvigilatorAllowanceRate rate = new InvigilatorAllowanceRate();
        rate.setId(appt.getRateId());
        rate.setCode(appt.getRateCode());
        rate.setAmount(appt.getAllowanceAmount() == null ? BigDecimal.ZERO : appt.getAllowanceAmount());
        rate.setRoleCode(appt.getRoleCode());
        return rate;
    }

    private void appendVersion(Appointment appt, String status, Invigilator holder,
                               InvigilatorAllowanceRate rate, boolean billable, String changeType,
                               String reason, Long replacementId, LocalDateTime signInAt,
                               String operator, LocalDateTime now) {
        AppointmentVersion v = newVersion(appt, appt.getCurrentVersion(), status, holder, rate,
                billable, changeType, reason, replacementId, signInAt, operator, now);
        versionMapper.insert(v);
    }

    private AppointmentVersion newVersion(Appointment appt, int versionNo, String status, Invigilator holder,
                                          InvigilatorAllowanceRate rate, boolean billable, String changeType,
                                          String reason, Long replacementId, LocalDateTime signInAt,
                                          String operator, LocalDateTime now) {
        AppointmentVersion v = new AppointmentVersion();
        v.setAppointmentId(appt.getId());
        v.setVersionNo(versionNo);
        v.setStatus(status);
        v.setInvigilatorId(holder.getId());
        v.setInvigilatorCode(holder.getCode());
        v.setInvigilatorName(holder.getName());
        v.setRoleCode(appt.getRoleCode());
        v.setRateId(rate == null ? null : rate.getId());
        v.setRateCode(rate == null ? null : rate.getCode());
        v.setAllowanceAmount(rate == null || rate.getAmount() == null ? BigDecimal.ZERO : rate.getAmount());
        v.setBillable(billable);
        v.setChangeType(changeType);
        v.setChangeReason(reason);
        v.setReplacementId(replacementId);
        v.setSignInAt(signInAt);
        v.setOperator(operator);
        v.setChangedAt(now);
        return v;
    }

    private LocalDateTime parseSignInAt(String text, LocalDateTime fallback) {
        if (text == null || text.trim().isEmpty()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(text.trim());
        } catch (Exception e) {
            try {
                return LocalDate.parse(text.trim()).atStartOfDay();
            } catch (Exception ex) {
                throw new BusinessException("SIGNIN_AT_INVALID",
                        "签到时间格式不正确（应为 yyyy-MM-ddTHH:mm:ss 或 yyyy-MM-dd）：" + text);
            }
        }
    }

    private LocalDateTime combine(LocalDate date, LocalTime time) {
        if (date == null) {
            return null;
        }
        return time == null ? date.atStartOfDay() : LocalDateTime.of(date, time);
    }

    private String normalizeRole(String roleCode) {
        if (roleCode == null || roleCode.trim().isEmpty()) {
            return AppointConstants.ROLE_INVIGILATOR;
        }
        String role = roleCode.trim().toUpperCase();
        if (!AppointConstants.ROLE_CHIEF.equals(role)
                && !AppointConstants.ROLE_INVIGILATOR.equals(role)
                && !AppointConstants.ROLE_PATROL.equals(role)) {
            throw new BusinessException("ROLE_NOT_SUPPORTED",
                    "不支持的监考角色：" + role + "（仅 CHIEF / INVIGILATOR / PATROL）");
        }
        return role;
    }

    private String roleLabel(String role) {
        if (AppointConstants.ROLE_CHIEF.equals(role)) {
            return "主考";
        }
        if (AppointConstants.ROLE_PATROL.equals(role)) {
            return "巡考";
        }
        return "监考";
    }

    /** 聘任号：AP + 批次编码 + 4 位流水（同批次聘任只追加不删除，取现有最大流水 +1）。 */
    private String nextAppointmentNo(ExamBatch batch) {
        String prefix = "AP" + batch.getCode() + "-";
        int max = 0;
        List<Appointment> exist = appointmentMapper.selectList(
                new QueryWrapper<Appointment>().eq("batch_id", batch.getId()));
        for (Appointment a : exist) {
            String no = a.getAppointmentNo();
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

    /** 排班预检解析中间结构。 */
    private static class ResolvedShift {
        private ExamBatch batch;
        private ExamRoom room;
        private String roleCode;
        private Invigilator invigilator;
        private InvigilatorAllowanceRate rate;
        private LocalDateTime shiftStart;
        private LocalDateTime shiftEnd;
        private boolean actionable;
        private String currentStatus;
        private String note;
    }
}
