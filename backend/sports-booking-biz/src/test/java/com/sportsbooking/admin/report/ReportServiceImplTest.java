package com.sportsbooking.admin.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.bo.ReportBookingBO;
import com.sportsbooking.admin.base.bo.ReportIntervalBO;
import com.sportsbooking.admin.base.bo.ReportMemberFlowBO;
import com.sportsbooking.admin.base.bo.ReportOperationDailyBO;
import com.sportsbooking.admin.base.bo.ReportOperationExtraBO;
import com.sportsbooking.admin.base.bo.ReportReceiptBO;
import com.sportsbooking.admin.base.mapper.ReportMapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.ReportBookingVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportOperationVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportUtilizationVO;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.service.impl.ReportServiceImpl;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {
    @Mock
    private ReportMapper reportMapper;
    @Mock
    private VenueService venueService;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(reportMapper, venueService);
    }

    @Test
    void aggregatesIndependentMetricsAndFillsMissingDates() {
        when(reportMapper.selectOperationDaily(any(), any(), any())).thenReturn(List.of(
                daily("2026-09-01", 10000L, 6000L, 10L, 4L, 5L, 1L),
                daily("2026-09-03", 20000L, 0L, 10L, 6L, 3L, 0L)
        ));
        when(reportMapper.selectOperationExtra(any(), any(), any()))
                .thenReturn(new ReportOperationExtraBO(4L, 1L, 30000L, 5000L));

        ReportOperationVO report = service.getOperationReport(null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3));

        assertThat(report.getOnlineBookingAmountFen()).isEqualTo(30000L);
        assertThat(report.getOfflineRegisteredAmountFen()).isEqualTo(6000L);
        assertThat(report.getUtilizationRateBasisPoints()).isEqualTo(5000);
        assertThat(report.getCancellationRateBasisPoints()).isEqualTo(1250);
        assertThat(report.getRepeatPurchaseRateBasisPoints()).isEqualTo(2500);
        assertThat(report.getMemberAvailableBalanceFen()).isEqualTo(30000L);
        assertThat(report.getDaily()).hasSize(3);
        assertThat(report.getDaily().get(1).getReportDate()).isEqualTo("2026-09-02");
        assertThat(report.getDaily().get(1).getBookingCount()).isZero();

        ArgumentCaptor<Instant> startAt = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endAt = ArgumentCaptor.forClass(Instant.class);
        verify(reportMapper).selectOperationDaily(eq(null), startAt.capture(), endAt.capture());
        assertThat(startAt.getValue()).isEqualTo(Instant.parse("2026-08-31T16:00:00Z"));
        assertThat(endAt.getValue()).isEqualTo(Instant.parse("2026-09-03T16:00:00Z"));
    }

    @Test
    void rejectsMoreThanNinetyInclusiveDays() {
        LocalDate start = LocalDate.of(2026, 1, 1);

        assertThat(service.listReceipts(1L, start, start.plusDays(89))).isEmpty();
        assertThatThrownBy(() -> service.listReceipts(1L, start, start.plusDays(90)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void dashboardOnlyAcceptsSevenOrThirtyDays() {
        assertThatThrownBy(() -> service.getDashboard(21L, 8)).isInstanceOf(ApiException.class);
    }

    @Test
    void dashboardUsesShanghaiBusinessDate() {
        LocalDate today = LocalDate.now(com.sportsbooking.admin.biz.constants.BusinessConstants.BUSINESS_ZONE);

        service.getDashboard(21L, 7);

        verify(reportMapper).selectOperationDaily(eq(21L),
                eq(today.minusDays(6).atStartOfDay(
                        com.sportsbooking.admin.biz.constants.BusinessConstants.BUSINESS_ZONE).toInstant()),
                eq(today.plusDays(1).atStartOfDay(
                        com.sportsbooking.admin.biz.constants.BusinessConstants.BUSINESS_ZONE).toInstant()));
    }

    @Test
    void unionsDifferentIntervalsAndKeepsMovedSnapshotsAndDistinctCancellations() {
        when(reportMapper.selectUtilizationIntervals(eq(21L), any(), any(), eq(null), eq(null))).thenReturn(List.of(
                interval(3L, "SUPPLY", null, "2026-09-01T10:00", "2026-09-01T11:00"),
                interval(3L, "SUPPLY", null, "2026-09-01T10:30", "2026-09-01T11:30"),
                interval(3L, "BOOKED", 1L, "2026-09-01T10:45", "2026-09-01T11:15"),
                interval(3L, "CANCELLED", 2L, "2026-09-01T10:15", "2026-09-01T11:15"),
                interval(4L, "BOOKED", 3L, "2026-09-01T19:00", "2026-09-01T20:00"),
                interval(5L, "UNAVAILABLE", null, "2026-09-01T12:00", "2026-09-01T13:00"),
                interval(5L, "CANCELLED", 4L, "2026-09-01T12:00", "2026-09-01T13:00")));
        ReportUtilizationVO report = service.getUtilizationReport(21L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null, null, true);
        var court = report.getCourts().stream().filter(row -> row.getCourtId() == 3).findFirst().orElseThrow();
        assertThat(court.getAvailableMinutes()).isEqualTo(90);
        assertThat(court.getBookedMinutes()).isEqualTo(30);
        assertThat(court.getBookingCount()).isEqualTo(2);
        assertThat(court.getCancelledCount()).isEqualTo(1);
        assertThat(report.getCourts().stream().filter(row -> row.getCourtId() == 4).findFirst().orElseThrow()
                .getBookedMinutes()).isEqualTo(60);
        assertThat(report.getCourts().stream().filter(row -> row.getCourtId() == 5).findFirst().orElseThrow()
                .getCancelledCount()).isEqualTo(1);
        assertThat(report.getHeatmap().stream().filter(cell -> cell.getDayOfWeek() == 2 && cell.getHourOfDay() == 12)
                .findFirst().orElseThrow().getDataStatus()).isEqualTo("UNAVAILABLE");
        assertThat(report.getHeatmap().stream().filter(cell -> cell.getDayOfWeek() == 2 && cell.getHourOfDay() == 9)
                .findFirst().orElseThrow().getDataStatus()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(report.getHeatmap().stream().mapToLong(cell -> cell.getCancelledCount()).sum()).isEqualTo(2);
    }

    @Test
    void missingRepeatedWeekdayStaysUnknownWithoutInventingCapacity() {
        ReportIntervalBO coverage = interval(3L, "UNKNOWN", null, "2026-09-01T00:00", "2026-09-09T00:00");
        ReportIntervalBO booked = interval(3L, "BOOKED", 1L, "2026-09-01T19:00", "2026-09-01T20:00");
        when(reportMapper.selectUtilizationIntervals(eq(21L), any(), any(), eq(null), eq(null)))
                .thenReturn(List.of(coverage, booked));

        ReportUtilizationVO report = service.getUtilizationReport(21L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8), null, null, true);
        var cell = report.getHeatmap().stream().filter(row -> row.getDayOfWeek() == 2 && row.getHourOfDay() == 19)
                .findFirst().orElseThrow();
        assertThat(cell.getDataStatus()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(cell.getAvailableMinutes()).isEqualTo(60);
        assertThat(cell.getBookedMinutes()).isEqualTo(60);
        assertThat(report.getCourts().get(0).getDataStatus()).isEqualTo("DATA_INSUFFICIENT");

        when(reportMapper.selectUtilizationIntervals(eq(21L), any(), any(), eq(null), eq(null)))
                .thenReturn(List.of(coverage, booked,
                        interval(3L, "UNAVAILABLE", null, "2026-09-08T19:00", "2026-09-08T20:00")));
        ReportUtilizationVO withClosure = service.getUtilizationReport(21L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8), null, null, true);
        var closedCell = withClosure.getHeatmap().stream()
                .filter(row -> row.getDayOfWeek() == 2 && row.getHourOfDay() == 19).findFirst().orElseThrow();
        assertThat(closedCell.getDataStatus()).isEqualTo("KNOWN");
        assertThat(closedCell.getAvailableMinutes()).isEqualTo(60);
    }

    @Test
    void includesEmptyCourtsButDoesNotCountDatesBeforeCourtCreation() {
        when(reportMapper.selectUtilizationIntervals(eq(21L), any(), any(), eq(null), eq(null))).thenReturn(List.of(
                interval(3L, "UNKNOWN", null, "2026-09-08T12:30", "2026-09-09T00:00"),
                interval(3L, "BOOKED", 1L, "2026-09-08T19:00", "2026-09-08T20:00"),
                interval(4L, "UNKNOWN", null, "2026-09-08T12:30", "2026-09-09T00:00")));
        ReportUtilizationVO report = service.getUtilizationReport(21L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8), null, null, true);
        assertThat(report.getCourts()).hasSize(2);
        assertThat(report.getCourts().stream().filter(row -> row.getCourtId() == 4).findFirst().orElseThrow()
                .getDataStatus()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(report.getLowUtilization()).isEmpty();
        assertThat(report.getHeatmap().stream().filter(row -> row.getDayOfWeek() == 2 && row.getHourOfDay() == 19)
                .findFirst().orElseThrow().getDataStatus()).isEqualTo("DATA_INSUFFICIENT");

        when(reportMapper.selectUtilizationIntervals(eq(21L), any(), any(), eq(null), eq(null))).thenReturn(List.of(
                interval(3L, "UNKNOWN", null, "2026-09-08T12:30", "2026-09-09T00:00"),
                interval(3L, "BOOKED", 1L, "2026-09-08T19:00", "2026-09-08T20:00")));
        ReportUtilizationVO newlyCreated = service.getUtilizationReport(21L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8), null, null, true);
        assertThat(newlyCreated.getHeatmap().stream().filter(row -> row.getDayOfWeek() == 2 && row.getHourOfDay() == 19)
                .findFirst().orElseThrow().getDataStatus()).isEqualTo("KNOWN");
    }

    @Test
    void separatesFutureAndHidesCourtDetailsForPlatform() {
        LocalDate tomorrow = LocalDate.now(com.sportsbooking.admin.biz.constants.BusinessConstants.BUSINESS_ZONE).plusDays(1);
        when(reportMapper.selectUtilizationIntervals(eq(null), any(), any(), eq(null), eq(null))).thenReturn(List.of(
                interval(7L, "SUPPLY", null, tomorrow + "T23:00", tomorrow.plusDays(1) + "T00:00")));
        ReportUtilizationVO report = service.getUtilizationReport(null, tomorrow, tomorrow, null, null, false);
        assertThat(report.getHeatmap()).allMatch(cell -> cell.getAvailableMinutes() == 0);
        assertThat(report.getFutureHeatmap().stream().mapToLong(cell -> cell.getAvailableMinutes()).sum()).isEqualTo(60);
        assertThat(report.getCourts()).isEmpty();
        assertThat(report.getFutureCourts()).isEmpty();
        assertThat(report.getLowUtilization()).isEmpty();
        assertThat(report.isCourtDetailsIncluded()).isFalse();
        assertThatThrownBy(() -> service.getUtilizationReport(null, tomorrow, tomorrow, 7L, null, false))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void midnightDrilldownPreservesSportAndFullInstantRange() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        LocalDateTime start = date.atTime(23, 0), end = date.plusDays(1).atStartOfDay();
        when(reportMapper.selectBookingsByCourtTime(eq(21L), any(), any(), eq(3L), any(), any(), eq(2L)))
                .thenReturn(List.of());
        service.listBookings(21L, date, date, 3L, 2L, start, end, false);
        verify(reportMapper).selectBookingsByCourtTime(21L, Instant.parse("2026-08-31T16:00:00Z"),
                Instant.parse("2026-09-01T16:00:00Z"), 3L, Instant.parse("2026-09-01T15:00:00Z"),
                Instant.parse("2026-09-01T16:00:00Z"), 2L);
    }

    private ReportIntervalBO interval(long courtId, String kind, Long bookingId, String start, String end) {
        ReportIntervalBO row = new ReportIntervalBO();
        row.setCourtId(courtId); row.setCourtName("测试场地" + courtId); row.setVenueId(21L);
        row.setBookingId(bookingId); row.setKind(kind);
        row.setStartAt(LocalDateTime.parse(start).atZone(com.sportsbooking.admin.biz.constants.BusinessConstants.BUSINESS_ZONE).toInstant());
        row.setEndAt(LocalDateTime.parse(end).atZone(com.sportsbooking.admin.biz.constants.BusinessConstants.BUSINESS_ZONE).toInstant());
        return row;
    }

    @Test
    void masksSensitiveBookingFieldsWithoutCustomerPermission() {
        when(reportMapper.selectBookings(eq(21L), any(), any())).thenReturn(List.of(
                new ReportBookingBO("YC1", "2026-09-01", "10:00:00", "11:00:00", "ONLINE",
                        "张三", "13800001234", "1号场", "羽毛球", "CONFIRMED", "PAID",
                        10000L, 0L, 3000L, 7000L, LocalDateTime.of(2026, 8, 20, 12, 0))
        ));

        ReportBookingVO row = service.listBookings(21L, LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 1), false).get(0);

        assertThat(row.getContactName()).isEqualTo("张**");
        assertThat(row.getContactPhone()).isEqualTo("138****1234");
    }

    @Test
    void keepsOfflineRegistrationOutOfRealReceiptQuery() throws Exception {
        byte[] mapperBytes;
        try (var input = ReportServiceImplTest.class.getResourceAsStream("/mapper/ReportMapper.xml")) {
            assertThat(input).isNotNull();
            mapperBytes = input.readAllBytes();
        }
        String mapperXml = new String(mapperBytes, StandardCharsets.UTF_8);
        int receiptsStart = mapperXml.indexOf("<select id=\"selectReceipts\"");
        String receiptsSql = mapperXml.substring(receiptsStart);

        assertThat(mapperXml).contains("b.source = 'OFFLINE' AND b.payment_status = 'PAID'");
        assertThat(receiptsSql).contains("p.status = 'SUCCESS'", "r.status = 'SUCCESS'",
                "rf.status = 'SUCCESS'", "rf.refunded_at", "-rf.amount_fen AS amountFen");
        assertThat(receiptsSql).doesNotContain("b.payment_status = 'PAID'");
    }

    @Test
    void createsOpenableSafeWorkbooksWithNumericAmountsAndTotals() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 1);
        when(reportMapper.selectBookings(eq(21L), any(), any())).thenReturn(List.of(
                new ReportBookingBO("=危险预约号", "2026-09-01", "10:00:00", "11:00:00", "ONLINE",
                        "张三", "13800001234", "1号场", "羽毛球", "CONFIRMED", "PAID",
                        12345L, 0L, 2345L, 10000L, LocalDateTime.of(2026, 8, 20, 12, 0))
        ));
        when(reportMapper.selectMemberFlows(eq(21L), any(), any())).thenReturn(List.of(
                new ReportMemberFlowBO(1L, "M001", "CONSUME", "DEBIT", 2345L, "YC1", null,
                        "+危险操作人", LocalDateTime.of(2026, 9, 1, 10, 0))
        ));
        when(reportMapper.selectReceipts(eq(21L), any(), any())).thenReturn(List.of(
                new ReportReceiptBO("2026-09-01", "-危险操作人", "MOCK", "ONLINE_BOOKING", 1L, 10000L),
                new ReportReceiptBO("2026-09-01", "系统退款", "MOCK", "ONLINE_REFUND", 1L, -7000L)
        ));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                service.exportBookings(21L, date, date, true)))) {
            assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(3);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("'=危险预约号");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(11).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(11).getNumericCellValue()).isEqualTo(123.45D);
            assertThat(workbook.getSheetAt(0).getPaneInformation().isFreezePane()).isTrue();
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                service.exportMemberFlows(21L, date, date)))) {
            assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(3);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(7).getStringCellValue())
                    .isEqualTo("'+危险操作人");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                service.exportReceipts(21L, date, date)))) {
            assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(4);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("'-危险操作人");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(5).getNumericCellValue()).isEqualTo(-70.0D);
            assertThat(workbook.getSheetAt(0).getRow(3).getCell(5).getNumericCellValue()).isEqualTo(30.0D);
        }
    }

    private ReportOperationDailyBO daily(String date, long onlineAmount, long offlineAmount,
                                         long available, long booked, long bookings, long cancelled) {
        return new ReportOperationDailyBO(date, onlineAmount, offlineAmount, available, booked, bookings,
                cancelled, 1L, 2L, 3L, 4L, 5000L, 2000L);
    }
}
