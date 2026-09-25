package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.po.Refund;
import com.sportsbooking.admin.base.enums.RefundStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 预约详情中的退款结果接口模型。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundVO {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /** 稳定退款业务号。 */
    @Schema(description = "退款业务号") @ApiModelProperty(value = "退款业务号")
    private String businessNo;

    /**
     * 金额，单位为分
     */
    @Schema(description = "金额，单位为分")
    @ApiModelProperty(value = "金额，单位为分")
    private Integer amountFen;

    /**
     * 原因
     */
    @Schema(description = "原因")
    @ApiModelProperty(value = "原因")
    private String reason;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ApiModelProperty(value = "状态")
    private RefundStatus status;

    /** 已发起退款次数。 */
    @Schema(description = "重试次数") @ApiModelProperty(value = "重试次数")
    private Integer retryCount;

    /** 下次自动重试时间。 */
    @Schema(description = "下次重试时间") @ApiModelProperty(value = "下次重试时间")
    private Instant nextRetryAt;

    /** 最近失败原因。 */
    @Schema(description = "最近失败原因") @ApiModelProperty(value = "最近失败原因")
    private String lastFailureReason;

    /**
     * 退款时间
     */
    @Schema(description = "退款时间")
    @ApiModelProperty(value = "退款时间")
    private Instant refundedAt;

    /**
     * 功能描述: 将退款实体转换为接口模型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param refund 退款持久化实体
     * @return 退款接口模型，记录不存在时返回空
     * @author gjm
     */
    public static RefundVO from(Refund refund) {
        return refund == null ? null : new RefundVO(refund.id, refund.refundNo, refund.amountFen, refund.reason,
                refund.status, refund.retryCount, refund.nextRetryAt, refund.lastFailureReason, refund.refundedAt);
    }
}
