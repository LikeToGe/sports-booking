package com.sportsbooking.admin.biz.service;

/**
 * 功能描述: 生成高熵随机令牌并计算不可逆摘要。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface TokenService {
    /**
     * 功能描述: 生成 URL 安全的高熵随机令牌。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    String generate();
    /**
     * 功能描述: 计算 SHA-256 摘要。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    String hash(String value);
}
