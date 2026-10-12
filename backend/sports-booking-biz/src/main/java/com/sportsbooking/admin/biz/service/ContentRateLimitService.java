package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.biz.exception.ApiException;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 固定时间桶内原子计数与过期，Redis 异常不放行内容写入。 */
@Service
public class ContentRateLimitService {
    private static final DefaultRedisScript<Long> COUNT = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]); "
            + "if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end; return n", Long.class);
    private final StringRedisTemplate redis;

    public ContentRateLimitService(StringRedisTemplate redis) { this.redis = redis; }

    public void post(long userId) { check(userId, "post", 5, Duration.ofDays(1)); }
    public void comment(long userId) { check(userId, "comment", 30, Duration.ofHours(1)); }
    public void like(long userId) { check(userId, "like", 120, Duration.ofHours(1)); }

    private void check(long userId, String kind, int limit, Duration window) {
        long seconds = window.getSeconds();
        long bucket = window.equals(Duration.ofDays(1))
                ? java.time.LocalDate.now(com.sportsbooking.admin.biz.constants.BusinessConstants.BUSINESS_ZONE).toEpochDay()
                : java.time.Instant.now().getEpochSecond() / seconds;
        Long count;
        try {
            count = redis.execute(COUNT, List.of("sports-booking:content-rate:" + kind + ":" + userId + ":" + bucket),
                    Long.toString(seconds));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.INTERNAL_ERROR);
        }
        if (count == null || count > limit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.CONTENT_RATE_LIMIT);
        }
    }
}
