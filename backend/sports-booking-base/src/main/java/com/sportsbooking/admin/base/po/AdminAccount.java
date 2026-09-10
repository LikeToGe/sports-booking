package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import com.sportsbooking.admin.base.enums.AdminRole;
import com.sportsbooking.admin.base.enums.EnableStatus;

/**
 * 功能描述: 后台管理员持久化实体。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@TableName("s_admin_account")
public class AdminAccount {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 所属场馆 ID，平台管理员为空。 */
    public Long venueId;
    /** 管理员角色。 */
    public AdminRole role;
    /** 登录用户名。 */
    public String username;
    /** 登录密码摘要。 */
    public String passwordHash;
    /** 管理员显示名称。 */
    public String displayName;
    /** 账号启用状态。 */
    public EnableStatus status;
    /** 是否必须在下次登录后修改密码。 */
    public Boolean mustChangePassword;
    /** 创建时间。 */
    public LocalDateTime createdAt;
    /** 最后更新时间。 */
    public LocalDateTime updatedAt;
}
