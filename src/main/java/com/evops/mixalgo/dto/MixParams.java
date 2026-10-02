package com.evops.mixalgo.dto;

import lombok.Data;

/**
 * 混编算法参数（发布时冻结为 JSON）。
 * - strategy：编排策略，当前仅支持 SCHOOL_SPREAD（同校错开）；
 * - minSchoolGap：同校学员在同一考场内的最小座位间隔（1=不相邻，2=隔一个座，…）；
 * - spreadRooms：是否把同校学员按考场容量比例摊到多个考场；
 * - maxSameSchoolPerRoom：单考场同校人数上限，可空（不设上限，仅按比例摊）；
 * - tries：随机试排次数，取其中同校相邻最少的一次。
 */
@Data
public class MixParams {
    private String strategy;
    private Integer minSchoolGap;
    private Boolean spreadRooms;
    private Integer maxSameSchoolPerRoom;
    private Integer tries;
}
