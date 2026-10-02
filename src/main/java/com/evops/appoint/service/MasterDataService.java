package com.evops.appoint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.appoint.common.AppointConstants;
import com.evops.appoint.dto.AllowanceRateRequest;
import com.evops.appoint.dto.InvigilatorRequest;
import com.evops.appoint.entity.Invigilator;
import com.evops.appoint.entity.InvigilatorAllowanceRate;
import com.evops.appoint.mapper.InvigilatorAllowanceRateMapper;
import com.evops.appoint.mapper.InvigilatorMapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 聘任主数据：监考人员与津贴标准。
 * 标准按“项目 + 角色 + 生效区间”配置，角色不同标准不同；
 * 生成聘任时解析一次并把 id/code/金额冻结进聘任版本，之后调价不改历史口径。
 */
@Service
public class MasterDataService {

    private final InvigilatorMapper invigilatorMapper;
    private final InvigilatorAllowanceRateMapper rateMapper;

    public MasterDataService(InvigilatorMapper invigilatorMapper,
                             InvigilatorAllowanceRateMapper rateMapper) {
        this.invigilatorMapper = invigilatorMapper;
        this.rateMapper = rateMapper;
    }

    // ---------- 监考人员 ----------

    @Transactional
    public Invigilator createInvigilator(InvigilatorRequest request) {
        Invigilator inv = new Invigilator();
        inv.setCode(request.getCode().trim());
        inv.setName(request.getName().trim());
        inv.setRoleCode(normalizeRole(request.getRoleCode()));
        inv.setPhone(request.getPhone());
        inv.setStaffKind(request.getStaffKind() == null || request.getStaffKind().trim().isEmpty()
                ? AppointConstants.STAFF_PART_TIME : request.getStaffKind().trim().toUpperCase());
        inv.setStatus(AppointConstants.ENABLED);
        inv.setRemark(request.getRemark());
        try {
            invigilatorMapper.insert(inv);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_INVIGILATOR_CODE", "监考人员编码已存在：" + inv.getCode());
        }
        return inv;
    }

    @Transactional(readOnly = true)
    public List<Invigilator> listInvigilators(String status) {
        QueryWrapper<Invigilator> wrapper = new QueryWrapper<Invigilator>().orderByAsc("code");
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim());
        }
        return invigilatorMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public Invigilator getInvigilator(Long id) {
        Invigilator inv = invigilatorMapper.selectById(id);
        if (inv == null) {
            throw new BusinessException("INVIGILATOR_NOT_FOUND", "监考人员不存在：id=" + id);
        }
        return inv;
    }

    /** 按编码取启用中的监考人员。 */
    @Transactional(readOnly = true)
    public Invigilator requireEnabledByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessException("INVIGILATOR_CODE_REQUIRED", "监考人员编码不能为空");
        }
        Invigilator inv = invigilatorMapper.selectOne(
                new QueryWrapper<Invigilator>().eq("code", code.trim()));
        if (inv == null) {
            throw new BusinessException("INVIGILATOR_NOT_FOUND", "监考人员不存在：" + code.trim());
        }
        if (!AppointConstants.ENABLED.equals(inv.getStatus())) {
            throw new BusinessException("INVIGILATOR_DISABLED",
                    "监考人员已停用，不能排班 / 接任：" + inv.getCode() + " " + inv.getName());
        }
        return inv;
    }

    // ---------- 津贴标准 ----------

    @Transactional
    public InvigilatorAllowanceRate createRate(AllowanceRateRequest request) {
        LocalDate from = request.getEffectiveFrom();
        LocalDate to = request.getEffectiveTo();
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException("RATE_RANGE_INVALID", "生效起必须不晚于生效止");
        }
        InvigilatorAllowanceRate rate = new InvigilatorAllowanceRate();
        rate.setCode(request.getCode().trim());
        rate.setName(request.getName().trim());
        rate.setProjectCode(normalizeProject(request.getProjectCode()));
        rate.setRoleCode(normalizeRole(request.getRoleCode()));
        rate.setAmount(request.getAmount());
        rate.setEffectiveFrom(from);
        rate.setEffectiveTo(to);
        rate.setStatus(AppointConstants.ENABLED);
        rate.setRemark(request.getRemark());
        try {
            rateMapper.insert(rate);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_RATE_CODE", "津贴标准编码已存在：" + rate.getCode());
        }
        return rate;
    }

    @Transactional(readOnly = true)
    public List<InvigilatorAllowanceRate> listRates(String projectCode, String roleCode) {
        QueryWrapper<InvigilatorAllowanceRate> wrapper =
                new QueryWrapper<InvigilatorAllowanceRate>().orderByAsc("project_code", "role_code", "id");
        if (projectCode != null && !projectCode.trim().isEmpty()) {
            wrapper.eq("project_code", projectCode.trim().toUpperCase());
        }
        if (roleCode != null && !roleCode.trim().isEmpty()) {
            wrapper.eq("role_code", normalizeRole(roleCode));
        }
        return rateMapper.selectList(wrapper);
    }

    /**
     * 解析某项目某角色在指定日期适用的津贴标准并冻结：
     * 先匹配项目专用，再回落 DEFAULT 通用；仅取启用且区间覆盖当日者；多条命中视为口径冲突直接报错；
     * 一条都没有时抛 RATE_NOT_CONFIGURED。
     */
    public InvigilatorAllowanceRate resolveRate(String projectCode, String roleCode, LocalDate onDate) {
        InvigilatorAllowanceRate rate = findRate(projectCode, roleCode, onDate);
        if (rate == null) {
            throw new BusinessException("RATE_NOT_CONFIGURED",
                    "项目 " + normalizeProject(projectCode) + " 角色 " + normalizeRole(roleCode)
                            + " 在 " + (onDate == null ? LocalDate.now() : onDate)
                            + " 没有适用的启用津贴标准，请先建档");
        }
        return rate;
    }

    /**
     * 取标准但不抛“未配置”：无命中返回 null（供排班生成逐条跳过），仅多标准口径冲突才抛错。
     * 刻意不加 @Transactional：本方法在生成聘任的事务内被调用且异常会被调用方吞掉做逐条跳过，
     * 若开启事务代理，抛异常会把外层事务标记 rollback-only，导致整请求意外回滚。
     */
    public InvigilatorAllowanceRate findRate(String projectCode, String roleCode, LocalDate onDate) {
        String project = normalizeProject(projectCode);
        String role = normalizeRole(roleCode);
        LocalDate day = onDate == null ? LocalDate.now() : onDate;

        InvigilatorAllowanceRate exact = pickSingle(project, role, day);
        if (exact != null) {
            return exact;
        }
        if (!"DEFAULT".equals(project)) {
            return pickSingle("DEFAULT", role, day);
        }
        return null;
    }

    /** 在给定项目+角色下取唯一一条覆盖当日的启用标准；无命中返回 null；多条命中报歧义。 */
    private InvigilatorAllowanceRate pickSingle(String project, String role, LocalDate day) {
        List<InvigilatorAllowanceRate> all = rateMapper.selectList(new QueryWrapper<InvigilatorAllowanceRate>()
                .eq("project_code", project)
                .eq("role_code", role)
                .eq("status", AppointConstants.ENABLED));
        List<InvigilatorAllowanceRate> hit = new ArrayList<>();
        for (InvigilatorAllowanceRate r : all) {
            if (covers(r, day)) {
                hit.add(r);
            }
        }
        if (hit.isEmpty()) {
            return null;
        }
        if (hit.size() > 1) {
            throw new BusinessException("RATE_AMBIGUOUS",
                    "项目 " + project + " 角色 " + role + " 在 " + day + " 命中多条生效津贴标准，口径冲突，请核对生效区间");
        }
        return hit.get(0);
    }

    private boolean covers(InvigilatorAllowanceRate r, LocalDate day) {
        if (r.getEffectiveFrom() != null && day.isBefore(r.getEffectiveFrom())) {
            return false;
        }
        return r.getEffectiveTo() == null || !day.isAfter(r.getEffectiveTo());
    }

    // ---------- 内部 ----------

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

    private String normalizeProject(String projectCode) {
        return projectCode == null || projectCode.trim().isEmpty()
                ? "DEFAULT" : projectCode.trim().toUpperCase();
    }
}
