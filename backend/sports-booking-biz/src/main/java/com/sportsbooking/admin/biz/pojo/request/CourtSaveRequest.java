package com.sportsbooking.admin.biz.pojo.request;

import com.sportsbooking.admin.base.enums.EnableStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 场地及支持运动保存请求。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "场地保存请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourtSaveRequest {

    /**
     * 场地名称
     */
    @Schema(description = "场地名称")
    @ApiModelProperty(value = "场地名称")
    private String name;

    /**
     * 场地支持的运动类型 ID 列表
     */
    @Schema(description = "场地支持的运动类型 ID 列表")
    @ApiModelProperty(value = "场地支持的运动类型 ID 列表")
    private List<Long> sportIds;

    /**
     * 场地启停状态，更新时使用
     */
    @Schema(description = "场地启停状态，更新时使用")
    @ApiModelProperty(value = "场地启停状态，更新时使用")
    private EnableStatus status;
}
