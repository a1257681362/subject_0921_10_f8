package com.evops.cert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.cert.entity.Certificate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CertificateMapper extends BaseMapper<Certificate> {

    /** 库内该规则+年度+批次下已占用的最大流水（含手工旧号补登），用于序列起跳防撞旧号。 */
    @Select("SELECT COALESCE(MAX(cert_seq),0) FROM t_certificate "
            + "WHERE rule_id = #{ruleId} AND cert_year = #{year} AND cert_batch = #{batch}")
    Integer maxUsedSeq(@Param("ruleId") Long ruleId,
                       @Param("year") Integer year,
                       @Param("batch") String batch);
}
