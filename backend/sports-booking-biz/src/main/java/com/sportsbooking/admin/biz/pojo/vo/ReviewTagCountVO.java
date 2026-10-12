package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.ReviewTagCountBO;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 公开评价标签统计。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTagCountVO {
    @Schema(description = "标签 ID") @ApiModelProperty("标签 ID") private Long tagId;
    @Schema(description = "标签名称") @ApiModelProperty("标签名称") private String tagName;
    @Schema(description = "使用评价数") @ApiModelProperty("使用评价数") private Long reviewCount;

    public static ReviewTagCountVO from(ReviewTagCountBO row) {
        return new ReviewTagCountVO(row.getTagId(), row.getTagName(), row.getReviewCount());
    }
}
