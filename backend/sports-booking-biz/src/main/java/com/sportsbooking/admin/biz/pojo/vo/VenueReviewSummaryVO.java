package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.VenueReviewSummaryBO;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 仅由正常公开评价计算的场馆评分。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueReviewSummaryVO {
    @Schema(description = "公开评价数量") @ApiModelProperty("公开评价数量") private Long reviewCount;
    @Schema(description = "总体平均评分") @ApiModelProperty("总体平均评分") private BigDecimal overallScore;
    @Schema(description = "场地平均评分") @ApiModelProperty("场地平均评分") private BigDecimal courtScore;
    @Schema(description = "环境平均评分") @ApiModelProperty("环境平均评分") private BigDecimal environmentScore;
    @Schema(description = "服务平均评分") @ApiModelProperty("服务平均评分") private BigDecimal serviceScore;
    @Schema(description = "标签分布") @ApiModelProperty("标签分布") private List<ReviewTagCountVO> tagCounts;

    public static VenueReviewSummaryVO from(VenueReviewSummaryBO summary) {
        List<ReviewTagCountVO> tagCounts = summary.getTagCounts() == null ? List.of()
                : summary.getTagCounts().stream().map(ReviewTagCountVO::from).toList();
        return new VenueReviewSummaryVO(summary.getReviewCount(), summary.getOverallScore(),
                summary.getCourtScore(), summary.getEnvironmentScore(), summary.getServiceScore(), tagCounts);
    }
}
