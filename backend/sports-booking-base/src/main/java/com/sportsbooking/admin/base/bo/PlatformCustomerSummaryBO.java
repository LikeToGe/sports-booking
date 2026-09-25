package com.sportsbooking.admin.base.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 平台场馆级顾客会员数量投影，不包含个人资料。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformCustomerSummaryBO {
    /** 场馆 ID。 */
    private Long venueId;
    /** 场馆名称。 */
    private String venueName;
    /** 顾客关系总数，包含已激活会员。 */
    private Long customerCount;
    /** 已激活会员数。 */
    private Long memberCount;
}
