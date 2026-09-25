package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.bo.BalanceSummaryBO;
import com.sportsbooking.admin.base.po.MemberBalanceLot;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 会员余额批次数据访问。 */
public interface MemberBalanceLotMapper extends BaseMapper<MemberBalanceLot> {
    List<MemberBalanceLot> selectSpendableForUpdate(@Param("venueCustomerId") long venueCustomerId,
                                                     @Param("now") Instant now);
    MemberBalanceLot selectForUpdate(long id);
    List<MemberBalanceLot> selectByRechargeForUpdate(@Param("rechargeId") long rechargeId);
    BalanceSummaryBO selectSummary(@Param("venueCustomerId") long venueCustomerId, @Param("now") Instant now);
    List<MemberBalanceLot> selectExpiringGiftLots(@Param("remindBefore") Instant remindBefore,
                                                  @Param("now") Instant now);
    List<MemberBalanceLot> selectExpiredGiftLots(@Param("now") Instant now);
}
