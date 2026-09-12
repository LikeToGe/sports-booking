package com.sportsbooking.admin.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.CourtMapper;
import com.sportsbooking.admin.base.mapper.SportTypeMapper;
import com.sportsbooking.admin.base.mapper.VenueMapper;
import com.sportsbooking.admin.base.po.SportType;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.service.impl.VenueCatalogServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.List;

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

    @Test
    void renamesGlobalSportAndNormalizedName() {
        SportTypeMapper sportTypeMapper = mock(SportTypeMapper.class);
        VenueCatalogServiceImpl service = service(sportTypeMapper);
        SportType sport = globalSport();
        when(sportTypeMapper.selectById(1L)).thenReturn(sport);

        SportType result = service.updateGlobalSportName(1L, " 沙滩 排球 ");

        assertThat(result.name).isEqualTo("沙滩 排球");
        assertThat(result.normalizedName).isEqualTo("沙滩排球");
        verify(sportTypeMapper).updateById(sport);
    }

    @Test
    void rejectsRenamingSportFromAnotherVenue() {
        SportTypeMapper sportTypeMapper = mock(SportTypeMapper.class);
        VenueCatalogServiceImpl service = service(sportTypeMapper);
        SportType sport = globalSport();
        sport.ownerVenueId = 3L;
        when(sportTypeMapper.selectById(1L)).thenReturn(sport);

        assertThatThrownBy(() -> service.updateVenueSportName(2L, 1L, "排球"))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.NOT_FOUND.getDescription());
        verify(sportTypeMapper, never()).updateById(sport);
    }

    @Test
    void preservesExistingDisabledSportWhenEditingCourt() {
        SportTypeMapper sportTypeMapper = mock(SportTypeMapper.class);
        CourtMapper courtMapper = mock(CourtMapper.class);
        VenueCatalogServiceImpl service = new VenueCatalogServiceImpl(
                mock(VenueService.class), mock(VenueMapper.class), sportTypeMapper, courtMapper);
        Court court = new Court();
        court.id = 5L;
        court.venueId = 2L;
        when(courtMapper.selectById(5L)).thenReturn(court);
        when(courtMapper.selectSportIds(5L)).thenReturn(List.of(1L));
        when(sportTypeMapper.selectCount(any())).thenReturn(1L);

        service.updateCourt(2L, 5L, "一号场", List.of(1L), EnableStatus.ACTIVE);

        verify(courtMapper).updateById(court);
        verify(courtMapper).replaceSports(5L, List.of(1L));
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
