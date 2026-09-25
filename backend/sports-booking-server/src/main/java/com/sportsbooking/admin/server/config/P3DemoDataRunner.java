package com.sportsbooking.admin.server.config;

import com.sportsbooking.admin.biz.constants.BusinessConstants;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 功能描述: 为本地演示主场馆补齐最近 90 天和未来 14 天的固定经营样本。
 *
 * @author gjm
 */
@Component
@Order(3)
public class P3DemoDataRunner implements CommandLineRunner {
    private static final String DEMO_USER_OPEN_ID = "p3-demo-member";
    private static final String DEMO_MEMBER_CODE = "P3DEMO000001";
    private static final String DEMO_RECHARGE_NO = "DEMO-P3-RECHARGE";

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public P3DemoDataRunner(JdbcTemplate jdbcTemplate,
                            @Value("${app.demo.seed-enabled}") boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    /**
     * 数据使用稳定业务号幂等写入；清空本地数据库并重启即可得到同一组相对日期样本。
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        Long venueId = firstId("SELECT id FROM s_venue ORDER BY id LIMIT 1");
        if (venueId == null) {
            return;
        }
        Long courtId = firstId("SELECT id FROM s_court WHERE venue_id = ? ORDER BY id LIMIT 1", venueId);
        Long sportId = firstId("""
                SELECT cs.sport_type_id FROM s_court_sport cs
                WHERE cs.court_id = ? ORDER BY cs.sport_type_id LIMIT 1
                """, courtId);
        if (courtId == null || sportId == null) {
            return;
        }

        DemoMember member = ensureMember(venueId);
        ensureRecharge(member.customerId());
        LocalDate today = LocalDate.now(BusinessConstants.BUSINESS_ZONE);
        for (int offset = -89; offset <= 14; offset++) {
            seedDay(venueId, courtId, sportId, member, today.plusDays(offset), offset);
        }
    }

    /** 创建稳定的演示会员。 */
    private DemoMember ensureMember(long venueId) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO s_user_account
                    (mock_openid, contact_name, phone, status, created_at, updated_at)
                VALUES (?, '演示会员', '13800000001', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, DEMO_USER_OPEN_ID);
        long userId = firstId("SELECT id FROM s_user_account WHERE mock_openid = ?", DEMO_USER_OPEN_ID);
        jdbcTemplate.update("""
                INSERT IGNORE INTO s_venue_customer
                    (venue_id, user_id, status, member_code, activated_at, created_at, updated_at)
                VALUES (?, ?, 'MEMBER', ?, UTC_TIMESTAMP(3) - INTERVAL 89 DAY,
                        UTC_TIMESTAMP(3) - INTERVAL 89 DAY, UTC_TIMESTAMP(3))
                """, venueId, userId, DEMO_MEMBER_CODE);
        long customerId = firstId("""
                SELECT id FROM s_venue_customer WHERE venue_id = ? AND user_id = ?
                """, venueId, userId);
        return new DemoMember(userId, customerId);
    }

    /** 创建充值、实付与赠送批次以及入账流水。 */
    private void ensureRecharge(long customerId) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO s_member_recharge
                    (recharge_no, venue_customer_id, plan_id, plan_name, paid_amount_fen, gift_amount_fen,
                     gift_valid_days, channel, operator_admin_id, client_idempotency_key, status,
                     offline_funds_handled, succeeded_at, created_at, updated_at)
                VALUES (?, ?, NULL, '充500赠50', 50000, 5000, 30, 'CASH',
                        (SELECT id FROM s_admin_account WHERE venue_id =
                            (SELECT venue_id FROM s_venue_customer WHERE id = ?) ORDER BY id LIMIT 1),
                        'p3-demo-recharge', 'SUCCESS', 1, UTC_TIMESTAMP(3) - INTERVAL 20 DAY,
                        UTC_TIMESTAMP(3) - INTERVAL 20 DAY, UTC_TIMESTAMP(3))
                """, DEMO_RECHARGE_NO, customerId, customerId);
        long rechargeId = firstId("SELECT id FROM s_member_recharge WHERE recharge_no = ?", DEMO_RECHARGE_NO);
        ensureBalanceLot(customerId, rechargeId, "PAID", 50000, 45000, null);
        ensureBalanceLot(customerId, rechargeId, "GIFT", 5000, 5000,
                Instant.now().plusSeconds(10L * 24 * 60 * 60));
    }

    /** 创建单个余额批次及唯一入账流水。 */
    private void ensureBalanceLot(long customerId, long rechargeId, String type,
                                  int originalFen, int availableFen, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO s_member_balance_lot
                    (venue_customer_id, source_recharge_id, type, original_amount_fen, available_amount_fen,
                     frozen_amount_fen, status, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 0, 'ACTIVE', ?,
                        UTC_TIMESTAMP(3) - INTERVAL 20 DAY, UTC_TIMESTAMP(3))
                """, customerId, rechargeId, type, originalFen, availableFen,
                expiresAt == null ? null : Timestamp.from(expiresAt));
        long lotId = firstId("""
                SELECT id FROM s_member_balance_lot WHERE source_recharge_id = ? AND type = ?
                """, rechargeId, type);
        jdbcTemplate.update("""
                INSERT IGNORE INTO s_member_balance_flow
                    (venue_customer_id, lot_id, direction, amount_fen, business_type, recharge_id,
                     operator_admin_id, idempotency_key, created_at)
                VALUES (?, ?, 'CREDIT', ?, 'RECHARGE', ?,
                        (SELECT operator_admin_id FROM s_member_recharge WHERE id = ?), ?,
                        UTC_TIMESTAMP(3) - INTERVAL 20 DAY)
                """, customerId, lotId, originalFen, rechargeId, rechargeId,
                "p3-demo-recharge:" + type.toLowerCase());
    }

    /** 创建某日场次，并按固定模数覆盖线上、线下、取消和未付场景。 */
    private void seedDay(long venueId, long courtId, long sportId, DemoMember member,
                         LocalDate date, int offset) {
        Instant startAt = date.atTime(LocalTime.of(22, 15)).atZone(BusinessConstants.BUSINESS_ZONE).toInstant();
        Instant endAt = startAt.plusSeconds(3600);
        String slotStatus = offset > 0 ? "OPEN" : "BOOKED";
        jdbcTemplate.update("""
                INSERT IGNORE INTO s_session_slot
                    (venue_id, court_id, sport_type_id, weekly_slot_id, slot_date, start_at, end_at,
                     price_fen, status, created_at, updated_at)
                VALUES (?, ?, ?, NULL, ?, ?, ?, 8000, ?, ?, ?)
                """, venueId, courtId, sportId, date, Timestamp.from(startAt), Timestamp.from(endAt),
                slotStatus, Timestamp.from(startAt.minusSeconds(7 * 24 * 60 * 60L)), Timestamp.from(startAt));
        if (offset > 0) {
            return;
        }
        long slotId = firstId("""
                SELECT id FROM s_session_slot WHERE court_id = ? AND start_at = ? AND end_at = ?
                """, courtId, Timestamp.from(startAt), Timestamp.from(endAt));
        if (Math.floorMod(offset, 3) == 0) {
            seedOfflineBooking(venueId, courtId, sportId, slotId, date, startAt, endAt, offset);
        } else {
            seedOnlineBooking(venueId, courtId, sportId, slotId, member, date, startAt, endAt, offset);
        }
    }

    /** 创建线下登记样本，轮换未记录、未付、已付与已退款状态。 */
    private void seedOfflineBooking(long venueId, long courtId, long sportId, long slotId,
                                    LocalDate date, Instant startAt, Instant endAt, int offset) {
        String bookingNo = "DEMO-P3-F-" + date.toString().replace("-", "");
        String[] paymentStatuses = {"UNRECORDED", "UNPAID", "PAID", "REFUNDED"};
        String paymentStatus = paymentStatuses[Math.floorMod(offset / 3, paymentStatuses.length)];
        insertBooking(bookingNo, null, null, venueId, courtId, sportId, slotId, "OFFLINE",
                "线下演示顾客", "13800000002", startAt, endAt, 8000, 0, 0,
                "ENDED_UNTRACKED", paymentStatus, date);
        linkSlot(slotId, bookingNo);
    }

    /** 创建线上经营样本；每十天一笔取消退款，其余覆盖完成和未到场。 */
    private void seedOnlineBooking(long venueId, long courtId, long sportId, long slotId, DemoMember member,
                                   LocalDate date, Instant startAt, Instant endAt, int offset) {
        String bookingNo = "DEMO-P3-O-" + date.toString().replace("-", "");
        boolean cancelled = Math.floorMod(offset, 10) == 0;
        String status = cancelled ? "CANCELLED" : Math.floorMod(offset, 7) == 0 ? "NO_SHOW" : "COMPLETED";
        String paymentStatus = cancelled ? "REFUNDED" : "PAID";
        insertBooking(bookingNo, member.userId(), member.customerId(), venueId, courtId, sportId, slotId,
                "ONLINE", "演示会员", "13800000001", startAt, endAt, 8000, 8000, 525,
                status, paymentStatus, date);
        long bookingId = firstId("SELECT id FROM s_booking WHERE booking_no = ?", bookingNo);
        jdbcTemplate.update("""
                INSERT IGNORE INTO s_payment
                    (booking_id, payment_no, provider, provider_no, amount_fen, status, paid_at, created_at, updated_at)
                VALUES (?, ?, 'MOCK', ?, 8000, 'SUCCESS', ?, ?, ?)
                """, bookingId, "PAY-" + bookingNo, "MOCK-" + bookingNo,
                Timestamp.from(startAt.minusSeconds(24 * 60 * 60L)),
                Timestamp.from(startAt.minusSeconds(24 * 60 * 60L)), Timestamp.from(startAt));
        if (cancelled) {
            long paymentId = firstId("SELECT id FROM s_payment WHERE booking_id = ?", bookingId);
            jdbcTemplate.update("""
                    INSERT IGNORE INTO s_refund
                        (booking_id, payment_id, refund_no, amount_fen, reason, status, retry_count,
                         refunded_at, created_at, updated_at)
                    VALUES (?, ?, ?, 8000, '演示取消', 'SUCCESS', 0, ?, ?, ?)
                    """, bookingId, paymentId, "REF-" + bookingNo,
                    Timestamp.from(startAt.minusSeconds(12 * 60 * 60L)),
                    Timestamp.from(startAt.minusSeconds(12 * 60 * 60L)), Timestamp.from(startAt));
            jdbcTemplate.update("UPDATE s_session_slot SET status = 'OPEN', hold_booking_id = NULL WHERE id = ?",
                    slotId);
        } else {
            linkSlot(slotId, bookingNo);
        }
    }

    /** 写入预约快照；表名称来自数据库，值全部通过预编译参数传入。 */
    private void insertBooking(String bookingNo, Long userId, Long customerId, long venueId, long courtId,
                               long sportId, long slotId, String source, String contactName, String contactPhone,
                               Instant startAt, Instant endAt, int amountFen, int externalFen, int feeRate,
                               String status, String paymentStatus, LocalDate date) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO s_booking
                    (booking_no, user_id, venue_customer_id, venue_id, court_id, sport_type_id, session_slot_id,
                     source, contact_name, contact_phone, venue_name, court_name, sport_name, start_at, end_at,
                     amount_fen, balance_amount_fen, gift_balance_amount_fen, paid_balance_amount_fen,
                     external_amount_fen, service_fee_rate_basis_points, status, payment_status,
                     completed_at, created_at, updated_at)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, v.name, c.name, s.name, ?, ?, ?, 0, 0, 0, ?, ?, ?, ?,
                       CASE WHEN ? IN ('COMPLETED', 'ENDED_UNTRACKED') THEN ? ELSE NULL END,
                       ?, ?
                FROM s_venue v, s_court c, s_sport_type s
                WHERE v.id = ? AND c.id = ? AND s.id = ?
                """, bookingNo, userId, customerId, venueId, courtId, sportId, slotId, source,
                contactName, contactPhone, Timestamp.from(startAt), Timestamp.from(endAt), amountFen,
                externalFen, feeRate, status, paymentStatus, status, Timestamp.from(endAt),
                Timestamp.valueOf(date.atTime(12, 0)), Timestamp.from(startAt), venueId, courtId, sportId);
    }

    /** 把已履约预约关联回库存场次。 */
    private void linkSlot(long slotId, String bookingNo) {
        jdbcTemplate.update("""
                UPDATE s_session_slot SET status = 'BOOKED', hold_booking_id =
                    (SELECT id FROM s_booking WHERE booking_no = ?), updated_at = UTC_TIMESTAMP(3)
                WHERE id = ?
                """, bookingNo, slotId);
    }

    private Long firstId(String sql, Object... args) {
        return jdbcTemplate.query(sql, result -> result.next() ? result.getLong(1) : null, args);
    }

    private static class DemoMember {
        private final long userId;
        private final long customerId;

        private DemoMember(long userId, long customerId) {
            this.userId = userId;
            this.customerId = customerId;
        }

        private long userId() {
            return userId;
        }

        private long customerId() {
            return customerId;
        }
    }
}
