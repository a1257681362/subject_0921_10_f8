package com.evops.exam.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class ArrangeRequest {
    @NotNull(message = "批次不能为空")
    private Long batchId;
    /** 可选：指定考场编排顺序（考场号），不填按考场号升序；仅允许本批次考点下启用考场 */
    private List<String> roomOrder;
    /** 可选：指定洗牌随机种子（用于复现座次）；不填由系统取种子 */
    private Long seed;
}
