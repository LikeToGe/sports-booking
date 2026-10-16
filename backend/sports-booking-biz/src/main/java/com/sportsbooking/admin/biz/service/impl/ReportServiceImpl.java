package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.bo.ReportBookingBO;
import com.sportsbooking.admin.base.bo.ReportIntervalBO;
import com.sportsbooking.admin.base.bo.ReportMemberFlowBO;
import com.sportsbooking.admin.base.bo.ReportOperationDailyBO;
import com.sportsbooking.admin.base.bo.ReportOperationExtraBO;
import com.sportsbooking.admin.base.bo.ReportReceiptBO;
import com.sportsbooking.admin.base.bo.ReportVenueMemberOperationBO;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.ReportMapper;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.ReportBookingVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportMemberFlowVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportOperationDailyVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportOperationVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportCourtPerformanceVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportLowUtilizationVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportUtilizationCellVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportUtilizationVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportReceiptVO;
import com.sportsbooking.admin.biz.pojo.vo.ReportVenueMemberOperationVO;
import com.sportsbooking.admin.biz.service.ReportService;
import com.sportsbooking.admin.biz.service.VenueService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 功能描述: 使用同一组 Mapper 查询生成经营页面数据及同步 Excel。
 *
 * @author gjm
 */
@Service
public class ReportServiceImpl implements ReportService {
    private static final int MAX_DATE_RANGE_DAYS = 90;
    private static final int RATE_BASIS_POINTS = 10_000;
    private static final int LOW_UTILIZATION_RATE_BASIS_POINTS = 3_000;
    private static final int MAX_LOW_UTILIZATION_ROWS = 100;
    private static final int MAX_COLUMN_WIDTH = 40 * 256;
    private static final int MIN_COLUMN_WIDTH = 10 * 256;

    private final ReportMapper reportMapper;
    private final VenueService venueService;

    public ReportServiceImpl(ReportMapper reportMapper, VenueService venueService) {
        this.reportMapper = reportMapper;
        this.venueService = venueService;
    }

    @Override
    public ReportOperationVO getDashboard(long venueId, int days) {
        if (days != 7 && days != 30) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        LocalDate endDate = LocalDate.now(BusinessConstants.BUSINESS_ZONE);
        return getOperationReport(venueId, endDate.minusDays(days - 1L), endDate);
    }

    /**
     * 功能描述: 查询经营汇总并补齐区间内无数据日期。
     *
     * @param venueId 场馆 ID，为空时汇总全平台
     * @param startDate 开始日期，包含
     * @param endDate 结束日期，包含
     * @return 经营汇总和每日趋势
     * @author gjm
     */
    @Override
    public ReportOperationVO getOperationReport(Long venueId, LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        if (venueId != null) {
            venueService.getVenue(venueId);
        }
        Instant startAt = toStartAt(startDate);
        Instant endAt = toEndAt(endDate);
        List<ReportOperationDailyBO> rows = reportMapper.selectOperationDaily(venueId, startAt, endAt);
        ReportOperationExtraBO extra = reportMapper.selectOperationExtra(venueId, startAt, endAt);

        Map<String, ReportOperationDailyBO> rowsByDate = new HashMap<>();
        for (ReportOperationDailyBO row : rows) {
            rowsByDate.put(row.getReportDate(), row);
        }

        long onlineAmount = 0;
        long offlineAmount = 0;
        long availableSessions = 0;
        long bookedSessions = 0;
        long bookings = 0;
        long cancelled = 0;
        long pendingVerification = 0;
        long offlineUnrecorded = 0;
        long offlineUnpaid = 0;
        long newMembers = 0;
        long rechargeInflow = 0;
        long balanceConsumption = 0;
        List<ReportOperationDailyVO> daily = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            ReportOperationDailyBO row = rowsByDate.getOrDefault(date.toString(), emptyDaily(date));
            onlineAmount += value(row.getOnlineBookingAmountFen());
            offlineAmount += value(row.getOfflineRegisteredAmountFen());
            availableSessions += value(row.getAvailableSessionCount());
            bookedSessions += value(row.getBookedSessionCount());
            bookings += value(row.getBookingCount());
            cancelled += value(row.getCancelledCount());
            pendingVerification += value(row.getPendingVerificationCount());
            offlineUnrecorded += value(row.getOfflineUnrecordedCount());
            offlineUnpaid += value(row.getOfflineUnpaidCount());
            newMembers += value(row.getNewMemberCount());
            rechargeInflow += value(row.getRechargeInflowFen());
            balanceConsumption += value(row.getBalanceConsumptionFen());
            daily.add(toDaily(row));
        }

        ReportOperationExtraBO safeExtra = extra == null ? new ReportOperationExtraBO(0L, 0L, 0L, 0L) : extra;
        return new ReportOperationVO(onlineAmount, offlineAmount, availableSessions, bookedSessions,
                rate(bookedSessions, availableSessions), bookings, cancelled, rate(cancelled, bookings),
                pendingVerification, offlineUnrecorded, offlineUnpaid, newMembers, rechargeInflow,
                balanceConsumption, value(safeExtra.getRepeatCustomerCount()),
                rate(value(safeExtra.getRepeatCustomerCount()), value(safeExtra.getEffectiveCustomerCount())),
                value(safeExtra.getMemberAvailableBalanceFen()), value(safeExtra.getMemberFrozenBalanceFen()), daily);
    }

    /** 批量读取事实，按场地合并区间，再分小时；快照本身贡献占用和容量。 */
    @Override
    public ReportUtilizationVO getUtilizationReport(Long venueId, LocalDate startDate, LocalDate endDate,
                                                     Long courtId, Long sportTypeId, boolean includeCourtDetails) {
        validateRange(startDate, endDate);
        if ((courtId != null && courtId <= 0) || (sportTypeId != null && sportTypeId <= 0)
                || (!includeCourtDetails && courtId != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        if (venueId != null) venueService.getVenue(venueId);
        List<ReportIntervalBO> facts = reportMapper.selectUtilizationIntervals(
                venueId, toStartAt(startDate), toEndAt(endDate), courtId, sportTypeId);
        Map<ReportHour, MinuteBucket> buckets = new java.util.LinkedHashMap<>();
        Map<Long, String> courtNames = new HashMap<>();
        Map<Long, LocalDateTime> courtStarts = new HashMap<>();
        Map<Long, String> sports = new java.util.TreeMap<>();
        for (ReportIntervalBO fact : facts) {
            courtNames.put(fact.getCourtId(), fact.getCourtName());
            if (fact.getSportTypeId() != null) sports.put(fact.getSportTypeId(), fact.getSportName());
            LocalDateTime begin = fact.getStartAt().atZone(BusinessConstants.BUSINESS_ZONE).toLocalDateTime();
            LocalDateTime finish = fact.getEndAt().atZone(BusinessConstants.BUSINESS_ZONE).toLocalDateTime();
            LocalDateTime clippedBegin = begin.isBefore(startDate.atStartOfDay()) ? startDate.atStartOfDay() : begin;
            LocalDateTime clippedEnd = finish.isAfter(endDate.plusDays(1).atStartOfDay())
                    ? endDate.plusDays(1).atStartOfDay() : finish;
            if ("UNKNOWN".equals(fact.getKind())) {
                // 场地存在不等于该时段可售；只记待核对范围，不给缺失数据补容量。
                courtStarts.put(fact.getCourtId(), clippedBegin.withMinute(0).withSecond(0).withNano(0));
                continue;
            }
            for (LocalDateTime hour = clippedBegin.withMinute(0).withSecond(0).withNano(0);
                 hour.isBefore(clippedEnd); hour = hour.plusHours(1)) {
                MinuteBucket bucket = buckets.computeIfAbsent(new ReportHour(fact.getCourtId(), hour), key -> new MinuteBucket());
                LocalDateTime left = clippedBegin.isAfter(hour) ? clippedBegin : hour;
                LocalDateTime right = clippedEnd.isBefore(hour.plusHours(1)) ? clippedEnd : hour.plusHours(1);
                MinuteSpan span = new MinuteSpan(java.time.Duration.between(hour, left).toMinutes(),
                        java.time.Duration.between(hour, right).toMinutes());
                if ("SUPPLY".equals(fact.getKind()) || "BOOKED".equals(fact.getKind())) {
                    bucket.supply.add(span);
                    bucket.known = true;
                }
                if ("BOOKED".equals(fact.getKind())) bucket.booked.add(span);
                if ("UNAVAILABLE".equals(fact.getKind())) bucket.known = true;
                // 取消和预约笔数只归开始小时，不随跨小时切片重复；不依赖当前库存是否关闭。
                if (fact.getBookingId() != null && !begin.isBefore(hour) && begin.isBefore(hour.plusHours(1))) {
                    bucket.bookingIds.add(fact.getBookingId());
                    if ("CANCELLED".equals(fact.getKind())) bucket.cancelledIds.add(fact.getBookingId());
                }
            }
        }
        LocalDate today = LocalDate.now(BusinessConstants.BUSINESS_ZONE);
        Map<String, MinuteSummary> history = new HashMap<>();
        Map<String, MinuteSummary> future = new HashMap<>();
        Map<Long, MinuteSummary> historicalCourts = new java.util.TreeMap<>();
        Map<Long, MinuteSummary> futureCourts = new java.util.TreeMap<>();
        List<ReportLowUtilizationVO> low = new ArrayList<>();
        for (Map.Entry<ReportHour, MinuteBucket> entry : buckets.entrySet()) {
            ReportHour key = entry.getKey();
            MinuteBucket bucket = entry.getValue();
            long available = unionMinutes(bucket.supply);
            long booked = unionMinutes(bucket.booked);
            boolean historical = key.getHour().toLocalDate().isBefore(today);
            String cell = key.getHour().getDayOfWeek().getValue() + ":" + key.getHour().getHour();
            (historical ? history : future).computeIfAbsent(cell, ignored -> new MinuteSummary())
                    .add(bucket, available, booked);
            (historical ? historicalCourts : futureCourts).computeIfAbsent(key.getCourtId(), ignored -> new MinuteSummary())
                    .add(bucket, available, booked);
            if (includeCourtDetails && historical && bucket.known && available > 0
                    && rate(booked, available) <= LOW_UTILIZATION_RATE_BASIS_POINTS) {
                low.add(new ReportLowUtilizationVO(key.getCourtId(), courtNames.get(key.getCourtId()),
                        key.getHour().toLocalDate().toString(), key.getHour().toString(), key.getHour().plusHours(1).toString(),
                        available, booked, rate(booked, available), bucket.cancelledIds.size()));
            }
        }
        // 每个存在的场地逐日核对，避免同一星期另一天无事实时被已有事实掩盖。
        // 缺失小时直接标记汇总，不为最多 90 天的全场地范围分配空分钟桶。
        for (Map.Entry<Long, LocalDateTime> court : courtStarts.entrySet()) {
            for (LocalDateTime hour = court.getValue(); hour.isBefore(endDate.plusDays(1).atStartOfDay());
                 hour = hour.plusHours(1)) {
                if (buckets.containsKey(new ReportHour(court.getKey(), hour))) continue;
                boolean historical = hour.toLocalDate().isBefore(today);
                String cell = hour.getDayOfWeek().getValue() + ":" + hour.getHour();
                (historical ? history : future).computeIfAbsent(cell, ignored -> new MinuteSummary()).unknown = true;
                (historical ? historicalCourts : futureCourts)
                        .computeIfAbsent(court.getKey(), ignored -> new MinuteSummary()).unknown = true;
            }
        }
        low.sort(java.util.Comparator.comparingInt(ReportLowUtilizationVO::getUtilizationRateBasisPoints)
                .thenComparing(ReportLowUtilizationVO::getStartTime).thenComparingLong(ReportLowUtilizationVO::getCourtId));
        if (low.size() > MAX_LOW_UTILIZATION_ROWS) low = new ArrayList<>(low.subList(0, MAX_LOW_UTILIZATION_ROWS));
        return new ReportUtilizationVO(startDate.toString(), endDate.toString(),
                BusinessConstants.BUSINESS_ZONE.getId(), LocalDateTime.now(BusinessConstants.BUSINESS_ZONE),
                "基于当前留存供给与预约快照，按场地区间并集统计分钟；历史截至昨日，今日及未来单列供给预订分布。"
                        + "缺失供给显示数据不足，不还原不存在的历史开售/封场记录；关闭和不可供给不计容量，"
                        + "支付占位不计确认占用；取消与预约数按开始日期去重。",
                utilizationCells(history), includeCourtDetails ? courtSummaries(historicalCourts, courtNames) : List.of(),
                low, utilizationCells(future), includeCourtDetails ? courtSummaries(futureCourts, courtNames) : List.of(),
                includeCourtDetails, sports);
    }

    /** 同场地同小时内取真正区间并集，重叠和相邻区间均不会重复容量。 */
    private long unionMinutes(List<MinuteSpan> spans) {
        spans.sort(java.util.Comparator.comparingLong(MinuteSpan::getStart));
        long total = 0, left = 0, right = 0;
        for (MinuteSpan span : spans) {
            if (span.getStart() > right) {
                total += right - left;
                left = span.getStart();
                right = span.getEnd();
            } else right = Math.max(right, span.getEnd());
        }
        return total + right - left;
    }

    /** 缺少事实的小时保持未知，不能把无数据伪装成不可售或零利用率。 */
    private List<ReportUtilizationCellVO> utilizationCells(Map<String, MinuteSummary> totals) {
        List<ReportUtilizationCellVO> cells = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            for (int hour = 0; hour < 24; hour++) {
                MinuteSummary summary = totals.get(day + ":" + hour);
                String state = summary == null || summary.unknown ? "DATA_INSUFFICIENT"
                        : summary.available == 0 ? "UNAVAILABLE" : "KNOWN";
                cells.add(new ReportUtilizationCellVO(day, hour, summary == null ? 0 : summary.available,
                        summary == null ? 0 : summary.booked,
                        summary == null ? 0 : rate(summary.booked, summary.available),
                        summary == null ? 0 : summary.cancelled.size(), state));
            }
        }
        return cells;
    }

    /** 场地笔数取业务 ID 集合，避免一笔跨小时预约计成多笔。 */
    private List<ReportCourtPerformanceVO> courtSummaries(Map<Long, MinuteSummary> totals, Map<Long, String> names) {
        List<ReportCourtPerformanceVO> rows = new ArrayList<>();
        for (Map.Entry<Long, MinuteSummary> entry : totals.entrySet()) {
            MinuteSummary total = entry.getValue();
            rows.add(new ReportCourtPerformanceVO(entry.getKey(), names.get(entry.getKey()), total.available, total.booked,
                    rate(total.booked, total.available), total.cancelled.size(), total.bookings.size(),
                    total.unknown ? "DATA_INSUFFICIENT" : total.available == 0 ? "UNAVAILABLE" : "KNOWN"));
        }
        rows.sort(java.util.Comparator.comparingInt(ReportCourtPerformanceVO::getUtilizationRateBasisPoints)
                .thenComparingLong(ReportCourtPerformanceVO::getCourtId));
        return rows;
    }

    /** 同一场地的自然小时分桶键。 */
    @lombok.Value
    private static class ReportHour {
        long courtId;
        LocalDateTime hour;
    }
    /** 单个小时内的半开分钟区间。 */
    @lombok.Value
    private static class MinuteSpan {
        long start;
        long end;
    }
    private static class MinuteBucket {
        private final List<MinuteSpan> supply = new ArrayList<>();
        private final List<MinuteSpan> booked = new ArrayList<>();
        private final java.util.Set<Long> bookingIds = new java.util.HashSet<>();
        private final java.util.Set<Long> cancelledIds = new java.util.HashSet<>();
        private boolean known;
    }
    private static class MinuteSummary {
        private long available;
        private long booked;
        private boolean unknown;
        private final java.util.Set<Long> bookings = new java.util.HashSet<>();
        private final java.util.Set<Long> cancelled = new java.util.HashSet<>();
        private void add(MinuteBucket bucket, long availableMinutes, long bookedMinutes) {
            available += availableMinutes;
            booked += bookedMinutes;
            unknown |= !bucket.known;
            bookings.addAll(bucket.bookingIds);
            cancelled.addAll(bucket.cancelledIds);
        }
    }

    @Override
    public List<ReportVenueMemberOperationVO> listVenueMemberOperations(LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        List<ReportVenueMemberOperationBO> rows = reportMapper.selectVenueMemberOperations(
                toStartAt(startDate), toEndAt(endDate));
        List<ReportVenueMemberOperationVO> result = new ArrayList<>();
        for (ReportVenueMemberOperationBO row : rows) {
            result.add(new ReportVenueMemberOperationVO(row.getVenueId(), row.getVenueName(),
                    value(row.getCustomerCount()), value(row.getMemberCount()), value(row.getNewMemberCount()),
                    value(row.getRepeatCustomerCount()),
                    rate(value(row.getRepeatCustomerCount()), value(row.getEffectiveCustomerCount())),
                    value(row.getRechargeInflowFen()), value(row.getBalanceConsumptionFen()),
                    value(row.getMemberAvailableBalanceFen()), value(row.getMemberFrozenBalanceFen())));
        }
        return result;
    }

    @Override
    public List<ReportBookingVO> listBookings(long venueId, LocalDate startDate, LocalDate endDate,
                                              boolean includeSensitive) {
        return listBookings(venueId, startDate, endDate, null, null, null, null, includeSensitive);
    }

    /** 下钻按区间重叠匹配，结束可为次日零点，运动条件与来源报表一致。 */
    @Override
    public List<ReportBookingVO> listBookings(long venueId, LocalDate startDate, LocalDate endDate,
            Long courtId, Long sportTypeId, LocalDateTime slotStart, LocalDateTime slotEnd, boolean includeSensitive) {
        validateRange(startDate, endDate);
        if ((courtId != null && courtId <= 0) || (sportTypeId != null && sportTypeId <= 0)
                || (slotStart == null) != (slotEnd == null)
                || (slotStart != null && (!slotStart.isBefore(slotEnd)
                    || slotStart.isBefore(startDate.atStartOfDay()) || slotEnd.isAfter(endDate.plusDays(1).atStartOfDay())))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        List<ReportBookingBO> rows;
        if (courtId == null && sportTypeId == null && slotStart == null) {
            rows = reportMapper.selectBookings(venueId, toStartAt(startDate), toEndAt(endDate));
        } else {
            rows = reportMapper.selectBookingsByCourtTime(venueId, toStartAt(startDate), toEndAt(endDate), courtId,
                    slotStart == null ? null : slotStart.atZone(BusinessConstants.BUSINESS_ZONE).toInstant(),
                    slotEnd == null ? null : slotEnd.atZone(BusinessConstants.BUSINESS_ZONE).toInstant(), sportTypeId);
        }
        List<ReportBookingVO> result = new ArrayList<>();
        for (ReportBookingBO row : rows) {
            String contactName = includeSensitive ? row.getContactName() : maskName(row.getContactName());
            String contactPhone = includeSensitive ? row.getContactPhone() : maskPhone(row.getContactPhone());
            result.add(new ReportBookingVO(row.getBookingNo(), row.getSessionDate(), row.getStartTime(),
                    row.getEndTime(), row.getSource(), contactName, contactPhone, row.getCourtName(),
                    row.getSportName(), row.getStatus(), row.getPaymentStatus(), value(row.getAmountFen()),
                    value(row.getOfflineRegisteredAmountFen()), value(row.getBalanceConsumptionFen()),
                    value(row.getExternalPaymentFen()), row.getCreatedAt()));
        }
        return result;
    }

    @Override
    public List<ReportMemberFlowVO> listMemberFlows(long venueId, LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        List<ReportMemberFlowBO> rows = reportMapper.selectMemberFlows(venueId, toStartAt(startDate), toEndAt(endDate));
        List<ReportMemberFlowVO> result = new ArrayList<>();
        for (ReportMemberFlowBO row : rows) {
            result.add(new ReportMemberFlowVO(row.getFlowId(), row.getMemberCode(), row.getBusinessType(),
                    row.getDirection(), value(row.getAmountFen()), row.getBookingNo(), row.getRechargeNo(),
                    row.getOperatorName(), row.getOccurredAt()));
        }
        return result;
    }

    @Override
    public List<ReportReceiptVO> listReceipts(long venueId, LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        List<ReportReceiptBO> rows = reportMapper.selectReceipts(venueId, toStartAt(startDate), toEndAt(endDate));
        List<ReportReceiptVO> result = new ArrayList<>();
        for (ReportReceiptBO row : rows) {
            result.add(new ReportReceiptVO(row.getReceiptDate(), row.getOperatorName(), row.getChannel(),
                    row.getBusinessType(), value(row.getCount()), value(row.getAmountFen())));
        }
        return result;
    }

    @Override
    public byte[] exportBookings(long venueId, LocalDate startDate, LocalDate endDate, boolean includeSensitive) {
        List<ReportBookingVO> rows = listBookings(venueId, startDate, endDate, includeSensitive);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("预约");
            ExcelStyles styles = new ExcelStyles(workbook);
            String[] headers = {"预约号", "场次日期", "开始时间", "结束时间", "来源", "联系人", "手机号", "场地", "运动",
                    "状态", "收款状态", "预约总金额", "线下登记金额", "余额消费", "外部支付", "创建时间"};
            writeHeader(sheet, headers, styles.header);
            long amount = 0;
            long offlineAmount = 0;
            long balanceAmount = 0;
            long externalAmount = 0;
            int rowIndex = 1;
            for (ReportBookingVO item : rows) {
                Row row = sheet.createRow(rowIndex++);
                text(row, 0, item.getBookingNo());
                date(row, 1, LocalDate.parse(item.getSessionDate()), styles.date);
                text(row, 2, item.getStartTime());
                text(row, 3, item.getEndTime());
                text(row, 4, item.getSource());
                text(row, 5, item.getContactName());
                text(row, 6, item.getContactPhone());
                text(row, 7, item.getCourtName());
                text(row, 8, item.getSportName());
                text(row, 9, item.getStatus());
                text(row, 10, item.getPaymentStatus());
                money(row, 11, item.getAmountFen(), styles.money);
                money(row, 12, item.getOfflineRegisteredAmountFen(), styles.money);
                money(row, 13, item.getBalanceConsumptionFen(), styles.money);
                money(row, 14, item.getExternalPaymentFen(), styles.money);
                dateTime(row, 15, item.getCreatedAt(), styles.dateTime);
                amount += item.getAmountFen();
                offlineAmount += item.getOfflineRegisteredAmountFen();
                balanceAmount += item.getBalanceConsumptionFen();
                externalAmount += item.getExternalPaymentFen();
            }
            Row total = sheet.createRow(rowIndex);
            text(total, 0, "总计");
            money(total, 11, amount, styles.totalMoney);
            money(total, 12, offlineAmount, styles.totalMoney);
            money(total, 13, balanceAmount, styles.totalMoney);
            money(total, 14, externalAmount, styles.totalMoney);
            return finish(workbook, sheet, headers.length);
        } catch (IOException exception) {
            throw new IllegalStateException("生成预约报表失败", exception);
        }
    }

    @Override
    public byte[] exportMemberFlows(long venueId, LocalDate startDate, LocalDate endDate) {
        List<ReportMemberFlowVO> rows = listMemberFlows(venueId, startDate, endDate);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("会员流水");
            ExcelStyles styles = new ExcelStyles(workbook);
            String[] headers = {"流水ID", "会员码", "业务类型", "方向", "金额", "预约号", "充值单号", "操作人", "时间"};
            writeHeader(sheet, headers, styles.header);
            long amount = 0;
            int rowIndex = 1;
            for (ReportMemberFlowVO item : rows) {
                Row row = sheet.createRow(rowIndex++);
                text(row, 0, String.valueOf(item.getFlowId()));
                text(row, 1, item.getMemberCode());
                text(row, 2, item.getBusinessType());
                text(row, 3, item.getDirection());
                money(row, 4, item.getAmountFen(), styles.money);
                text(row, 5, item.getBookingNo());
                text(row, 6, item.getRechargeNo());
                text(row, 7, item.getOperatorName());
                dateTime(row, 8, item.getOccurredAt(), styles.dateTime);
                amount += item.getAmountFen();
            }
            Row total = sheet.createRow(rowIndex);
            text(total, 0, "总计");
            money(total, 4, amount, styles.totalMoney);
            return finish(workbook, sheet, headers.length);
        } catch (IOException exception) {
            throw new IllegalStateException("生成会员流水报表失败", exception);
        }
    }

    @Override
    public byte[] exportReceipts(long venueId, LocalDate startDate, LocalDate endDate) {
        List<ReportReceiptVO> rows = listReceipts(venueId, startDate, endDate);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("每日收款");
            ExcelStyles styles = new ExcelStyles(workbook);
            String[] headers = {"日期", "操作人", "渠道", "业务类型", "笔数", "实收金额"};
            writeHeader(sheet, headers, styles.header);
            long count = 0;
            long amount = 0;
            int rowIndex = 1;
            for (ReportReceiptVO item : rows) {
                Row row = sheet.createRow(rowIndex++);
                date(row, 0, LocalDate.parse(item.getReceiptDate()), styles.date);
                text(row, 1, item.getOperatorName());
                text(row, 2, item.getChannel());
                text(row, 3, item.getBusinessType());
                number(row, 4, item.getCount());
                money(row, 5, item.getAmountFen(), styles.money);
                count += item.getCount();
                amount += item.getAmountFen();
            }
            Row total = sheet.createRow(rowIndex);
            text(total, 0, "总计");
            number(total, 4, count);
            money(total, 5, amount, styles.totalMoney);
            return finish(workbook, sheet, headers.length);
        } catch (IOException exception) {
            throw new IllegalStateException("生成每日收款报表失败", exception);
        }
    }

    /** 校验闭区间最多包含 90 个自然日。 */
    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)
                || startDate.plusDays(MAX_DATE_RANGE_DAYS - 1L).isBefore(endDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
    }

    private Instant toStartAt(LocalDate date) {
        return date.atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
    }

    private Instant toEndAt(LocalDate date) {
        return date.plusDays(1).atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
    }

    private ReportOperationDailyVO toDaily(ReportOperationDailyBO row) {
        long available = value(row.getAvailableSessionCount());
        long booked = value(row.getBookedSessionCount());
        long bookings = value(row.getBookingCount());
        long cancelled = value(row.getCancelledCount());
        return new ReportOperationDailyVO(row.getReportDate(), value(row.getOnlineBookingAmountFen()),
                value(row.getOfflineRegisteredAmountFen()), available, booked, rate(booked, available), bookings,
                cancelled, rate(cancelled, bookings), value(row.getPendingVerificationCount()),
                value(row.getOfflineUnrecordedCount()), value(row.getOfflineUnpaidCount()),
                value(row.getNewMemberCount()), value(row.getRechargeInflowFen()),
                value(row.getBalanceConsumptionFen()));
    }

    private ReportOperationDailyBO emptyDaily(LocalDate date) {
        return new ReportOperationDailyBO(date.toString(), 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private int rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : (int) (numerator * RATE_BASIS_POINTS / denominator);
    }

    private String maskName(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1) + "**";
    }

    private String maskPhone(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() < 7) {
            return "****";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private void writeHeader(Sheet sheet, String[] headers, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(style);
        }
        sheet.createFreezePane(0, 1);
    }

    private void text(Row row, int column, String value) {
        row.createCell(column).setCellValue(safeText(value));
    }

    private String safeText(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' ? "'" + value : value;
    }

    private void date(Row row, int column, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void dateTime(Row row, int column, LocalDateTime value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value);
        }
        cell.setCellStyle(style);
    }

    private void money(Row row, int column, long amountFen, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(amountFen / 100.0D);
        cell.setCellStyle(style);
    }

    private void number(Row row, int column, long value) {
        row.createCell(column).setCellValue(value);
    }

    private byte[] finish(XSSFWorkbook workbook, Sheet sheet, int columnCount) throws IOException {
        for (int index = 0; index < columnCount; index++) {
            sheet.autoSizeColumn(index);
            sheet.setColumnWidth(index, Math.max(MIN_COLUMN_WIDTH,
                    Math.min(MAX_COLUMN_WIDTH, sheet.getColumnWidth(index) + 512)));
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }

    /** 工作簿内复用的最小样式集合。 */
    private static final class ExcelStyles {
        private final CellStyle header;
        private final CellStyle date;
        private final CellStyle dateTime;
        private final CellStyle money;
        private final CellStyle totalMoney;

        private ExcelStyles(XSSFWorkbook workbook) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            header = workbook.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            dateTime = workbook.createCellStyle();
            dateTime.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
            totalMoney = workbook.createCellStyle();
            totalMoney.cloneStyleFrom(money);
            totalMoney.setFont(bold);
        }
    }
}
