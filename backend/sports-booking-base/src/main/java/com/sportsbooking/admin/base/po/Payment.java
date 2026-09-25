package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.time.LocalDateTime;
import com.sportsbooking.admin.base.enums.PaymentProvider;
import com.sportsbooking.admin.base.enums.PaymentStatus;

/**
 * 功能描述: 支付记录持久化实体。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@TableName("s_payment")
public class Payment {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 关联预约 ID。 */
    public Long bookingId;
    /** 稳定支付业务号。 */
    public String paymentNo;
    /** 支付渠道。 */
    public PaymentProvider provider;
    /** 支付渠道交易号。 */
    public String providerNo;
    /** 支付金额，单位为分。 */
    public Integer amountFen;
    /** 支付状态。 */
    public PaymentStatus status;
    /** 支付完成时间。 */
    public Instant paidAt;
    /** 支付关闭时间。 */
    public Instant closedAt;
    /** 最近渠道查单时间。 */
    public Instant lastQueryAt;
    /** 创建时间。 */
    public LocalDateTime createdAt;
    /** 最后更新时间。 */
    public LocalDateTime updatedAt;
}
