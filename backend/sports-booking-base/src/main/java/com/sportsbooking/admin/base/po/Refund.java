package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.time.LocalDateTime;
import com.sportsbooking.admin.base.enums.RefundStatus;

/**
 * 功能描述: 全额退款记录持久化实体。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@TableName("s_refund")
public class Refund {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 关联预约 ID。 */
    public Long bookingId;
    /** 关联原支付记录 ID。 */
    public Long paymentId;
    /** 稳定退款业务号。 */
    public String refundNo;
    /** 退款金额，单位为分。 */
    public Integer amountFen;
    /** 退款原因。 */
    public String reason;
    /** 退款状态。 */
    public RefundStatus status;
    /** 已发起退款次数。 */
    public Integer retryCount;
    /** 下次自动重试时间。 */
    public Instant nextRetryAt;
    /** 最近失败原因。 */
    public String lastFailureReason;
    /** 退款完成时间。 */
    public Instant refundedAt;
    /** 创建时间。 */
    public LocalDateTime createdAt;
    /** 最后更新时间。 */
    public LocalDateTime updatedAt;
}
