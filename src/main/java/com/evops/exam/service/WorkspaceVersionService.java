package com.evops.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.absence.entity.AbsenceMark;
import com.evops.absence.entity.AbsenceMarkVersion;
import com.evops.absence.entity.AbsenceStat;
import com.evops.absence.entity.ExamineeCheckin;
import com.evops.absence.entity.ScoreSnapshot;
import com.evops.absence.entity.ViolationRecord;
import com.evops.absence.mapper.AbsenceMarkMapper;
import com.evops.absence.mapper.AbsenceMarkVersionMapper;
import com.evops.absence.mapper.AbsenceStatMapper;
import com.evops.absence.mapper.ExamineeCheckinMapper;
import com.evops.absence.mapper.ScoreSnapshotMapper;
import com.evops.absence.mapper.ViolationRecordMapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.common.ExamConstants;
import com.evops.exam.entity.AdmissionTicket;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.ExamRoom;
import com.evops.exam.entity.ExamSite;
import com.evops.exam.entity.FinalConclusion;
import com.evops.exam.entity.Registration;
import com.evops.exam.entity.RegistrationCorrection;
import com.evops.exam.entity.StageResult;
import com.evops.exam.entity.WorkspaceVersion;
import com.evops.exam.entity.WorkspaceVersionEvent;
import com.evops.exam.mapper.ArrangementRunMapper;
import com.evops.exam.entity.ArrangementRun;
import com.evops.grade.entity.ExamScore;
import com.evops.grade.entity.GradeRule;
import com.evops.grade.entity.GradeRuleVersion;
import com.evops.grade.entity.ScoreChangeLog;
import com.evops.grade.entity.ScoreNotification;
import com.evops.grade.entity.ScoreReview;
import com.evops.mixalgo.entity.MixAlgorithm;
import com.evops.mixalgo.entity.MixAlgorithmVersion;
import com.evops.mixalgo.mapper.MixAlgorithmMapper;
import com.evops.mixalgo.mapper.MixAlgorithmVersionMapper;
import com.evops.appoint.entity.Appointment;
import com.evops.appoint.entity.AppointmentReplacement;
import com.evops.appoint.entity.AppointmentVersion;
import com.evops.appoint.entity.InvigilationShift;
import com.evops.appoint.entity.Invigilator;
import com.evops.appoint.entity.InvigilatorAllowanceRate;
import com.evops.appoint.entity.Settlement;
import com.evops.appoint.entity.SettlementAdjustment;
import com.evops.appoint.entity.SettlementItem;
import com.evops.appoint.mapper.AppointmentMapper;
import com.evops.appoint.mapper.AppointmentReplacementMapper;
import com.evops.appoint.mapper.AppointmentVersionMapper;
import com.evops.appoint.mapper.InvigilationShiftMapper;
import com.evops.appoint.mapper.InvigilatorAllowanceRateMapper;
import com.evops.appoint.mapper.InvigilatorMapper;
import com.evops.appoint.mapper.SettlementAdjustmentMapper;
import com.evops.appoint.mapper.SettlementItemMapper;
import com.evops.appoint.mapper.SettlementMapper;
import com.evops.vault.entity.VaultAccess;
import com.evops.vault.entity.VaultAccessNote;
import com.evops.vault.entity.VaultAccessVersion;
import com.evops.vault.entity.VaultAlert;
import com.evops.vault.entity.VaultBox;
import com.evops.vault.entity.VaultBoxMove;
import com.evops.vault.entity.VaultConfig;
import com.evops.vault.entity.VaultPerson;
import com.evops.vault.mapper.VaultAccessMapper;
import com.evops.vault.mapper.VaultAccessNoteMapper;
import com.evops.vault.mapper.VaultAccessVersionMapper;
import com.evops.vault.mapper.VaultAlertMapper;
import com.evops.vault.mapper.VaultBoxMapper;
import com.evops.vault.mapper.VaultBoxMoveMapper;
import com.evops.vault.mapper.VaultConfigMapper;
import com.evops.vault.mapper.VaultPersonMapper;
import com.evops.grade.mapper.ExamScoreMapper;
import com.evops.grade.mapper.GradeRuleMapper;
import com.evops.grade.mapper.GradeRuleVersionMapper;
import com.evops.grade.mapper.ScoreChangeLogMapper;
import com.evops.grade.mapper.ScoreNotificationMapper;
import com.evops.grade.mapper.ScoreReviewMapper;
import com.evops.cert.entity.CertNumberRule;
import com.evops.cert.entity.CertNumberRuleVersion;
import com.evops.cert.entity.CertSeq;
import com.evops.cert.entity.Certificate;
import com.evops.cert.entity.CertificateRevokeRecord;
import com.evops.cert.entity.CertVerifyConfig;
import com.evops.cert.mapper.CertNumberRuleMapper;
import com.evops.cert.mapper.CertNumberRuleVersionMapper;
import com.evops.cert.mapper.CertSeqMapper;
import com.evops.cert.mapper.CertificateMapper;
import com.evops.cert.mapper.CertificateRevokeRecordMapper;
import com.evops.cert.mapper.CertVerifyConfigMapper;
import com.evops.exam.mapper.AdmissionTicketMapper;
import com.evops.exam.mapper.ExamBatchMapper;
import com.evops.exam.mapper.ExamRoomMapper;
import com.evops.exam.mapper.ExamSiteMapper;
import com.evops.exam.mapper.FinalConclusionMapper;
import com.evops.exam.mapper.RegistrationCorrectionMapper;
import com.evops.exam.mapper.RegistrationMapper;
import com.evops.exam.mapper.RestoreMapper;
import com.evops.exam.mapper.StageResultMapper;
import com.evops.exam.mapper.WorkspaceVersionEventMapper;
import com.evops.exam.mapper.WorkspaceVersionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作区版本管理：
 * - SNAPSHOT 快照只追加，永不覆盖、不删除；
 * - SELECT 版本选择只切换当前标记，不动业务数据；
 * - ROLLBACK 状态回退：按快照还原业务表，并生成一条新的 ROLLBACK 版本，
 *   原版本原样保留；CREATE/SELECT/ROLLBACK 全部进事件表，回退不清事件。
 */
@Service
public class WorkspaceVersionService {

    private final WorkspaceVersionMapper versionMapper;
    private final WorkspaceVersionEventMapper eventMapper;
    private final ExamSiteMapper siteMapper;
    private final ExamRoomMapper roomMapper;
    private final ExamBatchMapper batchMapper;
    private final RegistrationMapper registrationMapper;
    private final RegistrationCorrectionMapper correctionMapper;
    private final AdmissionTicketMapper ticketMapper;
    private final StageResultMapper stageResultMapper;
    private final FinalConclusionMapper conclusionMapper;
    private final GradeRuleMapper gradeRuleMapper;
    private final GradeRuleVersionMapper gradeRuleVersionMapper;
    private final ExamScoreMapper scoreMapper;
    private final ScoreReviewMapper reviewMapper;
    private final ScoreChangeLogMapper changeLogMapper;
    private final ScoreNotificationMapper notificationMapper;
    private final MixAlgorithmMapper mixAlgorithmMapper;
    private final MixAlgorithmVersionMapper mixVersionMapper;
    private final ArrangementRunMapper arrangementRunMapper;
    private final InvigilatorMapper invigilatorMapper;
    private final InvigilatorAllowanceRateMapper allowanceRateMapper;
    private final InvigilationShiftMapper shiftMapper;
    private final AppointmentMapper appointmentMapper;
    private final AppointmentVersionMapper appointmentVersionMapper;
    private final AppointmentReplacementMapper replacementMapper;
    private final SettlementMapper settlementMapper;
    private final SettlementItemMapper settlementItemMapper;
    private final SettlementAdjustmentMapper settlementAdjustmentMapper;
    private final VaultPersonMapper vaultPersonMapper;
    private final VaultBoxMapper vaultBoxMapper;
    private final VaultAccessMapper vaultAccessMapper;
    private final VaultAccessVersionMapper vaultAccessVersionMapper;
    private final VaultBoxMoveMapper vaultBoxMoveMapper;
    private final VaultAccessNoteMapper vaultAccessNoteMapper;
    private final VaultAlertMapper vaultAlertMapper;
    private final VaultConfigMapper vaultConfigMapper;
    private final ExamineeCheckinMapper examineeCheckinMapper;
    private final ViolationRecordMapper violationRecordMapper;
    private final AbsenceMarkMapper absenceMarkMapper;
    private final AbsenceMarkVersionMapper absenceMarkVersionMapper;
    private final ScoreSnapshotMapper scoreSnapshotMapper;
    private final AbsenceStatMapper absenceStatMapper;
    private final CertNumberRuleMapper certRuleMapper;
    private final CertNumberRuleVersionMapper certRuleVersionMapper;
    private final CertSeqMapper certSeqMapper;
    private final CertificateMapper certificateMapper;
    private final CertificateRevokeRecordMapper certRevokeRecordMapper;
    private final CertVerifyConfigMapper certVerifyConfigMapper;
    private final ObjectMapper objectMapper;
    private final RestoreMapper restoreMapper;

    public WorkspaceVersionService(WorkspaceVersionMapper versionMapper,
                                   WorkspaceVersionEventMapper eventMapper,
                                   ExamSiteMapper siteMapper,
                                   ExamRoomMapper roomMapper,
                                   ExamBatchMapper batchMapper,
                                   RegistrationMapper registrationMapper,
                                   RegistrationCorrectionMapper correctionMapper,
                                   AdmissionTicketMapper ticketMapper,
                                   StageResultMapper stageResultMapper,
                                   FinalConclusionMapper conclusionMapper,
                                   GradeRuleMapper gradeRuleMapper,
                                   GradeRuleVersionMapper gradeRuleVersionMapper,
                                   ExamScoreMapper scoreMapper,
                                   ScoreReviewMapper reviewMapper,
                                   ScoreChangeLogMapper changeLogMapper,
                                   ScoreNotificationMapper notificationMapper,
                                   MixAlgorithmMapper mixAlgorithmMapper,
                                   MixAlgorithmVersionMapper mixVersionMapper,
                                   ArrangementRunMapper arrangementRunMapper,
                                   InvigilatorMapper invigilatorMapper,
                                   InvigilatorAllowanceRateMapper allowanceRateMapper,
                                   InvigilationShiftMapper shiftMapper,
                                   AppointmentMapper appointmentMapper,
                                   AppointmentVersionMapper appointmentVersionMapper,
                                   AppointmentReplacementMapper replacementMapper,
                                   SettlementMapper settlementMapper,
                                   SettlementItemMapper settlementItemMapper,
                                   SettlementAdjustmentMapper settlementAdjustmentMapper,
                                   VaultPersonMapper vaultPersonMapper,
                                   VaultBoxMapper vaultBoxMapper,
                                   VaultAccessMapper vaultAccessMapper,
                                   VaultAccessVersionMapper vaultAccessVersionMapper,
                                   VaultBoxMoveMapper vaultBoxMoveMapper,
                                   VaultAccessNoteMapper vaultAccessNoteMapper,
                                   VaultAlertMapper vaultAlertMapper,
                                   VaultConfigMapper vaultConfigMapper,
                                   ExamineeCheckinMapper examineeCheckinMapper,
                                   ViolationRecordMapper violationRecordMapper,
                                   AbsenceMarkMapper absenceMarkMapper,
                                   AbsenceMarkVersionMapper absenceMarkVersionMapper,
                                   ScoreSnapshotMapper scoreSnapshotMapper,
                                   AbsenceStatMapper absenceStatMapper,
                                   CertNumberRuleMapper certRuleMapper,
                                   CertNumberRuleVersionMapper certRuleVersionMapper,
                                   CertSeqMapper certSeqMapper,
                                   CertificateMapper certificateMapper,
                                   CertificateRevokeRecordMapper certRevokeRecordMapper,
                                   CertVerifyConfigMapper certVerifyConfigMapper,
                                   ObjectMapper objectMapper,
                                   RestoreMapper restoreMapper) {
        this.versionMapper = versionMapper;
        this.eventMapper = eventMapper;
        this.siteMapper = siteMapper;
        this.roomMapper = roomMapper;
        this.batchMapper = batchMapper;
        this.registrationMapper = registrationMapper;
        this.correctionMapper = correctionMapper;
        this.ticketMapper = ticketMapper;
        this.stageResultMapper = stageResultMapper;
        this.conclusionMapper = conclusionMapper;
        this.gradeRuleMapper = gradeRuleMapper;
        this.gradeRuleVersionMapper = gradeRuleVersionMapper;
        this.scoreMapper = scoreMapper;
        this.reviewMapper = reviewMapper;
        this.changeLogMapper = changeLogMapper;
        this.notificationMapper = notificationMapper;
        this.mixAlgorithmMapper = mixAlgorithmMapper;
        this.mixVersionMapper = mixVersionMapper;
        this.arrangementRunMapper = arrangementRunMapper;
        this.invigilatorMapper = invigilatorMapper;
        this.allowanceRateMapper = allowanceRateMapper;
        this.shiftMapper = shiftMapper;
        this.appointmentMapper = appointmentMapper;
        this.appointmentVersionMapper = appointmentVersionMapper;
        this.replacementMapper = replacementMapper;
        this.settlementMapper = settlementMapper;
        this.settlementItemMapper = settlementItemMapper;
        this.settlementAdjustmentMapper = settlementAdjustmentMapper;
        this.vaultPersonMapper = vaultPersonMapper;
        this.vaultBoxMapper = vaultBoxMapper;
        this.vaultAccessMapper = vaultAccessMapper;
        this.vaultAccessVersionMapper = vaultAccessVersionMapper;
        this.vaultBoxMoveMapper = vaultBoxMoveMapper;
        this.vaultAccessNoteMapper = vaultAccessNoteMapper;
        this.vaultAlertMapper = vaultAlertMapper;
        this.vaultConfigMapper = vaultConfigMapper;
        this.examineeCheckinMapper = examineeCheckinMapper;
        this.violationRecordMapper = violationRecordMapper;
        this.absenceMarkMapper = absenceMarkMapper;
        this.absenceMarkVersionMapper = absenceMarkVersionMapper;
        this.scoreSnapshotMapper = scoreSnapshotMapper;
        this.absenceStatMapper = absenceStatMapper;
        this.certRuleMapper = certRuleMapper;
        this.certRuleVersionMapper = certRuleVersionMapper;
        this.certSeqMapper = certSeqMapper;
        this.certificateMapper = certificateMapper;
        this.certRevokeRecordMapper = certRevokeRecordMapper;
        this.certVerifyConfigMapper = certVerifyConfigMapper;
        this.objectMapper = objectMapper;
        this.restoreMapper = restoreMapper;
    }

    // ---------- 创建快照 ----------

    @Transactional
    public WorkspaceVersion createSnapshot(String label, String remark) {
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        String snapshot = capture();

        WorkspaceVersion version = new WorkspaceVersion();
        version.setVersionNo(nextVersionNo());
        version.setLabel(label == null || label.trim().isEmpty() ? "工作区快照 " + now : label.trim());
        version.setKind(ExamConstants.VERSION_KIND_SNAPSHOT);
        version.setIsCurrent(false);
        version.setSnapshot(snapshot);
        version.setRemark(remark);
        version.setOperator(operator);
        versionMapper.insert(version);

        markCurrent(version, operator, "CREATE", "创建快照 " + version.getVersionNo());
        return version;
    }

    // ---------- 版本选择（只切当前标记，留事件） ----------

    @Transactional
    public WorkspaceVersion selectVersion(String versionNo) {
        WorkspaceVersion version = requireVersion(versionNo);
        String operator = OperatorHolder.current();
        markCurrent(version, operator, "SELECT", "选择版本 " + versionNo + " 作为当前版本（业务数据未变动）");
        return version;
    }

    // ---------- 状态回退（还原 + 生成新版本） ----------

    @Transactional
    public WorkspaceVersion rollback(String versionNo, String remark) {
        WorkspaceVersion source = requireVersion(versionNo);
        String operator = OperatorHolder.current();

        restore(source.getSnapshot());

        WorkspaceVersion version = new WorkspaceVersion();
        version.setVersionNo(nextVersionNo());
        version.setLabel("回退自 " + source.getVersionNo());
        version.setKind(ExamConstants.VERSION_KIND_ROLLBACK);
        version.setRestoredFromVersion(source.getVersionNo());
        version.setIsCurrent(false);
        version.setSnapshot(source.getSnapshot());
        version.setRemark(remark);
        version.setOperator(operator);
        versionMapper.insert(version);

        markCurrent(version, operator, "ROLLBACK",
                "按 " + source.getVersionNo() + " 还原业务状态并生成回退版本 " + version.getVersionNo());
        return version;
    }

    // ---------- 查询 ----------

    @Transactional(readOnly = true)
    public List<WorkspaceVersion> listVersions(boolean includeSnapshot) {
        QueryWrapper<WorkspaceVersion> wrapper = new QueryWrapper<WorkspaceVersion>()
                .orderByDesc("id");
        if (!includeSnapshot) {
            wrapper.select("id", "version_no", "label", "kind", "restored_from_version",
                    "is_current", "remark", "operator",
                    "create_by", "create_time", "update_by", "update_time");
        }
        return versionMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public WorkspaceVersion getVersion(String versionNo, boolean includeSnapshot) {
        WorkspaceVersion version = requireVersion(versionNo);
        if (!includeSnapshot) {
            version.setSnapshot(null);
        }
        return version;
    }

    @Transactional(readOnly = true)
    public WorkspaceVersion currentVersion() {
        WorkspaceVersion version = versionMapper.selectOne(
                new QueryWrapper<WorkspaceVersion>().eq("is_current", true));
        if (version != null) {
            version.setSnapshot(null);
        }
        return version;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceVersionEvent> listEvents(String versionNo) {
        QueryWrapper<WorkspaceVersionEvent> wrapper = new QueryWrapper<>();
        if (versionNo != null && !versionNo.trim().isEmpty()) {
            wrapper.eq("version_no", versionNo.trim());
        }
        wrapper.orderByAsc("event_at", "id");
        return eventMapper.selectList(wrapper);
    }

    // ---------- 内部：快照采集与还原 ----------

    private String capture() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("capturedAt", LocalDateTime.now().toString());
            data.put("sites", siteMapper.selectList(null));
            data.put("rooms", roomMapper.selectList(null));
            data.put("batches", batchMapper.selectList(null));
            data.put("registrations", registrationMapper.selectList(null));
            data.put("corrections", correctionMapper.selectList(null));
            data.put("tickets", ticketMapper.selectList(null));
            data.put("stageResults", stageResultMapper.selectList(null));
            data.put("conclusions", conclusionMapper.selectList(null));
            data.put("gradeRules", gradeRuleMapper.selectList(null));
            data.put("gradeRuleVersions", gradeRuleVersionMapper.selectList(null));
            data.put("scores", scoreMapper.selectList(null));
            data.put("reviews", reviewMapper.selectList(null));
            data.put("changeLogs", changeLogMapper.selectList(null));
            data.put("notifications", notificationMapper.selectList(null));
            data.put("mixAlgorithms", mixAlgorithmMapper.selectList(null));
            data.put("mixVersions", mixVersionMapper.selectList(null));
            data.put("arrangementRuns", arrangementRunMapper.selectList(null));
            data.put("invigilators", invigilatorMapper.selectList(null));
            data.put("allowanceRates", allowanceRateMapper.selectList(null));
            data.put("invigilationShifts", shiftMapper.selectList(null));
            data.put("appointments", appointmentMapper.selectList(null));
            data.put("appointmentVersions", appointmentVersionMapper.selectList(null));
            data.put("appointmentReplacements", replacementMapper.selectList(null));
            data.put("settlements", settlementMapper.selectList(null));
            data.put("settlementItems", settlementItemMapper.selectList(null));
            data.put("settlementAdjustments", settlementAdjustmentMapper.selectList(null));
            data.put("vaultPersons", vaultPersonMapper.selectList(null));
            data.put("vaultBoxes", vaultBoxMapper.selectList(null));
            data.put("vaultAccesses", vaultAccessMapper.selectList(null));
            data.put("vaultAccessVersions", vaultAccessVersionMapper.selectList(null));
            data.put("vaultBoxMoves", vaultBoxMoveMapper.selectList(null));
            data.put("vaultAccessNotes", vaultAccessNoteMapper.selectList(null));
            data.put("vaultAlerts", vaultAlertMapper.selectList(null));
            data.put("vaultConfigs", vaultConfigMapper.selectList(null));
            data.put("examineeCheckins", examineeCheckinMapper.selectList(null));
            data.put("violationRecords", violationRecordMapper.selectList(null));
            data.put("absenceMarks", absenceMarkMapper.selectList(null));
            data.put("absenceMarkVersions", absenceMarkVersionMapper.selectList(null));
            data.put("scoreSnapshots", scoreSnapshotMapper.selectList(null));
            data.put("absenceStats", absenceStatMapper.selectList(null));
            data.put("certRules", certRuleMapper.selectList(null));
            data.put("certRuleVersions", certRuleVersionMapper.selectList(null));
            data.put("certSeqs", certSeqMapper.selectList(null));
            data.put("certificates", certificateMapper.selectList(null));
            data.put("certRevokeRecords", certRevokeRecordMapper.selectList(null));
            data.put("certVerifyConfigs", certVerifyConfigMapper.selectList(null));
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new BusinessException("SNAPSHOT_FAILED", "生成工作区快照失败：" + e.getMessage());
        }
    }

    private void restore(String snapshot) {
        SnapshotData data;
        try {
            data = objectMapper.readValue(snapshot, SnapshotData.class);
        } catch (Exception e) {
            throw new BusinessException("SNAPSHOT_BROKEN", "快照内容无法解析，已取消回退：" + e.getMessage());
        }

        // 先清空业务表（版本表本身不动），再按快照原样写回。
        // 用显式带主键的原生 INSERT：准考证、成绩等表的外键依赖原始 id，
        // H2 的 GENERATED BY DEFAULT 允许指定 id 插入。
        restoreMapper.clearCertRevokeRecords();
        restoreMapper.clearCertificates();
        restoreMapper.clearCertSeqs();
        restoreMapper.clearCertRuleVersions();
        restoreMapper.clearCertRules();
        restoreMapper.clearCertVerifyConfigs();
        restoreMapper.clearAbsenceStats();
        restoreMapper.clearScoreSnapshots();
        restoreMapper.clearAbsenceMarkVersions();
        restoreMapper.clearAbsenceMarks();
        restoreMapper.clearViolationRecords();
        restoreMapper.clearExamineeCheckins();
        restoreMapper.clearVaultAlerts();
        restoreMapper.clearVaultAccessNotes();
        restoreMapper.clearVaultBoxMoves();
        restoreMapper.clearVaultAccessVersions();
        restoreMapper.clearVaultAccesses();
        restoreMapper.clearVaultBoxes();
        restoreMapper.clearVaultPersons();
        restoreMapper.clearVaultConfigs();
        restoreMapper.clearNotifications();
        restoreMapper.clearArrangementRuns();
        restoreMapper.clearSettlementAdjustments();
        restoreMapper.clearSettlementItems();
        restoreMapper.clearSettlements();
        restoreMapper.clearAppointmentReplacements();
        restoreMapper.clearAppointmentVersions();
        restoreMapper.clearAppointments();
        restoreMapper.clearInvigilationShifts();
        restoreMapper.clearAllowanceRates();
        restoreMapper.clearInvigilators();
        restoreMapper.clearMixVersions();
        restoreMapper.clearMixAlgorithms();
        restoreMapper.clearChangeLogs();
        restoreMapper.clearReviews();
        restoreMapper.clearScores();
        restoreMapper.clearGradeRuleVersions();
        restoreMapper.clearGradeRules();
        restoreMapper.clearConclusions();
        restoreMapper.clearStageResults();
        restoreMapper.clearTickets();
        restoreMapper.clearCorrections();
        restoreMapper.clearRegistrations();
        restoreMapper.clearBatches();
        restoreMapper.clearRooms();
        restoreMapper.clearSites();

        for (ExamSite s : nullSafe(data.sites)) {
            restoreMapper.insertSite(s);
        }
        for (ExamRoom r : nullSafe(data.rooms)) {
            restoreMapper.insertRoom(r);
        }
        for (ExamBatch b : nullSafe(data.batches)) {
            restoreMapper.insertBatch(b);
        }
        for (Registration r : nullSafe(data.registrations)) {
            restoreMapper.insertRegistration(r);
        }
        for (RegistrationCorrection c : nullSafe(data.corrections)) {
            restoreMapper.insertCorrection(c);
        }
        for (AdmissionTicket t : nullSafe(data.tickets)) {
            restoreMapper.insertTicket(t);
        }
        for (StageResult sr : nullSafe(data.stageResults)) {
            restoreMapper.insertStageResult(sr);
        }
        for (FinalConclusion fc : nullSafe(data.conclusions)) {
            restoreMapper.insertConclusion(fc);
        }
        for (GradeRule r : nullSafe(data.gradeRules)) {
            restoreMapper.insertGradeRule(r);
        }
        for (GradeRuleVersion v : nullSafe(data.gradeRuleVersions)) {
            restoreMapper.insertGradeRuleVersion(v);
        }
        for (ExamScore s : nullSafe(data.scores)) {
            restoreMapper.insertScore(s);
        }
        for (ScoreReview r : nullSafe(data.reviews)) {
            restoreMapper.insertReview(r);
        }
        for (ScoreChangeLog l : nullSafe(data.changeLogs)) {
            restoreMapper.insertChangeLog(l);
        }
        for (ScoreNotification n : nullSafe(data.notifications)) {
            restoreMapper.insertNotification(n);
        }
        for (ExamineeCheckin c : nullSafe(data.examineeCheckins)) {
            restoreMapper.insertExamineeCheckin(c);
        }
        for (ViolationRecord v : nullSafe(data.violationRecords)) {
            restoreMapper.insertViolationRecord(v);
        }
        for (AbsenceMark m : nullSafe(data.absenceMarks)) {
            restoreMapper.insertAbsenceMark(m);
        }
        for (AbsenceMarkVersion v : nullSafe(data.absenceMarkVersions)) {
            restoreMapper.insertAbsenceMarkVersion(v);
        }
        for (ScoreSnapshot s : nullSafe(data.scoreSnapshots)) {
            restoreMapper.insertScoreSnapshot(s);
        }
        for (AbsenceStat s : nullSafe(data.absenceStats)) {
            restoreMapper.insertAbsenceStat(s);
        }
        for (CertNumberRule r : nullSafe(data.certRules)) {
            restoreMapper.insertCertRule(r);
        }
        for (CertNumberRuleVersion v : nullSafe(data.certRuleVersions)) {
            restoreMapper.insertCertRuleVersion(v);
        }
        for (CertSeq s : nullSafe(data.certSeqs)) {
            restoreMapper.insertCertSeq(s);
        }
        for (Certificate c : nullSafe(data.certificates)) {
            restoreMapper.insertCertificate(c);
        }
        for (CertificateRevokeRecord r : nullSafe(data.certRevokeRecords)) {
            restoreMapper.insertCertRevokeRecord(r);
        }
        for (CertVerifyConfig c : nullSafe(data.certVerifyConfigs)) {
            restoreMapper.insertCertVerifyConfig(c);
        }
        for (MixAlgorithm a : nullSafe(data.mixAlgorithms)) {
            restoreMapper.insertMixAlgorithm(a);
        }
        for (MixAlgorithmVersion v : nullSafe(data.mixVersions)) {
            restoreMapper.insertMixVersion(v);
        }
        for (ArrangementRun r : nullSafe(data.arrangementRuns)) {
            restoreMapper.insertArrangementRun(r);
        }
        for (Invigilator i : nullSafe(data.invigilators)) {
            restoreMapper.insertInvigilator(i);
        }
        for (InvigilatorAllowanceRate r : nullSafe(data.allowanceRates)) {
            restoreMapper.insertAllowanceRate(r);
        }
        for (InvigilationShift s : nullSafe(data.invigilationShifts)) {
            restoreMapper.insertInvigilationShift(s);
        }
        for (Appointment a : nullSafe(data.appointments)) {
            restoreMapper.insertAppointment(a);
        }
        for (AppointmentVersion v : nullSafe(data.appointmentVersions)) {
            restoreMapper.insertAppointmentVersion(v);
        }
        for (AppointmentReplacement r : nullSafe(data.appointmentReplacements)) {
            restoreMapper.insertAppointmentReplacement(r);
        }
        for (Settlement s : nullSafe(data.settlements)) {
            restoreMapper.insertSettlement(s);
        }
        for (SettlementItem i : nullSafe(data.settlementItems)) {
            restoreMapper.insertSettlementItem(i);
        }
        for (SettlementAdjustment a : nullSafe(data.settlementAdjustments)) {
            restoreMapper.insertSettlementAdjustment(a);
        }
        for (VaultPerson p : nullSafe(data.vaultPersons)) {
            restoreMapper.insertVaultPerson(p);
        }
        for (VaultBox b : nullSafe(data.vaultBoxes)) {
            restoreMapper.insertVaultBox(b);
        }
        for (VaultConfig c : nullSafe(data.vaultConfigs)) {
            restoreMapper.insertVaultConfig(c);
        }
        for (VaultAccess a : nullSafe(data.vaultAccesses)) {
            restoreMapper.insertVaultAccess(a);
        }
        for (VaultAccessVersion v : nullSafe(data.vaultAccessVersions)) {
            restoreMapper.insertVaultAccessVersion(v);
        }
        for (VaultBoxMove m : nullSafe(data.vaultBoxMoves)) {
            restoreMapper.insertVaultBoxMove(m);
        }
        for (VaultAccessNote n : nullSafe(data.vaultAccessNotes)) {
            restoreMapper.insertVaultAccessNote(n);
        }
        for (VaultAlert a : nullSafe(data.vaultAlerts)) {
            restoreMapper.insertVaultAlert(a);
        }

        // 显式插 id 不推进 IDENTITY，统一把序列重置到 MAX(id)+1，避免回退后新增撞主键
        restoreMapper.restartSite(restoreMapper.maxSiteId() + 1);
        restoreMapper.restartRoom(restoreMapper.maxRoomId() + 1);
        restoreMapper.restartBatch(restoreMapper.maxBatchId() + 1);
        restoreMapper.restartRegistration(restoreMapper.maxRegistrationId() + 1);
        restoreMapper.restartCorrection(restoreMapper.maxCorrectionId() + 1);
        restoreMapper.restartTicket(restoreMapper.maxTicketId() + 1);
        restoreMapper.restartStageResult(restoreMapper.maxStageResultId() + 1);
        restoreMapper.restartConclusion(restoreMapper.maxConclusionId() + 1);
        restoreMapper.restartGradeRule(restoreMapper.maxGradeRuleId() + 1);
        restoreMapper.restartGradeRuleVersion(restoreMapper.maxGradeRuleVersionId() + 1);
        restoreMapper.restartScore(restoreMapper.maxScoreId() + 1);
        restoreMapper.restartReview(restoreMapper.maxReviewId() + 1);
        restoreMapper.restartChangeLog(restoreMapper.maxChangeLogId() + 1);
        restoreMapper.restartNotification(restoreMapper.maxNotificationId() + 1);
        restoreMapper.restartExamineeCheckin(restoreMapper.maxExamineeCheckinId() + 1);
        restoreMapper.restartViolationRecord(restoreMapper.maxViolationRecordId() + 1);
        restoreMapper.restartAbsenceMark(restoreMapper.maxAbsenceMarkId() + 1);
        restoreMapper.restartAbsenceMarkVersion(restoreMapper.maxAbsenceMarkVersionId() + 1);
        restoreMapper.restartScoreSnapshot(restoreMapper.maxScoreSnapshotId() + 1);
        restoreMapper.restartAbsenceStat(restoreMapper.maxAbsenceStatId() + 1);
        restoreMapper.restartCertRule(restoreMapper.maxCertRuleId() + 1);
        restoreMapper.restartCertRuleVersion(restoreMapper.maxCertRuleVersionId() + 1);
        restoreMapper.restartCertSeq(restoreMapper.maxCertSeqId() + 1);
        restoreMapper.restartCertificate(restoreMapper.maxCertificateId() + 1);
        restoreMapper.restartCertRevokeRecord(restoreMapper.maxCertRevokeRecordId() + 1);
        restoreMapper.restartCertVerifyConfig(restoreMapper.maxCertVerifyConfigId() + 1);
        restoreMapper.restartMixAlgorithm(restoreMapper.maxMixAlgorithmId() + 1);
        restoreMapper.restartMixVersion(restoreMapper.maxMixVersionId() + 1);
        restoreMapper.restartArrangementRun(restoreMapper.maxArrangementRunId() + 1);
        restoreMapper.restartInvigilator(restoreMapper.maxInvigilatorId() + 1);
        restoreMapper.restartAllowanceRate(restoreMapper.maxAllowanceRateId() + 1);
        restoreMapper.restartInvigilationShift(restoreMapper.maxInvigilationShiftId() + 1);
        restoreMapper.restartAppointment(restoreMapper.maxAppointmentId() + 1);
        restoreMapper.restartAppointmentVersion(restoreMapper.maxAppointmentVersionId() + 1);
        restoreMapper.restartAppointmentReplacement(restoreMapper.maxAppointmentReplacementId() + 1);
        restoreMapper.restartSettlement(restoreMapper.maxSettlementId() + 1);
        restoreMapper.restartSettlementItem(restoreMapper.maxSettlementItemId() + 1);
        restoreMapper.restartSettlementAdjustment(restoreMapper.maxSettlementAdjustmentId() + 1);
        restoreMapper.restartVaultPerson(restoreMapper.maxVaultPersonId() + 1);
        restoreMapper.restartVaultBox(restoreMapper.maxVaultBoxId() + 1);
        restoreMapper.restartVaultConfig(restoreMapper.maxVaultConfigId() + 1);
        restoreMapper.restartVaultAccess(restoreMapper.maxVaultAccessId() + 1);
        restoreMapper.restartVaultAccessVersion(restoreMapper.maxVaultAccessVersionId() + 1);
        restoreMapper.restartVaultBoxMove(restoreMapper.maxVaultBoxMoveId() + 1);
        restoreMapper.restartVaultAccessNote(restoreMapper.maxVaultAccessNoteId() + 1);
        restoreMapper.restartVaultAlert(restoreMapper.maxVaultAlertId() + 1);
    }

    private <T> List<T> nullSafe(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private WorkspaceVersion requireVersion(String versionNo) {
        if (versionNo == null || versionNo.trim().isEmpty()) {
            throw new BusinessException("VERSION_NO_REQUIRED", "版本号不能为空");
        }
        WorkspaceVersion version = versionMapper.selectOne(
                new QueryWrapper<WorkspaceVersion>().eq("version_no", versionNo.trim()));
        if (version == null) {
            throw new BusinessException("VERSION_NOT_FOUND", "工作区版本不存在：" + versionNo.trim());
        }
        return version;
    }

    private void markCurrent(WorkspaceVersion target, String operator, String action, String detail) {
        List<WorkspaceVersion> all = versionMapper.selectList(null);
        for (WorkspaceVersion v : all) {
            boolean should = v.getId().equals(target.getId());
            if (Boolean.TRUE.equals(v.getIsCurrent()) != should) {
                v.setIsCurrent(should);
                versionMapper.updateById(v);
            }
        }
        if (target.getIsCurrent() == null || !target.getIsCurrent()) {
            target.setIsCurrent(true);
            versionMapper.updateById(target);
        }
        recordEvent(target, action, detail, operator);
    }

    private void recordEvent(WorkspaceVersion version, String action, String detail, String operator) {
        WorkspaceVersionEvent event = new WorkspaceVersionEvent();
        event.setVersionId(version.getId());
        event.setVersionNo(version.getVersionNo());
        event.setAction(action);
        event.setDetail(detail);
        event.setOperator(operator);
        event.setEventAt(LocalDateTime.now());
        eventMapper.insert(event);
    }

    /** V + 递增序号；序号按现有版本最大值 +1，保证版本号不撞。 */
    private String nextVersionNo() {
        int max = 0;
        for (WorkspaceVersion v : versionMapper.selectList(null)) {
            String no = v.getVersionNo();
            if (no != null && no.startsWith("V")) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(1)));
                } catch (NumberFormatException ignored) {
                    // 非本规则版本号忽略
                }
            }
        }
        return "V" + (max + 1);
    }

    /** 快照反序列化结构。 */
    public static class SnapshotData {
        public String capturedAt;
        public List<ExamSite> sites;
        public List<ExamRoom> rooms;
        public List<ExamBatch> batches;
        public List<Registration> registrations;
        public List<RegistrationCorrection> corrections;
        public List<AdmissionTicket> tickets;
        public List<StageResult> stageResults;
        public List<FinalConclusion> conclusions;
        public List<GradeRule> gradeRules;
        public List<GradeRuleVersion> gradeRuleVersions;
        public List<ExamScore> scores;
        public List<ScoreReview> reviews;
        public List<ScoreChangeLog> changeLogs;
        public List<ScoreNotification> notifications;
        public List<MixAlgorithm> mixAlgorithms;
        public List<MixAlgorithmVersion> mixVersions;
        public List<ArrangementRun> arrangementRuns;
        public List<Invigilator> invigilators;
        public List<InvigilatorAllowanceRate> allowanceRates;
        public List<InvigilationShift> invigilationShifts;
        public List<Appointment> appointments;
        public List<AppointmentVersion> appointmentVersions;
        public List<AppointmentReplacement> appointmentReplacements;
        public List<Settlement> settlements;
        public List<SettlementItem> settlementItems;
        public List<SettlementAdjustment> settlementAdjustments;
        public List<VaultPerson> vaultPersons;
        public List<VaultBox> vaultBoxes;
        public List<VaultConfig> vaultConfigs;
        public List<VaultAccess> vaultAccesses;
        public List<VaultAccessVersion> vaultAccessVersions;
        public List<VaultBoxMove> vaultBoxMoves;
        public List<VaultAccessNote> vaultAccessNotes;
        public List<VaultAlert> vaultAlerts;
        public List<ExamineeCheckin> examineeCheckins;
        public List<ViolationRecord> violationRecords;
        public List<AbsenceMark> absenceMarks;
        public List<AbsenceMarkVersion> absenceMarkVersions;
        public List<ScoreSnapshot> scoreSnapshots;
        public List<AbsenceStat> absenceStats;
        public List<CertNumberRule> certRules;
        public List<CertNumberRuleVersion> certRuleVersions;
        public List<CertSeq> certSeqs;
        public List<Certificate> certificates;
        public List<CertificateRevokeRecord> certRevokeRecords;
        public List<CertVerifyConfig> certVerifyConfigs;
    }
}
