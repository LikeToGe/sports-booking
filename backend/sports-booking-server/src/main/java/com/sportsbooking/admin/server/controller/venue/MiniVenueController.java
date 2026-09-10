package com.sportsbooking.admin.server.controller.venue;

import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.biz.pojo.request.EmployeeBindRequest;
import com.sportsbooking.admin.biz.service.EmployeeService;
import com.sportsbooking.admin.biz.service.VenueCatalogService;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.pojo.vo.EmployeeVO;
import com.sportsbooking.admin.biz.pojo.vo.PublicVenueDetailVO;
import com.sportsbooking.admin.biz.pojo.vo.PublicVenueVO;
import com.sportsbooking.admin.biz.pojo.vo.SportTypeVO;
import com.sportsbooking.admin.server.context.AuthContext;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 功能描述: 小程序查询场馆和绑定员工身份的协议入口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@RestController
@RequestMapping("/mini")
public class MiniVenueController {
    @Autowired
    private VenueService venueService;
    @Autowired
    private VenueCatalogService venueCatalogService;
    @Autowired
    private EmployeeService employeeService;

    /**
     * 功能描述: 查询首页筛选使用的平台运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 平台运动类型列表
     * @author gjm
    */
    @GetMapping("/sports")
    public ApiResponse<List<SportTypeVO>> listSports() {
        return ApiResponse.success(venueCatalogService.listGlobalSports().stream().map(SportTypeVO::from).toList());
    }

    /**
     * 功能描述: 按名称、运动和日期筛选已上架场馆。
     * 创建时间: 2026/08/28 16:29
     *
     * @param keyword 场馆名称关键字
     * @param sportTypeId 平台运动类型 ID
     * @param date 预约日期
     * @return 首页场馆列表
     * @author gjm
     */
    @GetMapping("/venues")
    public ApiResponse<List<PublicVenueVO>> listVenues(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long sportTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(venueService.listPublicVenues(keyword, sportTypeId, date));
    }

    /**
     * 功能描述: 查询场馆资料、场地和开放场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param sportTypeId 运动类型 ID
     * @param date 预约日期
     * @return 场馆公开详情
     * @author gjm
     */
    @GetMapping("/venues/{venueId}")
    public ApiResponse<PublicVenueDetailVO> getVenue(
            @PathVariable long venueId,
            @RequestParam(required = false) Long sportTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(venueService.getPublicVenue(venueId, sportTypeId, date));
    }

    /**
     * 功能描述: 消费一次性绑定码并附加场馆员工身份。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 员工绑定请求
     * @return 不含绑定认证字段的员工身份
     * @author gjm
    */
    @PostMapping("/staff/bind")
    public ApiResponse<EmployeeVO> bindEmployee(@RequestBody EmployeeBindRequest body) {
        return ApiResponse.success(EmployeeVO.from(
                employeeService.bindEmployee(AuthContext.getMiniUserId(), body.getBindingCode())));
    }
}
