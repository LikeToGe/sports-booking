package com.sportsbooking.admin.booking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Verification;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.VerificationMapper;
import com.sportsbooking.admin.biz.service.CredentialService;
import com.sportsbooking.admin.biz.service.impl.VerificationServiceImpl;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.VerificationMethod;
import com.sportsbooking.admin.base.po.Employee;
import com.sportsbooking.admin.biz.service.EmployeeService;
import com.sportsbooking.admin.biz.service.RedisLockService;
import java.time.Instant;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 功能描述: 核销场馆边界和幂等状态单元测试。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
class VerificationServiceImplTest {
    private final BookingMapper bookingMapper = mock(BookingMapper.class);
    private final VerificationMapper verificationMapper = mock(VerificationMapper.class);
    private final CredentialService credentialService = mock(CredentialService.class);
    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final RedisLockService redisLockService = mock(RedisLockService.class);
    private VerificationServiceImpl service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new VerificationServiceImpl(
                bookingMapper, verificationMapper, credentialService, employeeService, redisLockService);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(redisLockService).execute(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        employee = new Employee();
        employee.id = 1L;
        employee.venueId = 2L;
        when(employeeService.getActiveEmployee(9L)).thenReturn(employee);
        when(credentialService.resolveQr("qr")).thenReturn(10L);
    }

    @Test
    void rejectsBookingFromAnotherVenue() {
        Booking booking = verifiableBooking();
        booking.venueId = 3L;
        when(bookingMapper.selectById(10L)).thenReturn(booking);
        when(bookingMapper.selectForUpdate(10L)).thenReturn(booking);

        assertThatThrownBy(() -> service.verify(9L, "qr", VerificationMethod.QR))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.NOT_VERIFIABLE.getDescription());
        verify(verificationMapper, never()).insert(org.mockito.ArgumentMatchers.any(Verification.class));
    }

    @Test
    void rejectsRepeatedVerification() {
        Booking booking = verifiableBooking();
        booking.venueId = 2L;
        booking.status = BookingStatus.COMPLETED;
        when(bookingMapper.selectById(10L)).thenReturn(booking);
        when(bookingMapper.selectForUpdate(10L)).thenReturn(booking);

        assertThatThrownBy(() -> service.verify(9L, "qr", VerificationMethod.QR))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.NOT_VERIFIABLE.getDescription());
        verify(verificationMapper, never()).insert(org.mockito.ArgumentMatchers.any(Verification.class));
    }

    @Test
    void rejectsOfflineBookingCredential() {
        Booking booking = verifiableBooking();
        booking.venueId = 2L;
        booking.source = BookingSource.OFFLINE;
        when(bookingMapper.selectById(10L)).thenReturn(booking);

        assertThatThrownBy(() -> service.verify(9L, "qr", VerificationMethod.QR))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.NOT_VERIFIABLE.getDescription());
        verify(verificationMapper, never()).insert(org.mockito.ArgumentMatchers.any(Verification.class));
    }

    private Booking verifiableBooking() {
        Booking booking = new Booking();
        booking.id = 10L;
        booking.source = BookingSource.ONLINE;
        booking.status = BookingStatus.CONFIRMED;
        booking.sessionSlotId = 20L;
        booking.startAt = Instant.now().minusSeconds(60);
        booking.endAt = Instant.now().plusSeconds(3600);
        return booking;
    }
}
