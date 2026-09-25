package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.biz.constants.RedisLockType;
import com.sportsbooking.admin.biz.exception.ApiException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 功能描述: Redis 业务锁的获取、事务后释放和多资源稳定排序。
 * 创建时间: 2026/09/14
 *
 * @author gjm
 */
@Service
public class RedisLockService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisLockService.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 功能描述: 按完整 key 排序并去重加锁，在事务提交或回滚后逆序释放。
     *
     * @param keys 完整业务锁 key
     * @param action 受锁和事务保护的业务操作
     * @return 业务操作结果
     * @author gjm
     */
    @Transactional
    public <T> T execute(List<String> keys, Supplier<T> action) {
        List<String> orderedKeys = new ArrayList<>(new TreeSet<>(keys));
        String ownerToken = UUID.randomUUID().toString();
        List<String> acquiredKeys = acquire(orderedKeys, ownerToken);
        boolean releaseAfterTransaction = false;
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        try {
                            release(acquiredKeys, ownerToken);
                        } catch (ApiException exception) {
                            LOGGER.warn("Redis 业务锁事务后释放失败，将等待租期兜底: {}", acquiredKeys, exception);
                        }
                    }
                });
                releaseAfterTransaction = true;
            }
            return action.get();
        } finally {
            if (!releaseAfterTransaction) {
                release(acquiredKeys, ownerToken);
            }
        }
    }

    private List<String> acquire(List<String> orderedKeys, String ownerToken) {
        List<String> acquiredKeys = new ArrayList<>();
        for (String key : orderedKeys) {
            try {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                        key, ownerToken, RedisLockType.SESSION_SLOT.getLeaseTime());
                if (!Boolean.TRUE.equals(acquired)) {
                    release(acquiredKeys, ownerToken);
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
                }
                acquiredKeys.add(key);
            } catch (ApiException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                releaseAfterAcquireFailure(acquiredKeys, ownerToken);
                throw unavailable();
            }
        }
        return acquiredKeys;
    }

    private void releaseAfterAcquireFailure(List<String> acquiredKeys, String ownerToken) {
        try {
            release(acquiredKeys, ownerToken);
        } catch (ApiException exception) {
            LOGGER.warn("Redis 业务锁获取异常后的清理失败，将等待租期兜底: {}", acquiredKeys, exception);
        }
    }

    private void release(List<String> acquiredKeys, String ownerToken) {
        try {
            for (int index = acquiredKeys.size() - 1; index >= 0; index--) {
                redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(acquiredKeys.get(index)), ownerToken);
            }
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.INTERNAL_ERROR);
    }
}
