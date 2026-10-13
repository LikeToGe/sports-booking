package com.sportsbooking.admin.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

import com.sportsbooking.admin.base.mapper.AdminAccountMapper;
import com.sportsbooking.admin.base.mapper.CourtMapper;
import com.sportsbooking.admin.base.mapper.VenueMapper;
import com.sportsbooking.admin.base.po.AdminAccount;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.VenueSaveRequest;
import com.sportsbooking.admin.biz.service.impl.VenueServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 功能描述: 场馆服务费率配置单元测试。
 * 创建时间: 2026/09/08
 *
 * @author gjm
 */
class VenueServiceImplTest {
    private final VenueMapper venueMapper = mock(VenueMapper.class);
    private final AdminAccountMapper adminAccountMapper = mock(AdminAccountMapper.class);
    private final BCryptPasswordEncoder passwordEncoder = mock(BCryptPasswordEncoder.class);
    private final VenueServiceImpl service = new VenueServiceImpl(venueMapper,
            adminAccountMapper, mock(CourtMapper.class), passwordEncoder, ".");

    @Test
    void carriesDistrictIntoSlotSearchAndKeepsUnfilteredCallsCompatible() {
        LocalDate date = LocalDate.of(2026, 12, 1);
        ArgumentCaptor<String> district = ArgumentCaptor.forClass(String.class);

        service.searchSlots(" 球馆 ", null, date, " 金水区 ", LocalTime.of(19, 0),
                LocalTime.of(21, 0), 100, 10000, 1);
        verify(venueMapper).selectMatchingSlots(eq("球馆"), eq(null), eq(date), district.capture(),
                any(), any(), eq(100), eq(10000), any(), any(), eq(0));
        assertThat(district.getValue()).isEqualTo("金水区");

        service.searchSlots(null, null, date, "  ", null, null, null, null, 1);
        verify(venueMapper).selectMatchingSlots(eq(null), eq(null), eq(date), eq(null), any(), any(),
                eq(null), eq(null), any(), any(), eq(0));
    }

    @Test
    void appliesAndClearsOptionalDistrictWithoutGuessingFromAddress() {
        Venue venue = new Venue();
        venue.id = 1L;
        when(venueMapper.selectById(1L)).thenReturn(venue);
        VenueSaveRequest body = new VenueSaveRequest();
        body.setName("测试场馆");
        body.setCity("郑州市");
        body.setDistrict(" 金水区 ");
        body.setAddress("金水区测试路");
        body.setPhone("00000000000");

        service.updateVenue(1L, body);
        assertThat(venue.district).isEqualTo("金水区");

        body.setDistrict(" ");
        service.updateVenue(1L, body);
        assertThat(venue.district).isNull();
        assertThat(venue.address).isEqualTo("金水区测试路");
    }

    @Test
    void savesExactServiceFeeRateAsBasisPoints() {
        Venue venue = new Venue();
        venue.id = 1L;
        when(venueMapper.selectById(1L)).thenReturn(venue);

        Venue updated = service.updateServiceFeeRate(1L, new BigDecimal("5.25"));

        assertThat(updated.serviceFeeRateBasisPoints).isEqualTo(525);
        verify(venueMapper).updateById(venue);
    }

    @Test
    void rejectsOutOfRangeOrOverPreciseServiceFeeRate() {
        assertThatThrownBy(() -> service.updateServiceFeeRate(1L, new BigDecimal("30.01")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.updateServiceFeeRate(1L, new BigDecimal("5.251")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void resetsVenueAdminPasswordWithBcryptHash() {
        AdminAccount admin = new AdminAccount();
        admin.id = 9L;
        when(adminAccountMapper.selectOne(any())).thenReturn(admin);
        when(passwordEncoder.encode("new-password")).thenReturn("bcrypt-hash");

        service.resetAdminPassword(1L, "new-password");

        assertThat(admin.passwordHash).isEqualTo("bcrypt-hash");
        verify(adminAccountMapper).updateById(admin);
    }

    @Test
    void listsFirstVenueAdminUsername() {
        AdminAccount first = new AdminAccount();
        first.venueId = 1L;
        first.username = "venue-admin";
        AdminAccount second = new AdminAccount();
        second.venueId = 1L;
        second.username = "later-admin";
        when(adminAccountMapper.selectList(any())).thenReturn(List.of(first, second));

        assertThat(service.listVenueAdminUsernames()).containsEntry(1L, "venue-admin");
    }

    @Test
    void updatesVenueAdminAndKeepsBlankPasswordUnchanged() {
        AdminAccount admin = new AdminAccount();
        admin.id = 9L;
        admin.passwordHash = "old-hash";
        when(adminAccountMapper.selectOne(any())).thenReturn(admin);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service.updateVenueAdmin(1L, " new-username ", "");
        assertThat(admin.username).isEqualTo("new-username");
        assertThat(admin.passwordHash).isEqualTo("old-hash");

        service.updateVenueAdmin(1L, "new-username", " new-password ");
        assertThat(admin.passwordHash).isEqualTo("new-hash");
        verify(adminAccountMapper, times(2)).updateById(admin);
    }
}
