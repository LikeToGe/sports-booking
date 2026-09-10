package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.vo.SignedCredentialVO;

/**
 * 功能描述: 生成和校验动态二维码及固定短码。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface CredentialService {
    /**
     * 功能描述: 为预约生成动态二维码和固定短码。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    SignedCredentialVO createCredential(long bookingId);
    /**
     * 功能描述: 校验动态二维码并解析预约 ID，失败返回 null。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    Long resolveQr(String content);
    /**
     * 功能描述: 生成预约固定 6 位短码。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    String createShortCode(long bookingId);
}
