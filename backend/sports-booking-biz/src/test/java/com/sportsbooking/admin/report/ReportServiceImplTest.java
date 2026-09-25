package com.sportsbooking.admin.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.bo.ReportBookingBO;
import com.sportsbooking.admin.base.bo.ReportMemberFlowBO;
import com.sportsbooking.admin.base.bo.ReportOperationDailyBO;
import com.sportsbooking.admin.base.bo.ReportOperationExtraBO;
import com.sportsbooking.admin.base.bo.ReportReceiptBO;
import com.sportsbooking.admin.base.mapper.ReportMapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.ReportBookingVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportOperationVO;
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
