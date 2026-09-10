package com.sportsbooking.admin.base.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 小程序会话中的最小可信身份。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MiniPrincipal {

    /**
     * 用户 ID
     */
    private long userId;
}
