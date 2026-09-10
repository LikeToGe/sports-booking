package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 将某天周场次复制到其他星期的请求。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "周场次按星期复制请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CopyDayRequest {

    /**
     * 源星期，取值 1 至 7
     */
    @Schema(description = "源星期，取值 1 至 7")
    @ApiModelProperty(value = "源星期，取值 1 至 7")
    private int sourceDay;

    /**
     * 目标星期列表，取值 1 至 7
     */
    @Schema(description = "目标星期列表，取值 1 至 7")
    @ApiModelProperty(value = "目标星期列表，取值 1 至 7")
    private List<Integer> targetDays;
}
