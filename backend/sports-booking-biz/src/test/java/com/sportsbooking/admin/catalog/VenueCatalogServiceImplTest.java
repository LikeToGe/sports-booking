package com.sportsbooking.admin.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.CourtMapper;
import com.sportsbooking.admin.base.mapper.SportTypeMapper;
import com.sportsbooking.admin.base.mapper.VenueMapper;
import com.sportsbooking.admin.base.po.SportType;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.service.impl.VenueCatalogServiceImpl;
import org.junit.jupiter.api.Test;

/**
 * 功能描述: 平台运动类型启停边界单元测试。
 * 创建时间: 2026/09/05
 *
 * @author gjm
 */
class VenueCatalogServiceImplTest {

    @Test
    void disablesGlobalSport() {
        SportTypeMapper sportTypeMapper = mock(SportTypeMapper.class);
        VenueCatalogServiceImpl service = service(sportTypeMapper);
        SportType sport = globalSport();
        when(sportTypeMapper.selectById(1L)).thenReturn(sport);

        SportType result = service.updateGlobalSportStatus(1L, EnableStatus.DISABLED);

        assertThat(result.status).isEqualTo(EnableStatus.DISABLED);
        verify(sportTypeMapper).updateById(sport);
    }

    @Test
    void rejectsVenueOnlySport() {
        SportTypeMapper sportTypeMapper = mock(SportTypeMapper.class);
        VenueCatalogServiceImpl service = service(sportTypeMapper);
        SportType sport = globalSport();
        sport.ownerVenueId = 2L;
        when(sportTypeMapper.selectById(1L)).thenReturn(sport);

        assertThatThrownBy(() -> service.updateGlobalSportStatus(1L, EnableStatus.DISABLED))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.NOT_FOUND.getDescription());
        verify(sportTypeMapper, never()).updateById(sport);
    }

    private VenueCatalogServiceImpl service(SportTypeMapper sportTypeMapper) {
        return new VenueCatalogServiceImpl(mock(VenueService.class), mock(VenueMapper.class), sportTypeMapper,
                mock(CourtMapper.class));
    }

    private SportType globalSport() {
        SportType sport = new SportType();
        sport.id = 1L;
        sport.status = EnableStatus.ACTIVE;
        return sport;
    }
}
