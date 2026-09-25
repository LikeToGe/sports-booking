package com.sportsbooking.admin.base.bo;

import com.sportsbooking.admin.base.enums.BalanceBusinessType;
import com.sportsbooking.admin.base.enums.BalanceFlowDirection;
import com.sportsbooking.admin.base.enums.BalanceType;
import java.time.LocalDateTime;
import lombok.Data;

/** 余额流水及批次类型查询投影。 */
@Data
public class MemberBalanceFlowBO {
    private Long id;
    private Long lotId;
    private BalanceType balanceType;
    private BalanceBusinessType businessType;
    private BalanceFlowDirection direction;
    private Integer amountFen;
    private Long bookingId;
    private Long rechargeId;
    private LocalDateTime createdAt;
}
