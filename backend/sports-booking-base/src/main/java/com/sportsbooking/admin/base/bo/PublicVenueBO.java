package com.sportsbooking.admin.base.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 小程序首页场馆列表项。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicVenueBO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * 封面访问路径
     */
    private String coverPath;

    /**
     * 所在城市
     */
    private String city;

    /**
     * 详细地址
     */
    private String address;

    /**
     * 运动名称列表
     */
    private String sportNames;

    /**
     * 起步价格，单位为分
     */
    private Integer startingPriceFen;

    /**
     * 下一个可预约时间
     */
    private String nextAvailableAt;
}
