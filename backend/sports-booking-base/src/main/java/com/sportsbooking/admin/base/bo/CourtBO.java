package com.sportsbooking.admin.base.bo;

import com.sportsbooking.admin.base.enums.EnableStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 场馆后台场地列表项。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourtBO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * 场地图片公开路径
     */
    private String imagePath;

    /**
     * 状态
     */
    private EnableStatus status;

    /**
     * 运动名称列表
     */
    private String sportNames;

    /**
     * 运动类型 ID 列表
     */
    private String sportIds;
}
