package com.evops.exam.vo;

import lombok.Data;

/**
 * 座次快照中的一条（随编排记录落库，可复现、可导出座次表）。
 */
@Data
public class SeatingEntry {
    private String ticketNo;
    private String regNo;
    private String name;
    private String school;
    private Long roomId;
    private String roomNo;
    private Integer seatNo;
}
