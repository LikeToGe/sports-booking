package com.sportsbooking.admin.community;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sportsbooking.admin.base.mapper.UserActionEventMapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.UserActionEventService;
import org.junit.jupiter.api.Test;

/** 找搭子发现导航只允许记录动态目标。 */
class RecruitmentDiscoveryEventTest {
    @Test
    void recordsPostNavigationAndRejectsArbitraryTarget() {
        UserActionEventMapper mapper = mock(UserActionEventMapper.class);
        UserActionEventService service = new UserActionEventService(mapper);

        service.navigation(11L, "RECRUITMENT_DISCOVERY_OPEN", "POST", 9L);
        verify(mapper).insert(11L, "RECRUITMENT_DISCOVERY_OPEN", "POST", 9L);
        assertThatThrownBy(() -> service.navigation(11L, "RECRUITMENT_DISCOVERY_OPEN", "BOOKING", 3L))
                .isInstanceOf(ApiException.class);
        verifyNoMoreInteractions(mapper);
    }
}
