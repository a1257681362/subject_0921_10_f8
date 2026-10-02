package com.evops.exam.vo;

import lombok.Data;

/**
 * 同校相邻一对座位（巡考抽查明细）：同一考场内座位号连续且两人同校。
 */
@Data
public class AdjacentPairView {
    private String roomNo;
    private Integer seatA;
    private Integer seatB;
    private String school;
    private String regNoA;
    private String nameA;
    private String regNoB;
    private String nameB;
}
