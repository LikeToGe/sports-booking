package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 平台固定评价标签维护请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTagRequest {
    /** 标签名称。 */
    @Schema(description = "标签名称") @ApiModelProperty("标签名称") private String name;
    /** 适用的平台运动类型 ID。 */
    @Schema(description = "适用平台运动类型 ID") @ApiModelProperty("适用平台运动类型 ID")
    private List<Long> sportTypeIds;
    /** ACTIVE 或 DISABLED。 */
    @Schema(description = "标签状态") @ApiModelProperty("标签状态") private String status;
}
