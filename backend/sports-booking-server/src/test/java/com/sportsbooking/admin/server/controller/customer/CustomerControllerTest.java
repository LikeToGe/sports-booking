package com.sportsbooking.admin.server.controller.customer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sportsbooking.admin.base.enums.AdminRole;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.biz.pojo.request.CustomerTagRequest;
import com.sportsbooking.admin.biz.pojo.request.CustomerFollowupCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.CustomerFollowupUpdateRequest;
import com.sportsbooking.admin.biz.service.CustomerOperationsService;
import com.sportsbooking.admin.server.context.AuthContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CustomerControllerTest {
    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void emptyFollowupFieldsReachBusinessValidationWithoutControllerUnboxing() {
        CustomerController controller = new CustomerController();
        CustomerOperationsService service = mock(CustomerOperationsService.class);
        ReflectionTestUtils.setField(controller, "customerOperationsService", service);
        AuthContext.setAdminPrincipal(new AdminPrincipal(17L, AdminRole.VENUE_ADMIN, 3L, "场馆管理员"));

        assertThatCode(() -> controller.createCustomerFollowup(new CustomerFollowupCreateRequest()))
                .doesNotThrowAnyException();
        verify(service).createFollowup(3L, 17L, null, "场馆管理员", null, null, null);
        assertThatCode(() -> controller.updateCustomerFollowup(7L, new CustomerFollowupUpdateRequest()))
                .doesNotThrowAnyException();
        verify(service).updateFollowup(3L, 17L, null, "场馆管理员", 7L, null, null);
    }

    @Test
    void venueAdministratorWritesTagsWithAccountAuditIdentity() {
        CustomerController controller = new CustomerController();
        CustomerOperationsService service = mock(CustomerOperationsService.class);
        ReflectionTestUtils.setField(controller, "customerOperationsService", service);
        AuthContext.setAdminPrincipal(new AdminPrincipal(17L, AdminRole.VENUE_ADMIN, 3L, "场馆管理员"));

        assertThatCode(() -> controller.createCustomerTag(new CustomerTagRequest("常约顾客")))
                .doesNotThrowAnyException();
        verify(service).createTag(3L, 17L, null, "场馆管理员", "常约顾客");
    }
}
