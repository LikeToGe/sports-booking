package com.sportsbooking.admin.server.controller.booking;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.enums.AdminRole;
import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.biz.service.BookingService;
import com.sportsbooking.admin.biz.service.ReportService;
import com.sportsbooking.admin.server.context.AuthContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OperationReportControllerTest {
    @Mock
    private BookingService bookingService;
    @Mock
    private ReportService reportService;

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void venueReportAlwaysUsesTrustedPrincipalVenueAndMasksWithoutCustomerPermission() {
        OperationReportController controller = controller();
        LocalDate date = LocalDate.of(2026, 9, 1);
        AdminPrincipal principal = new AdminPrincipal(0L, AdminRole.VENUE_EMPLOYEE, 21L, "员工",
                9L, Set.of(VenuePermission.REPORT_VIEW));
        AuthContext.setAdminPrincipal(principal);
        when(reportService.listBookings(21L, date, date, false)).thenReturn(List.of());

        controller.listBookings(date, date);

        verify(reportService).listBookings(21L, date, date, false);
    }

    @Test
    void dashboardUsesTrustedVenueWithDashboardPermission() {
        OperationReportController controller = controller();
        AdminPrincipal principal = new AdminPrincipal(0L, AdminRole.VENUE_EMPLOYEE, 21L, "员工",
                9L, Set.of(VenuePermission.DASHBOARD_VIEW));
        AuthContext.setAdminPrincipal(principal);

        controller.getVenueDashboard(7);

        verify(reportService).getDashboard(21L, 7);
    }

    @Test
    void platformUtilizationNeverRequestsCourtDetails() {
        OperationReportController controller = controller();
        LocalDate day = LocalDate.of(2026, 9, 1);
        AuthContext.setAdminPrincipal(new AdminPrincipal(1L, AdminRole.PLATFORM_ADMIN, null, "平台", null, Set.of()));
        controller.getPlatformUtilization(21L, day, day, null, null);
        verify(reportService).getUtilizationReport(21L, day, day, null, null, false);
    }

    @Test
    void venueUtilizationAndMidnightDrilldownUseTrustedScope() {
        OperationReportController controller = controller();
        LocalDate day = LocalDate.of(2026, 9, 1);
        AuthContext.setAdminPrincipal(new AdminPrincipal(0L, AdminRole.VENUE_EMPLOYEE, 21L, "员工",
                9L, Set.of(VenuePermission.REPORT_VIEW)));
        controller.getVenueUtilization(day, day, 3L, 2L);
        verify(reportService).getUtilizationReport(21L, day, day, 3L, 2L, true);
        controller.listFilteredBookings(day, day, 3L, day.atTime(23, 0), day.plusDays(1).atStartOfDay(), 2L);
        verify(reportService).listBookings(21L, day, day, 3L, 2L, day.atTime(23, 0), day.plusDays(1).atStartOfDay(), false);
    }

    private OperationReportController controller() {
        OperationReportController controller = new OperationReportController();
        ReflectionTestUtils.setField(controller, "bookingService", bookingService);
        ReflectionTestUtils.setField(controller, "reportService", reportService);
        return controller;
    }
}
