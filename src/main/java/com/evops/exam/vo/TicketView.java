package com.evops.exam.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 准考证查档视图：证号、考场、座位、报名信息一人一条。
 */
@Data
public class TicketView {
    private Long id;
    private String ticketNo;
    private String status;
    private Long batchId;
    private String batchCode;
    private String batchName;
    private Long registrationId;
    private String regNo;
    private String name;
    private String school;
    private String idType;
    private String idNo;
    private String gender;
    private String phone;
    private Long siteId;
    private String siteCode;
    private String siteName;
    private String siteAddress;
    private Long roomId;
    private String roomNo;
    private String building;
    private Integer floorNo;
    private String locationDesc;
    private Integer seatNo;
    private String printedBy;
    private LocalDateTime printedAt;
    private String issuedBy;
    private LocalDateTime issuedAt;
}
