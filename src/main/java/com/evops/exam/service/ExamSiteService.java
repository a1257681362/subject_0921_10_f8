package com.evops.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.exam.dto.RoomRequest;
import com.evops.exam.dto.SiteRequest;
import com.evops.exam.entity.ExamRoom;
import com.evops.exam.entity.ExamSite;
import com.evops.exam.mapper.ExamRoomMapper;
import com.evops.exam.mapper.ExamSiteMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExamSiteService {

    private final ExamSiteMapper siteMapper;
    private final ExamRoomMapper roomMapper;

    public ExamSiteService(ExamSiteMapper siteMapper, ExamRoomMapper roomMapper) {
        this.siteMapper = siteMapper;
        this.roomMapper = roomMapper;
    }

    // ---------- 考点 ----------

    @Transactional
    public ExamSite createSite(SiteRequest request) {
        ExamSite site = new ExamSite();
        site.setCode(request.getCode().trim());
        site.setName(request.getName().trim());
        site.setAddress(request.getAddress());
        site.setContactPerson(request.getContactPerson());
        site.setContactPhone(request.getContactPhone());
        site.setStatus("ENABLED");
        site.setRemark(request.getRemark());
        try {
            siteMapper.insert(site);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_SITE_CODE", "考点编码已存在：" + site.getCode());
        }
        return site;
    }

    public List<ExamSite> listSites() {
        return siteMapper.selectList(new QueryWrapper<ExamSite>().orderByAsc("code"));
    }

    public ExamSite getSite(Long id) {
        ExamSite site = siteMapper.selectById(id);
        if (site == null) {
            throw new BusinessException("SITE_NOT_FOUND", "考点不存在：id=" + id);
        }
        return site;
    }

    // ---------- 考场（容量、位置记全） ----------

    @Transactional
    public ExamRoom createRoom(RoomRequest request) {
        getSite(request.getSiteId());
        ExamRoom room = new ExamRoom();
        room.setSiteId(request.getSiteId());
        room.setRoomNo(request.getRoomNo().trim());
        room.setBuilding(request.getBuilding());
        room.setFloorNo(request.getFloorNo());
        room.setCapacity(request.getCapacity());
        room.setLocationDesc(request.getLocationDesc());
        room.setStatus("ENABLED");
        try {
            roomMapper.insert(room);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_ROOM_NO", "该考点下考场号已存在：" + room.getRoomNo());
        }
        return room;
    }

    public List<ExamRoom> listRooms(Long siteId) {
        QueryWrapper<ExamRoom> wrapper = new QueryWrapper<>();
        if (siteId != null) {
            wrapper.eq("site_id", siteId);
        }
        wrapper.orderByAsc("site_id", "room_no");
        return roomMapper.selectList(wrapper);
    }

    public ExamRoom getRoom(Long id) {
        ExamRoom room = roomMapper.selectById(id);
        if (room == null) {
            throw new BusinessException("ROOM_NOT_FOUND", "考场不存在：id=" + id);
        }
        return room;
    }
}
