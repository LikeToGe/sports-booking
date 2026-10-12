package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 发布或修改场地评价的内容。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueReviewRequest {
    /** 评价正文。 */
    @Schema(description = "评价正文") @ApiModelProperty("评价正文")
    private String body;
    /** 总体评分，1 至 5。 */
    @Schema(description = "总体评分，1 至 5") @ApiModelProperty("总体评分，1 至 5")
    private Integer overallScore;
    /** 场地评分，1 至 5。 */
    @Schema(description = "场地评分，1 至 5") @ApiModelProperty("场地评分，1 至 5")
    private Integer courtScore;
    /** 环境评分，1 至 5。 */
    @Schema(description = "环境评分，1 至 5") @ApiModelProperty("环境评分，1 至 5")
    private Integer environmentScore;
    /** 服务评分，1 至 5。 */
    @Schema(description = "服务评分，1 至 5") @ApiModelProperty("服务评分，1 至 5")
    private Integer serviceScore;
    /** 是否匿名公开。 */
    @Schema(description = "是否匿名公开") @ApiModelProperty("是否匿名公开")
    private Boolean anonymous;
    /** 最多三个有效的固定评价标签 ID。 */
    @Schema(description = "固定评价标签 ID") @ApiModelProperty("固定评价标签 ID")
    private List<Long> tagIds;
    /** null 沿用旧版本，空集合移除图片。 */
    @Schema(description = "本次新上传的图片 ID；修改时不传则沿用旧版本")
    @ApiModelProperty("本次新上传的图片 ID") private List<Long> uploadIds;
}
