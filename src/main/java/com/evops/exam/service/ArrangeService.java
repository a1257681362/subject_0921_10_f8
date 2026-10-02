package com.evops.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.common.ExamConstants;
import com.evops.exam.dto.ArrangeRequest;
import com.evops.exam.entity.AdmissionTicket;
import com.evops.exam.entity.ArrangementRun;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.ExamRoom;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.AdmissionTicketMapper;
import com.evops.exam.mapper.ArrangementRunMapper;
import com.evops.exam.mapper.ExamRoomMapper;
import com.evops.exam.vo.ActionPreview;
import com.evops.exam.vo.ActionResult;
import com.evops.exam.vo.AdjacentPairView;
import com.evops.exam.vo.AffectedItem;
import com.evops.exam.vo.SeatingEntry;
import com.evops.mixalgo.dto.MixParams;
import com.evops.mixalgo.engine.SeatingMixer;
import com.evops.mixalgo.entity.MixAlgorithmVersion;
import com.evops.mixalgo.service.MixAlgorithmService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 编排：按批次把已报名名册混编进考点各考场，一人一条准考证（证号、考场、座位）。
 *
 * 同校整改口径：
 * - 采用项目当前 ACTIVE 混编算法版本（无生效版本时用内置默认参数）；
 * - 同校学员按考场容量比例尽量摊到多个考场，考场内交错入座最小化同校相邻；
 * - 每次编排 / 重排把算法版本、随机种子、座次快照、同校相邻统计落 t_arrangement_run；
 * - 证号撞号 / 座位撞号直接拒收（数据库唯一约束兜底）。
 */
@Service
public class ArrangeService {

    /** 无生效算法版本时，结果中展示的版本口径。 */
    public static final String BUILTIN_VERSION_NO = "BUILTIN_DEFAULT";

    private final AdmissionTicketMapper ticketMapper;
    private final ArrangementRunMapper runMapper;
    private final ExamRoomMapper roomMapper;
    private final ExamBatchService batchService;
    private final ExamSiteService siteService;
    private final RegistrationService registrationService;
    private final StageResultService stageResultService;
    private final MixAlgorithmService mixAlgorithmService;
    private final ObjectMapper objectMapper;

    public ArrangeService(AdmissionTicketMapper ticketMapper,
                          ArrangementRunMapper runMapper,
                          ExamRoomMapper roomMapper,
                          ExamBatchService batchService,
                          ExamSiteService siteService,
                          RegistrationService registrationService,
                          StageResultService stageResultService,
                          MixAlgorithmService mixAlgorithmService,
                          ObjectMapper objectMapper) {
        this.ticketMapper = ticketMapper;
        this.runMapper = runMapper;
        this.roomMapper = roomMapper;
        this.batchService = batchService;
        this.siteService = siteService;
        this.registrationService = registrationService;
        this.stageResultService = stageResultService;
        this.mixAlgorithmService = mixAlgorithmService;
        this.objectMapper = objectMapper;
    }

    // ---------- 预检：展示混编影响范围、座次方案与同校相邻统计 ----------

    @Transactional(readOnly = true)
    public ActionPreview previewArrange(ArrangeRequest request) {
        return preview(buildPlan(request), "ARRANGE");
    }

    @Transactional(readOnly = true)
    public ActionPreview previewRearrange(ArrangeRequest request) {
        assertRearrangable(request.getBatchId());
        return preview(buildPlan(request), "REARRANGE");
    }

    private ActionPreview preview(Plan plan, String action) {
        List<AffectedItem> items = new ArrayList<>();
        int actionable = 0;
        int skip = 0;
        for (Registration reg : plan.regs) {
            SeatingMixer.Assignment assignment = plan.assignmentByReg.get(reg.getId());
            boolean ok = assignment != null;
            String note = ok
                    ? String.format("%s 考场 %s 第 %d 号座，证号 %s%s",
                            plan.siteName, assignment.roomNo, assignment.seatNo,
                            plan.ticketNoByReg.get(reg.getId()),
                            schoolSuffix(reg))
                    : "考位不足，无法编排（需增加考场或容量）";
            if (ok) {
                actionable++;
            } else {
                skip++;
            }
            items.add(new AffectedItem(reg.getRegNo(), reg.getName(),
                    reg.getStatus(), ok, note));
        }

        ActionPreview preview = new ActionPreview();
        preview.setAction(action);
        preview.setTotalScanned(plan.regs.size());
        preview.setActionableCount(actionable);
        preview.setSkipCount(skip);
        preview.setSummary(String.format(
                "批次 %s：报名 %d 人，可用考位 %d 个，可编排 %d 人，未排下 %d 人；混编版本 %s，种子 %d，同校相邻 %d 对",
                plan.batch.getCode(), plan.regs.size(), plan.totalCapacity, actionable, skip,
                plan.mixVersionNo, plan.seed, plan.mix.adjacentPairs.size()));
        preview.setItems(items);
        preview.setMixVersionNo(plan.mixVersionNo);
        preview.setSeed(plan.seed);
        preview.setRoomCount(plan.mix.usedRoomCount);
        preview.setSameSchoolAdjacentCount(plan.mix.adjacentPairs.size());
        preview.setAdjacentPairs(toPairViews(plan.mix.adjacentPairs));
        return preview;
    }

    // ---------- 执行编排 ----------

    @Transactional
    public ActionResult arrange(ArrangeRequest request) {
        Plan plan = buildPlan(request);

        // 已有准考证不允许重复编排，避免覆盖已打印 / 已发放状态
        Long existing = ticketMapper.selectCount(
                new QueryWrapper<AdmissionTicket>().eq("batch_id", plan.batch.getId()));
        if (existing != null && existing > 0) {
            throw new BusinessException("ALREADY_ARRANGED",
                    "批次 " + plan.batch.getCode() + " 已编排过，不能重复编排；如需调整请走整批重排");
        }
        return persistNewPlan(plan, ExamConstants.STAGE_ARRANGEMENT, "ARRANGE");
    }

    // ---------- 整批重排（删旧证→整批重建，单一事务，不留半新半旧） ----------

    @Transactional
    public ActionResult rearrange(ArrangeRequest request) {
        assertRearrangable(request.getBatchId());
        Plan plan = buildPlan(request);

        // 旧准考证整批删除（同事务内），随后按新方案整批重建；任一步失败整体回滚
        ticketMapper.delete(new QueryWrapper<AdmissionTicket>().eq("batch_id", plan.batch.getId()));
        return persistNewPlan(plan, ExamConstants.STAGE_REARRANGE, "REARRANGE");
    }

    /** 重排前置条件：证一旦打印 / 发放就锁定座次，防止打证后座次与纸面不符。 */
    private void assertRearrangable(Long batchId) {
        ExamBatch batch = batchService.getBatch(batchId);
        Long used = ticketMapper.selectCount(new QueryWrapper<AdmissionTicket>()
                .eq("batch_id", batchId)
                .in("status", ExamConstants.TICKET_PRINTED, ExamConstants.TICKET_ISSUED));
        if (used != null && used > 0) {
            throw new BusinessException("REARRANGE_TICKETS_USED",
                    "批次 " + batch.getCode() + " 已有 " + used
                            + " 张准考证打印或发放，座次已锁定，不能整批重排；如需调整请先收回并回退发证状态");
        }
        Long total = ticketMapper.selectCount(
                new QueryWrapper<AdmissionTicket>().eq("batch_id", batchId));
        if (total == null || total == 0) {
            throw new BusinessException("NOT_ARRANGED",
                    "批次 " + batch.getCode() + " 尚未编排，无需重排，请直接编排");
        }
    }

    /** 按方案落库：准考证 + 名册状态 + 批次状态 + 编排记录（种子/座次/统计）+ 阶段结果。 */
    private ActionResult persistNewPlan(Plan plan, String stage, String runType) {
        String operator = OperatorHolder.current();

        // 先把同批次旧编排记录取消当前标记（重排场景），稍后追加新记录，旧记录保留可追溯
        List<ArrangementRun> oldRuns = runMapper.selectList(
                new QueryWrapper<ArrangementRun>().eq("batch_id", plan.batch.getId()));
        for (ArrangementRun old : oldRuns) {
            if (Boolean.TRUE.equals(old.getIsCurrent())) {
                old.setIsCurrent(false);
                runMapper.updateById(old);
            }
        }

        List<String> affected = new ArrayList<>();
        // 按考场、座位顺序生成证号流水，保证座次表与证号顺序一致
        List<SeatingMixer.Assignment> ordered = new ArrayList<>(plan.mix.assignments);
        for (int i = 0; i < ordered.size(); i++) {
            SeatingMixer.Assignment a = ordered.get(i);
            Registration reg = plan.regById.get(a.student.key);
            if (reg == null) {
                throw new BusinessException("ARRANGE_STATE_INVALID", "编排内部状态异常：" + a.student.key);
            }
            String ticketNo = generateTicketNo(plan.batch, i + 1);

            AdmissionTicket ticket = new AdmissionTicket();
            ticket.setTicketNo(ticketNo);
            ticket.setRegistrationId(reg.getId());
            ticket.setBatchId(plan.batch.getId());
            ticket.setSiteId(plan.batch.getSiteId());
            ticket.setRoomId(a.roomId);
            ticket.setSeatNo(a.seatNo);
            ticket.setStatus(ExamConstants.TICKET_PENDING);
            try {
                ticketMapper.insert(ticket);
            } catch (DuplicateKeyException e) {
                // 证号撞号 / 同批次考场座位撞号：直接拒收，事务回滚
                throw new BusinessException("TICKET_COLLISION",
                        "准考证撞号（证号或座位冲突），整批拒收：" + ticketNo);
            }

            reg.setStatus(ExamConstants.REG_ARRANGED);
            registrationService.updateRegistration(reg);
            affected.add(ticketNo);
        }

        // 容量不足：整批拒收，不落半条（异常抛出，事务回滚）
        if (affected.size() < plan.regs.size()) {
            throw new BusinessException("CAPACITY_EXCEEDED",
                    String.format("考位不足：报名 %d 人，可用考位 %d 个，缺口 %d 个",
                            plan.regs.size(), plan.totalCapacity,
                            plan.regs.size() - plan.totalCapacity));
        }

        ArrangementRun run = saveRun(plan, runType, operator, ordered, affected.size());

        plan.batch.setStatus(ExamConstants.BATCH_ARRANGED);
        batchService.updateBatch(plan.batch);

        String stageLabel = ExamConstants.STAGE_REARRANGE.equals(stage) ? "整批重排" : "编排";
        stageResultService.record(plan.batch.getId(), stage, affected.size(),
                String.format("%s %d 人，使用考场 %d 间，考位 %d 个，混编版本 %s，种子 %d，同校相邻 %d 对",
                        stageLabel, affected.size(), plan.mix.usedRoomCount, plan.totalCapacity,
                        plan.mixVersionNo, plan.seed, plan.mix.adjacentPairs.size()),
                operator);

        ActionResult result = new ActionResult();
        result.setAction(runType);
        result.setAffectedCount(affected.size());
        result.setSkipCount(0);
        result.setOperator(operator);
        result.setExecutedAt(LocalDateTime.now().toString());
        result.setAffectedKeys(affected);
        result.setSkipped(new ArrayList<>());
        result.setArrangementRunId(run.getId());
        result.setMixVersionNo(plan.mixVersionNo);
        result.setSeed(plan.seed);
        result.setRoomCount(plan.mix.usedRoomCount);
        result.setSameSchoolAdjacentCount(plan.mix.adjacentPairs.size());
        result.setAdjacentPairs(toPairViews(plan.mix.adjacentPairs));
        return result;
    }

    private ArrangementRun saveRun(Plan plan, String runType, String operator,
                                   List<SeatingMixer.Assignment> ordered, int totalCount) {
        List<SeatingEntry> seating = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            SeatingMixer.Assignment a = ordered.get(i);
            Registration reg = plan.regById.get(a.student.key);
            SeatingEntry entry = new SeatingEntry();
            entry.setTicketNo(generateTicketNo(plan.batch, i + 1));
            entry.setRegNo(reg.getRegNo());
            entry.setName(reg.getName());
            entry.setSchool(reg.getSchool());
            entry.setRoomId(a.roomId);
            entry.setRoomNo(a.roomNo);
            entry.setSeatNo(a.seatNo);
            seating.add(entry);
        }

        ArrangementRun run = new ArrangementRun();
        run.setBatchId(plan.batch.getId());
        run.setRunType(runType);
        run.setMixVersionId(plan.mixVersion == null ? null : plan.mixVersion.getId());
        run.setMixVersionNo(plan.mixVersionNo);
        run.setSeed(plan.seed);
        run.setSeating(writeJson(seating, "SEATING_SERIALIZE_FAILED", "座次快照"));
        run.setTotalCount(totalCount);
        run.setRoomCount(plan.mix.usedRoomCount);
        run.setSameSchoolAdjacentCount(plan.mix.adjacentPairs.size());
        run.setAdjacentDetail(writeJson(toPairViews(plan.mix.adjacentPairs),
                "ADJACENT_SERIALIZE_FAILED", "同校相邻明细"));
        run.setIsCurrent(true);
        run.setOperator(operator);
        run.setExecutedAt(LocalDateTime.now());
        runMapper.insert(run);
        return run;
    }

    // ---------- 编排记录查询（巡考抽查） ----------

    @Transactional(readOnly = true)
    public ArrangementRun getCurrentRun(Long batchId) {
        batchService.getBatch(batchId);
        ArrangementRun run = runMapper.selectOne(new QueryWrapper<ArrangementRun>()
                .eq("batch_id", batchId)
                .eq("is_current", true)
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (run == null) {
            throw new BusinessException("ARRANGEMENT_RUN_NOT_FOUND",
                    "批次还没有编排记录（未编排）：batchId=" + batchId);
        }
        return run;
    }

    @Transactional(readOnly = true)
    public List<ArrangementRun> listRuns(Long batchId) {
        batchService.getBatch(batchId);
        List<ArrangementRun> runs = runMapper.selectList(new QueryWrapper<ArrangementRun>()
                .eq("batch_id", batchId)
                .orderByDesc("id"));
        // 列表只看版本/种子/统计，不带座次与明细两个 CLOB 大字段
        for (ArrangementRun run : runs) {
            run.setSeating(null);
            run.setAdjacentDetail(null);
        }
        return runs;
    }

    /** 单次编排记录详情（含座次快照与同校相邻明细）。 */
    @Transactional(readOnly = true)
    public ArrangementRun getRun(Long runId) {
        ArrangementRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException("ARRANGEMENT_RUN_NOT_FOUND", "编排记录不存在：id=" + runId);
        }
        return run;
    }

    /** 当前座次：供座次表导出与抽查读取。 */
    @Transactional(readOnly = true)
    public List<SeatingEntry> currentSeating(Long batchId) {
        ArrangementRun run = getCurrentRun(batchId);
        return readSeating(run.getSeating());
    }

    public List<SeatingEntry> readSeating(String json) {
        try {
            List<SeatingEntry> list = objectMapper.readValue(json, new TypeReference<List<SeatingEntry>>() {
            });
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            throw new BusinessException("SEATING_BROKEN", "座次快照无法解析：" + e.getMessage());
        }
    }

    // ---------- 编排方案构建（预检与执行共用，保证两者一致） ----------

    private Plan buildPlan(ArrangeRequest request) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        siteService.getSite(batch.getSiteId());

        List<Registration> regs = registrationService.listByBatch(batch.getId());
        if (regs.isEmpty()) {
            throw new BusinessException("NO_REGISTRATION", "批次下还没有报名名册，无法编排：" + batch.getCode());
        }

        List<ExamRoom> rooms = resolveRooms(batch, request.getRoomOrder());

        int totalCapacity = 0;
        for (ExamRoom room : rooms) {
            totalCapacity += room.getCapacity();
        }
        String siteName = siteService.getSite(batch.getSiteId()).getName();

        // 解析项目当前生效混编算法版本；没有生效版则用内置默认参数
        MixAlgorithmVersion version = mixAlgorithmService.findActiveVersion(batch.getProjectCode());
        MixParams params = version == null
                ? mixAlgorithmService.defaultParams()
                : mixAlgorithmService.readParams(version.getParams());
        String mixVersionNo = version == null ? BUILTIN_VERSION_NO : version.getVersionNo();

        long seed = resolveSeed(request.getSeed(), batch, regs.size(), totalCapacity);

        List<SeatingMixer.Student> students = new ArrayList<>();
        Map<String, Registration> regByRegNo = new HashMap<>();
        for (Registration reg : regs) {
            students.add(new SeatingMixer.Student(reg.getRegNo(), reg.getName(), reg.getSchool()));
            regByRegNo.put(reg.getRegNo(), reg);
        }
        List<SeatingMixer.Room> mixRooms = new ArrayList<>();
        for (ExamRoom room : rooms) {
            mixRooms.add(new SeatingMixer.Room(room.getId(), room.getRoomNo(), room.getCapacity()));
        }

        SeatingMixer.Plan mix = SeatingMixer.mix(students, mixRooms, params, seed);

        Plan plan = new Plan();
        plan.batch = batch;
        plan.rooms = rooms;
        plan.totalCapacity = totalCapacity;
        plan.siteName = siteName;
        plan.regs = regs;
        plan.regById = regByRegNo;
        plan.mix = mix;
        plan.mixVersion = version;
        plan.mixVersionNo = mixVersionNo;
        plan.seed = seed;

        Set<Long> assignedRegIds = new HashSet<>();
        for (SeatingMixer.Assignment a : mix.assignments) {
            Registration reg = regByRegNo.get(a.student.key);
            plan.assignmentByReg.put(reg.getId(), a);
            assignedRegIds.add(reg.getId());
        }
        // 证号按考场+座位顺序生成
        List<SeatingMixer.Assignment> ordered = new ArrayList<>(mix.assignments);
        for (int i = 0; i < ordered.size(); i++) {
            Registration reg = regByRegNo.get(ordered.get(i).student.key);
            plan.ticketNoByReg.put(reg.getId(), generateTicketNo(batch, i + 1));
        }
        return plan;
    }

    private List<ExamRoom> resolveRooms(ExamBatch batch, List<String> roomOrder) {
        List<ExamRoom> rooms = roomMapper.selectList(new QueryWrapper<ExamRoom>()
                .eq("site_id", batch.getSiteId())
                .eq("status", "ENABLED")
                .orderByAsc("room_no"));
        if (rooms.isEmpty()) {
            throw new BusinessException("NO_ROOM", "考点下没有启用的考场，无法编排");
        }

        // 指定考场顺序时校验：必须全部属于本考点且不重不漏
        if (roomOrder != null && !roomOrder.isEmpty()) {
            List<String> order = roomOrder;
            if (new HashSet<>(order).size() != order.size()) {
                throw new BusinessException("ROOM_ORDER_DUP", "指定的考场顺序存在重复考场号");
            }
            List<ExamRoom> ordered = new ArrayList<>();
            for (String roomNo : order) {
                ExamRoom hit = null;
                for (ExamRoom room : rooms) {
                    if (room.getRoomNo().equals(roomNo.trim())) {
                        hit = room;
                        break;
                    }
                }
                if (hit == null) {
                    throw new BusinessException("ROOM_NOT_IN_SITE",
                            "考场不属于本批次考点或未启用：" + roomNo);
                }
                ordered.add(hit);
            }
            // 未列入顺序的启用考场追加在后面，容量仍可用
            Set<String> picked = new HashSet<>(order);
            for (ExamRoom room : rooms) {
                if (!picked.contains(room.getRoomNo())) {
                    ordered.add(room);
                }
            }
            rooms = ordered;
        }
        return rooms;
    }

    /**
     * 种子：显式指定优先；否则按 批次+人数+容量+历史编排次数 取确定性种子。
     * 这样"预检→执行"同请求结果一致，而每次整批重排默认种子不同、座次会变；显式传种子可复现。
     */
    private long resolveSeed(Long requested, ExamBatch batch, int regCount, int totalCapacity) {
        if (requested != null) {
            return requested;
        }
        Long runCount = runMapper.selectCount(
                new QueryWrapper<ArrangementRun>().eq("batch_id", batch.getId()));
        String basis = batch.getCode() + "|" + regCount + "|" + totalCapacity + "|" + (runCount == null ? 0 : runCount);
        return fnv1a64(basis);
    }

    private static long fnv1a64(String s) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private String schoolSuffix(Registration reg) {
        return reg.getSchool() == null || reg.getSchool().trim().isEmpty()
                ? "（未填学校）" : "（学校：" + reg.getSchool().trim() + "）";
    }

    private List<AdjacentPairView> toPairViews(List<SeatingMixer.AdjacentPair> pairs) {
        List<AdjacentPairView> views = new ArrayList<>();
        for (SeatingMixer.AdjacentPair p : pairs) {
            AdjacentPairView v = new AdjacentPairView();
            v.setRoomNo(p.roomNo);
            v.setSeatA(p.seatA);
            v.setSeatB(p.seatB);
            v.setSchool(p.school);
            v.setRegNoA(p.keyA);
            v.setNameA(p.nameA);
            v.setRegNoB(p.keyB);
            v.setNameB(p.nameB);
            views.add(v);
        }
        return views;
    }

    private String writeJson(Object value, String code, String label) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(code, label + "序列化失败：" + e.getMessage());
        }
    }

    /**
     * 证号规则：ZK + 批次编码 + 4 位流水。批次编码已唯一，故证号全局唯一；
     * 唯一约束 uk_ticket_no 再兜底，撞号直接拒收。
     */
    private String generateTicketNo(ExamBatch batch, int seq) {
        return "ZK" + batch.getCode() + "-" + String.format("%04d", seq);
    }

    private static class Plan {
        private ExamBatch batch;
        private List<ExamRoom> rooms;
        private int totalCapacity;
        private String siteName;
        private List<Registration> regs;
        private Map<String, Registration> regById;
        private SeatingMixer.Plan mix;
        private MixAlgorithmVersion mixVersion;
        private String mixVersionNo;
        private long seed;
        private final Map<Long, SeatingMixer.Assignment> assignmentByReg = new HashMap<>();
        private final Map<Long, String> ticketNoByReg = new HashMap<>();
    }
}
