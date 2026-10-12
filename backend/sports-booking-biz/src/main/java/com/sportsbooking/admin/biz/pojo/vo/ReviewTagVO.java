package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.po.ReviewTag;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 固定评价标签接口模型。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTagVO {
    @Schema(description = "标签 ID") @ApiModelProperty("标签 ID") private Long id;
    @Schema(description = "标签名称") @ApiModelProperty("标签名称") private String name;
    @Schema(description = "标签状态") @ApiModelProperty("标签状态") private String status;
    @Schema(description = "适用平台运动类型 ID") @ApiModelProperty("适用平台运动类型 ID")
    private List<Long> sportTypeIds;

    public static ReviewTagVO from(ReviewTag tag, List<Long> sportTypeIds) {
        return new ReviewTagVO(tag.id, tag.name, tag.status, sportTypeIds);
    }
}
