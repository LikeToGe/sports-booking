package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.bo.MemberBalanceFlowBO;
import com.sportsbooking.admin.base.po.MemberBalanceFlow;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 不可变余额流水数据访问。 */
public interface MemberBalanceFlowMapper extends BaseMapper<MemberBalanceFlow> {
    List<MemberBalanceFlowBO> selectByCustomer(@Param("venueCustomerId") long venueCustomerId);
    int countConsumedByRecharge(@Param("rechargeId") long rechargeId);
}
