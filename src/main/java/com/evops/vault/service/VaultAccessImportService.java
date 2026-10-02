package com.evops.vault.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.vault.common.VaultConstants;
import com.evops.vault.dto.AccessImportRequest;
import com.evops.vault.entity.VaultAccess;
import com.evops.vault.entity.VaultPerson;
import com.evops.vault.mapper.VaultAccessMapper;
import com.evops.vault.vo.ImportPreview;
import com.evops.vault.vo.ImportPreviewItem;
import com.evops.vault.vo.ImportResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 纸面存量导入：把纸面登记簿的历史进出整批搬进电子台账。
 * - 两步：先 preview 看影响范围，确认后再 execute；
 * - 结构性问题（人员/卷箱未建档、方向或时间非法、未来时间、文件内重复）整批拒收、不落半条；
 * - 与库内既有登记同键（人+方向+钟点）的逐条幂等跳过；
 * - 历史既成事实：落库即生效（CONFIRMED，版本 IMPORT），卷箱位置按纸面原样记录、不做在室校验；
 * - 异常时段同样自动标记并告警保卫。
 */
@Service
public class VaultAccessImportService {

    private final VaultAccessMapper accessMapper;
    private final VaultAccessService accessService;
    private final VaultMasterDataService masterData;

    public VaultAccessImportService(VaultAccessMapper accessMapper,
                                    VaultAccessService accessService,
                                    VaultMasterDataService masterData) {
        this.accessMapper = accessMapper;
        this.accessService = accessService;
        this.masterData = masterData;
    }

    // ==================== 第一步：预检 ====================

    @Transactional(readOnly = true)
    public ImportPreview preview(AccessImportRequest request) {
        List<ResolvedItem> plan = buildPlan(request);
        ImportPreview preview = new ImportPreview();
        preview.setAction("IMPORT_PAPER_ACCESS");
        preview.setTotalScanned(plan.size());
        int actionable = 0;
        List<ImportPreviewItem> items = new ArrayList<>();
        for (ResolvedItem r : plan) {
            if (r.actionable) {
                actionable++;
            }
            items.add(r.view);
        }
        preview.setActionableCount(actionable);
        preview.setSkipCount(plan.size() - actionable);
        preview.setSummary(String.format("纸面存量共 %d 条：可导入 %d 条，跳过 %d 条（重复）",
                plan.size(), actionable, plan.size() - actionable));
        preview.setItems(items);
        return preview;
    }

    // ==================== 第二步：执行 ====================

    @Transactional
    public ImportResult execute(AccessImportRequest request) {
        List<ResolvedItem> plan = buildPlan(request);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        List<String> accessNos = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int abnormalCount = 0;
        for (ResolvedItem r : plan) {
            if (!r.actionable) {
                skipped.add(r.view.getKey() + "（" + r.view.getNote() + "）");
                continue;
            }
            VaultAccess access = accessService.newAccess(r.person, r.direction, r.occurredAt,
                    VaultConstants.SOURCE_PAPER, r.boxNos, r.remark, operator, now);
            // 历史既成事实：落库即生效，版本记 IMPORT
            access.setStatus(VaultConstants.ACCESS_CONFIRMED);
            access.setReviewComment("纸面存量导入，按纸面原样生效");
            access.setReviewedAt(now);
            accessMapper.insert(access);
            accessService.appendVersion(access, VaultConstants.ACCESS_CONFIRMED, VaultConstants.CHANGE_IMPORT,
                    "纸面存量导入（三个月纸面登记簿电子化）", null, null, operator, now);
            accessService.insertBoxMoves(access, r.boxNos);
            accessService.raiseAlertIfAbnormal(access);
            if (Boolean.TRUE.equals(access.getAbnormal())) {
                abnormalCount++;
            }
            accessNos.add(access.getAccessNo());
        }

        ImportResult result = new ImportResult();
        result.setAction("IMPORT_PAPER_ACCESS");
        result.setImportedCount(accessNos.size());
        result.setSkipCount(skipped.size());
        result.setAbnormalCount(abnormalCount);
        result.setOperator(operator);
        result.setExecutedAt(now.toString());
        result.setAccessNos(accessNos);
        result.setSkipped(skipped);
        return result;
    }

    // ==================== 解析（预检与执行共用；结构性问题整批拒收） ====================

    private List<ResolvedItem> buildPlan(AccessImportRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Set<String> seenInFile = new HashSet<>();
        List<ResolvedItem> plan = new ArrayList<>();

        int index = 0;
        for (AccessImportRequest.Item item : request.getItems()) {
            index++;
            String label = "第 " + index + " 条";

            // 人员：须已建档（历史口径，停用人员也允许导入其历史记录）
            if (item.getPersonCode() == null || item.getPersonCode().trim().isEmpty()) {
                throw new BusinessException("IMPORT_PERSON_REQUIRED", label + "：人员编码不能为空，整批拒收");
            }
            VaultPerson person = masterData.findPersonByCode(item.getPersonCode().trim());
            if (person == null) {
                throw new BusinessException("IMPORT_PERSON_NOT_FOUND",
                        label + "：人员编码未建档 " + item.getPersonCode().trim() + "，整批拒收（请先建档）");
            }

            // 方向 / 时间：非法即结构性问题，整批拒收
            String direction = accessService.normalizeDirection(item.getDirection());
            if (item.getOccurredAt() == null || item.getOccurredAt().trim().isEmpty()) {
                throw new BusinessException("IMPORT_OCCURRED_AT_REQUIRED",
                        label + "：纸面记录时间不能为空，整批拒收");
            }
            LocalDateTime occurredAt = accessService.parseOccurredAt(item.getOccurredAt(), null);
            accessService.assertNotFuture(occurredAt, now);

            // 卷箱：须已建档（历史口径不校验在室位置）
            List<String> boxNos = accessService.normalizeBoxes(item.getBoxes());
            for (String boxNo : boxNos) {
                if (masterData.findBoxByNo(boxNo) == null) {
                    throw new BusinessException("IMPORT_BOX_NOT_FOUND",
                            label + "：卷箱未建档 " + boxNo + "，整批拒收（请先建档）");
                }
            }

            ResolvedItem r = new ResolvedItem();
            r.person = person;
            r.direction = direction;
            r.occurredAt = occurredAt;
            r.boxNos = boxNos;
            r.remark = item.getRemark();

            ImportPreviewItem view = new ImportPreviewItem();
            view.setKey(label + " " + person.getCode() + " " + direction + " " + occurredAt);
            view.setPersonCode(person.getCode());
            view.setPersonName(person.getName());
            view.setDirection(direction);
            view.setOccurredAt(occurredAt.toString());
            view.setBoxCount(boxNos.size());
            view.setAbnormal(masterData.isOffHours(occurredAt));
            r.view = view;

            // 重复：文件内同键，或库内已有同人同向同钟点 → 逐条跳过（幂等）
            String naturalKey = person.getCode() + "|" + direction + "|" + occurredAt;
            if (!seenInFile.add(naturalKey)) {
                r.actionable = false;
                view.setNote("文件内与前面条目重复（同人同向同钟点），跳过");
            } else {
                Long exist = accessMapper.selectCount(new QueryWrapper<VaultAccess>()
                        .eq("person_code", person.getCode())
                        .eq("direction", direction)
                        .eq("occurred_at", occurredAt));
                if (exist != null && exist > 0) {
                    r.actionable = false;
                    view.setNote("库内已有同键登记（同人同向同钟点），跳过");
                } else {
                    r.actionable = true;
                    view.setNote(Boolean.TRUE.equals(view.getAbnormal())
                            ? "将导入并生效；落在异常时段，生效后自动告警保卫"
                            : "将导入并生效");
                }
            }
            plan.add(r);
        }
        return plan;
    }

    /** 导入解析中间结构。 */
    private static class ResolvedItem {
        private VaultPerson person;
        private String direction;
        private LocalDateTime occurredAt;
        private List<String> boxNos;
        private String remark;
        private boolean actionable;
        private ImportPreviewItem view;
    }
}
