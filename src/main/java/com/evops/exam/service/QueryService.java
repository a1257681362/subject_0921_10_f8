package com.evops.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.exam.entity.AdmissionTicket;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.ExamRoom;
import com.evops.exam.entity.ExamSite;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.AdmissionTicketMapper;
import com.evops.exam.mapper.ExamRoomMapper;
import com.evops.exam.mapper.ExamSiteMapper;
import com.evops.exam.vo.TicketView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查档：按证号精确查、按姓名查（先查名册再联到准考证），重名全返回。
 */
@Service
public class QueryService {

    private final AdmissionTicketMapper ticketMapper;
    private final ExamSiteMapper siteMapper;
    private final ExamRoomMapper roomMapper;
    private final RegistrationService registrationService;
    private final ExamBatchService batchService;

    public QueryService(AdmissionTicketMapper ticketMapper,
                        ExamSiteMapper siteMapper,
                        ExamRoomMapper roomMapper,
                        RegistrationService registrationService,
                        ExamBatchService batchService) {
        this.ticketMapper = ticketMapper;
        this.siteMapper = siteMapper;
        this.roomMapper = roomMapper;
        this.registrationService = registrationService;
        this.batchService = batchService;
    }

    @Transactional(readOnly = true)
    public TicketView getByTicketNo(String ticketNo) {
        if (ticketNo == null || ticketNo.trim().isEmpty()) {
            throw new BusinessException("TICKET_NO_REQUIRED", "准考证号不能为空");
        }
        AdmissionTicket ticket = ticketMapper.selectOne(
                new QueryWrapper<AdmissionTicket>().eq("ticket_no", ticketNo.trim()));
        if (ticket == null) {
            throw new BusinessException("TICKET_NOT_FOUND", "未查到准考证：" + ticketNo.trim());
        }
        return toViews(Collections.singletonList(ticket)).get(0);
    }

    @Transactional(readOnly = true)
    public List<TicketView> findTicketsByName(String name) {
        List<Registration> regs = registrationService.findByName(name);
        if (regs.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> regIds = new ArrayList<>();
        for (Registration reg : regs) {
            regIds.add(reg.getId());
        }
        List<AdmissionTicket> tickets = ticketMapper.selectList(
                new QueryWrapper<AdmissionTicket>().in("registration_id", regIds).orderByAsc("ticket_no"));
        return toViews(tickets);
    }

    @Transactional(readOnly = true)
    public List<TicketView> listByBatch(Long batchId) {
        batchService.getBatch(batchId);
        List<AdmissionTicket> tickets = ticketMapper.selectList(
                new QueryWrapper<AdmissionTicket>().eq("batch_id", batchId).orderByAsc("ticket_no"));
        return toViews(tickets);
    }

    private List<TicketView> toViews(List<AdmissionTicket> tickets) {
        if (tickets.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> siteIds = new HashSet<>();
        Set<Long> roomIds = new HashSet<>();
        Set<Long> batchIds = new HashSet<>();
        Set<Long> regIds = new HashSet<>();
        for (AdmissionTicket t : tickets) {
            siteIds.add(t.getSiteId());
            roomIds.add(t.getRoomId());
            batchIds.add(t.getBatchId());
            regIds.add(t.getRegistrationId());
        }

        Map<Long, ExamSite> sites = new HashMap<>();
        for (ExamSite s : siteMapper.selectBatchIds(siteIds)) {
            sites.put(s.getId(), s);
        }
        Map<Long, ExamRoom> rooms = new HashMap<>();
        for (ExamRoom r : roomMapper.selectBatchIds(roomIds)) {
            rooms.put(r.getId(), r);
        }
        Map<Long, ExamBatch> batches = new HashMap<>();
        for (ExamBatch b : batchService.listByIds(batchIds)) {
            batches.put(b.getId(), b);
        }
        Map<Long, Registration> regs = registrationService.loadByIds(new ArrayList<>(regIds));

        List<TicketView> views = new ArrayList<>();
        for (AdmissionTicket t : tickets) {
            TicketView v = new TicketView();
            v.setId(t.getId());
            v.setTicketNo(t.getTicketNo());
            v.setStatus(t.getStatus());
            v.setBatchId(t.getBatchId());
            v.setRegistrationId(t.getRegistrationId());
            v.setSiteId(t.getSiteId());
            v.setRoomId(t.getRoomId());
            v.setSeatNo(t.getSeatNo());
            v.setPrintedBy(t.getPrintedBy());
            v.setPrintedAt(t.getPrintedAt());
            v.setIssuedBy(t.getIssuedBy());
            v.setIssuedAt(t.getIssuedAt());

            ExamBatch b = batches.get(t.getBatchId());
            if (b != null) {
                v.setBatchCode(b.getCode());
                v.setBatchName(b.getName());
            }
            Registration r = regs.get(t.getRegistrationId());
            if (r != null) {
                v.setRegNo(r.getRegNo());
                v.setName(r.getName());
                v.setSchool(r.getSchool());
                v.setIdType(r.getIdType());
                v.setIdNo(r.getIdNo());
                v.setGender(r.getGender());
                v.setPhone(r.getPhone());
            }
            ExamSite s = sites.get(t.getSiteId());
            if (s != null) {
                v.setSiteCode(s.getCode());
                v.setSiteName(s.getName());
                v.setSiteAddress(s.getAddress());
            }
            ExamRoom room = rooms.get(t.getRoomId());
            if (room != null) {
                v.setRoomNo(room.getRoomNo());
                v.setBuilding(room.getBuilding());
                v.setFloorNo(room.getFloorNo());
                v.setLocationDesc(room.getLocationDesc());
            }
            views.add(v);
        }
        return views;
    }
}
