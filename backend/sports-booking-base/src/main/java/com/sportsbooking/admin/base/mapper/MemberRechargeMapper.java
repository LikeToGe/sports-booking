package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.MemberRecharge;
import java.util.List;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** 会员充值单数据访问。 */
public interface MemberRechargeMapper extends BaseMapper<MemberRecharge> {
    MemberRecharge selectForUpdate(long id);
    MemberRecharge selectByClientKey(@Param("venueCustomerId") long venueCustomerId, @Param("key") String key);
    List<MemberRecharge> selectByCustomer(@Param("venueCustomerId") long venueCustomerId);
    List<Long> selectPendingIds();
    MemberRecharge selectByBusinessNo(String businessNo);
}
