package com.evops.vault.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 纸面存量导入：把纸面登记簿的历史进出记录整批搬进来。
 * 两步动作：先 preview 看影响范围，确认后再 execute；结构性问题整批拒收，重复记录逐条跳过。
 */
@Data
public class AccessImportRequest {

    @NotEmpty(message = "导入明细不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        /** 人员编码（须已建档）。 */
        private String personCode;
        /** IN 进 / OUT 出。 */
        private String direction;
        /** 纸面记录时间：yyyy-MM-dd HH:mm(:ss) 或 yyyy-MM-dd。 */
        private String occurredAt;
        /** 随本次进出挂钩的卷箱箱号（可空，须已建档）。 */
        private List<String> boxes;
        private String remark;
    }
}
