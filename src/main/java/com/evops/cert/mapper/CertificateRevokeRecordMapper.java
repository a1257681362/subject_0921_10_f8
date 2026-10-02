package com.evops.cert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.cert.entity.CertificateRevokeRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CertificateRevokeRecordMapper extends BaseMapper<CertificateRevokeRecord> {
}
