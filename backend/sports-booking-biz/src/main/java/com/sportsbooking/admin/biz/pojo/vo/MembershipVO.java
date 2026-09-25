package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.VenueCustomerStatus;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 小程序当前用户的场馆顾客或会员关系。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MembershipVO {
    /** 场馆 ID。 */
    @Schema(description = "场馆 ID")
    @ApiModelProperty(value = "场馆 ID")
    private Long venueId;

    /** 场馆名称。 */
    @Schema(description = "场馆名称")
    @ApiModelProperty(value = "场馆名称")
    private String venueName;

    /** 顾客或会员状态。 */
    @Schema(description = "顾客或会员状态")
    @ApiModelProperty(value = "顾客或会员状态")
    private VenueCustomerStatus status;

    /** 场馆内会员码。 */
    @Schema(description = "场馆内会员码")
    @ApiModelProperty(value = "场馆内会员码")
    private String memberCode;

    /** 会员码二维码 PNG Data URL。 */
    @Schema(description = "会员码二维码 PNG Data URL")
    @ApiModelProperty(value = "会员码二维码 PNG Data URL")
    private String memberQrDataUrl;

    /** P1 尚未启用储值，固定为 0。 */
    @Schema(description = "实付余额，单位分；P1 固定为 0")
    @ApiModelProperty(value = "实付余额，单位分；P1 固定为 0")
    private Long paidBalanceFen;

    /** P1 尚未启用赠送余额，固定为 0。 */
    @Schema(description = "赠送余额，单位分；P1 固定为 0")
    @ApiModelProperty(value = "赠送余额，单位分；P1 固定为 0")
    private Long giftBalanceFen;

    /** 最近到期赠送余额。 */
    @Schema(description = "最近到期赠送余额，单位分")
    @ApiModelProperty(value = "最近到期赠送余额，单位分")
    private Long expiringGiftBalanceFen;

    /** 最近赠送余额到期时间。 */
    @Schema(description = "最近赠送余额到期时间")
    @ApiModelProperty(value = "最近赠送余额到期时间")
    private java.time.Instant nextGiftExpiresAt;
}
