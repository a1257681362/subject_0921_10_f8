package com.evops.mixalgo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.mixalgo.common.MixConstants;
import com.evops.mixalgo.dto.MixAlgorithmRequest;
import com.evops.mixalgo.dto.MixAlgorithmVersionRequest;
import com.evops.mixalgo.dto.MixParams;
import com.evops.mixalgo.entity.MixAlgorithm;
import com.evops.mixalgo.entity.MixAlgorithmVersion;
import com.evops.mixalgo.mapper.MixAlgorithmMapper;
import com.evops.mixalgo.mapper.MixAlgorithmVersionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 混编算法模块：
 * - 算法按项目建档，算法下挂多个版本；
 * - 版本状态 DRAFT → ACTIVE → DISABLED：发布即冻结参数 JSON，自动停用同算法旧生效版；
 * - 编排只认项目当前 ACTIVE 版本；停用不删版本，历史编排保留版本号。
 */
@Service
public class MixAlgorithmService {

    private final MixAlgorithmMapper algorithmMapper;
    private final MixAlgorithmVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public MixAlgorithmService(MixAlgorithmMapper algorithmMapper,
                               MixAlgorithmVersionMapper versionMapper,
                               ObjectMapper objectMapper) {
        this.algorithmMapper = algorithmMapper;
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
    }

    // ---------- 算法模块 ----------

    @Transactional
    public MixAlgorithm createAlgorithm(MixAlgorithmRequest request) {
        MixAlgorithm algorithm = new MixAlgorithm();
        algorithm.setCode(request.getCode().trim());
        algorithm.setName(request.getName().trim());
        algorithm.setProjectCode(normalizeProject(request.getProjectCode()));
        algorithm.setRemark(request.getRemark());
        try {
            algorithmMapper.insert(algorithm);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_ALGORITHM_CODE", "算法编码已存在：" + algorithm.getCode());
        }
        return algorithm;
    }

    public List<MixAlgorithm> listAlgorithms(String projectCode) {
        QueryWrapper<MixAlgorithm> wrapper = new QueryWrapper<MixAlgorithm>()
                .orderByAsc("project_code", "code");
        if (projectCode != null && !projectCode.trim().isEmpty()) {
            wrapper.eq("project_code", projectCode.trim());
        }
        return algorithmMapper.selectList(wrapper);
    }

    public MixAlgorithm getAlgorithm(Long algorithmId) {
        MixAlgorithm algorithm = algorithmMapper.selectById(algorithmId);
        if (algorithm == null) {
            throw new BusinessException("ALGORITHM_NOT_FOUND", "混编算法不存在：id=" + algorithmId);
        }
        return algorithm;
    }

    // ---------- 算法版本 ----------

    /** 新建草稿版本：校验并补全参数后以 JSON 冻结落库（草稿也不允许半截配置）。 */
    @Transactional
    public MixAlgorithmVersion createDraftVersion(Long algorithmId, MixAlgorithmVersionRequest request) {
        MixAlgorithm algorithm = getAlgorithm(algorithmId);
        MixParams normalized = validateParams(request.getParams());

        MixAlgorithmVersion version = new MixAlgorithmVersion();
        version.setAlgorithmId(algorithm.getId());
        version.setVersionNo(nextVersionNo(algorithm.getId()));
        version.setStatus(MixConstants.VERSION_DRAFT);
        version.setParams(writeParams(normalized));
        version.setRemark(request.getRemark());
        version.setOperator(OperatorHolder.current());
        versionMapper.insert(version);
        return version;
    }

    /** 发布：DRAFT → ACTIVE；同一算法的旧 ACTIVE 自动置 DISABLED。 */
    @Transactional
    public MixAlgorithmVersion publishVersion(Long versionId) {
        MixAlgorithmVersion version = requireVersion(versionId);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        if (MixConstants.VERSION_ACTIVE.equals(version.getStatus())) {
            throw new BusinessException("VERSION_ALREADY_ACTIVE",
                    "版本已是生效状态，无需重复发布：" + version.getVersionNo());
        }
        if (MixConstants.VERSION_DISABLED.equals(version.getStatus())) {
            throw new BusinessException("VERSION_DISABLED", "停用版本不可重新发布，请新建版本：" + version.getVersionNo());
        }

        List<MixAlgorithmVersion> actives = versionMapper.selectList(new QueryWrapper<MixAlgorithmVersion>()
                .eq("algorithm_id", version.getAlgorithmId())
                .eq("status", MixConstants.VERSION_ACTIVE));
        for (MixAlgorithmVersion active : actives) {
            active.setStatus(MixConstants.VERSION_DISABLED);
            active.setDisabledBy(operator);
            active.setDisabledAt(now);
            versionMapper.updateById(active);
        }

        version.setStatus(MixConstants.VERSION_ACTIVE);
        version.setPublishedBy(operator);
        version.setPublishedAt(now);
        versionMapper.updateById(version);
        return version;
    }

    /** 人工停用生效版。 */
    @Transactional
    public MixAlgorithmVersion disableVersion(Long versionId) {
        MixAlgorithmVersion version = requireVersion(versionId);
        if (!MixConstants.VERSION_ACTIVE.equals(version.getStatus())) {
            throw new BusinessException("VERSION_NOT_ACTIVE", "仅生效版本可停用：" + version.getVersionNo());
        }
        version.setStatus(MixConstants.VERSION_DISABLED);
        version.setDisabledBy(OperatorHolder.current());
        version.setDisabledAt(LocalDateTime.now());
        versionMapper.updateById(version);
        return version;
    }

    @Transactional(readOnly = true)
    public List<MixAlgorithmVersion> listVersions(Long algorithmId) {
        getAlgorithm(algorithmId);
        return versionMapper.selectList(new QueryWrapper<MixAlgorithmVersion>()
                .eq("algorithm_id", algorithmId)
                .orderByDesc("id"));
    }

    public MixAlgorithmVersion getVersion(Long versionId) {
        return requireVersion(versionId);
    }

    /**
     * 取项目当前生效算法版本：项目下所有算法中仅允许一条 ACTIVE；
     * 多条 ACTIVE 无法唯一确定时抛错；没有生效版返回 null（编排按内置默认口径兜底）。
     */
    @Transactional(readOnly = true)
    public MixAlgorithmVersion findActiveVersion(String projectCode) {
        String project = normalizeProject(projectCode);
        List<MixAlgorithm> algorithms = algorithmMapper.selectList(
                new QueryWrapper<MixAlgorithm>().eq("project_code", project));
        if (algorithms.isEmpty()) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (MixAlgorithm a : algorithms) {
            ids.add(a.getId());
        }
        List<MixAlgorithmVersion> actives = versionMapper.selectList(new QueryWrapper<MixAlgorithmVersion>()
                .in("algorithm_id", ids)
                .eq("status", MixConstants.VERSION_ACTIVE)
                .orderByDesc("id"));
        if (actives.isEmpty()) {
            return null;
        }
        if (actives.size() > 1) {
            throw new BusinessException("MULTIPLE_ACTIVE_MIX_VERSION",
                    "项目 " + project + " 存在多个生效混编算法版本，请先停用多余版本");
        }
        return actives.get(0);
    }

    // ---------- 参数校验 / 序列化 ----------

    /** 校验并补全默认值，返回用于冻结的规范化参数。 */
    public MixParams validateParams(MixParams raw) {
        MixParams p = raw == null ? new MixParams() : raw;
        MixParams norm = new MixParams();

        String strategy = p.getStrategy() == null || p.getStrategy().trim().isEmpty()
                ? MixConstants.STRATEGY_SCHOOL_SPREAD : p.getStrategy().trim().toUpperCase();
        if (!MixConstants.STRATEGY_SCHOOL_SPREAD.equals(strategy)) {
            throw new BusinessException("PARAM_STRATEGY_INVALID",
                    "不支持的混编策略：" + strategy + "，当前仅支持 SCHOOL_SPREAD");
        }
        norm.setStrategy(strategy);

        int gap = p.getMinSchoolGap() == null ? MixConstants.DEFAULT_MIN_SCHOOL_GAP : p.getMinSchoolGap();
        if (gap < 1) {
            throw new BusinessException("PARAM_GAP_INVALID", "同校最小座位间隔必须 ≥ 1");
        }
        norm.setMinSchoolGap(gap);

        norm.setSpreadRooms(p.getSpreadRooms() == null ? MixConstants.DEFAULT_SPREAD_ROOMS : p.getSpreadRooms());

        if (p.getMaxSameSchoolPerRoom() != null && p.getMaxSameSchoolPerRoom() < 1) {
            throw new BusinessException("PARAM_MAX_PER_ROOM_INVALID", "单考场同校人数上限必须 ≥ 1");
        }
        norm.setMaxSameSchoolPerRoom(p.getMaxSameSchoolPerRoom());

        int tries = p.getTries() == null ? MixConstants.DEFAULT_TRIES : p.getTries();
        if (tries < MixConstants.MIN_TRIES || tries > MixConstants.MAX_TRIES) {
            throw new BusinessException("PARAM_TRIES_INVALID",
                    "试排次数须在 [" + MixConstants.MIN_TRIES + ", " + MixConstants.MAX_TRIES + "] 之间");
        }
        norm.setTries(tries);
        return norm;
    }

    /** 内置默认参数（项目无生效算法版本时的兜底口径）。 */
    public MixParams defaultParams() {
        MixParams p = new MixParams();
        p.setStrategy(MixConstants.STRATEGY_SCHOOL_SPREAD);
        p.setMinSchoolGap(MixConstants.DEFAULT_MIN_SCHOOL_GAP);
        p.setSpreadRooms(MixConstants.DEFAULT_SPREAD_ROOMS);
        p.setMaxSameSchoolPerRoom(null);
        p.setTries(MixConstants.DEFAULT_TRIES);
        return p;
    }

    public String writeParams(MixParams params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            throw new BusinessException("PARAMS_SERIALIZE_FAILED", "算法参数序列化失败：" + e.getMessage());
        }
    }

    public MixParams readParams(String json) {
        try {
            MixParams params = objectMapper.readValue(json, new TypeReference<MixParams>() {
            });
            if (params == null) {
                throw new BusinessException("PARAMS_BROKEN", "算法版本参数为空或已损坏");
            }
            return params;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("PARAMS_BROKEN", "算法版本参数无法解析：" + e.getMessage());
        }
    }

    // ---------- 内部 ----------

    private MixAlgorithmVersion requireVersion(Long versionId) {
        if (versionId == null) {
            throw new BusinessException("VERSION_ID_REQUIRED", "算法版本 id 不能为空");
        }
        MixAlgorithmVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("MIX_VERSION_NOT_FOUND", "混编算法版本不存在：id=" + versionId);
        }
        return version;
    }

    /** 版本号：算法 code + A + 递增序号（如 MIX-DEFAULT-A1）。 */
    private String nextVersionNo(Long algorithmId) {
        MixAlgorithm algorithm = getAlgorithm(algorithmId);
        int max = 0;
        String prefix = algorithm.getCode() + "-A";
        for (MixAlgorithmVersion v : versionMapper.selectList(
                new QueryWrapper<MixAlgorithmVersion>().eq("algorithm_id", algorithmId))) {
            String no = v.getVersionNo();
            if (no != null && no.startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 非本算法序号忽略
                }
            }
        }
        return prefix + (max + 1);
    }

    private String normalizeProject(String projectCode) {
        if (projectCode == null || projectCode.trim().isEmpty()) {
            return MixConstants.PROJECT_DEFAULT;
        }
        return projectCode.trim().toUpperCase();
    }
}
