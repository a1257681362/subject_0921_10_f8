package com.evops.exam.dto;

import lombok.Data;

import java.util.List;

/**
 * 打证 / 发放批量动作请求：给 ticketNos 则按证号操作，否则按 batchId 整批处理。
 */
@Data
public class TicketActionRequest {
    private Long batchId;
    private List<String> ticketNos;
}
