package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.po.VenueCustomer;
import com.sportsbooking.admin.biz.pojo.vo.MembershipVO;
import com.sportsbooking.admin.biz.pojo.vo.PlatformCustomerSummaryVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueCustomerVO;
import java.util.List;

/**
 * 功能描述: 场馆顾客建档、识别和会员关系查询业务接口。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
public interface VenueCustomerService {

    /**
     * 功能描述: 按场馆和用户幂等取得顾客档案。
     *
     * @param venueId 场馆 ID
     * @param userId 用户 ID
     * @return 唯一顾客档案
     * @author gjm
     */
    VenueCustomer ensureCustomer(long venueId, long userId);

    /**
     * 功能描述: 查询当前场馆顾客列表。
     *
     * @param venueId 场馆 ID
     * @param keyword 姓名关键字、完整手机号或会员码
     * @return 顾客列表
     * @author gjm
     */
    List<VenueCustomerVO> listVenueCustomers(long venueId, String keyword);

    /**
     * 功能描述: 查询当前场馆单个顾客详情并校验场馆归属。
     *
     * @param venueId 场馆 ID
     * @param customerId 顾客档案 ID
     * @return 顾客详情
     * @author gjm
     */
    VenueCustomerVO getVenueCustomer(long venueId, long customerId);

    /**
     * 功能描述: 通过会员码或完整手机号识别当前场馆单个顾客。
     *
     * @param venueId 场馆 ID
     * @param memberCode 可选会员码，与手机号至少提供一项
     * @param phone 可选完整手机号，与会员码至少提供一项
     * @return 识别出的顾客
     * @author gjm
     */
    VenueCustomerVO identifyVenueCustomer(long venueId, String memberCode, String phone);

    /**
     * 功能描述: 查询当前小程序用户已有的场馆关系。
     *
     * @param userId 用户 ID
     * @return 场馆关系列表
     * @author gjm
     */
    List<MembershipVO> listMemberships(long userId);

    /**
     * 功能描述: 查询当前小程序用户在指定场馆的关系及会员码二维码。
     *
     * @param userId 用户 ID
     * @param venueId 场馆 ID
     * @return 场馆关系和二维码
     * @author gjm
     */
    MembershipVO getMembership(long userId, long venueId);

    /**
     * 功能描述: 查询平台场馆级顾客会员数量汇总。
     *
     * @return 各场馆数量汇总，不包含个人字段
     * @author gjm
     */
    List<PlatformCustomerSummaryVO> listPlatformSummaries();
}
