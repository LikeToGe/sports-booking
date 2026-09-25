package com.sportsbooking.admin.base.model;

import com.sportsbooking.admin.base.enums.AdminRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.sportsbooking.admin.base.enums.VenuePermission;
import java.util.Set;

/**
 * 功能描述: 后台会话中的最小可信身份。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
public class AdminPrincipal {

    /**
     * 账号 ID
     */
    private long accountId;

    /**
     * 管理员角色
     */
    private AdminRole role;

    /**
     * 场馆 ID
     */
    private Long venueId;

    /**
     * 显示名称
     */
    private String displayName;

    /** 普通员工 ID；密码管理员为空。 */
    private Long employeeId;

    /** 当前请求实时解析出的权限；密码管理员不依赖该集合。 */
    private Set<VenuePermission> permissions;

    public AdminPrincipal(long accountId, AdminRole role, Long venueId, String displayName) {
        this(accountId, role, venueId, displayName, null, Set.of());
    }

    public AdminPrincipal(long accountId, AdminRole role, Long venueId, String displayName,
                          Long employeeId, Set<VenuePermission> permissions) {
        this.accountId = accountId;
        this.role = role;
        this.venueId = venueId;
        this.displayName = displayName;
        this.employeeId = employeeId;
        this.permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
