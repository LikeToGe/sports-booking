package com.sportsbooking.admin.biz.pojo.request;

import com.sportsbooking.admin.base.enums.VenueStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 场馆状态更新请求。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "场馆状态更新请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueStatusUpdateRequest {

    /**
     * 目标场馆状态
     */
    @Schema(description = "目标场馆状态")
    @ApiModelProperty(value = "目标场馆状态")
    private VenueStatus status;
}
