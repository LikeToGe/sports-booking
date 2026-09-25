package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.bo.PlatformCustomerSummaryBO;
import com.sportsbooking.admin.base.bo.VenueCustomerBO;
import com.sportsbooking.admin.base.po.VenueCustomer;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 功能描述: 场馆顾客关系及查询数据访问。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
public interface VenueCustomerMapper extends BaseMapper<VenueCustomer> {

    /**
     * 功能描述: 按场馆和用户查询顾客档案。
     *
     * @param venueId 场馆 ID
     * @param userId 用户 ID
     * @return 顾客档案，不存在时返回 null
     * @author gjm
     */
    VenueCustomer selectByVenueAndUser(@Param("venueId") long venueId, @Param("userId") long userId);

    /**
     * 功能描述: 唯一冲突后以当前读查询同馆用户档案。
     *
     * @param venueId 场馆 ID
     * @param userId 用户 ID
     * @return 已由并发请求创建的顾客档案，不存在时返回 null
     * @author gjm
     */
    VenueCustomer selectByVenueAndUserForUpdate(@Param("venueId") long venueId, @Param("userId") long userId);

    /**
     * 功能描述: 按姓名关键字、完整手机号或会员码查询场馆顾客列表。
     *
     * @param venueId 场馆 ID
     * @param keyword 查询关键字，为空时返回全部
     * @return 当前场馆顾客列表
     * @author gjm
     */
    List<VenueCustomerBO> selectVenueCustomers(@Param("venueId") long venueId, @Param("keyword") String keyword);

    /**
     * 功能描述: 按场馆和顾客档案 ID 查询详情。
     *
     * @param venueId 场馆 ID
     * @param customerId 顾客档案 ID
     * @return 顾客详情，不存在时返回 null
     * @author gjm
     */
    VenueCustomerBO selectVenueCustomer(@Param("venueId") long venueId, @Param("customerId") long customerId);

    /**
     * 功能描述: 通过会员码或完整手机号识别场馆顾客。
     *
     * @param venueId 场馆 ID
     * @param memberCode 会员码，与手机号二选一
     * @param phone 完整手机号，与会员码二选一
     * @return 最多两个匹配顾客，用于识别手机号歧义
     * @author gjm
     */
    List<VenueCustomerBO> selectByIdentity(@Param("venueId") long venueId,
                                           @Param("memberCode") String memberCode,
                                           @Param("phone") String phone);

    /**
     * 功能描述: 查询用户已有的全部场馆顾客关系。
     *
     * @param userId 用户 ID
     * @return 场馆顾客关系列表
     * @author gjm
     */
    List<VenueCustomerBO> selectUserMemberships(@Param("userId") long userId);

    /**
     * 功能描述: 查询用户在指定场馆的顾客关系。
     *
     * @param userId 用户 ID
     * @param venueId 场馆 ID
     * @return 场馆顾客关系，不存在时返回 null
     * @author gjm
     */
    VenueCustomerBO selectUserMembership(@Param("userId") long userId, @Param("venueId") long venueId);

    /**
     * 功能描述: 查询平台场馆级顾客会员数量汇总。
     *
     * @return 各场馆数量汇总
     * @author gjm
     */
    List<PlatformCustomerSummaryBO> selectPlatformSummaries();
}
