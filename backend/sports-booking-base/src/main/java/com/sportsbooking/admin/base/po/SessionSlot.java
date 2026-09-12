package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;

/**
 * 功能描述: 具体日期可预约场次及库存状态持久化实体。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@TableName("s_session_slot")
public class SessionSlot {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 所属场馆 ID。 */
    public Long venueId;
    /** 所属场地 ID。 */
    public Long courtId;
    /** 运动类型 ID。 */
    public Long sportTypeId;
    /** 来源周场次模板 ID，临时加场时为空。 */
    public Long weeklySlotId;
    /** 是否由场馆人工编辑，人工覆盖后不再跟随周场次同步。 */
    public Boolean manualOverride;
    /** 周场次原本生成的日期，人工改期后仍用于阻止模板重复生成。 */
    public LocalDate templateSlotDate;
    /** 场次所属日期。 */
    public LocalDate slotDate;
    /** 场次开始时间。 */
    public Instant startAt;
    /** 场次结束时间。 */
    public Instant endAt;
    /** 场次价格，单位为分。 */
    public Integer priceFen;
    /** 场次库存状态。 */
    public SessionSlotStatus status;
    /** 当前占用该场次的预约 ID。 */
    public Long holdBookingId;
    /** 当前预约占用的失效时间。 */
    public Instant holdExpiresAt;
    /** 创建时间。 */
    public LocalDateTime createdAt;
    /** 最后更新时间。 */
    public LocalDateTime updatedAt;
}
