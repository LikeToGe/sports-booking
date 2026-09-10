package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 关闭指定日期全部开放场次的请求。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "关闭指定日期场次请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CloseDayRequest {

    /**
     * 需要关闭场次的日期
     */
    @Schema(description = "需要关闭场次的日期")
    @ApiModelProperty(value = "需要关闭场次的日期")
    private LocalDate date;
}
