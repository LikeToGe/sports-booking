package com.sportsbooking.admin.server.controller.venue;

import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.biz.pojo.request.EnableStatusUpdateRequest;
import com.sportsbooking.admin.biz.pojo.request.NameRequest;
import com.sportsbooking.admin.biz.pojo.request.VenueCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.ServiceFeeRateUpdateRequest;
import com.sportsbooking.admin.biz.pojo.request.VenueStatusUpdateRequest;
import com.sportsbooking.admin.biz.pojo.vo.PlatformVenueVO;
import com.sportsbooking.admin.biz.service.VenueCatalogService;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.server.support.Principals;
import com.sportsbooking.admin.biz.pojo.vo.SportTypeVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 功能描述: 平台管理员维护场馆和全局运动类型的协议入口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@RestController
@RequestMapping("/platform")
public class PlatformVenueController {
    @Autowired
    private VenueService venueService;
    @Autowired
    private VenueCatalogService venueCatalogService;

    /**
     * 功能描述: 查询平台全部场馆。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 平台场馆列表
     * @author gjm
    */
    @GetMapping("/venues/list")
    public ApiResponse<List<PlatformVenueVO>> listVenues() {
        Principals.platform();
        return ApiResponse.success(venueService.listPlatformVenues().stream().map(PlatformVenueVO::from).toList());
    }

    /**
     * 功能描述: 创建场馆和首个场馆管理员。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 场馆创建请求
     * @return 新建场馆
     * @author gjm
    */
    @PostMapping("/venues/create")
    public ApiResponse<VenueVO> createVenue(@RequestBody VenueCreateRequest body) {
        Principals.platform();
        return ApiResponse.success(VenueVO.from(venueService.createVenue(
                body.getVenue(), body.getAdminUsername(), body.getAdminPassword()
        )));
    }

    /**
     * 功能描述: 更新场馆上架状态。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param body 状态更新请求
     * @return 更新后的场馆
     * @author gjm
    */
    @PatchMapping("/venues/{venueId}/status")
    public ApiResponse<VenueVO> updateStatus(@PathVariable long venueId, @RequestBody VenueStatusUpdateRequest body) {
        Principals.platform();
        return ApiResponse.success(VenueVO.from(venueService.updateVenueStatus(venueId, body.getStatus())));
    }

    /**
     * 功能描述: 更新场馆服务费率，仅平台管理员可操作。
     * 创建时间: 2026/09/08
     *
     * @param venueId 场馆 ID
     * @param body 服务费率请求
     * @return 更新后的平台场馆列表项
     * @author gjm
    */
    @PatchMapping("/venues/{venueId}/service-fee-rate")
    public ApiResponse<PlatformVenueVO> updateServiceFeeRate(
            @PathVariable long venueId, @RequestBody ServiceFeeRateUpdateRequest body) {
        Principals.platform();
        return ApiResponse.success(PlatformVenueVO.from(
                venueService.updateServiceFeeRate(venueId, body.getServiceFeeRatePercent())));
    }

    /**
     * 功能描述: 查询首页可筛选的全局运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 平台运动类型列表
     * @author gjm
    */
    @GetMapping("/sports/list")
    public ApiResponse<List<SportTypeVO>> listSports() {
        Principals.platform();
        return ApiResponse.success(venueCatalogService.listPlatformSports().stream().map(SportTypeVO::from).toList());
    }

    /**
     * 功能描述: 创建全局运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 运动名称请求
     * @return 新建运动类型
     * @author gjm
    */
    @PostMapping("/sports/create")
    public ApiResponse<SportTypeVO> createSport(@RequestBody NameRequest body) {
        Principals.platform();
        return ApiResponse.success(SportTypeVO.from(venueCatalogService.createGlobalSport(body.getName())));
    }

    /**
     * 功能描述: 更新平台全局运动类型启停状态。
     * 创建时间: 2026/09/05
     *
     * @param sportTypeId 运动类型 ID
     * @param body 启停状态请求
     * @return 更新后的运动类型
     * @author gjm
     */
    @PatchMapping("/sports/{sportTypeId}/status")
    public ApiResponse<SportTypeVO> updateSportStatus(
            @PathVariable long sportTypeId, @RequestBody EnableStatusUpdateRequest body) {
        Principals.platform();
        return ApiResponse.success(SportTypeVO.from(
                venueCatalogService.updateGlobalSportStatus(sportTypeId, body.getStatus())));
    }
}
