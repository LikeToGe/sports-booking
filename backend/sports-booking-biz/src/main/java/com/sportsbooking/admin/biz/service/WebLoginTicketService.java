package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.vo.AdminLoginVO;
import com.sportsbooking.admin.biz.pojo.vo.ScanTicketVO;

/** 员工 Web 扫码登录一次性票据。 */
public interface WebLoginTicketService {
    ScanTicketVO create();
    ScanTicketVO get(String ticket);
    void confirm(String ticket, long userId, long venueId);
    AdminLoginVO consume(String ticket);
}
