package com.sportsbooking.admin.review;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.ReviewTagMapper;
import com.sportsbooking.admin.base.po.ReviewTag;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.ReviewTagRequest;
import com.sportsbooking.admin.biz.service.impl.ReviewTagServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 固定标签只能关联启用的平台运动类型。 */
class ReviewTagServiceImplTest {
    private final ReviewTagMapper mapper = Mockito.mock(ReviewTagMapper.class);
    private final ReviewTagServiceImpl service = new ReviewTagServiceImpl(mapper, true);

    @Test
    void createsTagWithEligibleSports() {
        when(mapper.selectActivePlatformSportIds(List.of(1L, 2L))).thenReturn(List.of(1L, 2L));
        Mockito.doAnswer(call -> {
            ((ReviewTag) call.getArgument(0)).id = 8L;
            return 1;
        }).when(mapper).insert(any(ReviewTag.class));

        service.create(new ReviewTagRequest("场地整洁", List.of(1L, 2L), "ACTIVE"));

        verify(mapper).insertSports(8L, List.of(1L, 2L));
    }

    @Test
    void rejectsVenueSpecificOrDisabledSport() {
        when(mapper.selectActivePlatformSportIds(List.of(1L, 2L))).thenReturn(List.of(1L));

        assertThatThrownBy(() -> service.create(new ReviewTagRequest("场地整洁", List.of(1L, 2L), "ACTIVE")))
                .isInstanceOf(ApiException.class).hasMessage(ErrorCode.INVALID_ARGUMENT.getDescription());
    }
}
