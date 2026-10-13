package com.sportsbooking.admin.favourite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sportsbooking.admin.base.bo.FavouriteVenueBO;
import com.sportsbooking.admin.base.enums.VenueStatus;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.mapper.FavouriteVenueMapper;
import com.sportsbooking.admin.base.mapper.VenueMapper;
import com.sportsbooking.admin.base.mapper.UserAccountMapper;
import com.sportsbooking.admin.base.po.UserAccount;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.impl.FavouriteVenueServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;

class FavouriteVenueServiceImplTest {
    private final FavouriteVenueMapper favourites = mock(FavouriteVenueMapper.class);
    private final VenueMapper venues = mock(VenueMapper.class);
    private final UserAccountMapper users = mock(UserAccountMapper.class);
    private final FavouriteVenueServiceImpl service = new FavouriteVenueServiceImpl(favourites, venues, users);

    @Test
    void addsOnlyExistingNonDraftVenueAndKeepsDuplicateIdempotent() {
        UserAccount user = new UserAccount();
        user.status = EnableStatus.ACTIVE;
        when(users.selectForUpdate(11L)).thenReturn(user);
        Venue venue = new Venue();
        venue.status = VenueStatus.ACTIVE;
        when(venues.selectById(7L)).thenReturn(venue);

        service.add(11L, 7L);
        service.add(11L, 7L);

        verify(favourites, times(2)).insertIdempotent(11L, 7L);
        verify(users, times(2)).selectForUpdate(11L);
        verify(venues, times(2)).selectById(7L);
    }

    @Test
    void rejectsMissingAndDraftVenueWithoutWritingPreference() {
        UserAccount user = new UserAccount();
        user.status = EnableStatus.ACTIVE;
        when(users.selectForUpdate(11L)).thenReturn(user);
        when(venues.selectById(7L)).thenReturn(null);
        assertThatThrownBy(() -> service.add(11L, 7L)).isInstanceOf(ApiException.class);
        Venue draft = new Venue();
        draft.status = VenueStatus.DRAFT;
        when(venues.selectById(8L)).thenReturn(draft);
        assertThatThrownBy(() -> service.add(11L, 8L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(favourites);
    }

    @Test
    void rejectsDeletedUserBeforeReadingVenueOrWritingPreference() {
        UserAccount user = new UserAccount();
        user.status = EnableStatus.DISABLED;
        when(users.selectForUpdate(11L)).thenReturn(user);
        assertThatThrownBy(() -> service.add(11L, 7L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(venues, favourites);
    }

    @Test
    void mapsOwnRowsAndClearsOnlyRequestedUser() {
        FavouriteVenueBO row = new FavouriteVenueBO();
        row.setId(7L);
        row.setName("测试馆");
        row.setStatus("SUSPENDED");
        when(favourites.selectFavourites(11L)).thenReturn(List.of(row));
        assertThat(service.favourites(11L)).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(7L);
            assertThat(item.getStatus()).isEqualTo("SUSPENDED");
        });
        service.clear(11L);
        verify(favourites).deleteByUser(11L);
    }
}
