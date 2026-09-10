package com.sportsbooking.admin.biz.constants;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Set;

/**
 * 功能描述: 跨模块共享且不会按场馆变化的业务常量。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public final class BusinessConstants {
    public static final String BUSINESS_ZONE_ID = "Asia/Shanghai";
    public static final ZoneId BUSINESS_ZONE = ZoneId.of(BUSINESS_ZONE_ID);
    public static final String DEFAULT_CITY = "郑州市";
    public static final Duration EMPLOYEE_BINDING_TTL = Duration.ofMinutes(15);
    public static final Duration VERIFICATION_EARLY_WINDOW = Duration.ofMinutes(30);
    public static final Duration BOOKING_CREATE_LIMIT_WINDOW = Duration.ofMinutes(10);
    public static final int QR_VALID_MINUTES = 1;
    public static final int SHORT_CODE_BOUND = 1_000_000;
    public static final int BOOKING_NO_RANDOM_LENGTH = 18;
    public static final Set<String> COVER_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private BusinessConstants() {
    }
}
