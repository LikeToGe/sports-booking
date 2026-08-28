package com.sportsbooking.admin.auth;

import com.sportsbooking.admin.api.ApiException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RedisAvailabilityService {
    private final StringRedisTemplate redisTemplate;

    public RedisAvailabilityService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void requireAvailable() {
        try {
            redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, 0, "Redis当前不可用，请稍后重试");
        }
    }
}
