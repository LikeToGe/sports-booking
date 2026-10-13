package com.sportsbooking.admin.biz.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 用户预约详情聚合模型。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingDetailVO {

    /**
     * 预约信息
     */
    @Schema(description = "预约信息")
    @ApiModelProperty(value = "预约信息")
    private BookingVO booking;

    /** 当前场馆导航与入场资料；预约时间、价格仍以预约快照为准。 */
    @Schema(description = "当前场馆导航与入场资料")
    @ApiModelProperty(value = "当前场馆导航与入场资料")
    private VenueVO venue;

    /**
     * 支付信息
     */
    @Schema(description = "支付信息")
    @ApiModelProperty(value = "支付信息")
    private PaymentVO payment;

    /**
     * 退款信息
     */
    @Schema(description = "退款信息")
    @ApiModelProperty(value = "退款信息")
    private RefundVO refund;

    /** 固定支付组成。 */
    @Schema(description = "固定支付组成")
    @ApiModelProperty(value = "固定支付组成")
    private PaymentCompositionVO composition;

    /**
     * 核销凭证
     */
    @Schema(description = "核销凭证")
    @ApiModelProperty(value = "核销凭证")
    private CredentialVO credential;
}
