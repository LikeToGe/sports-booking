package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.vo.AdminLoginVO;
import com.sportsbooking.admin.biz.pojo.vo.ScanTicketVO;

/** 员工 Web 扫码登录一次性票据。 */
public interface WebLoginTicketService {
    ScanTicketVO create();

    /**
     * 查询票据状态；只有持有发起端密钥的 Web 页面可以轮询。
     *
     * @param ticket 一次性票据
     * @param clientSecret 创建票据时返回的发起端密钥
     * @return 票据状态，不再返回发起端密钥
     * @author gjm
     */
    ScanTicketVO get(String ticket, String clientSecret);

    void confirm(String ticket, long userId, long venueId);

    /**
     * 消费已确认票据并创建员工后台会话；密钥不匹配时不消耗票据。
     *
     * @param ticket 一次性票据
     * @param clientSecret 创建票据时返回的发起端密钥
     * @return 员工后台登录结果
     * @author gjm
     */
    AdminLoginVO consume(String ticket, String clientSecret);
}
