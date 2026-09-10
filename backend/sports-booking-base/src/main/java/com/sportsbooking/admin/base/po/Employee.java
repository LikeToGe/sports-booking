package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import com.sportsbooking.admin.base.enums.EnableStatus;

/**
 * 功能描述: 场馆员工及微信绑定信息持久化实体。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@TableName("s_employee")
public class Employee {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 所属场馆 ID。 */
    public Long venueId;
    /** 已绑定的小程序用户 ID，未绑定时为空。 */
    public Long userId;
    /** 员工姓名。 */
    public String name;
    /** 员工启用状态。 */
    public EnableStatus status;
    /** 一次性绑定码摘要。 */
    public String bindingCodeHash;
    /** 一次性绑定码失效时间。 */
    public LocalDateTime bindingExpiresAt;
    /** 创建时间。 */
    public LocalDateTime createdAt;
    /** 最后更新时间。 */
    public LocalDateTime updatedAt;
}
