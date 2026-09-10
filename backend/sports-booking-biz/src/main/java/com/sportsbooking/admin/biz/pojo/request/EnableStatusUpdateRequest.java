package com.sportsbooking.admin.biz.pojo.request;

import com.sportsbooking.admin.base.enums.EnableStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 启停对象状态更新请求。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "启停状态更新请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnableStatusUpdateRequest {

    /**
     * 目标启停状态
     */
    @Schema(description = "目标启停状态")
    @ApiModelProperty(value = "目标启停状态")
    private EnableStatus status;
}
