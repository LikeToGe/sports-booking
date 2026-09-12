package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 场馆修改线下预约联系人请求。
 * 创建时间: 2026/09/11
 *
 * @author gjm
 */
@Schema(description = "线下预约联系人修改请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingContactUpdateRequest {

    /** 联系人姓名。 */
    @Schema(description = "联系人姓名")
    @ApiModelProperty(value = "联系人姓名")
    private String contactName;

    /** 联系人手机号。 */
    @Schema(description = "联系人手机号")
    @ApiModelProperty(value = "联系人手机号")
    private String contactPhone;
}
