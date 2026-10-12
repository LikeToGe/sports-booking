package com.sportsbooking.admin.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.biz.constants.RedisLockType;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.RedisLockService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 功能描述: Redis 业务锁目录、并发占用、故障关闭与事务释放测试。
 * 创建时间: 2026/09/14
 *
 * @author gjm
 */
@SuppressWarnings("unchecked")
class RedisLockServiceTest {

    @Test
    void exposesFixedPrefixAndThirtySecondLease() {
        assertThat(RedisLockType.ROOT_PREFIX).isEqualTo("sports-booking:lock:");
        assertThat(RedisLockType.values()).containsExactly(
                RedisLockType.SESSION_SLOT, RedisLockType.BOOKING,
                RedisLockType.MEMBER_BALANCE, RedisLockType.RECHARGE, RedisLockType.RECRUITMENT);
        assertThat(RedisLockType.RECRUITMENT.key(9L)).isEqualTo("sports-booking:lock:recruitment:9");
        assertThat(RedisLockType.SESSION_SLOT.getBusinessPrefix()).isEqualTo("session-slot:");
        assertThat(RedisLockType.BOOKING.getBusinessPrefix()).isEqualTo("booking:");
        assertThat(RedisLockType.values()).allSatisfy(type -> {
            assertThat(type.getLeaseTime()).isEqualTo(Duration.ofSeconds(30));
            assertThat(type.getDescription()).isNotBlank();
        });
    }

    @Test
    void rejectsOccupiedLockWithoutRunningAction() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(30)))).thenReturn(false);
        AtomicBoolean called = new AtomicBoolean();

        assertThatThrownBy(() -> new RedisLockService(redisTemplate).execute(
                List.of(RedisLockType.SESSION_SLOT.key(1L)), () -> called.getAndSet(true)))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.OPERATION_IN_PROGRESS.getDescription());

        assertThat(called).isFalse();
    }

    @Test
    void serializesTwoActionsForTheSameSessionSlot() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        Map<String, String> locks = new ConcurrentHashMap<>();
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation ->
                locks.putIfAbsent(invocation.getArgument(0), invocation.getArgument(1)) == null);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenAnswer(invocation -> {
            List<String> keys = invocation.getArgument(1);
            Object releaseArgument = invocation.getArgument(2);
            Object ownerToken = releaseArgument instanceof Object[] arguments ? arguments[0] : releaseArgument;
            return locks.remove(keys.get(0), ownerToken) ? 1L : 0L;
        });
        RedisLockService firstService = new RedisLockService(redisTemplate);
        RedisLockService secondService = new RedisLockService(redisTemplate);
        String key = RedisLockType.SESSION_SLOT.key(1L);
        AtomicBoolean secondCalled = new AtomicBoolean();

        assertThat(firstService.execute(List.of(key), () -> {
            assertThatThrownBy(() -> secondService.execute(List.of(key), () -> secondCalled.getAndSet(true)))
                    .isInstanceOfSatisfying(ApiException.class,
                            exception -> assertThat(exception.getCode())
                                    .isEqualTo(ErrorCode.OPERATION_IN_PROGRESS));
            assertThat(secondCalled).isFalse();
            return "first";
        })).isEqualTo("first");
        assertThat(locks).isEmpty();

        assertThat(secondService.execute(List.of(key), () -> {
            secondCalled.set(true);
            return "second";
        })).isEqualTo("second");
        assertThat(secondCalled).isTrue();
    }

    @Test
    void failsClosedWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> new RedisLockService(redisTemplate).execute(
                List.of(RedisLockType.BOOKING.key(1L)), () -> true))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void sortsDeduplicatesAndCleansUpWhenLaterLockIsBusy() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true, false);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
        String first = RedisLockType.BOOKING.key(1L);
        String second = RedisLockType.SESSION_SLOT.key(2L);

        assertThatThrownBy(() -> new RedisLockService(redisTemplate).execute(
                List.of(second, first, second), () -> true))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.OPERATION_IN_PROGRESS));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(values, org.mockito.Mockito.times(2))
                .setIfAbsent(keyCaptor.capture(), anyString(), eq(Duration.ofSeconds(30)));
        assertThat(keyCaptor.getAllValues()).containsExactly(first, second);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(first)), any());
    }

    @Test
    void releasesMultipleLocksInReverseOrder() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
        String first = RedisLockType.BOOKING.key(1L);
        String second = RedisLockType.SESSION_SLOT.key(2L);

        new RedisLockService(redisTemplate).execute(List.of(second, first), () -> true);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, org.mockito.Mockito.times(2))
                .execute(any(RedisScript.class), keysCaptor.capture(), any());
        assertThat(keysCaptor.getAllValues()).containsExactly(List.of(second), List.of(first));
    }

    @Test
    void luaReleaseUsesOwnerTokenAndDoesNotDeleteForAnotherOwner() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(0L);

        new RedisLockService(redisTemplate).execute(List.of(RedisLockType.BOOKING.key(7L)), () -> true);

        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(anyString(), ownerCaptor.capture(), eq(Duration.ofSeconds(30)));
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(scriptCaptor.capture(), anyList(), argsCaptor.capture());
        assertThat(scriptCaptor.getValue().getScriptAsString()).contains("redis.call('get'", "redis.call('del'");
        assertThat(argsCaptor.getValue()).containsExactly(ownerCaptor.getValue());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void releasesAfterCommitAndRollback() {
        assertReleasedAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        assertReleasedAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    private void assertReleasedAfterCompletion(int status) {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
        TransactionSynchronizationManager.initSynchronization();
        try {
            new RedisLockService(redisTemplate).execute(List.of(RedisLockType.BOOKING.key(status)), () -> true);
            verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(status));

            verify(redisTemplate).execute(any(RedisScript.class), anyList(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
