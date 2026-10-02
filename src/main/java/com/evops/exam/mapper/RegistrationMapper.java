package com.evops.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.exam.entity.Registration;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RegistrationMapper extends BaseMapper<Registration> {
}
