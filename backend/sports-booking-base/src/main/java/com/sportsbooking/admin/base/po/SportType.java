package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import com.sportsbooking.admin.base.enums.EnableStatus;

/**
 * 功能描述: 平台或场馆范围运动类型持久化实体。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@TableName("s_sport_type")
public class SportType {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 所属场馆 ID，平台公共运动类型为空。 */
    public Long ownerVenueId;
    /** 运动类型名称。 */
    public String name;
    /** 用于同一作用域内判重的标准化名称。 */
    public String normalizedName;
    /** 运动类型启用状态。 */
    public EnableStatus status;
    /** 创建时间。 */
    public LocalDateTime createdAt;
}
