package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.PlatformCustomerSummaryBO;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 平台场馆级顾客会员数量汇总，不包含个人字段。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformCustomerSummaryVO {
    /** 场馆 ID。 */
    @Schema(description = "场馆 ID")
    @ApiModelProperty(value = "场馆 ID")
    private Long venueId;

    /** 场馆名称。 */
    @Schema(description = "场馆名称")
    @ApiModelProperty(value = "场馆名称")
    private String venueName;

    /** 顾客关系总数，包含已激活会员。 */
    @Schema(description = "顾客关系总数，包含已激活会员")
    @ApiModelProperty(value = "顾客关系总数，包含已激活会员")
    private Long customerCount;

    /** 已激活会员数。 */
    @Schema(description = "已激活会员数")
    @ApiModelProperty(value = "已激活会员数")
    private Long memberCount;

    /**
     * 功能描述: 将无个人信息的数据库投影转换为平台接口模型。
     *
     * @param summary 场馆级数量投影
     * @return 平台数量汇总模型
     * @author gjm
     */
    public static PlatformCustomerSummaryVO from(PlatformCustomerSummaryBO summary) {
        return new PlatformCustomerSummaryVO(summary.getVenueId(), summary.getVenueName(),
                summary.getCustomerCount(), summary.getMemberCount());
    }
}
