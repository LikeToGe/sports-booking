package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 场馆取消预约请求。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "场馆取消预约请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingCancelRequest {

    /**
     * 取消原因
     */
    @Schema(description = "取消原因")
    @ApiModelProperty(value = "取消原因")
    private String reason;
}
