package com.evops.exam.controller;

import com.evops.common.ApiResponse;
import com.evops.exam.dto.RoomRequest;
import com.evops.exam.dto.SiteRequest;
import com.evops.exam.entity.ExamRoom;
import com.evops.exam.entity.ExamSite;
import com.evops.exam.service.ExamSiteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SiteController {

    private final ExamSiteService siteService;

    public SiteController(ExamSiteService siteService) {
        this.siteService = siteService;
    }

    @PostMapping("/sites")
    public ApiResponse<ExamSite> createSite(@Valid @RequestBody SiteRequest request) {
        return ApiResponse.ok(siteService.createSite(request));
    }

    @GetMapping("/sites")
    public ApiResponse<List<ExamSite>> listSites() {
        return ApiResponse.ok(siteService.listSites());
    }

    @PostMapping("/rooms")
    public ApiResponse<ExamRoom> createRoom(@Valid @RequestBody RoomRequest request) {
        return ApiResponse.ok(siteService.createRoom(request));
    }

    @GetMapping("/rooms")
    public ApiResponse<List<ExamRoom>> listRooms(@RequestParam(required = false) Long siteId) {
        return ApiResponse.ok(siteService.listRooms(siteId));
    }
}
