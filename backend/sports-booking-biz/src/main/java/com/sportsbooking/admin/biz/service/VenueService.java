package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.enums.VenueStatus;
import com.sportsbooking.admin.biz.pojo.request.VenueSaveRequest;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.biz.pojo.vo.PublicVenueDetailVO;
import com.sportsbooking.admin.biz.pojo.vo.PublicVenueVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 功能描述: 场馆资料、平台建档和公开详情业务接口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface VenueService {

    /**
     * 功能描述: 查询平台全部场馆。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 平台场馆列表
     * @author gjm
     */
    List<Venue> listPlatformVenues();

    /**
     * 功能描述: 创建场馆及其首个管理员。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 场馆资料和预约规则
     * @param adminUsername 首个管理员用户名
     * @param adminPassword 首个管理员密码
     * @return 新建场馆
     * @author gjm
     */
    Venue createVenue(VenueSaveRequest body, String adminUsername, String adminPassword);

    /**
     * 功能描述: 按 ID 查询场馆，不存在时抛出业务异常。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @return 场馆实体
     * @author gjm
     */
    Venue getVenue(long venueId);

    /**
     * 功能描述: 更新场馆资料和预约规则。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param body 场馆资料和预约规则
     * @return 更新后的场馆
     * @author gjm
     */
    Venue updateVenue(long venueId, VenueSaveRequest body);

    /**
     * 功能描述: 更新场馆上架状态。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param status 上架状态
     * @return 更新后的场馆
     * @author gjm
     */
    Venue updateVenueStatus(long venueId, VenueStatus status);

    /**
     * 功能描述: 平台更新场馆当前服务费率。
     * 创建时间: 2026/09/08
     *
     * @param venueId 场馆 ID
     * @param ratePercent 服务费率百分比
     * @return 更新后的场馆
     * @author gjm
     */
    Venue updateServiceFeeRate(long venueId, BigDecimal ratePercent);

    /**
     * 功能描述: 保存场馆封面并返回公开访问路径。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param file 封面图片
     * @return 公开访问路径
     * @author gjm
     */
    String uploadCover(long venueId, MultipartFile file);

    /**
     * 功能描述: 保存当前场馆指定场地的主图。
     * 创建时间: 2026/09/04
     *
     * @param venueId 场馆 ID
     * @param courtId 场地 ID
     * @param file 场地图片
     * @return 更新后的场地
     * @author gjm
     */
    Court uploadCourtImage(long venueId, long courtId, MultipartFile file);

    /**
     * 功能描述: 按运动、日期和名称筛选已上架场馆。
     * 创建时间: 2026/08/28 16:29
     *
     * @param keyword 场馆名称关键字
     * @param sportTypeId 平台运动类型 ID
     * @param date 预约日期
     * @return 首页场馆列表
     * @author gjm
     */
    List<PublicVenueVO> listPublicVenues(String keyword, Long sportTypeId, LocalDate date);

    /**
     * 功能描述: 查询场馆公开资料和指定日期的开放场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param sportTypeId 运动类型 ID
     * @param date 预约日期
     * @return 场馆公开详情
     * @author gjm
     */
    PublicVenueDetailVO getPublicVenue(long venueId, Long sportTypeId, LocalDate date);
}
