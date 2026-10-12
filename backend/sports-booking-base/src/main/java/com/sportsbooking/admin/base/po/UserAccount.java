package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import com.sportsbooking.admin.base.enums.EnableStatus;

/**
 * 功能描述: 小程序用户持久化实体。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@TableName("s_user_account")
public class UserAccount {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 本地模拟的微信 OpenID。 */
    public String mockOpenid;
    /** 默认预约联系人姓名。 */
    public String contactName;
    /** 默认预约联系人手机号。 */
    public String phone;
    /** 用户账号启用状态。 */
    public EnableStatus status;
    /** 创建时间。 */
    public LocalDateTime createdAt;
    /** 最后更新时间。 */
    public LocalDateTime updatedAt;
    /** 注销时间，作为不可逆状态事实。 */
    public LocalDateTime deletedAt;
    /** 社区昵称，不复用预约联系人。 */
    public String nickname;
    /** 社区头像地址。 */
    public String avatarUrl;
}
