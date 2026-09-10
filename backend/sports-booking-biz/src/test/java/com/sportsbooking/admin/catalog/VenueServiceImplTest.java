package com.sportsbooking.admin.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.mapper.AdminAccountMapper;
import com.sportsbooking.admin.base.mapper.CourtMapper;
import com.sportsbooking.admin.base.mapper.VenueMapper;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.impl.VenueServiceImpl;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 功能描述: 场馆服务费率配置单元测试。
 * 创建时间: 2026/09/08
 *
 * @author gjm
 */
class VenueServiceImplTest {
    private final VenueMapper venueMapper = mock(VenueMapper.class);
    private final VenueServiceImpl service = new VenueServiceImpl(venueMapper,
            mock(AdminAccountMapper.class), mock(CourtMapper.class), mock(BCryptPasswordEncoder.class), ".");

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
}
