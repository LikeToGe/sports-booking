package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 平台创建场馆及首个管理员的请求。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "平台创建场馆请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueCreateRequest {

    /**
     * 场馆资料和预约规则
     */
    @Schema(description = "场馆资料和预约规则")
    @ApiModelProperty(value = "场馆资料和预约规则")
    private VenueSaveRequest venue;

    /**
     * 首个场馆管理员用户名
     */
    @Schema(description = "首个场馆管理员用户名")
    @ApiModelProperty(value = "首个场馆管理员用户名")
    private String adminUsername;

    /**
     * 首个场馆管理员密码
     */
    @Schema(description = "首个场馆管理员密码")
    @ApiModelProperty(value = "首个场馆管理员密码")
    private String adminPassword;
}
