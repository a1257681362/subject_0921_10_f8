package com.evops.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.common.ExamConstants;
import com.evops.exam.dto.RegistrationImportRequest;
import com.evops.exam.dto.RegistrationRequest;
import com.evops.exam.entity.Registration;
import com.evops.exam.entity.RegistrationCorrection;
import com.evops.exam.mapper.RegistrationCorrectionMapper;
import com.evops.exam.mapper.RegistrationMapper;
import com.evops.exam.vo.ActionPreview;
import com.evops.exam.vo.ActionResult;
import com.evops.exam.vo.AffectedItem;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报名名册：挂进批次；导入是批量动作，先 preview 影响范围再 execute；勘误只追加不抹旧值。
 */
@Service
public class RegistrationService {

    /** 允许勘误的字段 -> 中文名 */
    private static final Map<String, String> CORRECTABLE_FIELDS = new LinkedHashMap<>();

    static {
        CORRECTABLE_FIELDS.put("NAME", "姓名");
        CORRECTABLE_FIELDS.put("ID_NO", "证件号");
        CORRECTABLE_FIELDS.put("GENDER", "性别");
        CORRECTABLE_FIELDS.put("PHONE", "联系电话");
        CORRECTABLE_FIELDS.put("SUBJECT", "报考科目");
        CORRECTABLE_FIELDS.put("SCHOOL", "所属学校");
    }

    private final RegistrationMapper registrationMapper;
    private final RegistrationCorrectionMapper correctionMapper;
    private final ExamBatchService batchService;
    private final StageResultService stageResultService;

    public RegistrationService(RegistrationMapper registrationMapper,
                               RegistrationCorrectionMapper correctionMapper,
                               ExamBatchService batchService,
                               StageResultService stageResultService) {
        this.registrationMapper = registrationMapper;
        this.correctionMapper = correctionMapper;
        this.batchService = batchService;
        this.stageResultService = stageResultService;
    }

    // ---------- 批量导入：预检影响范围 ----------

    @Transactional(readOnly = true)
    public ActionPreview previewImport(RegistrationImportRequest request) {
        batchService.getBatch(request.getBatchId());
        Map<String, Registration> existing = loadExistingRegNos(request.getRegistrations());

        List<AffectedItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int actionable = 0;
        int skip = 0;
        for (RegistrationRequest r : request.getRegistrations()) {
            String regNo = r.getRegNo().trim();
            boolean dupInFile = !seen.add(regNo);
            boolean exists = existing.containsKey(regNo);
            boolean ok = !dupInFile && !exists;
            String note = dupInFile ? "名册文件内报名号重复，将拒收"
                    : exists ? "报名号已存在（可能在其他批次），将拒收"
                    : (r.getSchool() == null || r.getSchool().trim().isEmpty()
                            ? "新增报名（未填学校）"
                            : "新增报名（学校：" + r.getSchool().trim() + "）");
            if (ok) {
                actionable++;
            } else {
                skip++;
            }
            items.add(new AffectedItem(regNo, r.getName(),
                    exists ? ExamConstants.REG_REGISTERED : "-", ok, note));
        }

        ActionPreview preview = new ActionPreview();
        preview.setAction("IMPORT");
        preview.setTotalScanned(items.size());
        preview.setActionableCount(actionable);
        preview.setSkipCount(skip);
        preview.setSummary(String.format("本批名册 %d 人：可新增 %d 人，拒收 %d 人", items.size(), actionable, skip));
        preview.setItems(items);
        return preview;
    }

    // ---------- 批量导入：执行（任何撞号整体拒收） ----------

    @Transactional
    public ActionResult importRegistrations(RegistrationImportRequest request) {
        batchService.getBatch(request.getBatchId());
        String operator = OperatorHolder.current();

        // 执行前再校验一次：文件内重复或库内已存在，整体拒收，不落半条数据
        Set<String> seen = new HashSet<>();
        for (RegistrationRequest r : request.getRegistrations()) {
            String regNo = r.getRegNo().trim();
            if (!seen.add(regNo)) {
                throw new BusinessException("REG_DUP_IN_FILE", "名册文件内报名号重复，整批拒收：" + regNo);
            }
            Registration exists = registrationMapper.selectOne(
                    new QueryWrapper<Registration>().eq("reg_no", regNo));
            if (exists != null) {
                throw new BusinessException("REG_DUP_EXISTING", "报名号已存在，整批拒收：" + regNo);
            }
        }

        List<String> affected = new ArrayList<>();
        for (RegistrationRequest r : request.getRegistrations()) {
            Registration reg = new Registration();
            reg.setRegNo(r.getRegNo().trim());
            reg.setName(r.getName().trim());
            reg.setIdType(r.getIdType() == null ? "ID_CARD" : r.getIdType());
            reg.setIdNo(r.getIdNo());
            reg.setGender(r.getGender());
            reg.setPhone(r.getPhone());
            reg.setBatchId(request.getBatchId());
            reg.setSubject(r.getSubject());
            reg.setSchool(trimToNull(r.getSchool()));
            reg.setStatus(ExamConstants.REG_REGISTERED);
            reg.setCorrectionCount(0);
            reg.setRemark(r.getRemark());
            try {
                registrationMapper.insert(reg);
            } catch (DuplicateKeyException e) {
                // 并发撞号：同样拒收，事务回滚
                throw new BusinessException("REG_DUP_EXISTING", "报名号冲突，整批拒收：" + reg.getRegNo());
            }
            affected.add(reg.getRegNo());
        }

        stageResultService.record(request.getBatchId(), ExamConstants.STAGE_IMPORT,
                affected.size(), "报名名册导入 " + affected.size() + " 人", operator);

        ActionResult result = new ActionResult();
        result.setAction("IMPORT");
        result.setAffectedCount(affected.size());
        result.setSkipCount(0);
        result.setOperator(operator);
        result.setExecutedAt(LocalDateTime.now().toString());
        result.setAffectedKeys(affected);
        result.setSkipped(new ArrayList<>());
        return result;
    }

    public List<Registration> listByBatch(Long batchId) {
        return registrationMapper.selectList(
                new QueryWrapper<Registration>().eq("batch_id", batchId).orderByAsc("reg_no"));
    }

    public Registration getRegistration(Long id) {
        Registration reg = registrationMapper.selectById(id);
        if (reg == null) {
            throw new BusinessException("REG_NOT_FOUND", "报名记录不存在：id=" + id);
        }
        return reg;
    }

    public void updateRegistration(Registration reg) {
        registrationMapper.updateById(reg);
    }

    // ---------- 勘误：不抹旧值，标更正痕迹 ----------

    @Transactional
    public RegistrationCorrection correct(Long registrationId, String fieldName, String newValue, String reason) {
        Registration reg = getRegistration(registrationId);
        String field = fieldName == null ? "" : fieldName.trim().toUpperCase();
        if (!CORRECTABLE_FIELDS.containsKey(field)) {
            throw new BusinessException("FIELD_NOT_CORRECTABLE",
                    "不允许勘误该字段，可勘误字段：" + CORRECTABLE_FIELDS.keySet());
        }
        if (newValue == null || newValue.trim().isEmpty()) {
            throw new BusinessException("NEW_VALUE_REQUIRED", "更正后的值不能为空");
        }
        String operator = OperatorHolder.current();

        String oldValue = readField(reg, field);
        String trimmedNew = newValue.trim();
        if (trimmedNew.equals(oldValue)) {
            throw new BusinessException("NO_CHANGE", "更正后的值与原值相同，无需勘误");
        }

        // 1) 旧值进勘误表（只追加）
        RegistrationCorrection correction = new RegistrationCorrection();
        correction.setRegistrationId(registrationId);
        correction.setFieldName(field);
        correction.setFieldLabel(CORRECTABLE_FIELDS.get(field));
        correction.setOldValue(oldValue);
        correction.setNewValue(trimmedNew);
        correction.setReason(reason);
        correction.setOperator(operator);
        correction.setCorrectedAt(LocalDateTime.now());
        correctionMapper.insert(correction);

        // 2) 名册主表更新为新值，并累加更正次数（更正痕迹）
        applyField(reg, field, trimmedNew);
        reg.setCorrectionCount((reg.getCorrectionCount() == null ? 0 : reg.getCorrectionCount()) + 1);
        registrationMapper.updateById(reg);
        return correction;
    }

    public List<RegistrationCorrection> listCorrections(Long registrationId) {
        return correctionMapper.selectList(new QueryWrapper<RegistrationCorrection>()
                .eq("registration_id", registrationId)
                .orderByAsc("corrected_at", "id"));
    }

    // ---------- 查询 ----------

    public List<Registration> findByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException("NAME_REQUIRED", "姓名查询条件不能为空");
        }
        // 姓名按包含匹配，重名全部返回
        return registrationMapper.selectList(
                new QueryWrapper<Registration>().like("name", name.trim()).orderByAsc("reg_no"));
    }

    public Registration findByRegNo(String regNo) {
        if (regNo == null || regNo.trim().isEmpty()) {
            throw new BusinessException("REG_NO_REQUIRED", "报名号不能为空");
        }
        return registrationMapper.selectOne(new QueryWrapper<Registration>().eq("reg_no", regNo.trim()));
    }

    /** 按报名号批量查（报名号全局唯一），供成绩录入等场景定位名册。 */
    public List<Registration> findByRegNos(List<String> regNos) {
        if (regNos == null || regNos.isEmpty()) {
            return new ArrayList<>();
        }
        return registrationMapper.selectList(new QueryWrapper<Registration>().in("reg_no", regNos));
    }

    public Map<Long, Registration> loadByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, Registration> map = new HashMap<>();
        for (Registration reg : registrationMapper.selectBatchIds(ids)) {
            map.put(reg.getId(), reg);
        }
        return map;
    }

    private Map<String, Registration> loadExistingRegNos(List<RegistrationRequest> requests) {
        List<String> regNos = new ArrayList<>();
        for (RegistrationRequest r : requests) {
            regNos.add(r.getRegNo().trim());
        }
        Map<String, Registration> map = new HashMap<>();
        if (regNos.isEmpty()) {
            return map;
        }
        // 报名号全局唯一（uk_registration_reg_no），跨批次撞号同样要在预检中提示拒收
        for (Registration reg : registrationMapper.selectList(
                new QueryWrapper<Registration>().in("reg_no", regNos))) {
            map.put(reg.getRegNo(), reg);
        }
        return map;
    }

    private String readField(Registration reg, String field) {
        switch (field) {
            case "NAME": return reg.getName();
            case "ID_NO": return reg.getIdNo();
            case "GENDER": return reg.getGender();
            case "PHONE": return reg.getPhone();
            case "SUBJECT": return reg.getSubject();
            case "SCHOOL": return reg.getSchool();
            default: return null;
        }
    }

    private void applyField(Registration reg, String field, String value) {
        switch (field) {
            case "NAME": reg.setName(value); break;
            case "ID_NO": reg.setIdNo(value); break;
            case "GENDER": reg.setGender(value); break;
            case "PHONE": reg.setPhone(value); break;
            case "SUBJECT": reg.setSubject(value); break;
            case "SCHOOL": reg.setSchool(value); break;
            default: break;
        }
    }

    public static Set<String> correctableFields() {
        return new LinkedHashMap<>(CORRECTABLE_FIELDS).keySet();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
