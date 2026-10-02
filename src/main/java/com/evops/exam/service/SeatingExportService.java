package com.evops.exam.service;

import com.evops.exam.entity.ArrangementRun;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.ExamRoom;
import com.evops.exam.entity.ExamSite;
import com.evops.exam.mapper.ExamRoomMapper;
import com.evops.exam.vo.SeatingEntry;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 座次表导出：按当前编排记录（t_arrangement_run.is_current）渲染打印页 / 生成 CSV。
 * 每个考场一张表，含考场号、座位号、证号、姓名、学校；空位也列出，便于巡考核对。
 */
@Service
public class SeatingExportService {

    private final ArrangeService arrangeService;
    private final ExamBatchService batchService;
    private final ExamSiteService siteService;
    private final ExamRoomMapper roomMapper;

    public SeatingExportService(ArrangeService arrangeService,
                                ExamBatchService batchService,
                                ExamSiteService siteService,
                                ExamRoomMapper roomMapper) {
        this.arrangeService = arrangeService;
        this.batchService = batchService;
        this.siteService = siteService;
        this.roomMapper = roomMapper;
    }

    /** 打印页 / CSV 共用的数据装配。 */
    @Transactional(readOnly = true)
    public ChartModel buildChart(Long batchId) {
        ExamBatch batch = batchService.getBatch(batchId);
        ExamSite site = siteService.getSite(batch.getSiteId());
        ArrangementRun run = arrangeService.getCurrentRun(batchId);
        List<SeatingEntry> seating = arrangeService.readSeating(run.getSeating());

        List<ExamRoom> rooms = roomMapper.selectList(new QueryWrapper<ExamRoom>()
                .eq("site_id", batch.getSiteId())
                .orderByAsc("room_no"));
        Map<Long, ExamRoom> roomById = new LinkedHashMap<>();
        for (ExamRoom room : rooms) {
            roomById.put(room.getId(), room);
        }

        // 按考场归集座位（仅本次实际使用的考场）
        Map<String, RoomSheet> sheetByNo = new LinkedHashMap<>();
        for (SeatingEntry entry : seating) {
            RoomSheet sheet = sheetByNo.computeIfAbsent(entry.getRoomNo(), no -> {
                RoomSheet s = new RoomSheet();
                s.roomNo = no;
                ExamRoom room = roomById.get(entry.getRoomId());
                s.capacity = room == null ? null : room.getCapacity();
                s.building = room == null ? null : room.getBuilding();
                s.floorNo = room == null ? null : room.getFloorNo();
                return s;
            });
            SeatRow row = new SeatRow();
            row.seatNo = entry.getSeatNo();
            row.ticketNo = entry.getTicketNo();
            row.regNo = entry.getRegNo();
            row.name = entry.getName();
            row.school = entry.getSchool();
            row.empty = false;
            sheet.rows.add(row);
        }
        List<RoomSheet> sheets = new ArrayList<>(sheetByNo.values());
        sheets.sort(Comparator.comparing(s -> s.roomNo));

        // 每个考场补空位到实际最大座位号（编排从 1 号连续排，空位为理论富余）
        for (RoomSheet sheet : sheets) {
            sheet.rows.sort(Comparator.comparingInt(r -> r.seatNo));
            int maxSeat = sheet.rows.isEmpty() ? 0 : sheet.rows.get(sheet.rows.size() - 1).seatNo;
            Map<Integer, SeatRow> bySeat = new LinkedHashMap<>();
            for (SeatRow r : sheet.rows) {
                bySeat.put(r.seatNo, r);
            }
            List<SeatRow> full = new ArrayList<>();
            for (int seat = 1; seat <= maxSeat; seat++) {
                SeatRow r = bySeat.get(seat);
                if (r == null) {
                    r = new SeatRow();
                    r.seatNo = seat;
                    r.empty = true;
                }
                full.add(r);
            }
            sheet.rows = full;
        }

        ChartModel model = new ChartModel();
        model.batchId = batch.getId();
        model.batchCode = batch.getCode();
        model.batchName = batch.getName();
        model.subject = batch.getSubject();
        model.examDate = batch.getExamDate() == null ? null : batch.getExamDate().toString();
        model.siteName = site.getName();
        model.siteAddress = site.getAddress();
        model.mixVersionNo = run.getMixVersionNo();
        model.seed = run.getSeed();
        model.sameSchoolAdjacentCount = run.getSameSchoolAdjacentCount();
        model.totalCount = run.getTotalCount();
        model.sheets = sheets;
        return model;
    }

    /** 生成带 BOM 的 UTF-8 CSV（Excel 直接打开中文不乱码）。 */
    @Transactional(readOnly = true)
    public byte[] buildCsv(Long batchId) {
        ChartModel model = buildChart(batchId);
        StringBuilder sb = new StringBuilder('﻿');
        sb.append("考场号,楼栋,楼层,座位号,准考证号,报名号,姓名,学校,备注\r\n");
        for (RoomSheet sheet : model.sheets) {
            for (SeatRow row : sheet.rows) {
                sb.append(csv(sheet.roomNo)).append(',')
                        .append(csv(sheet.building)).append(',')
                        .append(sheet.floorNo == null ? "" : sheet.floorNo).append(',')
                        .append(row.seatNo).append(',')
                        .append(csv(row.empty ? "" : row.ticketNo)).append(',')
                        .append(csv(row.empty ? "" : row.regNo)).append(',')
                        .append(csv(row.empty ? "" : row.name)).append(',')
                        .append(csv(row.empty ? "" : (row.school == null ? "未填学校" : row.school))).append(',')
                        .append(row.empty ? "空位" : "").append("\r\n");
            }
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    /** 座次表整表模型。 */
    public static class ChartModel {
        public Long batchId;
        public String batchCode;
        public String batchName;
        public String subject;
        public String examDate;
        public String siteName;
        public String siteAddress;
        public String mixVersionNo;
        public Long seed;
        public Integer sameSchoolAdjacentCount;
        public Integer totalCount;
        public List<RoomSheet> sheets;
    }

    /** 单个考场一张表。 */
    public static class RoomSheet {
        public String roomNo;
        public Integer capacity;
        public String building;
        public Integer floorNo;
        public List<SeatRow> rows = new ArrayList<>();
    }

    /** 一个座位一行。 */
    public static class SeatRow {
        public int seatNo;
        public String ticketNo;
        public String regNo;
        public String name;
        public String school;
        public boolean empty;
    }
}
