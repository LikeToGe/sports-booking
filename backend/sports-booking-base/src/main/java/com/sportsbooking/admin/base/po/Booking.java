package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.time.LocalDateTime;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.BookingPaymentStatus;
import com.sportsbooking.admin.base.enums.CancelledBy;

/**
 * 功能描述: 预约及下单快照持久化实体。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@TableName("s_booking")
public class Booking {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 对外展示的预约单号。 */
    public String bookingNo;
    /** 下单用户 ID，场馆线下代订时为空。 */
    public Long userId;
    /** 关联的场馆级顾客档案 ID。 */
    public Long venueCustomerId;
    /** 所属场馆 ID。 */
    public Long venueId;
    /** 预约场地 ID。 */
    public Long courtId;
    /** 预约运动类型 ID。 */
    public Long sportTypeId;
    /** 预约具体场次 ID。 */
    public Long sessionSlotId;
    /** 预约来源。 */
    public BookingSource source;
    /** 预约联系人姓名。 */
    public String contactName;
    /** 预约联系人手机号。 */
    public String contactPhone;
    /** 下单时的场馆名称快照。 */
    public String venueName;
    /** 下单时的场地名称快照。 */
    public String courtName;
    /** 下单时的运动类型名称快照。 */
    public String sportName;
    /** 预约开始时间。 */
    public Instant startAt;
    /** 预约结束时间。 */
    public Instant endAt;
    /** 预约金额，单位为分。 */
    public Integer amountFen;
    /** 固定余额支付总额，单位为分。 */
    public Integer balanceAmountFen;
    /** 固定赠送余额支付额，单位为分。 */
    public Integer giftBalanceAmountFen;
    /** 固定实付余额支付额，单位为分。 */
    public Integer paidBalanceAmountFen;
    /** 固定外部支付额，单位为分。 */
    public Integer externalAmountFen;
    /** 创建预约时的平台服务费率快照，单位为基点。 */
    public Integer serviceFeeRateBasisPoints;
    /** 预约状态。 */
    public BookingStatus status;
    /** 预约整体收款或线下登记收款状态。 */
    public BookingPaymentStatus paymentStatus;
    /** 待支付预约的支付截止时间。 */
    public Instant paymentExpiresAt;
    /** 客户端创建幂等键。 */
    public String clientIdempotencyKey;
    /** 可选预约入口，仅用于关联预约状态观察转化，不参与计价。 */
    public String entryType;
    /** 取消操作方。 */
    public CancelledBy cancelledBy;
    /** 取消原因。 */
    public String cancelReason;
    /** 取消时间。 */
    public Instant cancelledAt;
    /** 完成时间。 */
    public Instant completedAt;
    /** 创建时间。 */
    public LocalDateTime createdAt;
    /** 最后更新时间。 */
    public LocalDateTime updatedAt;
}
