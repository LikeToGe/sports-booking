package com.sportsbooking.admin.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.bo.CustomerFollowupBO;
import com.sportsbooking.admin.base.bo.VenueCustomerTagBO;
import com.sportsbooking.admin.base.po.VenueCustomerTag;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.CustomerOperationsMapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.impl.CustomerOperationsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CustomerOperationsServiceImplTest {
    @Mock
    private CustomerOperationsMapper mapper;

    private CustomerOperationsServiceImpl service() {
        return new CustomerOperationsServiceImpl(mapper);
    }

    @Test
    void preservesTagNamesContainingCommasAndQuotes() {
        var row = new com.sportsbooking.admin.base.bo.CustomerOperationBO();
        row.setTagNames("[\"新手,低峰\",\"AA\\\"约球\\\"\"]");
        when(mapper.selectOperations(3L, null)).thenReturn(java.util.List.of(row));
        assertThat(service().listOperations(3L, null)).singleElement().satisfies(customer ->
                assertThat(customer.getTags()).containsExactly("AA\"约球\"", "新手,低峰"));
    }

    @Test
    void storesVenueAdministratorInAccountColumnWithoutEmployeeId() {
        VenueCustomerTagBO saved = new VenueCustomerTagBO();
        saved.setId(55L);
        saved.setName("常约顾客");
        saved.setCreatedByName("场馆管理员");
        doAnswer(invocation -> {
            ((VenueCustomerTag) invocation.getArgument(0)).id = 55L;
            return 1;
        }).when(mapper).insertTag(any(VenueCustomerTag.class));
        when(mapper.selectTag(3L, 55L)).thenReturn(saved);

        service().createTag(3L, 17L, null, "场馆管理员", " 常约顾客 ");

        ArgumentCaptor<VenueCustomerTag> tag = ArgumentCaptor.forClass(VenueCustomerTag.class);
        verify(mapper).insertTag(tag.capture());
        assertThat(tag.getValue().createdByAdminId).isEqualTo(17L);
        assertThat(tag.getValue().createdByEmployeeId).isNull();
        assertThat(tag.getValue().createdByName).isEqualTo("场馆管理员");
        verify(mapper).insertTagAudit(3L, null, 55L, "常约顾客", "TAG_CREATED", 17L, null, "场馆管理员");
    }

    @Test
    void duplicateTagAssignmentIsIdempotentAndDoesNotCreateAnotherAuditRow() {
        VenueCustomerTagBO tag = new VenueCustomerTagBO();
        tag.setId(4L);
        tag.setName("常约顾客");
        when(mapper.countCustomer(3L, 9L)).thenReturn(1);
        when(mapper.selectTag(3L, 4L)).thenReturn(tag);
        when(mapper.countTagRelation(3L, 9L, 4L)).thenReturn(1);

        service().addTag(3L, 17L, null, "场馆管理员", 9L, 4L);

        verify(mapper, never()).addTag(anyLong(), anyLong(), anyLong(), any(), any(), any());
        verify(mapper, never()).insertTagAudit(anyLong(), any(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsClientClaimedFollowupSourceOutsideTheOnlySupportedGroup() {
        when(mapper.countCustomer(3L, 9L)).thenReturn(1);

        assertThatThrownBy(() -> service().createFollowup(3L, 11L, null, "场馆管理员", 9L,
                "30_DAY_NO_REBOOK", "任意客户端来源"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
        verify(mapper, never()).insertFollowup(anyLong(), anyLong(), any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void rejectsNullCustomerIdAndNullOutcomeAtTheBusinessBoundary() {
        assertThatThrownBy(() -> service().createFollowup(3L, 11L, null, "场馆管理员", null,
                "30_DAY_NO_REBOOK", "30_DAY_NO_REBOOK"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
        assertThatThrownBy(() -> service().updateFollowup(3L, 11L, null, "场馆管理员",
                7L, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
        org.mockito.Mockito.verifyNoInteractions(mapper);
    }

    @Test
    void reportsConcurrentFollowupHandlingAsConflictInsteadOfOverwriting() {
        CustomerFollowupBO pending = new CustomerFollowupBO();
        pending.setId(7L);
        pending.setStatus("PENDING");
        when(mapper.selectFollowup(3L, 7L)).thenReturn(pending);
        when(mapper.updateFollowup(eq(3L), eq(7L), eq("COMPLETED"), eq("done"), eq(11L), eq(null),
                eq("场馆管理员"))).thenReturn(0);

        assertThatThrownBy(() -> service().updateFollowup(3L, 11L, null, "场馆管理员",
                7L, "COMPLETED", "done"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
