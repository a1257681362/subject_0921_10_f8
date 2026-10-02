package com.evops.vault.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.vault.common.VaultConstants;
import com.evops.vault.dto.AccessWindowRequest;
import com.evops.vault.dto.BoxRequest;
import com.evops.vault.dto.PersonRequest;
import com.evops.vault.entity.VaultBox;
import com.evops.vault.entity.VaultConfig;
import com.evops.vault.entity.VaultPerson;
import com.evops.vault.mapper.VaultBoxMapper;
import com.evops.vault.mapper.VaultConfigMapper;
import com.evops.vault.mapper.VaultPersonMapper;
import com.evops.vault.vo.AccessWindowView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 保密室主数据：准入人员、卷箱、允许进出时段配置。
 * 人员停用后不能登记也不能当复核人；卷箱停用后不能随登记挂钩。
 */
@Service
public class VaultMasterDataService {

    private final VaultPersonMapper personMapper;
    private final VaultBoxMapper boxMapper;
    private final VaultConfigMapper configMapper;

    public VaultMasterDataService(VaultPersonMapper personMapper,
                                  VaultBoxMapper boxMapper,
                                  VaultConfigMapper configMapper) {
        this.personMapper = personMapper;
        this.boxMapper = boxMapper;
        this.configMapper = configMapper;
    }

    // ==================== 准入人员 ====================

    @Transactional
    public VaultPerson createPerson(PersonRequest request) {
        String code = request.getCode().trim();
        String cardNo = request.getCardNo().trim();
        if (personMapper.selectCount(new QueryWrapper<VaultPerson>().eq("code", code)) > 0) {
            throw new BusinessException("PERSON_CODE_DUP", "人员编码已存在：" + code);
        }
        if (personMapper.selectCount(new QueryWrapper<VaultPerson>().eq("card_no", cardNo)) > 0) {
            throw new BusinessException("PERSON_CARD_DUP", "刷证卡号已存在：" + cardNo);
        }
        VaultPerson person = new VaultPerson();
        person.setCode(code);
        person.setName(request.getName().trim());
        person.setCardNo(cardNo);
        person.setDuty(request.getDuty());
        person.setStatus(VaultConstants.ENABLED);
        person.setRemark(request.getRemark());
        personMapper.insert(person);
        return person;
    }

    @Transactional(readOnly = true)
    public List<VaultPerson> listPersons(String status) {
        QueryWrapper<VaultPerson> wrapper = new QueryWrapper<VaultPerson>().orderByAsc("code");
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        return personMapper.selectList(wrapper);
    }

    /** 停用：退出准入名单，之后不能登记也不能复核；历史登记保留。 */
    @Transactional
    public VaultPerson disablePerson(Long id) {
        VaultPerson person = requirePerson(id);
        person.setStatus(VaultConstants.DISABLED);
        personMapper.updateById(person);
        return person;
    }

    @Transactional
    public VaultPerson enablePerson(Long id) {
        VaultPerson person = requirePerson(id);
        person.setStatus(VaultConstants.ENABLED);
        personMapper.updateById(person);
        return person;
    }

    public VaultPerson requirePerson(Long id) {
        VaultPerson person = personMapper.selectById(id);
        if (person == null) {
            throw new BusinessException("PERSON_NOT_FOUND", "保密室准入人员不存在：id=" + id);
        }
        return person;
    }

    /** 按人员编码或刷证卡号定位（刷证二选一），须在册且启用。 */
    public VaultPerson resolveEnabledPerson(String personCode, String cardNo) {
        String code = blank(personCode) ? null : personCode.trim();
        String card = blank(cardNo) ? null : cardNo.trim();
        if (code == null && card == null) {
            throw new BusinessException("PERSON_REF_REQUIRED", "人员编码与刷证卡号至少填一项");
        }
        QueryWrapper<VaultPerson> wrapper = new QueryWrapper<>();
        if (code != null) {
            wrapper.eq("code", code);
        } else {
            wrapper.eq("card_no", card);
        }
        VaultPerson person = personMapper.selectOne(wrapper);
        if (person == null) {
            throw new BusinessException("PERSON_NOT_FOUND",
                    "未建档的" + (code != null ? "人员编码：" + code : "刷证卡号：" + card) + "，请先建档");
        }
        assertEnabled(person);
        return person;
    }

    /** 复核人：必须在册且启用。 */
    public VaultPerson requireEnabledByCode(String personCode) {
        if (blank(personCode)) {
            throw new BusinessException("PERSON_CODE_REQUIRED", "人员编码不能为空");
        }
        VaultPerson person = personMapper.selectOne(
                new QueryWrapper<VaultPerson>().eq("code", personCode.trim()));
        if (person == null) {
            throw new BusinessException("PERSON_NOT_FOUND", "未建档的人员编码：" + personCode.trim());
        }
        assertEnabled(person);
        return person;
    }

    /** 按编码查人员（不要求启用；纸面存量导入等历史口径用），查无返回 null。 */
    public VaultPerson findPersonByCode(String personCode) {
        if (blank(personCode)) {
            return null;
        }
        return personMapper.selectOne(new QueryWrapper<VaultPerson>().eq("code", personCode.trim()));
    }

    /** 按箱号查卷箱（不要求启用；纸面存量导入等历史口径用），查无返回 null。 */
    public VaultBox findBoxByNo(String boxNo) {
        if (blank(boxNo)) {
            return null;
        }
        return boxMapper.selectOne(new QueryWrapper<VaultBox>().eq("box_no", boxNo.trim()));
    }

    private void assertEnabled(VaultPerson person) {
        if (!VaultConstants.ENABLED.equals(person.getStatus())) {
            throw new BusinessException("PERSON_DISABLED",
                    "人员 " + person.getCode() + " " + person.getName() + " 已停用，不能登记或复核");
        }
    }

    // ==================== 卷箱 ====================

    @Transactional
    public VaultBox createBox(BoxRequest request) {
        String boxNo = request.getBoxNo().trim();
        if (boxMapper.selectCount(new QueryWrapper<VaultBox>().eq("box_no", boxNo)) > 0) {
            throw new BusinessException("BOX_NO_DUP", "卷箱箱号已存在：" + boxNo);
        }
        VaultBox box = new VaultBox();
        box.setBoxNo(boxNo);
        box.setName(request.getName());
        box.setStatus(VaultConstants.ENABLED);
        box.setRemark(request.getRemark());
        boxMapper.insert(box);
        return box;
    }

    @Transactional(readOnly = true)
    public List<VaultBox> listBoxes() {
        return boxMapper.selectList(new QueryWrapper<VaultBox>().orderByAsc("box_no"));
    }

    public VaultBox requireEnabledBox(String boxNo) {
        VaultBox box = boxMapper.selectOne(new QueryWrapper<VaultBox>().eq("box_no", boxNo.trim()));
        if (box == null) {
            throw new BusinessException("BOX_NOT_FOUND", "卷箱未建档：" + boxNo.trim() + "，请先建档");
        }
        if (!VaultConstants.ENABLED.equals(box.getStatus())) {
            throw new BusinessException("BOX_DISABLED", "卷箱 " + boxNo.trim() + " 已停用，不能登记出入");
        }
        return box;
    }

    // ==================== 允许进出时段 ====================

    @Transactional(readOnly = true)
    public AccessWindowView getWindow() {
        return new AccessWindowView(windowStartText(), windowEndText());
    }

    /** 调整允许进出时段：起止必须成对、起早于止，HH:mm。 */
    @Transactional
    public AccessWindowView setWindow(AccessWindowRequest request) {
        LocalTime start = parseClock(request.getStart(), "窗口起点");
        LocalTime end = parseClock(request.getEnd(), "窗口终点");
        if (!start.isBefore(end)) {
            throw new BusinessException("WINDOW_INVALID",
                    "允许进出时段起点须早于终点：" + request.getStart() + " ~ " + request.getEnd());
        }
        upsertConfig(VaultConstants.CFG_WINDOW_START, formatClock(start), "允许进出时段起点（HH:mm，含）");
        upsertConfig(VaultConstants.CFG_WINDOW_END, formatClock(end), "允许进出时段终点（HH:mm，不含）");
        return new AccessWindowView(formatClock(start), formatClock(end));
    }

    /** 发生时间是否落在允许窗口外（异常时段）。 */
    public boolean isOffHours(LocalDateTime occurredAt) {
        LocalTime time = occurredAt.toLocalTime();
        LocalTime start = parseClock(windowStartText(), "窗口起点");
        LocalTime end = parseClock(windowEndText(), "窗口终点");
        return time.isBefore(start) || !time.isBefore(end);
    }

    public String windowText() {
        return windowStartText() + "-" + windowEndText();
    }

    private String windowStartText() {
        return configValue(VaultConstants.CFG_WINDOW_START, VaultConstants.DEFAULT_WINDOW_START);
    }

    private String windowEndText() {
        return configValue(VaultConstants.CFG_WINDOW_END, VaultConstants.DEFAULT_WINDOW_END);
    }

    private String configValue(String key, String fallback) {
        VaultConfig config = configMapper.selectOne(new QueryWrapper<VaultConfig>().eq("config_key", key));
        if (config == null || blank(config.getConfigValue())) {
            return fallback;
        }
        return config.getConfigValue().trim();
    }

    private void upsertConfig(String key, String value, String remark) {
        VaultConfig config = configMapper.selectOne(new QueryWrapper<VaultConfig>().eq("config_key", key));
        if (config == null) {
            config = new VaultConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setRemark(remark);
            config.setOperator(OperatorHolder.current());
            configMapper.insert(config);
        } else {
            config.setConfigValue(value);
            config.setOperator(OperatorHolder.current());
            configMapper.updateById(config);
        }
    }

    static LocalTime parseClock(String text, String label) {
        if (blank(text)) {
            throw new BusinessException("WINDOW_INVALID", label + "不能为空（HH:mm）");
        }
        try {
            return LocalTime.parse(text.trim());
        } catch (Exception e) {
            throw new BusinessException("WINDOW_INVALID", label + "格式不正确（应为 HH:mm）：" + text);
        }
    }

    static String formatClock(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
