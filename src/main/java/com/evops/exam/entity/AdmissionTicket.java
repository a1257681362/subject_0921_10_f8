package com.evops.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_admission_ticket")
public class AdmissionTicket extends BaseEntity {
    private String ticketNo;
    private Long registrationId;
    private Long batchId;
    private Long siteId;
    private Long roomId;
    private Integer seatNo;
    /** PENDING 待打印 / PRINTED 已打印 / ISSUED 已发放 */
    private String status;
    private String printedBy;
    private LocalDateTime printedAt;
    private String issuedBy;
    private LocalDateTime issuedAt;
}
