package com.sportsbooking.admin.server.config;

import com.sportsbooking.admin.biz.service.ScheduleService;
import com.sportsbooking.admin.biz.pojo.request.WeeklySlotSaveRequest;
import com.sportsbooking.admin.biz.pojo.vo.SessionSlotVO;
import com.sportsbooking.admin.biz.service.BookingService;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import com.sportsbooking.admin.base.enums.VenueStatus;
import com.sportsbooking.admin.biz.pojo.request.VenueSaveRequest;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.service.VenueCatalogService;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.base.po.SportType;
import com.sportsbooking.admin.base.po.Venue;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 功能描述: 仅在本地演示开关启用且数据库为空时初始化可操作数据。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Component
@Profile("demo")
@Order(2)
public class DemoDataRunner implements CommandLineRunner {
    private final VenueService venueService;
    private final VenueCatalogService venueCatalogService;
    private final ScheduleService scheduleService;
    private final BookingService bookingService;
    private final BookingMapper bookingMapper;
    private final boolean enabled;

    public DemoDataRunner(VenueService venueService, VenueCatalogService venueCatalogService,
                          ScheduleService scheduleService, BookingService bookingService, BookingMapper bookingMapper,
                          @Value("${app.demo.seed-enabled}") boolean enabled) {
        this.venueService = venueService;
        this.venueCatalogService = venueCatalogService;
        this.scheduleService = scheduleService;
        this.bookingService = bookingService;
        this.bookingMapper = bookingMapper;
        this.enabled = enabled;
    }

    /**
     * 功能描述: 数据库为空时初始化本地演示场馆、排期和预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        if (venueService.listPlatformVenues().isEmpty()) {
            List<SportType> sports = venueCatalogService.listGlobalSports();
            SportType basketball = findSport(sports, "篮球", "篮球1");
            SportType badminton = findSport(sports, "羽毛球");
            SportType tennis = findSport(sports, "网球");
            SportType tableTennis = findSport(sports, "乒乓球");
            seedVenue("郑东运动中心", "郑东新区商务内环路 22 号", "0371-5555-1001",
                    new BigDecimal("34.7751000"), new BigDecimal("113.7285000"), "venue1", badminton, badminton);
            seedVenue("二七全民健身馆", "二七区大学中路 86 号", "0371-5555-1002",
                    new BigDecimal("34.7138000"), new BigDecimal("113.6389000"), "venue2", badminton, tableTennis);
            seedVenue("高新活力球馆", "高新区科学大道 57 号", "0371-5555-1003",
                    new BigDecimal("34.8105000"), new BigDecimal("113.5696000"), "venue3", basketball, tennis);
        }
        if (bookingMapper.selectCount(null) == 0) {
            for (Venue venue : venueService.listPlatformVenues()) {
                createDemoBooking(venue);
            }
        }
    }

    /**
     * 演示场景固定使用真实运动名称，避免依赖数据库插入顺序把场馆配到测试或停用类型。
     */
    private SportType findSport(List<SportType> sports, String... names) {
        return sports.stream()
                .filter(item -> java.util.Arrays.stream(names).anyMatch(name -> name.equals(item.name)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("演示数据缺少运动类型: " + String.join(" / ", names)));
    }

    /**
     * 功能描述: 创建演示场馆、管理员、两个场地及未来周场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private void seedVenue(String name, String address, String phone, BigDecimal latitude, BigDecimal longitude,
                           String adminUsername, SportType firstSport, SportType secondSport) {
        Venue venue = venueService.createVenue(new VenueSaveRequest(
                name, BusinessConstants.DEFAULT_CITY, address, latitude, longitude, phone,
                "更衣室、淋浴、停车场、饮水", "请提前 15 分钟到场，穿着适合场地的运动鞋。",
                7, 30, 120
        ), adminUsername, "venue123");
        venueService.updateVenueStatus(venue.id, VenueStatus.ACTIVE);

        Court firstCourt = venueCatalogService.createCourt(venue.id, firstSport.name + " 1 号场",
                List.of(firstSport.id));
        // 同运动的两片场地使用不同序号，避免触发场馆内名称唯一约束。
        String secondCourtName = secondSport.name + (firstSport.id.equals(secondSport.id) ? " 2 号场" : " 1 号场");
        Court secondCourt = venueCatalogService.createCourt(venue.id, secondCourtName,
                List.of(secondSport.id));
        for (int day = 1; day <= 7; day++) {
            addSlot(venue.id, firstCourt.id, firstSport.id, day, 9, 10, 6000);
            addSlot(venue.id, firstCourt.id, firstSport.id, day, 14, 15, 7000);
            addSlot(venue.id, firstCourt.id, firstSport.id, day, 19, 20, 9000);
            addSlot(venue.id, secondCourt.id, secondSport.id, day, 10, 11, 5000);
            addSlot(venue.id, secondCourt.id, secondSport.id, day, 16, 17, 6500);
            addSlot(venue.id, secondCourt.id, secondSport.id, day, 20, 21, 8000);
        }
    }

    /**
     * 功能描述: 保存一条演示周场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private void addSlot(long venueId, long courtId, long sportId, int day, int startHour, int endHour,
                         int priceFen) {
        scheduleService.saveWeekly(venueId, null, new WeeklySlotSaveRequest(
                courtId, sportId, day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0), priceFen
        ));
    }

    /**
     * 功能描述: 为今日场次页面选择最近开放场次创建一笔线下预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private void createDemoBooking(Venue venue) {
        LocalDate date = LocalDate.now(BusinessConstants.BUSINESS_ZONE);
        Instant earliestStart = Instant.now().plus(Duration.ofMinutes(venue.minBookingMinutes));
        for (int offset = 0; offset < 2; offset++) {
            List<SessionSlotVO> sessions = scheduleService.listDay(venue.id, date.plusDays(offset));
            for (SessionSlotVO session : sessions) {
                // 演示预约也需满足场馆最晚预约规则，跳过今日已过期或临近开始的场次。
                if (session.getStatus() == SessionSlotStatus.OPEN
                        && OffsetDateTime.parse(session.getStartAt()).toInstant().isAfter(earliestStart)) {
                    bookingService.createOffline(venue.id, session.getId(),
                            "本地演示用户", "13800000000", BookingPaymentStatus.UNPAID, null);
                    return;
                }
            }
        }
    }
}
