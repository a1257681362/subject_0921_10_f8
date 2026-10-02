package com.evops.regcond.vo;

import com.evops.regcond.entity.QualificationCheckItem;
import com.evops.regcond.entity.RegistrationQualification;
import lombok.Data;

import java.util.List;

/**
 * 核验单视图：主单 + 逐项核验结果。
 */
@Data
public class QualificationView {
    private RegistrationQualification qualification;
    private List<QualificationCheckItem> items;
}
