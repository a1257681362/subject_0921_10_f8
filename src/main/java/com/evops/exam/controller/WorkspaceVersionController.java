package com.evops.exam.controller;

import com.evops.common.ApiResponse;
import com.evops.exam.entity.WorkspaceVersion;
import com.evops.exam.entity.WorkspaceVersionEvent;
import com.evops.exam.service.WorkspaceVersionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作区版本：创建快照、版本选择、状态回退、事件追溯。
 */
@RestController
@RequestMapping("/api/workspace/versions")
public class WorkspaceVersionController {

    private final WorkspaceVersionService versionService;

    public WorkspaceVersionController(WorkspaceVersionService versionService) {
        this.versionService = versionService;
    }

    @PostMapping
    public ApiResponse<WorkspaceVersion> createSnapshot(@RequestParam(required = false) String label,
                                                        @RequestParam(required = false) String remark) {
        WorkspaceVersion version = versionService.createSnapshot(label, remark);
        version.setSnapshot(null); // 列表式返回不带大快照
        return ApiResponse.ok(version);
    }

    @GetMapping
    public ApiResponse<List<WorkspaceVersion>> list() {
        return ApiResponse.ok(versionService.listVersions(false));
    }

    @GetMapping("/current")
    public ApiResponse<WorkspaceVersion> current() {
        return ApiResponse.ok(versionService.currentVersion());
    }

    @GetMapping("/{versionNo}")
    public ApiResponse<WorkspaceVersion> detail(@PathVariable String versionNo,
                                                @RequestParam(required = false, defaultValue = "false")
                                                boolean includeSnapshot) {
        return ApiResponse.ok(versionService.getVersion(versionNo, includeSnapshot));
    }

    /** 版本选择：只切换当前标记并留痕，不改业务数据。 */
    @PostMapping("/{versionNo}/select")
    public ApiResponse<WorkspaceVersion> select(@PathVariable String versionNo) {
        WorkspaceVersion version = versionService.selectVersion(versionNo);
        version.setSnapshot(null);
        return ApiResponse.ok(version);
    }

    /** 状态回退：按该版本快照还原，生成新的回退版本，原版本保留。 */
    @PostMapping("/{versionNo}/rollback")
    public ApiResponse<WorkspaceVersion> rollback(@PathVariable String versionNo,
                                                  @RequestParam(required = false) String remark) {
        WorkspaceVersion version = versionService.rollback(versionNo, remark);
        version.setSnapshot(null);
        return ApiResponse.ok(version);
    }

    /** 版本事件：CREATE/SELECT/ROLLBACK 全程可追溯。 */
    @GetMapping("/events")
    public ApiResponse<List<WorkspaceVersionEvent>> events(
            @RequestParam(required = false) String versionNo) {
        return ApiResponse.ok(versionService.listEvents(versionNo));
    }
}
