package com.sportsbooking.admin.base.bo;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/** 场馆正常公开评价的评分汇总。 */
@Data
public class VenueReviewSummaryBO {
    private Long reviewCount;
    private BigDecimal overallScore;
    private BigDecimal courtScore;
    private BigDecimal environmentScore;
    private BigDecimal serviceScore;
    private List<ReviewTagCountBO> tagCounts;
}
