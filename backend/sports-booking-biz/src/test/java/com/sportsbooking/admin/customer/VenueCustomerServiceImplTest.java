package com.sportsbooking.admin.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.bo.PlatformCustomerSummaryBO;
import com.sportsbooking.admin.base.bo.VenueCustomerBO;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.VenueCustomerStatus;
import com.sportsbooking.admin.base.mapper.VenueCustomerMapper;
import com.sportsbooking.admin.base.po.VenueCustomer;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.PlatformCustomerSummaryVO;
import com.sportsbooking.admin.biz.service.CredentialService;
import com.sportsbooking.admin.biz.service.impl.VenueCustomerServiceImpl;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * 功能描述: 场馆顾客建档、归属和平台字段隔离单元测试。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
class VenueCustomerServiceImplTest {
    private final VenueCustomerMapper venueCustomerMapper = mock(VenueCustomerMapper.class);
    private final CredentialService credentialService = mock(CredentialService.class);
    private final com.sportsbooking.admin.base.mapper.UserAccountMapper users =
            mock(com.sportsbooking.admin.base.mapper.UserAccountMapper.class);
    private final VenueCustomerServiceImpl service = new VenueCustomerServiceImpl(
            venueCustomerMapper, credentialService, users);

    @org.junit.jupiter.api.BeforeEach
    void activeUser() {
        var user = new com.sportsbooking.admin.base.po.UserAccount();
        user.status = com.sportsbooking.admin.base.enums.EnableStatus.ACTIVE;
        when(users.selectForUpdate(org.mockito.ArgumentMatchers.anyLong())).thenReturn(user);
    }

    @Test
    void ensuresOneCustomerForRepeatedBookingsInSameVenue() {
        VenueCustomer stored = customer(7L, 1L, 9L);
        when(venueCustomerMapper.selectByVenueAndUser(1L, 9L)).thenReturn(null, stored);
        doAnswer(invocation -> {
            VenueCustomer inserted = invocation.getArgument(0);
            inserted.id = 7L;
            return 1;
        }).when(venueCustomerMapper).insert(any(VenueCustomer.class));

        VenueCustomer first = service.ensureCustomer(1L, 9L);
        VenueCustomer second = service.ensureCustomer(1L, 9L);

        assertThat(first.id).isEqualTo(7L);
        assertThat(second.id).isEqualTo(7L);
        verify(venueCustomerMapper).insert(any(VenueCustomer.class));
    }

    @Test
    void createsIndependentCustomersForSameUserAcrossVenues() {
        AtomicLong ids = new AtomicLong(10L);
        when(venueCustomerMapper.selectByVenueAndUser(1L, 9L)).thenReturn(null);
        when(venueCustomerMapper.selectByVenueAndUser(2L, 9L)).thenReturn(null);
        doAnswer(invocation -> {
            VenueCustomer inserted = invocation.getArgument(0);
            inserted.id = ids.getAndIncrement();
            return 1;
        }).when(venueCustomerMapper).insert(any(VenueCustomer.class));

        VenueCustomer first = service.ensureCustomer(1L, 9L);
        VenueCustomer second = service.ensureCustomer(2L, 9L);

        assertThat(first.venueId).isEqualTo(1L);
        assertThat(second.venueId).isEqualTo(2L);
        assertThat(first.memberCode).isNotEqualTo(second.memberCode);
        verify(venueCustomerMapper, times(2)).insert(any(VenueCustomer.class));
    }

    @Test
    void returnsWinningCustomerAfterConcurrentUniqueConflict() {
        VenueCustomer winner = customer(7L, 1L, 9L);
        when(venueCustomerMapper.selectByVenueAndUser(1L, 9L)).thenReturn(null);
        when(venueCustomerMapper.insert(any(VenueCustomer.class)))
                .thenThrow(new DuplicateKeyException("uk_venue_customer_user"));
        when(venueCustomerMapper.selectByVenueAndUserForUpdate(1L, 9L)).thenReturn(winner);

        assertThat(service.ensureCustomer(1L, 9L)).isSameAs(winner);
        verify(venueCustomerMapper).selectByVenueAndUserForUpdate(1L, 9L);
    }

    @Test
    void enforcesVenueAndUserOwnershipAsNotFound() {
        when(venueCustomerMapper.selectVenueCustomer(2L, 7L)).thenReturn(null);
        when(venueCustomerMapper.selectUserMembership(10L, 2L)).thenReturn(null);

        assertNotFound(() -> service.getVenueCustomer(2L, 7L));
        assertNotFound(() -> service.getMembership(10L, 2L));
    }

    @Test
    void requiresAtLeastOneIdentificationValue() {
        assertInvalid(() -> service.identifyVenueCustomer(1L, null, null));

        VenueCustomerBO customer = customerView();
        when(venueCustomerMapper.selectByIdentity(1L, "ABC", null)).thenReturn(List.of(customer));
        assertThat(service.identifyVenueCustomer(1L, "abc", null).getCustomerId()).isEqualTo(7L);
        when(venueCustomerMapper.selectByIdentity(1L, "ABC", "13800000000")).thenReturn(List.of(customer));
        assertThat(service.identifyVenueCustomer(1L, "abc", "13800000000").getCustomerId()).isEqualTo(7L);
    }

    @Test
    void refusesAmbiguousPhoneIdentification() {
        when(venueCustomerMapper.selectByIdentity(1L, null, "13800000000"))
                .thenReturn(List.of(customerView(), customerView()));

        assertNotFound(() -> service.identifyVenueCustomer(1L, null, "13800000000"));
    }

    @Test
    void platformSummaryContainsNoPersonalFields() {
        when(venueCustomerMapper.selectPlatformSummaries())
                .thenReturn(List.of(new PlatformCustomerSummaryBO(1L, "测试场馆", 3L, 1L)));

        PlatformCustomerSummaryVO summary = service.listPlatformSummaries().get(0);
        assertThat(summary.getCustomerCount()).isEqualTo(3L);
        assertThat(summary.getMemberCount()).isEqualTo(1L);
        assertThat(Arrays.stream(PlatformCustomerSummaryVO.class.getDeclaredFields()).map(field -> field.getName()))
                .doesNotContain("contactName", "phone", "memberCode", "userId", "customerId");
    }

    @Test
    void membershipDetailReturnsQrAndEmptyP1Balances() {
        when(venueCustomerMapper.selectUserMembership(9L, 1L)).thenReturn(customerView());
        when(credentialService.createQrDataUrl("ABC")).thenReturn("data:image/png;base64,mock");

        var membership = service.getMembership(9L, 1L);

        assertThat(membership.getMemberQrDataUrl()).startsWith("data:image/png;base64,");
        assertThat(membership.getPaidBalanceFen()).isZero();
        assertThat(membership.getGiftBalanceFen()).isZero();
    }

    private VenueCustomer customer(long id, long venueId, long userId) {
        VenueCustomer customer = new VenueCustomer();
        customer.id = id;
        customer.venueId = venueId;
        customer.userId = userId;
        customer.status = VenueCustomerStatus.CUSTOMER;
        customer.memberCode = "ABC";
        return customer;
    }

    private VenueCustomerBO customerView() {
        return new VenueCustomerBO(7L, 1L, "测试场馆", "测试用户", "13800000000",
                VenueCustomerStatus.CUSTOMER, "ABC", null, null);
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }
}
