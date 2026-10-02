package com.evops.vault.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.vault.entity.VaultConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VaultConfigMapper extends BaseMapper<VaultConfig> {
}
