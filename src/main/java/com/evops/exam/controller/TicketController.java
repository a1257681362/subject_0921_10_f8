package com.evops.exam.controller;

import com.evops.common.ApiResponse;
import com.evops.exam.dto.TicketActionRequest;
import com.evops.exam.service.TicketService;
import com.evops.exam.vo.ActionPreview;
import com.evops.exam.vo.ActionResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    // ---------- 打证 ----------

    @PostMapping("/print/preview")
    public ApiResponse<ActionPreview> previewPrint(@RequestBody TicketActionRequest request) {
        return ApiResponse.ok(ticketService.previewPrint(request));
    }

    @PostMapping("/print")
    public ApiResponse<ActionResult> print(@RequestBody TicketActionRequest request) {
        return ApiResponse.ok(ticketService.print(request));
    }

    // ---------- 发放 ----------

    @PostMapping("/issue/preview")
    public ApiResponse<ActionPreview> previewIssue(@RequestBody TicketActionRequest request) {
        return ApiResponse.ok(ticketService.previewIssue(request));
    }

    @PostMapping("/issue")
    public ApiResponse<ActionResult> issue(@RequestBody TicketActionRequest request) {
        return ApiResponse.ok(ticketService.issue(request));
    }
}
