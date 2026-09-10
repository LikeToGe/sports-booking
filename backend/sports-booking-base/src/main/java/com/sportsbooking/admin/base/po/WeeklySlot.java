package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.time.LocalTime;
import com.sportsbooking.admin.base.enums.EnableStatus;

/**
 * 功能描述: 按星期重复的固定场次模板持久化实体。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@TableName("s_weekly_slot")
public class WeeklySlot {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 所属场地 ID。 */
    public Long courtId;
    /** 运动类型 ID。 */
    public Long sportTypeId;
    /** 星期序号，取值范围为 1-7。 */
    public Integer dayOfWeek;
    /** 模板场次开始时间。 */
    public LocalTime startTime;
    /** 模板场次结束时间。 */
    public LocalTime endTime;
    /** 模板场次价格，单位为分。 */
    public Integer priceFen;
    /** 周场次模板启用状态。 */
    public EnableStatus status;
    /** 创建时间。 */
    public LocalDateTime createdAt;
}
