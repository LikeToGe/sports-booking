package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 小程序创建待支付预约请求。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "小程序预约创建请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreateRequest {

    /**
     * 具体场次 ID
     */
    @Schema(description = "具体场次 ID")
    @ApiModelProperty(value = "具体场次 ID")
    private long sessionSlotId;

    /**
     * 联系人姓名
     */
    @Schema(description = "联系人姓名")
    @ApiModelProperty(value = "联系人姓名")
    private String contactName;

    /**
     * 联系人手机号
     */
    @Schema(description = "联系人手机号")
    @ApiModelProperty(value = "联系人手机号")
    private String contactPhone;
}
