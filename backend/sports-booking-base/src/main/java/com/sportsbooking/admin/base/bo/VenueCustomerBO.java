package com.sportsbooking.admin.base.bo;

import com.sportsbooking.admin.base.enums.VenueCustomerStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 场馆顾客查询投影。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueCustomerBO {
    /** 兼容 P1 查询测试的无余额构造。 */
    public VenueCustomerBO(Long customerId, Long venueId, String venueName, String contactName, String phone,
                           VenueCustomerStatus status, String memberCode, Instant activatedAt,
                           LocalDateTime createdAt) {
        this(customerId, venueId, venueName, contactName, phone, status, memberCode, activatedAt, createdAt,
                0L, 0L, 0L, null);
    }
    /** 顾客档案 ID。 */
    private Long customerId;
    /** 场馆 ID。 */
    private Long venueId;
    /** 场馆名称。 */
    private String venueName;
    /** 联系人姓名。 */
    private String contactName;
    /** 完整手机号。 */
    private String phone;
    /** 顾客或会员状态。 */
    private VenueCustomerStatus status;
    /** 场馆内会员码。 */
    private String memberCode;
    /** 会员激活时间。 */
    private Instant activatedAt;
    /** 建档时间。 */
    private LocalDateTime createdAt;

    /** 实付可用余额。 */
    private Long paidBalanceFen;
    /** 赠送可用余额。 */
    private Long giftBalanceFen;
    /** 最近到期赠送额。 */
    private Long expiringGiftBalanceFen;
    /** 最近赠送到期时间。 */
    private Instant nextGiftExpiresAt;
}
