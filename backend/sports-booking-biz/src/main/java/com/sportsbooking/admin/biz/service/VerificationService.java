package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.biz.pojo.vo.StaffVerificationRecordVO;
import com.sportsbooking.admin.base.enums.VerificationMethod;
import java.util.List;

/**
 * 功能描述: 场馆员工核销与本人核销记录业务接口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface VerificationService {

    /**
     * 功能描述: 校验员工身份和凭证后完成预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @param staffUserId 员工微信用户 ID
     * @param credential 二维码内容或六位短码
     * @param method 核销方式
     * @return 已完成预约
     * @author gjm
     */
    Booking verify(long staffUserId, String credential, VerificationMethod method);
    Booking verify(long staffUserId, long venueId, String credential, VerificationMethod method);

    /**
     * 功能描述: 查询当前员工本人的核销记录。
     * 创建时间: 2026/08/28 16:29
     *
     * @param staffUserId 员工微信用户 ID
     * @return 本人核销记录
     * @author gjm
     */
    List<StaffVerificationRecordVO> listStaffRecords(long staffUserId);
    List<StaffVerificationRecordVO> listStaffRecords(long staffUserId, long venueId);
}
