package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 平台可见的场馆级会员经营汇总，不包含个人资料或逐笔流水。
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportVenueMemberOperationVO {
    /** 场馆 ID。 */
    @Schema(description = "场馆 ID")
    @ApiModelProperty(value = "场馆 ID")
    private Long venueId;
    /** 场馆名称。 */
    @Schema(description = "场馆名称")
    @ApiModelProperty(value = "场馆名称")
    private String venueName;
    /** 顾客数。 */
    @Schema(description = "顾客数")
    @ApiModelProperty(value = "顾客数")
    private Long customerCount;
    /** 会员数。 */
    @Schema(description = "会员数")
    @ApiModelProperty(value = "会员数")
    private Long memberCount;
    /** 区间新增会员数。 */
    @Schema(description = "区间新增会员数")
    @ApiModelProperty(value = "区间新增会员数")
    private Long newMemberCount;
    /** 区间复购顾客数。 */
    @Schema(description = "区间复购顾客数")
    @ApiModelProperty(value = "区间复购顾客数")
    private Long repeatCustomerCount;
    /** 复购率，万分比。 */
    @Schema(description = "复购率，万分比")
    @ApiModelProperty(value = "复购率，万分比")
    private Integer repeatPurchaseRateBasisPoints;
    /** 充值实付流入，单位为分。 */
    @Schema(description = "充值实付流入，单位为分")
    @ApiModelProperty(value = "充值实付流入，单位为分")
    private Long rechargeInflowFen;
    /** 余额消费金额，单位为分。 */
    @Schema(description = "余额消费金额，单位为分")
    @ApiModelProperty(value = "余额消费金额，单位为分")
    private Long balanceConsumptionFen;
    /** 当前会员可用余额，单位为分。 */
    @Schema(description = "当前会员可用余额，单位为分")
    @ApiModelProperty(value = "当前会员可用余额，单位为分")
    private Long memberAvailableBalanceFen;
    /** 当前会员冻结余额，单位为分。 */
    @Schema(description = "当前会员冻结余额，单位为分")
    @ApiModelProperty(value = "当前会员冻结余额，单位为分")
    private Long memberFrozenBalanceFen;
}
