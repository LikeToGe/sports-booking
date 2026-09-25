package com.sportsbooking.admin.biz.constants;

import java.time.Duration;

/**
 * 功能描述: Redis 业务锁目录，统一锁前缀、租期和资源语义。
 * 创建时间: 2026/09/14
 *
 * @author gjm
 */
public enum RedisLockType {
    SESSION_SLOT("session-slot:", Duration.ofSeconds(30), "具体场次"),
    BOOKING("booking:", Duration.ofSeconds(30), "预约"),
    MEMBER_BALANCE("member-balance:", Duration.ofSeconds(30), "会员余额"),
    RECHARGE("recharge:", Duration.ofSeconds(30), "充值");

    public static final String ROOT_PREFIX = "sports-booking:lock:";

    private final String businessPrefix;
    private final Duration leaseTime;
    private final String description;

    RedisLockType(String businessPrefix, Duration leaseTime, String description) {
        this.businessPrefix = businessPrefix;
        this.leaseTime = leaseTime;
        this.description = description;
    }

    public String getBusinessPrefix() {
        return businessPrefix;
    }

    public Duration getLeaseTime() {
        return leaseTime;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 功能描述: 生成业务资源的完整 Redis key。
     *
     * @param resourceId 业务资源 ID
     * @return 完整 Redis key
     * @author gjm
     */
    public String key(long resourceId) {
        return ROOT_PREFIX + businessPrefix + resourceId;
    }
}
