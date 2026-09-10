package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.base.po.SportType;
import com.sportsbooking.admin.biz.pojo.vo.CourtVO;
import java.util.List;

/**
 * 功能描述: 场馆运动类型与物理场地业务接口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface VenueCatalogService {

    /**
     * 功能描述: 查询平台启用的全局运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 全局运动类型列表
     * @author gjm
     */
    List<SportType> listGlobalSports();

    /**
     * 功能描述: 查询平台全部全局运动类型，包含已停用类型。
     * 创建时间: 2026/09/05
     *
     * @return 平台全局运动类型列表
     * @author gjm
     */
    List<SportType> listPlatformSports();

    /**
     * 功能描述: 查询平台类型与当前场馆私有类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @return 当前场馆可用运动类型
     * @author gjm
     */
    List<SportType> listVenueSports(long venueId);

    /**
     * 功能描述: 创建平台全局运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param name 运动名称
     * @return 新建运动类型
     * @author gjm
     */
    SportType createGlobalSport(String name);

    /**
     * 功能描述: 更新平台全局运动类型启停状态。
     * 创建时间: 2026/09/05
     *
     * @param sportTypeId 运动类型 ID
     * @param status 目标启停状态
     * @return 更新后的运动类型
     * @author gjm
     */
    SportType updateGlobalSportStatus(long sportTypeId, EnableStatus status);

    /**
     * 功能描述: 创建仅属于指定场馆的运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param name 运动名称
     * @return 新建运动类型
     * @author gjm
     */
    SportType createVenueSport(long venueId, String name);

    /**
     * 功能描述: 查询场馆物理场地及其关联运动。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @return 场地列表
     * @author gjm
     */
    List<CourtVO> listCourts(long venueId);

    /**
     * 功能描述: 创建物理场地并保存支持的运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param name 场地名称
     * @param sportIds 运动类型 ID 列表
     * @return 新建场地
     * @author gjm
     */
    Court createCourt(long venueId, String name, List<Long> sportIds);

    /**
     * 功能描述: 更新物理场地、支持运动和启停状态。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param courtId 场地 ID
     * @param name 场地名称
     * @param sportIds 运动类型 ID 列表
     * @param status 启停状态
     * @return 更新后的场地
     * @author gjm
     */
    Court updateCourt(long venueId, long courtId, String name, List<Long> sportIds, EnableStatus status);
}
