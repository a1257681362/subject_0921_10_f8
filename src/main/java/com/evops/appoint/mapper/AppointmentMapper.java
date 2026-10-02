package com.evops.appoint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.appoint.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {
}
