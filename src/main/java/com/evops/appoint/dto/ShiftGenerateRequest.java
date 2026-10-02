package com.evops.appoint.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 监考排班并生成聘任：按批次把监考人员排进具体考场角色场次，一条排班生成一条聘任。
 * 两步动作：先 preview 看影响范围，确认后再 execute。
 */
@Data
public class ShiftGenerateRequest {

    @NotNull(message = "批次 id 不能为空")
    private Long batchId;

    @NotEmpty(message = "排班明细不能为空")
    @Valid
    private List<ShiftAssignItem> assignments;

    /** 场次时间，为空时取批次时间。 */
    private LocalDateTime shiftStart;
    private LocalDateTime shiftEnd;

    @Data
    public static class ShiftAssignItem {
        @NotNull(message = "考场 id 不能为空")
        private Long roomId;
        /** CHIEF / INVIGILATOR / PATROL；缺省监考。 */
        private String roleCode;
        /** 监考人员编码。 */
        private String invigilatorCode;
    }
}
