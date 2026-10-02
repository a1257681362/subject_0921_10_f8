package com.evops.exam.controller;

import com.evops.common.ApiResponse;
import com.evops.exam.service.QueryService;
import com.evops.exam.vo.TicketView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 查档：按证号查、按姓名查。
 */
@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    /** 按证号精确查，返回证号、考场、座位一人一条完整信息。 */
    @GetMapping("/by-ticket-no")
    public ApiResponse<TicketView> byTicketNo(@RequestParam String ticketNo) {
        return ApiResponse.ok(queryService.getByTicketNo(ticketNo));
    }

    /** 按姓名查（先查名册再联准考证），重名全返回。 */
    @GetMapping("/by-name")
    public ApiResponse<List<TicketView>> byName(@RequestParam String name) {
        return ApiResponse.ok(queryService.findTicketsByName(name));
    }

    /** 按批次列出全部准考证。 */
    @GetMapping("/by-batch")
    public ApiResponse<List<TicketView>> byBatch(@RequestParam Long batchId) {
        return ApiResponse.ok(queryService.listByBatch(batchId));
    }
}
