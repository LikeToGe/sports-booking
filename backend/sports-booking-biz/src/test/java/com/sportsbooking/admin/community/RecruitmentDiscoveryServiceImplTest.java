package com.sportsbooking.admin.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sportsbooking.admin.base.bo.RecruitmentDiscoveryBO;
import com.sportsbooking.admin.base.mapper.RecruitmentDiscoveryMapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.ContentGovernanceService;
import com.sportsbooking.admin.biz.service.ContentAgreementService;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.biz.service.impl.RecruitmentDiscoveryServiceImpl;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecruitmentDiscoveryServiceImplTest {
    private final RecruitmentDiscoveryMapper mapper = mock(RecruitmentDiscoveryMapper.class);
    private final ContentGovernanceService governance = mock(ContentGovernanceService.class);
    private final ContentAgreementService agreement = mock(ContentAgreementService.class);
    private final RecruitmentDiscoveryServiceImpl service = new RecruitmentDiscoveryServiceImpl(mapper, governance, agreement);

    @Test
    void returnsRemainingCapacityFromApprovedMembers() {
        RecruitmentDiscoveryBO row = new RecruitmentDiscoveryBO();
        row.setRecruitmentId(7L); row.setPostId(8L); row.setMaxMembers(4); row.setApprovedCount(2);
        row.setLevel("历史说明"); row.setStatus("RECRUITING");
        Instant dayStart = LocalDate.of(2026, 10, 13).atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
        Instant nextDayStart = LocalDate.of(2026, 10, 14).atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
        when(mapper.select(eq(9L), eq(dayStart), eq(nextDayStart), isNull(), isNull(), isNull(), eq(1), eq(0), eq(20)))
                .thenReturn(List.of(row));
        var result = service.list(9L, LocalDate.of(2026, 10, 13), null, null, null, 1, 1, 20);
        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getRemainingCount()).isEqualTo(2);
            assertThat(item.getLevelTag()).isNull();
            assertThat(java.util.Arrays.stream(item.getClass().getDeclaredFields()).map(java.lang.reflect.Field::getName).toList())
                    .doesNotContain("bookingId");
        });
        verify(governance).requireAllowed(9L, "RECRUITMENT");
        verify(agreement).requireAccepted(9L);
    }

    @Test
    void rejectsUnknownLevelAndBadPageBeforeQuery() {
        assertThatThrownBy(() -> service.list(9L, null, null, null, "基础", null, 1, 20))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.list(9L, null, null, null, null, null, 0, 20))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.list(9L, null, null, null, null, null, Integer.MAX_VALUE, 50))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
    }
}
