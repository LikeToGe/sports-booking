package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 平台更新场馆服务费率请求。
 * 创建时间: 2026/09/08
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceFeeRateUpdateRequest {

    /**
     * 服务费率百分比，支持两位小数
     */
    @Schema(description = "服务费率百分比，范围 0 至 30，支持两位小数")
    @ApiModelProperty(value = "服务费率百分比，范围 0 至 30，支持两位小数")
    private BigDecimal serviceFeeRatePercent;
}
