package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.enums.PaymentProvider;
import com.sportsbooking.admin.base.enums.PaymentStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 预约详情中的支付结果接口模型。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVO {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /** 稳定支付业务号。 */
    @Schema(description = "支付业务号") @ApiModelProperty(value = "支付业务号")
    private String businessNo;

    /**
     * 支付渠道
     */
    @Schema(description = "支付渠道")
    @ApiModelProperty(value = "支付渠道")
    private PaymentProvider provider;

    /**
     * 支付渠道流水号
     */
    @Schema(description = "支付渠道流水号")
    @ApiModelProperty(value = "支付渠道流水号")
    private String providerNo;

    /**
     * 金额，单位为分
     */
    @Schema(description = "金额，单位为分")
    @ApiModelProperty(value = "金额，单位为分")
    private Integer amountFen;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ApiModelProperty(value = "状态")
    private PaymentStatus status;

    /**
     * 支付时间
     */
    @Schema(description = "支付时间")
    @ApiModelProperty(value = "支付时间")
    private Instant paidAt;

    /**
     * 功能描述: 将支付实体转换为接口模型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param payment 支付持久化实体
     * @return 支付接口模型，记录不存在时返回空
     * @author gjm
     */
    public static PaymentVO from(Payment payment) {
        return payment == null ? null : new PaymentVO(payment.id, payment.paymentNo, payment.provider, payment.providerNo,
                payment.amountFen, payment.status, payment.paidAt);
    }
}
