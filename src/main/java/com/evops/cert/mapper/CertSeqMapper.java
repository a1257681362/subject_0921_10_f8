package com.evops.cert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.cert.entity.CertSeq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CertSeqMapper extends BaseMapper<CertSeq> {

    /** 发号行锁：按 规则+年度+批次 锁定序列行，保证并发发号不重号。 */
    @Select("SELECT * FROM t_cert_seq WHERE rule_id = #{ruleId} AND cert_year = #{year} "
            + "AND cert_batch = #{batch} FOR UPDATE")
    CertSeq selectForUpdate(@Param("ruleId") Long ruleId,
                            @Param("year") Integer year,
                            @Param("batch") String batch);
}
